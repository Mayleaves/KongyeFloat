package com.kongye.floatball;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** User-driven local region capture and scroll stitching. Owns only its own overlay windows. */
final class AutoCaptureController {
    private final Context context;private final WindowManager wm;private final Consumer<Boolean> showBall;
    private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private View overlay;private boolean active,automatic,working,finishRequested;private int generation,operation;
    private Rect area;private final ArrayList<Bitmap> frames=new ArrayList<>();private final ArrayList<Integer> shifts=new ArrayList<>();
    private Bitmap pending;private int outputHeight;private TextView status;private boolean alive=true;
    private Bitmap selectionImage;
    AutoCaptureController(Context c,Consumer<Boolean> showBall){context=c;wm=OverlayWindows.get(c);this.showBall=showBall;}
    boolean isActive(){return active;}
    private int dp(float n){return Ui.dp(context,n);}
    private LinearLayout.LayoutParams half(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),0,dp(3),0);return p;}
    private void toast(String value){if(alive)Feedback.show(context,value);}
    void start(boolean longShot){
        if(active){toast("请先完成或取消当前截图");return;}
        if(Build.VERSION.SDK_INT<30||AssistService.instance==null){toast("请在 Android 11 或以上开启空叶无障碍服务");return;}
        active=true;int token=++generation;showBall.accept(false);
        main.postDelayed(()->{
            AssistService service=AssistService.instance;if(service==null){cancel();toast("无障碍服务已断开");return;}
            Rect scroll=longShot?service.scrollableBounds():null;
            service.captureBitmap((image,error)->{if(token!=generation){if(image!=null)image.recycle();return;}if(image==null){cancel();toast(error);return;}select(image,longShot,scroll);});
        },350);
    }
    private void addOverlay(View view,boolean full){
        removeOverlay();overlay=view;int width=Math.min(context.getResources().getDisplayMetrics().widthPixels-dp(32),dp(420));WindowManager.LayoutParams p=new WindowManager.LayoutParams(full?-1:width,full?-1:-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        p.gravity=full?Gravity.TOP|Gravity.LEFT:Gravity.TOP|Gravity.CENTER_HORIZONTAL;p.y=full?0:dp(44);
        if(Build.VERSION.SDK_INT>=30&&full)p.setFitInsetsTypes(0);
        if(Build.VERSION.SDK_INT>=28)p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        try{wm.addView(view,p);}catch(RuntimeException e){overlay=null;cancel();toast("无法打开截图窗口");}
    }
    private void removeOverlay(){if(overlay!=null){try{wm.removeView(overlay);}catch(IllegalArgumentException ignored){}overlay=null;}}
    private void select(Bitmap image,boolean longShot,Rect suggestion){
        selectionImage=image;
        FrameLayout root=new FrameLayout(context){@Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getKeyCode()==KeyEvent.KEYCODE_BACK){if(e.getAction()==KeyEvent.ACTION_UP){if(!image.isRecycled())image.recycle();cancel();}return true;}return super.dispatchKeyEvent(e);}};root.setFocusableInTouchMode(true);
        Selection selection=new Selection(context,image);root.addView(selection,new FrameLayout.LayoutParams(-1,-1));
        if(suggestion!=null&&suggestion.width()>50&&suggestion.height()>100)selection.chosen.set(suggestion);
        LinearLayout bar=Ui.column(context);Ui.pad(bar,10,14,10,10);bar.setBackground(Ui.bg(Ui.SURFACE,24,context));bar.setElevation(dp(8));TextView prompt=Ui.text(context,longShot?"框选可滚动的正文区域":"拖动框选要保存的区域",15,Ui.INK,true);Ui.pad(prompt,6,0,6,0);bar.addView(prompt);Ui.gap(bar,12);
        LinearLayout buttons=Ui.row(context);TextView cancel=Ui.button(context,"取消",false,()->{removeOverlay();if(!image.isRecycled())image.recycle();cancel();});buttons.addView(cancel,half());
        TextView confirm=Ui.button(context,longShot?"开始长截图":"保存区域",true,()->{
            Rect chosen=selection.sourceRect();if(chosen.width()<32||chosen.height()<32){toast("请拖动选择更大的区域");return;}
            removeOverlay();
            selectionImage=null;
            try{if(longShot){area=chosen;Bitmap cropped=crop(image,area,true);image.recycle();frames.add(cropped);shifts.add(0);outputHeight=cropped.getHeight();Prefs.of(context).edit().putInt("last_long_frame_count",1).apply();showControls("已截取第 1 屏，选择继续或自动拼接");}
                else{Bitmap cropped=crop(image,chosen,false);if(cropped!=image)image.recycle();finishBitmap(cropped,"Region");}}
            catch(RuntimeException|OutOfMemoryError e){if(!image.isRecycled())image.recycle();cancel();toast("截图内存不足，请缩小区域");}
        });LinearLayout.LayoutParams b=half();buttons.addView(confirm,b);bar.addView(buttons);
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);position.setMargins(dp(16),dp(44),dp(16),0);root.addView(bar,position);addOverlay(root,true);root.requestFocus();
    }
    private Bitmap crop(Bitmap source,Rect bounds,boolean scale){
        Rect safe=new Rect(bounds);safe.intersect(0,0,source.getWidth(),source.getHeight());Bitmap cropped=Bitmap.createBitmap(source,safe.left,safe.top,safe.width(),safe.height());
        if(scale&&cropped.getWidth()>720){Bitmap resized=Bitmap.createScaledBitmap(cropped,720,Math.max(1,cropped.getHeight()*720/cropped.getWidth()),true);if(cropped!=source)cropped.recycle();return resized;}
        if(cropped==source)return cropped.copy(Bitmap.Config.ARGB_8888,false);return cropped;
    }
    private void showControls(String message){
        if(finishRequested&&!working){finishRequested=false;finishLong();return;}
        LinearLayout controls=Ui.column(context);Ui.pad(controls,10,12,10,10);controls.setBackground(Ui.bg(Ui.SURFACE,24,context));controls.setElevation(dp(8));status=Ui.text(context,message,13,Ui.INK,false);Ui.pad(status,6,0,6,0);status.setLineSpacing(dp(2),1);status.setMinLines(2);status.setMaxLines(2);status.setEllipsize(android.text.TextUtils.TruncateAt.END);controls.addView(status);Ui.gap(controls,8);
        LinearLayout row=Ui.row(context);
        row.addView(Ui.button(context,pending==null?"继续一屏":"整屏追加",false,()->{automatic=false;if(pending!=null){if((long)(outputHeight+pending.getHeight())*pending.getWidth()>12000000){pending.recycle();pending=null;showControls("已达长度上限，请完成保存");return;}append(pending,pending.getHeight());pending=null;showControls("已按整屏追加；可继续或完成");}else next();}),half());
        row.addView(Ui.button(context,automatic?"停止自动":"自动拼接",false,()->{automatic=!automatic;if(automatic)next();else showControls("自动已停止，可完成保存");}),half());
        controls.addView(row);Ui.gap(controls,8);LinearLayout second=Ui.row(context);TextView finish=Ui.button(context,"完成保存",true,this::finishLong);finish.setOnTouchListener((view,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN)view.performClick();return true;});second.addView(finish,half());TextView cancel=Ui.button(context,"取消",false,this::cancel);cancel.setOnTouchListener((view,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN)view.performClick();return true;});second.addView(cancel,half());controls.addView(second);addOverlay(controls,false);
    }
    private void next(){
        if(working||!active)return;if(pending!=null){toast("请先整屏追加或完成保存");automatic=false;return;}
        if(frames.size()>=10){automatic=false;showControls("已达到 10 屏上限，请完成保存");return;}
        AssistService service=AssistService.instance;if(service==null){automatic=false;showControls("无障碍已断开，可保存已截部分");return;}
        working=true;int token=generation;int step=++operation;main.postDelayed(()->{if(token==generation&&step==operation&&working){operation++;working=false;automatic=false;showControls("滚动或截图超时，可保存已截部分或继续");}},6000);if(status!=null)status.setText("正在滚动，可随时完成或取消");
        service.scrollPage(area,()->main.postDelayed(()->{
            if(token!=generation||step!=operation)return;AssistService current=AssistService.instance;if(current==null){working=false;automatic=false;showControls("无障碍已断开，可完成保存");return;}
            if(overlay!=null)overlay.setVisibility(View.INVISIBLE);
            main.postDelayed(()->{
            if(token!=generation||step!=operation)return;
            current.captureBitmap((image,error)->{
                if(token!=generation||step!=operation){if(image!=null)image.recycle();return;}
                if(overlay!=null)overlay.setVisibility(View.VISIBLE);
                if(image==null){working=false;automatic=false;showControls(error+"；可保存已截部分");return;}
                if(area.right>image.getWidth()||area.bottom>image.getHeight()){image.recycle();working=false;automatic=false;showControls("屏幕方向变化，请保存并重新截图");return;}
                Bitmap frame;
                try{frame=crop(image,area,true);image.recycle();Bitmap previous=frames.get(frames.size()-1);int w=frame.getWidth(),h=frame.getHeight();if(w!=previous.getWidth()||h!=previous.getHeight()){frame.recycle();working=false;automatic=false;showControls("画面尺寸变化，请完成保存");return;}int[] oldPixels=new int[w*h],newPixels=new int[w*h];previous.getPixels(oldPixels,0,w,0,0,w,h);frame.getPixels(newPixels,0,w,0,0,w,h);
                    worker.execute(()->{int shift=FrameStitcher.findShift(oldPixels,newPixels,w,h);main.post(()->{if(token!=generation||step!=operation){frame.recycle();return;}working=false;if(shift==0){frame.recycle();automatic=false;showControls("已到页面底部或画面未变化，请完成保存");}else if(shift<0){pending=frame;automatic=false;showControls("无法可靠对齐：可整屏追加或完成保存");}else if((long)(outputHeight+shift)*w>12000000){frame.recycle();automatic=false;showControls("已达图片长度上限，请完成保存");}else{append(frame,shift);showControls("已拼接 "+frames.size()+" 屏");if(automatic)main.postDelayed(this::next,500);}});});
                }catch(RuntimeException|OutOfMemoryError e){if(!image.isRecycled())image.recycle();working=false;automatic=false;showControls("内存不足，请保存已截部分");}
            });
            },220);
        },120));
    }
    private void append(Bitmap frame,int shift){Prefs.of(context).edit().putInt("last_long_frame_count",frames.size()+1).putInt("last_long_shift",shift).putInt("last_long_source_width",area.width()).putInt("last_long_output_width",frame.getWidth()).apply();frames.add(frame);shifts.add(shift);outputHeight+=shift;}
    private void finishLong(){
        if(working){finishRequested=true;automatic=false;if(status!=null)status.setText("本屏处理完后立即保存");return;}if(frames.isEmpty()){cancel();return;}
        ArrayList<Bitmap> owned=new ArrayList<>(frames);ArrayList<Integer> offsets=new ArrayList<>(shifts);int height=outputHeight,width=owned.get(0).getWidth();frames.clear();shifts.clear();if(pending!=null){pending.recycle();pending=null;}removeOverlay();active=false;automatic=false;++generation;showBall.accept(true);
        worker.execute(()->{Bitmap result=null;String message;try{result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(result);int y=0;for(int i=0;i<owned.size();i++){Bitmap part=owned.get(i);int amount=i==0?part.getHeight():offsets.get(i);canvas.drawBitmap(part,new Rect(0,part.getHeight()-amount,width,part.getHeight()),new Rect(0,y,width,y+amount),null);y+=amount;}MediaFiles.saveBitmap(context,result,"Long");message="长截图已保存到相册 Pictures/Kongye";}catch(Exception|OutOfMemoryError e){message="长截图保存失败，请检查剩余空间";}finally{for(Bitmap part:owned)part.recycle();if(result!=null)result.recycle();}String value=message;main.post(()->toast(value));});
    }
    private void finishBitmap(Bitmap image,String prefix){removeOverlay();active=false;++generation;showBall.accept(true);worker.execute(()->{String message;try{MediaFiles.saveBitmap(context,image,prefix);message="区域截图已保存到相册 Pictures/Kongye";}catch(Exception e){message="保存失败，请检查剩余空间";}finally{image.recycle();}String value=message;main.post(()->toast(value));});}
    void cancel(){++generation;main.removeCallbacksAndMessages(null);removeOverlay();if(selectionImage!=null&&!selectionImage.isRecycled())selectionImage.recycle();selectionImage=null;for(Bitmap bitmap:frames)bitmap.recycle();frames.clear();shifts.clear();if(pending!=null){pending.recycle();pending=null;}active=false;working=false;automatic=false;finishRequested=false;showBall.accept(true);}
    void destroy(){alive=false;cancel();worker.shutdown();}
    private static class Selection extends View {
        final Bitmap image;final Paint paint=new Paint(3);final RectF chosen=new RectF();float downX,downY;
        Selection(Context c,Bitmap image){super(c);this.image=image;setContentDescription("拖动选择截图区域");}
        Rect sourceRect(){float sx=image.getWidth()/(float)Math.max(1,getWidth()),sy=image.getHeight()/(float)Math.max(1,getHeight());return new Rect(Math.max(0,(int)(chosen.left*sx)),Math.max(0,(int)(chosen.top*sy)),Math.min(image.getWidth(),(int)(chosen.right*sx)),Math.min(image.getHeight(),(int)(chosen.bottom*sy)));}
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){if(chosen.isEmpty())chosen.set(w*.05f,h*.24f,w*.95f,h*.86f);else if(oldW==0){chosen.left*=w/(float)image.getWidth();chosen.right*=w/(float)image.getWidth();chosen.top*=h/(float)image.getHeight();chosen.bottom*=h/(float)image.getHeight();}}
        @Override protected void onDraw(Canvas canvas){if(image.isRecycled())return;canvas.drawBitmap(image,null,new Rect(0,0,getWidth(),getHeight()),paint);paint.setColor(0x99000000);paint.setStyle(Paint.Style.FILL);canvas.drawRect(0,0,getWidth(),chosen.top,paint);canvas.drawRect(0,chosen.bottom,getWidth(),getHeight(),paint);canvas.drawRect(0,chosen.top,chosen.left,chosen.bottom,paint);canvas.drawRect(chosen.right,chosen.top,getWidth(),chosen.bottom,paint);paint.setColor(0xFF83CCA8);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);canvas.drawRect(chosen,paint);paint.setStyle(Paint.Style.FILL);paint.setTextSize(Ui.dp(getContext(),13));canvas.drawText((int)chosen.width()+" × "+(int)chosen.height(),chosen.left+12,chosen.bottom-14,paint);}
        @Override public boolean onTouchEvent(MotionEvent e){float x=Math.max(0,Math.min(getWidth(),e.getX())),y=Math.max(0,Math.min(getHeight(),e.getY()));if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=x;downY=y;chosen.set(x,y,x,y);}else if(e.getActionMasked()==MotionEvent.ACTION_MOVE||e.getActionMasked()==MotionEvent.ACTION_UP){chosen.set(Math.min(x,downX),Math.min(y,downY),Math.max(x,downX),Math.max(y,downY));if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();}invalidate();return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
