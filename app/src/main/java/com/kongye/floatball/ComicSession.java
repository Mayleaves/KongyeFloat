package com.kongye.floatball;

import android.app.KeyguardManager;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.*;
import com.google.android.gms.tasks.*;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.*;
import java.util.function.BooleanSupplier;

/** User-started browser session. Pixel stability gates OCR and prevents stale page overlays. */
final class ComicSession {
    private final Context context;
    private final BooleanSupplier blocked;
    private final java.util.function.Consumer<Boolean> onLoadingChanged;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable poll=this::capture;
    private final WindowManager windows;
    private final Rect area=new Rect(),screen=new Rect();
    private final LinkedHashMap<String,String> cache=new LinkedHashMap<>();
    private final ArrayList<View> regionViews=new ArrayList<>();
    private ComicOverlayView overlay;
    private FrameLayout panel;
    private TextRecognizer reader;
    private Translator translator;
    private okhttp3.Call call;
    private ComicApiSettings.Config config;
    private boolean active,busy,translating,modelReady,api,ocrRunning,loadingReported;
    private int generation;
    private int[] candidate,displayed;
    private String language,status="",boundPackage="",launcherPackage="";
    private long retryAt,lastScroll;
    private Runnable translationTimeout;
    ComicSession(Context c,BooleanSupplier blocked){this(c,blocked,null);}
    ComicSession(Context c,BooleanSupplier blocked,java.util.function.Consumer<Boolean> onLoadingChanged){context=c;this.blocked=blocked;this.onLoadingChanged=onLoadingChanged;windows=OverlayWindows.get(c);}
    boolean isActive(){return active;}
    private void syncLoading(){
        boolean now=active&&panel==null&&(ocrRunning||translating);
        if(now==loadingReported)return;
        loadingReported=now;
        if(onLoadingChanged!=null){
            if(Looper.myLooper()==Looper.getMainLooper())onLoadingChanged.accept(now);
            else main.post(()->onLoadingChanged.accept(loadingReported));
        }
    }
    void start(){
        if(active){showActions();return;}
        if(Build.VERSION.SDK_INT<30||AssistService.instance==null){Feedback.show(context,"漫画翻译需要 Android 11 或以上，并开启无障碍服务");return;}
        language=Prefs.of(context).getString("comic_source","ja");api="api".equals(Prefs.of(context).getString("comic_engine","offline"));
        try{if(api){config=ComicApiSettings.load(context);config.validate();}}catch(java.io.IOException error){Feedback.show(context,error.getMessage());settings();return;}
        var home=context.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0);
        launcherPackage=home==null?"":home.activityInfo.packageName;
        boundPackage="";active=true;screen.set(bounds());
        reader=switch(language){case "en"->TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);case "ko"->TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());default->TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());};
        overlay=new ComicOverlayView(context);
        try{windows.addView(overlay,window(false,"空叶漫画译文"));}catch(RuntimeException error){stop();Feedback.show(context,"无法显示漫画译文");return;}
        editRegion();
    }
    private Rect bounds(){android.util.DisplayMetrics m=new android.util.DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(m);return new Rect(0,0,m.widthPixels,m.heightPixels);}
    private WindowManager.LayoutParams window(boolean touch,String title){
        int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|(touch?WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL:WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        var p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,flags,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.LEFT;p.setTitle(title);
        if(Build.VERSION.SDK_INT>=30)p.setFitInsetsTypes(0);if(Build.VERSION.SDK_INT>=28)p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        if(!touch)p.alpha=OverlayWindows.enabled(context)&&AssistService.instance!=null?1:.8f;return p;
    }
    private WindowManager.LayoutParams regionWindow(int w,int h,int gravity,int x,int y,boolean touch,String title){
        int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|(touch?WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL:WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        var p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,flags,PixelFormat.TRANSLUCENT);
        p.gravity=gravity;p.x=x;p.y=y;p.setTitle(title);
        if(Build.VERSION.SDK_INT>=30)p.setFitInsetsTypes(0);
        if(Build.VERSION.SDK_INT>=28)p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        if(!touch)p.alpha=.7f;
        return p;
    }
    private void editRegion(){
        invalidate();removePanel();
        FrameLayout root=new FrameLayout(context);
        ResizableCropView crop=new ResizableCropView(context,null,true);
        crop.useLongshotControls();
        Rect initial=area.isEmpty()?new Rect(screen.width()/20,screen.height()/8,screen.width()*19/20,screen.height()*7/8):area;
        RectF liveArea=new RectF(initial);
        crop.setInitial(initial,screen.width(),screen.height());
        root.addView(crop,new FrameLayout.LayoutParams(-1,-1));

        ArrayList<View> handles=new ArrayList<>();
        boolean[] resizing={false};
        Runnable positionHandles=()->{
            if(handles.size()!=5||crop.getWidth()==0||crop.getHeight()==0)return;
            float[][] edges=crop.points();
            float[][] pts={edges[1],edges[3],edges[5],edges[7],{liveArea.centerX(),liveArea.centerY()}};
            int size=dp(48),half=size/2;
            for(int i=0;i<handles.size();i++){
                View handle=handles.get(i);
                WindowManager.LayoutParams p=(WindowManager.LayoutParams)handle.getLayoutParams();
                p.x=Math.max(0,Math.min(screen.width()-size,Math.round(pts[i][0])-half));
                p.y=Math.max(dp(26),Math.min(screen.height()-size,Math.round(pts[i][1])-half));
                try{windows.updateViewLayout(handle,p);}catch(IllegalArgumentException ignored){}
            }
        };
        crop.setChanged(rect->{liveArea.set(rect);if(!resizing[0])positionHandles.run();});

        int[] modes={CropGeometry.TOP,CropGeometry.RIGHT,CropGeometry.BOTTOM,CropGeometry.LEFT,CropGeometry.MOVE};
        String[] labels={"调整上边","调整右边","调整下边","调整左边","移动漫画区域"};
        int handleSize=dp(48);
        for(int i=0;i<modes.length;i++){
            View handle=new View(context);
            handle.setContentDescription(labels[i]);
            handle.setClickable(true);
            if(Build.VERSION.SDK_INT>=29)handle.setSystemGestureExclusionRects(Collections.singletonList(new Rect(0,0,handleSize,handleSize)));
            int mode=modes[i];
            handle.setOnTouchListener(new View.OnTouchListener(){
                float downX,downY;float[] original;
                @Override public boolean onTouch(View view,MotionEvent event){
                    switch(event.getActionMasked()){
                        case MotionEvent.ACTION_DOWN->{resizing[0]=true;downX=event.getRawX();downY=event.getRawY();original=new float[]{liveArea.left,liveArea.top,liveArea.right,liveArea.bottom};return true;}
                        case MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP->{
                            if(original==null)return false;
                            float[] result=CropGeometry.adjust(original,mode,event.getRawX()-downX,event.getRawY()-downY,screen.width(),screen.height(),dp(60));
                            liveArea.set(result[0],result[1],result[2],result[3]);
                            crop.area.set(liveArea);crop.invalidate();
                            if(event.getActionMasked()==MotionEvent.ACTION_UP){resizing[0]=false;positionHandles.run();view.performClick();}
                            return true;
                        }
                        case MotionEvent.ACTION_CANCEL->{resizing[0]=false;positionHandles.run();return true;}
                    }
                    return true;
                }
            });
            handles.add(handle);
        }

        LinearLayout buttons=Ui.row(context);Ui.pad(buttons,8,6,8,6);buttons.setBackground(Ui.bg(Ui.toolbar(),24,context));
        buttons.addView(Ui.tool(context,"close","退出漫画翻译",Color.WHITE,this::stop),new LinearLayout.LayoutParams(dp(48),dp(44)));
        buttons.addView(Ui.text(context,"框选漫画阅读区域",14,Color.WHITE,true));
        buttons.addView(Ui.tool(context,"check","开始漫画翻译",Color.WHITE,()->{int[] origin=new int[2];crop.getLocationOnScreen(origin);Rect r=crop.sourceRect(crop.getWidth(),crop.getHeight());r.offset(origin[0],origin[1]);if(r.width()<dp(60)||r.height()<dp(60))return;area.set(r);removePanel();String pkg=currentPackage();if(eligible(pkg))boundPackage=pkg;status=boundPackage.isEmpty()?"等待打开漫画页面":"画面停稳后自动翻译";schedule(180);}),new LinearLayout.LayoutParams(dp(48),dp(44)));

        panel=root;syncLoading();
        try{
            windows.addView(root,regionWindow(-1,-1,Gravity.TOP|Gravity.LEFT,0,0,false,"空叶漫画选区"));
            for(View handle:handles){
                windows.addView(handle,regionWindow(handleSize,handleSize,Gravity.TOP|Gravity.LEFT,0,0,true,"空叶漫画选区:"+handle.getContentDescription()));
                regionViews.add(handle);
            }
            windows.addView(buttons,regionWindow(-2,-2,Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM,0,dp(48),true,"空叶漫画选区工具栏"));
            regionViews.add(buttons);
            positionHandles.run();
        }catch(RuntimeException error){stop();}
    }
    private boolean ready(){
        if(!active||panel!=null||area.isEmpty()||blocked.getAsBoolean())return false;
        KeyguardManager lock=context.getSystemService(KeyguardManager.class);if(lock!=null&&lock.isKeyguardLocked())return false;
        String pkg=currentPackage();if(!eligible(pkg))return false;
        if(boundPackage.isEmpty())boundPackage=pkg;return boundPackage.equals(pkg);
    }
    private boolean eligible(String pkg){return ComicTargetPolicy.eligible(pkg,context.getPackageName(),launcherPackage);}
    private String currentPackage(){
        AssistService assist=AssistService.instance;
        if(assist!=null){android.view.accessibility.AccessibilityNodeInfo root=assist.getRootInActiveWindow();
            if(root!=null)try{CharSequence pkg=root.getPackageName();if(pkg!=null)return pkg.toString();}finally{root.recycle();}
        }return AssistService.foregroundPackage;
    }
    void screenEvent(AccessibilityEvent event){
        if(!active||panel!=null)return;
        String pkg=event.getPackageName()==null?"":event.getPackageName().toString();
        boolean changed=event.getEventType()==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                &&(event.getContentChangeTypes()&(AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE|AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT))!=0;
        if((event.getEventType()==AccessibilityEvent.TYPE_VIEW_SCROLLED||changed)&&pkg.equals(boundPackage)){
            lastScroll=SystemClock.uptimeMillis();invalidate();schedule(650);
        }else if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&!pkg.equals(context.getPackageName())&&!pkg.equals("com.android.systemui")&&!pkg.equals(boundPackage)){
            invalidate();schedule(650);
        }
    }
    private void schedule(long delay){main.removeCallbacks(poll);if(active&&panel==null)main.postDelayed(poll,delay);}
    private void capture(){
        if(!active)return;if(busy){schedule(650);return;}
        if(!ready()){if(overlay!=null)overlay.clear();displayed=null;schedule(650);return;}
        if(SystemClock.uptimeMillis()-lastScroll<600){schedule(650);return;}
        AssistService assist=AssistService.instance;if(assist==null){stop();return;}
        Rect now=bounds();if(!screen.equals(now)){screen.set(now);area.setEmpty();editRegion();return;}
        busy=true;int token=generation;
        if(assist.captureSubtitleWindow(area,(bitmap,error)->read(bitmap,error,token)))return;
        overlay.setVisibility(View.INVISIBLE);
        main.postDelayed(()->{
            if(!active||token!=generation){busy=false;if(overlay!=null)overlay.setVisibility(View.VISIBLE);schedule(650);return;}
            assist.captureBitmap((bitmap,error)->{Bitmap crop=null;try{if(bitmap!=null)crop=AssistService.cropSubtitle(bitmap,area,screen);}finally{if(bitmap!=null&&bitmap!=crop)bitmap.recycle();if(overlay!=null)overlay.setVisibility(View.VISIBLE);}read(crop,error,token);});
        },60);
    }
    private void read(Bitmap bitmap,String error,int token){
        if(bitmap==null||token!=generation||!ready()){if(bitmap!=null)bitmap.recycle();busy=false;if(error!=null)status=error;schedule(800);return;}
        int[] fingerprint=ComicFrame.fingerprint(bitmap);
        if(!ComicFrame.similar(candidate,fingerprint)){
            candidate=fingerprint;displayed=null;overlay.clear();cancelTranslation();generation++;bitmap.recycle();busy=false;schedule(650);return;
        }
        if(ComicFrame.similar(displayed,fingerprint)||translating||SystemClock.uptimeMillis()<retryAt){bitmap.recycle();busy=false;schedule(650);return;}
        ocrRunning=true;syncLoading();
        try{reader.process(InputImage.fromBitmap(bitmap,0)).addOnCompleteListener(task->{
            try{if(token==generation&&ready()){
                if(!task.isSuccessful()){status="漫画识别失败，正在重试";retryAt=SystemClock.uptimeMillis()+2000;}
                else{ComicFrame frame=ComicFrame.read(task.getResult(),bitmap,language);if(frame.blocks.isEmpty()){displayed=fingerprint;status="未识别到普通气泡文字";}else translate(frame,fingerprint,token);}
            }}catch(RuntimeException failure){status="漫画识别失败，正在重试";retryAt=SystemClock.uptimeMillis()+2000;}
            finally{bitmap.recycle();busy=false;ocrRunning=false;syncLoading();schedule(650);}
        });}catch(RuntimeException failure){bitmap.recycle();busy=false;ocrRunning=false;syncLoading();retryAt=SystemClock.uptimeMillis()+3000;status="漫画识别失败，正在重试";schedule(800);}
    }
    private void translate(ComicFrame frame,int[] fingerprint,int token){
        ArrayList<String> originals=new ArrayList<>();for(var b:frame.blocks)originals.add(b.text());
        ArrayList<String> cached=new ArrayList<>();for(String text:originals)cached.add(cache.get(text));
        if(!cached.contains(null)){complete(frame,cached,fingerprint,token);return;}
        translating=true;status=api?"正在联网翻译":"正在离线翻译";syncLoading();
        translationTimeout=()->{if(active&&token==generation&&translating){generation++;cancelTranslation();failure("翻译准备超时，请检查网络或离线模型");}};main.postDelayed(translationTimeout,api?55000:60000);
        if(api){try{call=ComicApi.translate(config,originals,language,(values,error)->main.post(()->{if(token!=generation||!active)return;call=null;if(error!=null)failure(error);else complete(frame,values,fingerprint,token);}));}
            catch(Exception error){failure("API 配置不可用，请检查设置");}return;}
        if(translator==null)translator=Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(language).setTargetLanguage("zh").build());
        Translator client=translator;
        if(!modelReady){status="首次使用正在准备离线模型，需要 Wi-Fi";
            client.downloadModelIfNeeded(new DownloadConditions.Builder().requireWifi().build()).addOnCompleteListener(task->{
                if(token!=generation||!active)return;
                if(!task.isSuccessful()){failure("模型准备失败，请连接 Wi-Fi 后重试");return;}modelReady=true;offline(client,frame,originals,fingerprint,token);
            });
        }else offline(client,frame,originals,fingerprint,token);
    }
    private void offline(Translator client,ComicFrame frame,List<String> originals,int[] fingerprint,int token){
        ArrayList<Task<String>> jobs=new ArrayList<>();for(String text:originals){String value=cache.get(text);jobs.add(value==null?client.translate(text):Tasks.forResult(value));}
        Tasks.whenAllComplete(jobs).addOnCompleteListener(task->{if(token!=generation||!active)return;
            ArrayList<String> result=new ArrayList<>();for(Task<String> job:jobs){if(!job.isSuccessful()||job.getResult()==null||job.getResult().isBlank()){failure("离线翻译失败，请稍后重试");return;}result.add(job.getResult());}complete(frame,result,fingerprint,token);
        });
    }
    private void complete(ComicFrame frame,List<String> text,int[] fingerprint,int token){
        if(!active||token!=generation)return;translating=false;syncLoading();
        if(translationTimeout!=null)main.removeCallbacks(translationTimeout);translationTimeout=null;
        for(int i=0;i<text.size();i++)cache.put(frame.blocks.get(i).text(),text.get(i));
        while(cache.size()>300)cache.remove(cache.keySet().iterator().next());
        if(ready()&&ComicFrame.similar(candidate,fingerprint)){displayed=fingerprint;overlay.show(frame,text,area);status="已翻译 "+text.size()+" 组文字";}
    }
    private void failure(String message){if(translationTimeout!=null)main.removeCallbacks(translationTimeout);translationTimeout=null;translating=false;syncLoading();status=message;retryAt=SystemClock.uptimeMillis()+10000;Feedback.show(context,message);}
    private void cancelTranslation(){if(translationTimeout!=null)main.removeCallbacks(translationTimeout);translationTimeout=null;if(call!=null){call.cancel();call=null;}translating=false;syncLoading();}
    private void invalidate(){generation++;candidate=null;displayed=null;ocrRunning=false;cancelTranslation();if(overlay!=null)overlay.clear();}
    void showActions(){
        if(!active)return;removePanel();main.removeCallbacks(poll);
        FrameLayout root=new FrameLayout(context);root.setBackgroundColor(0x3308150E);LinearLayout card=Ui.card(context);Ui.pad(card,20,18,20,18);
        LinearLayout head=Ui.row(context);FrameLayout mark=new FrameLayout(context);mark.setBackground(Ui.bg(Ui.PALE,16,context));mark.addView(Ui.icon(context,"comic",Ui.GREEN,26),new FrameLayout.LayoutParams(dp(26),dp(26),Gravity.CENTER));head.addView(mark,new LinearLayout.LayoutParams(dp(50),dp(50)));
        LinearLayout titles=Ui.column(context);Ui.pad(titles,12,0,0,0);titles.addView(Ui.text(context,"漫画翻译",19,Ui.INK,true));Ui.gap(titles,4);String from=switch(language){case "en"->"英语";case "ko"->"韩语";default->"日语";};titles.addView(Ui.text(context,from+" → 中文  ·  "+(api?"API 翻译":"离线翻译"),12,Ui.MUTED,false));head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));head.addView(Ui.tool(context,"close","继续阅读",()->{removePanel();schedule(650);}),new LinearLayout.LayoutParams(dp(40),dp(40)));card.addView(head);Ui.gap(card,18);
        TextView state=Ui.text(context,status.isEmpty()?"画面停稳后自动翻译":status,13,Ui.GREEN,false);Ui.pad(state,14,12,14,12);state.setBackground(Ui.bg(Ui.BG,14,context));card.addView(state,new LinearLayout.LayoutParams(-1,-2));Ui.gap(card,14);
        LinearLayout actions=Ui.row(context);LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(50),1);left.rightMargin=dp(5);actions.addView(Ui.action(context,"position","调整选区","调整阅读区域",this::editRegion),left);LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,dp(50),1);right.leftMargin=dp(5);actions.addView(Ui.action(context,"settings","翻译设置","翻译设置",()->{stop();settings();}),right);card.addView(actions);Ui.gap(card,14);card.addView(Ui.button(context,"退出翻译",true,this::stop),new LinearLayout.LayoutParams(-1,dp(48)));
        root.setOnClickListener(v->{removePanel();schedule(650);});card.setOnClickListener(v->{});root.addView(card,new FrameLayout.LayoutParams(Math.min(screen.width()-dp(28),dp(370)),-2,Gravity.CENTER));panel=root;syncLoading();
        try{windows.addView(root,window(true,"空叶漫画翻译操作"));}catch(RuntimeException error){stop();}
    }
    private void settings(){context.startActivity(new Intent(context,ComicSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    private void removePanel(){
        for(View v:regionViews)try{windows.removeViewImmediate(v);}catch(IllegalArgumentException ignored){}
        regionViews.clear();
        if(panel!=null){try{windows.removeViewImmediate(panel);}catch(IllegalArgumentException ignored){}panel=null;syncLoading();}
    }
    void foregroundChanged(){if(active&&!ready()){invalidate();schedule(650);}}
    void displayChanged(){if(active){invalidate();screen.set(bounds());area.setEmpty();editRegion();}}
    void stop(){active=false;invalidate();main.removeCallbacks(poll);removePanel();if(overlay!=null){try{windows.removeViewImmediate(overlay);}catch(IllegalArgumentException ignored){}overlay=null;}if(reader!=null){reader.close();reader=null;}if(translator!=null){translator.close();translator=null;}cache.clear();modelReady=false;syncLoading();}
    private int dp(float n){return Ui.dp(context,n);}
}
