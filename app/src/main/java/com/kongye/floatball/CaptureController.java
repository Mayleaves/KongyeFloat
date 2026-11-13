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
    private boolean active,destroyed,directSave;private int generation;private Bitmap frozen;private RegionResult result;private StitchSession stitching;
    CaptureController(Context c,Consumer<Boolean> ball){context=c;this.ball=ball;window=new CaptureWindow(c);automatic=new AutoCaptureController(c,ball);}
    boolean isActive(){return active||automatic.isActive();}
    boolean allowsBall(){return (result!=null&&result.isPinned())||automatic.allowsBall();}
    void startWhole(){start(false,new Rect(-1,-1,-1,-1));}
    void startRegion(Rect selected){start(false,new Rect(selected));}
    void start(boolean longShot){start(longShot,null);}
    private void start(boolean longShot,Rect requested){start(longShot,requested,false);}
    private void start(boolean longShot,Rect requested,boolean saveOnly){if(isActive()){toast("请先完成当前截图");return;}if(Build.VERSION.SDK_INT<30||AssistService.instance==null){toast("请在 Android 11 或以上开启空叶无障碍服务");return;}active=true;directSave=saveOnly;ball.accept(false);int token=++generation;
        if(longShot){stitching=new StitchSession(context,worker,this::done,(image,edit)->adopt(image,"Long",edit));stitching.start();return;}
        if(requested==null){showLiveRegion();return;}
        handler.postDelayed(()->{if(token==generation&&active&&frozen==null&&result==null){cancel();toast("截图等待超时，请重试");}},8000);
        handler.postDelayed(()->{AssistService service=AssistService.instance;if(service==null){cancel();toast("无障碍服务已断开");return;}try{service.captureBitmap((image,error)->{if(token!=generation){if(image!=null)image.recycle();return;}if(image==null){cancel();toast(error);return;}handler.removeCallbacksAndMessages(null);frozen=image;if(requested!=null&&requested.left==-1){showResult(image,new Rect(0,0,image.getWidth(),image.getHeight()),"Screen");return;}if(requested==null)showRegion(image);else{android.util.DisplayMetrics m=new android.util.DisplayMetrics();context.getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(m);Rect area=new Rect(Math.max(0,requested.left*image.getWidth()/m.widthPixels),Math.max(0,requested.top*image.getHeight()/m.heightPixels),Math.min(image.getWidth(),requested.right*image.getWidth()/m.widthPixels),Math.min(image.getHeight(),requested.bottom*image.getHeight()/m.heightPixels));if(area.width()<32||area.height()<32)showRegion(image);else showResult(image,area);}});}catch(RuntimeException e){cancel();toast("截图服务暂不可用，请重试");}},300);
    }
    void startAutomatic(){if(isActive()){toast("请先完成当前截图");return;}automatic.start(true);}
    private void showLiveRegion(){showRegion(null);}
    private void showRegion(Bitmap image){
        ThemePolicy.apply(context);
        FrameLayout root=window.frame(this::cancel);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        ResizableCropView crop=new ResizableCropView(context,image,false);
        crop.useRegionFrame();
        root.addView(crop,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout actions=Ui.row(context);
        actions.setContentDescription("区域截图跟随工具栏");
        Ui.pad(actions,6,5,6,5);
        android.graphics.drawable.GradientDrawable background=Ui.bg(0xF0182A20,24,context);
        background.setStroke(Math.max(1,Ui.dp(context,.7f)),0x38FFFFFF);
        actions.setBackground(background);
        actions.setElevation(Ui.dp(context,14));
        if(Build.VERSION.SDK_INT>=28){
            actions.setOutlineAmbientShadowColor(0x61000000);
            actions.setOutlineSpotShadowColor(0x61000000);
        }
        LinearLayout cancel=selectionButton("close","取消",false,this::cancel);
        actions.addView(cancel,new LinearLayout.LayoutParams(-2,-1));
        View divider=new View(context);
        divider.setBackgroundColor(0x2EFFFFFF);
        LinearLayout.LayoutParams dividerAt=new LinearLayout.LayoutParams(Math.max(1,Ui.dp(context,.7f)),Ui.dp(context,18));
        dividerAt.setMargins(Ui.dp(context,4),0,Ui.dp(context,4),0);
        actions.addView(divider,dividerAt);
        FrameLayout save=new FrameLayout(context);
        save.setContentDescription("直接保存图片");
        save.setFocusable(true);
        save.setBackground(Ui.ripple(context,Color.TRANSPARENT,18));
        save.addView(Ui.icon(context,"update",0xFFD5E3D7,17),new FrameLayout.LayoutParams(Ui.dp(context,17),Ui.dp(context,17),Gravity.CENTER));
        save.setOnClickListener(v->acceptSelection(crop,image,true));
        actions.addView(save,new LinearLayout.LayoutParams(Ui.dp(context,36),-1));
        LinearLayout confirm=selectionButton("scan_text","识别并查看",true,()->acceptSelection(crop,image,false));
        LinearLayout.LayoutParams confirmAt=new LinearLayout.LayoutParams(-2,-1);
        confirmAt.leftMargin=Ui.dp(context,6);
        actions.addView(confirm,confirmAt);
        root.addView(actions,new FrameLayout.LayoutParams(-2,Ui.dp(context,48),Gravity.TOP|Gravity.LEFT));
        crop.setChanged(area->positionActions(root,crop,actions));
        root.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->positionActions(root,crop,actions));
        window.show(root,true);
    }
    private LinearLayout selectionButton(String icon,String label,boolean primary,Runnable action){
        LinearLayout button=Ui.row(context);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(label);
        button.setFocusable(true);
        Ui.pad(button,primary?16:12,0,primary?16:12,0);
        android.graphics.drawable.GradientDrawable surface=primary
                ?new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{0xFF85A867,0xFF2B6C4C})
                :Ui.bg(Color.TRANSPARENT,18,context);
        surface.setCornerRadius(Ui.dp(context,18));
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22FFFFFF),surface,null));
        button.addView(Ui.icon(context,icon,primary?0xFFF6C945:0xFFD5E3D7,primary?15:14));
        TextView name=Ui.text(context,label,13,primary?Color.WHITE:0xFFD5E3D7,true);
        name.setSingleLine();
        LinearLayout.LayoutParams textAt=new LinearLayout.LayoutParams(-2,-2);
        textAt.leftMargin=Ui.dp(context,6);
        button.addView(name,textAt);
        button.setOnClickListener(v->action.run());
        return button;
    }
    private void positionActions(FrameLayout root,ResizableCropView crop,View actions){
        if(actions.getMeasuredWidth()==0||root.getHeight()==0)return;
        int padding=Ui.dp(context,8);
        int left=padding,right=padding,top=padding,bottom=padding;
        if(Build.VERSION.SDK_INT>=30&&root.getRootWindowInsets()!=null){
            Insets safe=root.getRootWindowInsets().getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            left+=safe.left;right+=safe.right;top+=safe.top;bottom+=safe.bottom;
        }
        float x=Math.max(left,Math.min(root.getWidth()-right-actions.getMeasuredWidth(),crop.area.centerX()-actions.getMeasuredWidth()/2f));
        float y=crop.area.bottom+Ui.dp(context,16);
        if(y+actions.getMeasuredHeight()>root.getHeight()-bottom)y=crop.area.top-Ui.dp(context,16)-actions.getMeasuredHeight();
        y=Math.max(top,Math.min(root.getHeight()-bottom-actions.getMeasuredHeight(),y));
        actions.setX(x);
        actions.setY(y);
    }
    private void acceptSelection(ResizableCropView crop,Bitmap image,boolean saveOnly){
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();
        context.getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(metrics);
        Rect selected=crop.sourceRect(image==null?metrics.widthPixels:image.getWidth(),image==null?metrics.heightPixels:image.getHeight());
        if(selected.width()<32||selected.height()<32){toast("请选择更大的区域");return;}
        if(image!=null){directSave=saveOnly;showResult(image,selected);return;}
        window.visible(false);
        active=false;
        start(false,selected,saveOnly);
    }
    private void showResult(Bitmap image,Rect area){showResult(image,area,"Region");}
    private void showResult(Bitmap image,Rect area,String kind){
        Bitmap selected;try{selected=Bitmap.createBitmap(image,area.left,area.top,area.width(),area.height());if(selected==image)selected=image.copy(Bitmap.Config.ARGB_8888,false);}catch(RuntimeException|OutOfMemoryError e){toast("截图内存不足，请缩小区域");if(frozen!=null)showRegion(image);return;}
        java.util.List<TextRegion> original=java.util.Collections.emptyList();if(!directSave&&kind.equals("Region")&&AssistService.instance!=null){android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();context.getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(metrics);Rect screenArea=new Rect((int)Math.floor(area.left*(double)metrics.widthPixels/image.getWidth()),(int)Math.floor(area.top*(double)metrics.heightPixels/image.getHeight()),(int)Math.ceil(area.right*(double)metrics.widthPixels/image.getWidth()),(int)Math.ceil(area.bottom*(double)metrics.heightPixels/image.getHeight()));original=RegionTextFusion.inCrop(AssistService.instance.textRegionsInRegion(screenArea),metrics.widthPixels,metrics.heightPixels,image.getWidth(),image.getHeight(),area.left,area.top,area.right,area.bottom);}window.remove();image.recycle();frozen=null;if(directSave){saveSelection(selected,kind);return;}try{result=new RegionResult(context,selected,worker,this::done,pinned->ball.accept(pinned),kind,original);}catch(RuntimeException|OutOfMemoryError e){selected.recycle();cancel();toast("无法打开截图结果，请重试");}
    }
    private void saveSelection(Bitmap selected,String kind){
        toast("正在保存图片…");
        int token=generation;
        worker.execute(()->{
            boolean saved;
            try{MediaFiles.saveBitmap(context,selected,kind);saved=true;}
            catch(Exception error){saved=false;}
            boolean success=saved;
            new Handler(Looper.getMainLooper()).post(()->{
                if(token!=generation){selected.recycle();return;}
                if(success){
                    selected.recycle();
                    toast("图片已保存到相册");
                    done();
                }else{
                    adopt(selected,kind,false);
                    toast("保存失败，请检查剩余空间");
                }
            });
        });
    }
    private void adopt(Bitmap image,String kind,boolean edit){active=true;stitching=null;ball.accept(false);try{result=new RegionResult(context,image,worker,this::done,pinned->ball.accept(pinned),kind);if(edit)result.openEditor();}catch(RuntimeException|OutOfMemoryError error){image.recycle();cancel();toast("无法显示拼接结果，请重试");}}
    private void done(){active=false;result=null;stitching=null;++generation;ball.accept(true);}
    private void toast(String value){if(!destroyed)Feedback.show(context,value);}
    void cancel(){++generation;handler.removeCallbacksAndMessages(null);window.remove();if(frozen!=null){frozen.recycle();frozen=null;}if(result!=null){RegionResult old=result;result=null;old.close();}if(stitching!=null){StitchSession old=stitching;stitching=null;old.close();}automatic.cancel();active=false;ball.accept(true);}
    void destroy(){destroyed=true;cancel();automatic.destroy();worker.shutdown();}
}
