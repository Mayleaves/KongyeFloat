package com.kongye.floatball;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

final class CaptureController {
    private final Context context;private final Consumer<Boolean> ball;private final CaptureWindow window;
    private final Handler handler=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();private final AutoCaptureController automatic;
    private boolean active,destroyed;private int generation;private Bitmap frozen;private RegionResult result;private StitchSession stitching;
    CaptureController(Context c,Consumer<Boolean> ball){context=c;this.ball=ball;window=new CaptureWindow(c);automatic=new AutoCaptureController(c,ball);}
    boolean isActive(){return active||automatic.isActive();}
    boolean allowsBall(){return result!=null&&result.isPinned();}
    void startWhole(){start(false,new Rect(-1,-1,-1,-1));}
    void startRegion(Rect selected){start(false,new Rect(selected));}
    void start(boolean longShot){start(longShot,null);}
    private void start(boolean longShot,Rect requested){if(isActive()){toast("请先完成当前截图");return;}if(Build.VERSION.SDK_INT<30||AssistService.instance==null){toast("请在 Android 11 或以上开启空叶无障碍服务");return;}active=true;ball.accept(false);int token=++generation;
        if(longShot){stitching=new StitchSession(context,worker,this::done,(image,edit)->adopt(image,"Long",edit));stitching.start();return;}
        handler.postDelayed(()->{if(token==generation&&active&&frozen==null&&result==null){cancel();toast("截图等待超时，请重试");}},8000);
        handler.postDelayed(()->{AssistService service=AssistService.instance;if(service==null){cancel();toast("无障碍服务已断开");return;}try{service.captureBitmap((image,error)->{if(token!=generation){if(image!=null)image.recycle();return;}if(image==null){cancel();toast(error);return;}frozen=image;if(requested!=null&&requested.left==-1){showResult(image,new Rect(0,0,image.getWidth(),image.getHeight()),"Screen");return;}if(requested==null)showRegion(image);else{android.util.DisplayMetrics m=new android.util.DisplayMetrics();context.getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(m);Rect area=new Rect(Math.max(0,requested.left*image.getWidth()/m.widthPixels),Math.max(0,requested.top*image.getHeight()/m.heightPixels),Math.min(image.getWidth(),requested.right*image.getWidth()/m.widthPixels),Math.min(image.getHeight(),requested.bottom*image.getHeight()/m.heightPixels));if(area.width()<32||area.height()<32)showRegion(image);else showResult(image,area);}});}catch(RuntimeException e){cancel();toast("截图服务暂不可用，请重试");}},300);
    }
    void startAutomatic(){if(isActive()){toast("请先完成当前截图");return;}automatic.start(true);}
    private void showRegion(Bitmap image){
        FrameLayout root=window.frame(this::cancel);ResizableCropView crop=new ResizableCropView(context,image,false);root.addView(crop,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout actions=Ui.row(context);Ui.pad(actions,6,6,6,6);actions.setBackground(Ui.bg(Ui.SURFACE,24,context));actions.setElevation(Ui.dp(context,8));LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-2,1);half.setMargins(Ui.dp(context,3),0,Ui.dp(context,3),0);actions.addView(Ui.button(context,"取消",false,this::cancel),half);actions.addView(Ui.button(context,"识别并查看",true,()->{
            Rect area=crop.sourceRect(image.getWidth(),image.getHeight());if(area.width()<32||area.height()<32){toast("请选择更大的区域");return;}
            showResult(image,area);
        }),new LinearLayout.LayoutParams(half));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);p.setMargins(Ui.dp(context,16),Ui.dp(context,44),Ui.dp(context,16),0);root.addView(actions,p);window.show(root,true);
    }
    private void showResult(Bitmap image,Rect area){showResult(image,area,"Region");}
    private void showResult(Bitmap image,Rect area,String kind){
        Bitmap selected;try{selected=Bitmap.createBitmap(image,area.left,area.top,area.width(),area.height());if(selected==image)selected=image.copy(Bitmap.Config.ARGB_8888,false);}catch(RuntimeException|OutOfMemoryError e){toast("截图内存不足，请缩小区域");if(frozen!=null)showRegion(image);return;}
        window.remove();image.recycle();frozen=null;try{result=new RegionResult(context,selected,worker,this::done,pinned->ball.accept(pinned),kind);}catch(RuntimeException|OutOfMemoryError e){selected.recycle();cancel();toast("无法打开截图结果，请重试");}
    }
    private void adopt(Bitmap image,String kind,boolean edit){active=true;stitching=null;ball.accept(false);try{result=new RegionResult(context,image,worker,this::done,pinned->ball.accept(pinned),kind);if(edit)result.openEditor();}catch(RuntimeException|OutOfMemoryError error){image.recycle();cancel();toast("无法显示拼接结果，请重试");}}
    private void done(){active=false;result=null;stitching=null;++generation;ball.accept(true);}
    private void toast(String value){if(!destroyed)Toast.makeText(context,value,Toast.LENGTH_LONG).show();}
    void cancel(){++generation;handler.removeCallbacksAndMessages(null);window.remove();if(frozen!=null){frozen.recycle();frozen=null;}if(result!=null){RegionResult old=result;result=null;old.close();}if(stitching!=null){StitchSession old=stitching;stitching=null;old.close();}automatic.cancel();active=false;ball.accept(true);}
    void destroy(){destroyed=true;cancel();automatic.destroy();worker.shutdown();}
}
