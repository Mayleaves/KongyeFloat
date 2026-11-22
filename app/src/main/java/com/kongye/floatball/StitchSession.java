package com.kongye.floatball;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.ExecutorService;

/** Manual capture document: live crop, +, thumbnail, preview, reorder/delete, save. */
final class StitchSession {
    private static final class Part {final int id;final Bitmap image;Part(int id,Bitmap image){this.id=id;this.image=image;}}
    private final java.util.function.BiConsumer<Bitmap,Boolean> completed;private final Context context;private final ExecutorService worker;private final Runnable finished;private final CaptureWindow page;private final WindowManager wm;
    private final Handler main=new Handler(Looper.getMainLooper());private final ArrayList<Part> parts=new ArrayList<>();private final ArrayList<View> liveViews=new ArrayList<>();private final ArrayList<View> handles=new ArrayList<>();
    private ResizableCropView crop;private RectF area;private Bitmap composite,thumbnail;private ImageView thumbnailView;private LinearLayout thumbnailBox;private TextView thumbnailCaption;private FrameLayout done;
    private int width,height,nextId=1,jobs,token;private boolean horizontal,closed,busy,resizing;
    private FrameLayout verticalButton,horizontalButton;private View compactPlus,toolbar;private AutoScrollCapture autoScroll;private int autoAdded;
    private boolean toolsExpanded,liveVisible;
    private final Runnable collapseTools=()->{if(!closed&&liveVisible&&!busy&&!resizing)setToolsExpanded(false);else if(!closed&&toolsExpanded)deferCollapse();};
    StitchSession(Context c,ExecutorService worker,Runnable finished,java.util.function.BiConsumer<Bitmap,Boolean> completed){this.completed=completed;context=c;this.worker=worker;this.finished=finished;wm=OverlayWindows.get(c);page=new CaptureWindow(c);horizontal="horizontal".equals(Prefs.of(c).getString("stitch_axis","vertical"));}
    private int dp(float value){return Ui.dp(context,value);}
    private void toast(String message){if(!closed)Feedback.show(context,message);}
    void start(){DisplayMetrics metrics=new DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(metrics);width=metrics.widthPixels;height=metrics.heightPixels;area=new RectF(dp(4),dp(42),width-dp(4),height-dp(26));showLive();}
    private WindowManager.LayoutParams params(int w,int h,int x,int y,boolean visual){
        int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if(visual)flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,flags,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.LEFT;p.x=x;p.y=y;if(visual)p.alpha=.7f;
        if(Build.VERSION.SDK_INT>=30)p.setFitInsetsTypes(0);if(Build.VERSION.SDK_INT>=28)p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;return p;
    }
    private void addLive(View view,WindowManager.LayoutParams p){p.setTitle("空叶长截图:"+(view.getContentDescription()==null?"工具栏":view.getContentDescription()));wm.addView(view,p);liveViews.add(view);}
    private void clearLive(){
        main.removeCallbacks(collapseTools);liveVisible=false;compactPlus=null;toolbar=null;
        // removeView detaches on a later traversal. Clear the drawable before recycling its pixels.
        if(thumbnailView!=null){thumbnailView.setImageDrawable(null);thumbnailView=null;}
        thumbnailBox=null;thumbnailCaption=null;
        for(View v:liveViews)try{wm.removeView(v);}catch(IllegalArgumentException ignored){}liveViews.clear();handles.clear();crop=null;
    }
    private void visibleLive(boolean visible){liveVisible=visible;updateLiveVisibility();}
    private void updateLiveVisibility(){
        for(View view:liveViews){boolean show=liveVisible;
            if(view==toolbar)show&=toolsExpanded;
            if(view==compactPlus)show&=!toolsExpanded;
            if(view==thumbnailBox)show&=!parts.isEmpty()&&thumbnail!=null;
            view.setVisibility(show?View.VISIBLE:View.INVISIBLE);
        }
    }
    private void deferCollapse(){main.postDelayed(collapseTools,1000);}
    private void setToolsExpanded(boolean expanded){
        toolsExpanded=expanded;main.removeCallbacks(collapseTools);updateLiveVisibility();
        if(expanded&&!parts.isEmpty())main.postDelayed(collapseTools,7000);
    }
    private void showLive(){
        if(closed)return;page.remove();clearLive();recycleComposite();ThemePolicy.apply(context);
        crop=new ResizableCropView(context,null,true);crop.setInitial(new Rect(Math.round(area.left),Math.round(area.top),Math.round(area.right),Math.round(area.bottom)),width,height);crop.setChanged(rect->{area.set(rect);if(!resizing)positionHandles();});addLive(crop,params(-1,-1,0,0,true));
        int[] modes={CropGeometry.LEFT|CropGeometry.TOP,CropGeometry.TOP,CropGeometry.RIGHT|CropGeometry.TOP,CropGeometry.RIGHT,CropGeometry.RIGHT|CropGeometry.BOTTOM,CropGeometry.BOTTOM,CropGeometry.LEFT|CropGeometry.BOTTOM,CropGeometry.LEFT,CropGeometry.MOVE};
        String[] labels={"调整左上角","调整上边","调整右上角","调整右边","调整右下角","调整下边","调整左下角","调整左边","移动截图区域"};
        for(int i=0;i<modes.length;i++){View handle=new View(context);handle.setContentDescription(labels[i]);handle.setClickable(true);if(Build.VERSION.SDK_INT>=29)handle.setSystemGestureExclusionRects(Collections.singletonList(new Rect(0,0,dp(48),dp(48))));int mode=modes[i];handle.setOnTouchListener(new View.OnTouchListener(){float downX,downY;float[] original;public boolean onTouch(View view,MotionEvent event){switch(event.getActionMasked()){case MotionEvent.ACTION_DOWN->{if(busy)return false;resizing=true;downX=event.getRawX();downY=event.getRawY();original=new float[]{area.left,area.top,area.right,area.bottom};return true;}case MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP->{if(original==null)return false;float[] result=CropGeometry.adjust(original,mode,event.getRawX()-downX,event.getRawY()-downY,width,height,dp(40));area.set(result[0],result[1],result[2],result[3]);crop.area.set(area);crop.invalidate();if(event.getActionMasked()==MotionEvent.ACTION_UP){resizing=false;positionHandles();view.performClick();receipt();}return true;}case MotionEvent.ACTION_CANCEL->{resizing=false;positionHandles();return true;}}return true;}});handles.add(handle);addLive(handle,params(dp(48),dp(48),0,0,false));}
        positionHandles();
        done=new FrameLayout(context);
        done.setOnClickListener(v->{if(parts.isEmpty())close();else preview();});
        done.setFocusable(true);
        updateDoneButton();
        addLive(done,params(dp(48),dp(48),dp(12),dp(42),false));
        createToolbar();
        createThumbnail();
        compactPlus=round("plus","添加截图，长按展开长截图工具",Ui.toolbar(),this::capture);compactPlus.setElevation(0);
        compactPlus.setOnLongClickListener(v->{setToolsExpanded(true);return true;});
        WindowManager.LayoutParams plusAt=params(dp(52),dp(52),(width-dp(52))/2,height-dp(200),false);plusAt.alpha=.55f;addLive(compactPlus,plusAt);
        toolsExpanded=parts.isEmpty();updateThumbnail();visibleLive(true);receipt();
    }

    private void createToolbar() {
        LinearLayout bar = Ui.row(context);
        toolbar = bar;
        bar.setContentDescription("手动长截图工具栏");
        Ui.pad(bar, 6, 4, 6, 4);
        bar.setBackground(Ui.bg(Ui.toolbar(), 22, context));

        LinearLayout axes = Ui.row(context);
        verticalButton = axisButton("竖拼接", false);
        horizontalButton = axisButton("横拼接", true);
        axes.addView(verticalButton, new LinearLayout.LayoutParams(0, -1, 1));
        axes.addView(horizontalButton, new LinearLayout.LayoutParams(0, -1, 1));
        bar.addView(axes, new LinearLayout.LayoutParams(0, dp(48), 1));

        FrameLayout add = new FrameLayout(context);
        add.setContentDescription("添加截图，长按收起工具");
        add.setFocusable(true);
        add.setBackground(new android.graphics.drawable.InsetDrawable(
                Ui.ripple(context, Ui.GREEN, 14), dp(8), dp(4), dp(8), dp(4)));
        add.addView(Ui.icon(context, "plus", primaryIconTint(), 24),
                new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        add.setOnClickListener(v -> capture());
        add.setOnLongClickListener(v -> { setToolsExpanded(false); return true; });
        bar.addView(add, new LinearLayout.LayoutParams(dp(58), dp(48)));

        LinearLayout actions = Ui.row(context);
        actions.addView(Ui.tool(context, "auto_longshot", "滚动截图", Color.WHITE, this::startAutoScroll),
                new LinearLayout.LayoutParams(0, -1, 1));
        actions.addView(Ui.tool(context, "close", "取消长截图", Color.WHITE, this::close),
                new LinearLayout.LayoutParams(0, -1, 1));
        bar.addView(actions, new LinearLayout.LayoutParams(0, dp(48), 1));

        int barWidth = Math.min(dp(278), width - dp(24));
        addLive(bar, params(barWidth, dp(56), (width - barWidth) / 2, height - dp(202), false));
    }

    private void createThumbnail() {
        thumbnailBox = Ui.column(context);
        thumbnailBox.setGravity(Gravity.CENTER_HORIZONTAL);
        thumbnailBox.setBackground(Ui.bg(Ui.toolbar(), 14, context));
        Ui.pad(thumbnailBox, 5, 5, 5, 5);
        thumbnailBox.setFocusable(true);
        thumbnailBox.setOnClickListener(v -> composePreview(false));
        thumbnailView = new ImageView(context);
        thumbnailView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Ui.frame(thumbnailView, Color.TRANSPARENT, 5);
        thumbnailView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        thumbnailBox.addView(thumbnailView, new LinearLayout.LayoutParams(dp(48), dp(28)));
        thumbnailCaption = Ui.centeredText(context, "", 11, Color.WHITE, true);
        thumbnailCaption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        thumbnailBox.addView(thumbnailCaption, new LinearLayout.LayoutParams(-1, dp(20)));
        thumbnailBox.setContentDescription("长截图缩略预览");
        addLive(thumbnailBox, params(dp(58), dp(58), width - dp(70), dp(42), false));
    }

    private void updateDoneButton() {
        boolean ready = !parts.isEmpty();
        String label = ready ? "完成截图" : "取消截图";
        if (TextUtils.equals(label, done.getContentDescription())) return;
        done.setContentDescription(label);
        done.setBackground(new android.graphics.drawable.InsetDrawable(
                Ui.ripple(context, ready ? Ui.GREEN : Ui.toolbar(), 14), dp(3)));
        done.removeAllViews();
        done.addView(Ui.icon(context, ready ? "check" : "back", ready ? primaryIconTint() : Color.WHITE, 22),
                new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
    }

    private int primaryIconTint() { return Ui.dark ? 0xFF102E21 : Color.WHITE; }
    /** Round dark button matching the capture toolbar. */
    private FrameLayout round(String icon,String description,int fill,Runnable action){FrameLayout view=new FrameLayout(context);view.setContentDescription(description);view.setBackground(Ui.bg(fill,100,context));view.setElevation(dp(4));view.addView(new Ui.Icon(context,icon,Color.WHITE),new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));view.setOnClickListener(v->action.run());return view;}
    /** Yellow confirm pill, the same accent as the add button. */
    private TextView pill(String title,String description,Runnable action){TextView view=Ui.centeredText(context,title,14,Ui.GREEN,true);view.setContentDescription(description);view.setBackground(Ui.bg(Ui.PALE,22,context));view.setElevation(dp(4));Ui.pad(view,20,0,20,0);view.setOnClickListener(v->action.run());return view;}
    private FrameLayout axisButton(String title, boolean value) {
        FrameLayout button = new FrameLayout(context);
        button.setContentDescription("选择" + title);
        button.setFocusable(true);
        button.setOnClickListener(v -> setAxis(value));
        updateAxis(button, value);
        return button;
    }
    private void positionHandles(){if(crop==null||handles.size()!=9)return;float[][] edges=crop.points();float[][] points=java.util.Arrays.copyOf(edges,9);points[8]=new float[]{area.centerX(),area.centerY()};for(int i=0;i<handles.size();i++){View handle=handles.get(i);WindowManager.LayoutParams p=(WindowManager.LayoutParams)handle.getLayoutParams();p.x=Math.max(0,Math.min(width-dp(48),Math.round(points[i][0])-dp(24)));p.y=Math.max(dp(26),Math.min(height-dp(48),Math.round(points[i][1])-dp(24)));try{wm.updateViewLayout(handle,p);}catch(IllegalArgumentException ignored){}}}
    private void setAxis(boolean value){if(horizontal==value)return;if(busy){toast("请等待当前操作完成");return;}boolean old=horizontal;horizontal=value;try{if(!parts.isEmpty())layout(parts);}catch(IllegalArgumentException e){horizontal=old;toast("该方向图片尺寸过大，请删除部分截图");return;}Prefs.of(context).edit().putString("stitch_axis",horizontal?"horizontal":"vertical").apply();recycleComposite();updateAxis(verticalButton,false);updateAxis(horizontalButton,true);updateThumbnail();receipt();}
    private void updateAxis(FrameLayout box, boolean value) {
        boolean selected = horizontal == value;
        box.setSelected(selected);
        box.setBackground(new android.graphics.drawable.InsetDrawable(
                Ui.ripple(context, selected ? Ui.PALE : 0, 11), dp(3), dp(4), dp(3), dp(4)));
        box.removeAllViews();
        box.addView(Ui.icon(context, value ? "horizontal" : "vertical", selected ? Ui.GREEN : Color.WHITE, 22),
                new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
    }
    private CompositionLayout layout(java.util.List<Part> source){int[] w=new int[source.size()],h=new int[source.size()];for(int i=0;i<source.size();i++){w[i]=source.get(i).image.getWidth();h[i]=source.get(i).image.getHeight();}return CompositionLayout.create(w,h,horizontal);}
    private Bitmap compose(java.util.List<Part> source, int maximum) {
        CompositionLayout plan = layout(source);
        float scale = maximum > 0 ? Math.min(1f, maximum / (float) Math.max(plan.width, plan.height)) : 1;
        Bitmap image = Bitmap.createBitmap(Math.max(1, Math.round(plan.width * scale)),
                Math.max(1, Math.round(plan.height * scale)), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        Rect target = new Rect();
        for (int i = 0; i < source.size(); i++) {
            Bitmap part = source.get(i).image;
            int left = Math.round(plan.left[i] * scale);
            int top = Math.round(plan.top[i] * scale);
            int right = Math.round((plan.left[i] + part.getWidth()) * scale);
            int bottom = Math.round((plan.top[i] + part.getHeight()) * scale);
            target.set(left, top, Math.max(left + 1, right), Math.max(top + 1, bottom));
            canvas.drawBitmap(part, null, target, paint);
        }
        return image;
    }
    private void capture(){
        if(closed||busy)return;if(parts.size()>=20){toast("最多添加 20 张，请编辑或完成");return;}AssistService service=AssistService.instance;if(service==null){toast("请开启空叶无障碍服务");return;}
        setToolsExpanded(false);Feedback.clear();busy=true;int current=++token;RectF chosen=new RectF(area);visibleLive(false);
        main.postDelayed(()->{if(!closed&&busy&&current==token){++token;busy=false;visibleLive(true);toast("截图等待超时，可重试或完成已有截图");}},8000);
        main.postDelayed(()->{if(closed||current!=token)return;AssistService now=AssistService.instance;if(now==null){++token;busy=false;visibleLive(true);toast("无障碍服务已断开");return;}
            try{now.captureBitmap((image,error)->{if(closed||current!=token){if(image!=null)image.recycle();return;}++token;busy=false;visibleLive(true);if(image==null){toast(error);return;}
                Bitmap cropped=null;try{int left=Math.max(0,Math.round(chosen.left*image.getWidth()/width)),top=Math.max(0,Math.round(chosen.top*image.getHeight()/height));int right=Math.min(image.getWidth(),Math.round(chosen.right*image.getWidth()/width)),bottom=Math.min(image.getHeight(),Math.round(chosen.bottom*image.getHeight()/height));if(right-left<32||bottom-top<32)throw new IllegalArgumentException();cropped=Bitmap.createBitmap(image,left,top,right-left,bottom-top);if(cropped==image)cropped=image.copy(Bitmap.Config.ARGB_8888,false);if(cropped.getWidth()>1080){Bitmap smaller=Bitmap.createScaledBitmap(cropped,1080,Math.max(1,cropped.getHeight()*1080/cropped.getWidth()),true);cropped.recycle();cropped=smaller;}Part part=new Part(nextId++,cropped);parts.add(part);try{layout(parts);}catch(IllegalArgumentException e){parts.remove(parts.size()-1);cropped.recycle();cropped=null;toast("长图达到尺寸上限，可删除部分截图或缩小区域");return;}cropped=null;updateThumbnail();receipt();}
                catch(RuntimeException|OutOfMemoryError e){if(cropped!=null&&!cropped.isRecycled())cropped.recycle();toast("无法添加截图，请缩小区域");}finally{image.recycle();}
            });}catch(RuntimeException e){++token;busy=false;visibleLive(true);toast("截图服务暂不可用，请重试");}
        },300);
    }
    private void startAutoScroll(){
        if(closed||busy)return;if(AssistService.instance==null){toast("请开启空叶无障碍服务");return;}if(parts.size()>=20){toast("最多添加 20 张，请预览或整理");return;}if(horizontal){setAxis(false);if(horizontal)return;}Feedback.clear();busy=true;autoAdded=0;visibleLive(false);Rect chosen=new Rect(Math.round(area.left),Math.round(area.top),Math.round(area.right),Math.round(area.bottom));Bitmap baseline=parts.isEmpty()?null:parts.get(parts.size()-1).image;
        autoScroll=new AutoScrollCapture(context,chosen,width,height,baseline,worker,new AutoScrollCapture.Listener(){public boolean append(Bitmap image){if(closed||parts.size()>=20){image.recycle();return false;}Part part=new Part(nextId++,image);parts.add(part);try{layout(parts);}catch(IllegalArgumentException error){parts.remove(parts.size()-1);image.recycle();return false;}autoAdded++;updateThumbnail();visibleLive(false);receipt();return true;}public void finished(String reason){autoScroll=null;if(closed)return;busy=false;visibleLive(true);setToolsExpanded(false);receipt();if(reason!=null)toast(reason);}});receipt();autoScroll.start();
    }
    private void updateThumbnail() {
        updateDoneButton();
        if (parts.isEmpty()) {
            thumbnailView.setImageDrawable(null);
            if (thumbnail != null) thumbnail.recycle();
            thumbnail = null;
            updateLiveVisibility();
            return;
        }
        String caption = parts.size() + " 张";
        thumbnailCaption.setText(caption);
        thumbnailBox.setContentDescription("长截图缩略预览，" + caption + "，点击放大");
        try {
            Bitmap next = compose(parts, 320);
            thumbnailView.setImageBitmap(next);
            if (thumbnail != null) thumbnail.recycle();
            thumbnail = next;

            float scale = Math.min(dp(96) / (float) next.getWidth(), dp(94) / (float) next.getHeight());
            int imageWidth = Math.max(dp(40), Math.round(next.getWidth() * scale));
            int imageHeight = Math.max(dp(24), Math.round(next.getHeight() * scale));
            thumbnailView.setLayoutParams(new LinearLayout.LayoutParams(imageWidth, imageHeight));
            WindowManager.LayoutParams frame = (WindowManager.LayoutParams) thumbnailBox.getLayoutParams();
            frame.width = imageWidth + dp(5) * 2;
            frame.height = imageHeight + dp(20) + dp(5) * 2;
            frame.x = width - dp(12) - frame.width;
            wm.updateViewLayout(thumbnailBox, frame);
        } catch (RuntimeException | OutOfMemoryError error) {
            toast("缩略预览暂不可用，仍可完成保存");
        }
        updateLiveVisibility();
    }
    private void receipt(){StringBuilder ids=new StringBuilder();for(Part part:parts){if(ids.length()>0)ids.append(',');ids.append(part.id);}Prefs.of(context).edit().putInt("manual_count",parts.size()).putString("manual_order",ids.toString()).putString("manual_axis",horizontal?"horizontal":"vertical").putInt("manual_left",Math.round(area.left)).putInt("manual_top",Math.round(area.top)).putInt("manual_right",Math.round(area.right)).putInt("manual_bottom",Math.round(area.bottom)).putBoolean("manual_auto_running",autoScroll!=null).putInt("manual_auto_added",autoAdded).apply();}
    private void preview(){composePreview(true);}
    private void composePreview(boolean finish){
        if(closed||busy)return;if(parts.isEmpty()){toast("请先点击 + 添加截图");return;}busy=true;clearLive();FrameLayout waiting=page.frame(this::close);waiting.setBackgroundColor(Ui.BG);TextView text=Ui.centeredText(context,"正在生成长截图预览…",17,Ui.INK,true);waiting.addView(text,new FrameLayout.LayoutParams(-1,-1));page.show(waiting,true);ArrayList<Part> snapshot=new ArrayList<>(parts);jobs++;
        worker.execute(()->{Bitmap image=null;try{image=compose(snapshot,0);}catch(RuntimeException|OutOfMemoryError ignored){}Bitmap result=image;main.post(()->{jobs--;busy=false;if(closed){if(result!=null)result.recycle();disposeIfReady();return;}if(result==null){toast("预览内存不足，请删除部分截图");showLive();return;}recycleComposite();composite=result;Prefs.of(context).edit().putInt("manual_preview_width",result.getWidth()).putInt("manual_preview_height",result.getHeight()).apply();if(finish)complete(false);else showPeek();});});
    }
    private void complete(boolean edit){if(composite==null||busy)return;Bitmap image=composite;composite=null;close();completed.accept(image,edit);}
    private void showPeek(){FrameLayout root=page.frame(this::showLive);root.setBackgroundColor(0xED101A16);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(-1,-1);ip.setMargins(dp(6),dp(92),dp(6),dp(28));root.addView(new PanZoomImageView(context,composite),ip);LinearLayout top=Ui.row(context);top.addView(round("back","继续截图",Ui.toolbar(),this::showLive),new LinearLayout.LayoutParams(dp(44),dp(44)));top.addView(new View(context),new LinearLayout.LayoutParams(0,1,1));top.addView(round("check","完成拼接",Ui.toolbar(),()->complete(false)),new LinearLayout.LayoutParams(dp(44),dp(44)));FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,dp(48),Gravity.TOP);tp.setMargins(dp(14),dp(40),dp(14),0);root.addView(top,tp);FrameLayout pen=round("reorder","调整截图顺序和删除",Ui.toolbar(),this::edit);FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(dp(56),dp(56),Gravity.BOTTOM|Gravity.RIGHT);pp.setMargins(0,0,dp(18),dp(42));root.addView(pen,pp);page.show(root,true);}
    private void edit(){
        if(closed||busy)return;if(parts.isEmpty()){toast("请先添加截图");return;}clearLive();FrameLayout root=page.frame(()->composePreview(false));root.setBackgroundColor(0xED101A16);LinearLayout body=Ui.column(context);Ui.pad(body,12,40,12,24);LinearLayout heading=Ui.row(context);heading.addView(round("back","返回长图预览",Ui.toolbar(),()->composePreview(false)),new LinearLayout.LayoutParams(dp(44),dp(44)));TextView headingTitle=Ui.text(context,"调整顺序",17,Color.WHITE,true);Ui.pad(headingTitle,12,0,0,0);heading.addView(headingTitle,new LinearLayout.LayoutParams(0,-2,1));heading.addView(round("check","确认拼接",Ui.toolbar(),()->composePreview(true)),new LinearLayout.LayoutParams(dp(44),dp(44)));body.addView(heading);Ui.gap(body,12);TextView tip=Ui.text(context,"长按拖动截图调整顺序，× 删除。",12,0xFFD5DFD6,false);Ui.pad(tip,4,0,0,0);body.addView(tip);Ui.gap(body,12);ScrollView scroll=new ScrollView(context);LinearLayout grid=Ui.column(context);scroll.addView(grid);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        for(int row=0;row<(parts.size()+1)/2;row++){LinearLayout line=Ui.row(context);for(int column=0;column<2;column++){int index=row*2+column;if(index>=parts.size()){line.addView(new View(context),new LinearLayout.LayoutParams(0,1,1));continue;}Part part=parts.get(index);LinearLayout cell=Ui.column(context);cell.setBackgroundColor(Color.TRANSPARENT);Ui.pad(cell,10,4,4,10);LinearLayout actions=Ui.row(context);TextView number=Ui.text(context,"第 "+(index+1)+" 张",13,Color.WHITE,true);Ui.pad(number,2,0,0,0);actions.addView(number,new LinearLayout.LayoutParams(0,-2,1));FrameLayout delete=Ui.tool(context,"close","删除第"+(index+1)+"张",()->remove(part));delete.setBackground(Ui.bg(Ui.PALE,12,context));actions.addView(delete,new LinearLayout.LayoutParams(dp(40),dp(40)));cell.addView(actions);ImageView image=new ImageView(context);image.setImageBitmap(part.image);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackgroundColor(Color.TRANSPARENT);Ui.pad(image,4,4,4,4);LinearLayout.LayoutParams imageAt=new LinearLayout.LayoutParams(-1,dp(150));imageAt.rightMargin=dp(6);image.setContentDescription("截图第"+(index+1)+"张，长按拖动排序");image.setOnLongClickListener(v->{v.startDragAndDrop(ClipData.newPlainText("空叶截图顺序",""),new View.DragShadowBuilder(v),part,0);return true;});cell.addView(image,imageAt);cell.setOnDragListener((v,event)->{if(!(event.getLocalState() instanceof Part dragged))return false;if(event.getAction()==DragEvent.ACTION_DROP){CompositionLayout.move(parts,parts.indexOf(dragged),parts.indexOf(part));changed();main.postDelayed(this::edit,100);}return true;});LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMargins(dp(3),0,dp(3),dp(8));line.addView(cell,cp);}grid.addView(line);}
        root.addView(body,new FrameLayout.LayoutParams(-1,-1));page.show(root,true);
    }
    private void remove(Part part){page.remove();parts.remove(part);part.image.recycle();changed();if(parts.isEmpty())showLive();else edit();}
    private void changed(){recycleComposite();receipt();}
    private void recycleComposite(){if(composite!=null){composite.recycle();composite=null;}}
    void close(){if(closed)return;closed=true;if(autoScroll!=null){autoScroll.stop(null);autoScroll=null;}++token;main.removeCallbacksAndMessages(null);clearLive();page.remove();disposeIfReady();finished.run();}
    private void disposeIfReady(){if(!closed||jobs!=0)return;for(Part part:parts)part.image.recycle();parts.clear();recycleComposite();if(thumbnail!=null){thumbnail.recycle();thumbnail=null;}}
}
