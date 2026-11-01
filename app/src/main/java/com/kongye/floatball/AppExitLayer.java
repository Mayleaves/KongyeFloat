package com.kongye.floatball;

import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.ImageView;
import android.widget.FrameLayout;
import java.util.function.BooleanSupplier;

/** A private in-memory frame of our own window, above the live desktop during task handoff.
 * Never captures a display, other windows or secure content; never writes an image file.
 */
final class AppExitLayer {
    private final Activity activity;private final BooleanSupplier handoff;private final Runnable mask,done,fallback;
    private final Handler main=new Handler(Looper.getMainLooper()),copyHandler=new Handler(Looper.getMainLooper());private Bitmap frame;private WindowManager manager;private ImageView view;private FrameLayout host;
    private boolean cancelled,copying,handedOff,masking,finishing;private int width,height;private int originX,originY;
    AppExitLayer(Activity activity,Runnable mask,BooleanSupplier handoff,Runnable done,Runnable fallback){this.activity=activity;this.mask=mask;this.handoff=handoff;this.done=done;this.fallback=fallback;}
    void begin(){
        View decor=activity.getWindow().getDecorView();width=decor.getWidth();height=decor.getHeight();
        if(!Settings.canDrawOverlays(activity)||width<1||height<1||(activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0){fail();return;}
        int[] origin=new int[2];decor.getLocationOnScreen(origin);originX=origin[0];originY=origin[1];
        try{frame=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);}catch(RuntimeException|OutOfMemoryError e){fail();return;}
        copying=true;
        try{PixelCopy.request(activity.getWindow(),frame,result->{copying=false;if(cancelled){release();return;}if(result!=PixelCopy.SUCCESS){try{frame.eraseColor(Color.TRANSPARENT);decor.draw(new Canvas(frame));}catch(RuntimeException e){fail();return;}}show();},copyHandler);}
        catch(RuntimeException e){copying=false;fail();}
    }
    private void show(){
        if(cancelled||activity.isDestroyed()||activity.isFinishing()){cancel();done.run();return;}
        android.content.Context app=activity.getApplicationContext();manager=app.getSystemService(WindowManager.class);view=new ImageView(app);view.setImageBitmap(frame);view.setScaleType(ImageView.ScaleType.FIT_XY);view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // A stationary transparent root clears the previous buffer; only the image child moves.
        host=new FrameLayout(app){@Override protected void onDraw(Canvas canvas){canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);}};
        host.setWillNotDraw(false);host.setBackgroundColor(Color.TRANSPARENT);host.addView(view,new FrameLayout.LayoutParams(-1,-1));
        int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        WindowManager.LayoutParams at=new WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,flags,PixelFormat.TRANSLUCENT);at.gravity=Gravity.TOP|Gravity.LEFT;at.x=originX;at.y=originY;at.windowAnimations=0;at.setTitle("空叶退出过渡");if(Build.VERSION.SDK_INT>=30)at.setFitInsetsTypes(0);if(Build.VERSION.SDK_INT>=28)at.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        try{manager.addView(host,at);}catch(RuntimeException e){fail();return;}
        main.postDelayed(()->{if(!cancelled&&!handedOff)fail();},500);
        afterFrame(this::handoff);
    }
    private void afterFrame(Runnable ready){
        if(cancelled||host==null)return;
        host.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(cancelled||host==null)return true;host.getViewTreeObserver().removeOnPreDrawListener(this);
            if(Build.VERSION.SDK_INT>=29&&host.isHardwareAccelerated())host.getViewTreeObserver().registerFrameCommitCallback(()->main.post(()->{if(!cancelled)ready.run();}));
            else host.postOnAnimation(()->{if(!cancelled)host.postOnAnimation(()->{if(!cancelled)ready.run();});});
            return true;
        }});host.invalidate();
    }
    private void handoff(){
        if(cancelled||handedOff||masking)return;masking=true;mask.run();
        // Let the original windows become hidden before the task's own exit transition starts.
        host.postOnAnimation(()->{if(!cancelled)host.postOnAnimation(()->{
            if(cancelled)return;if(!handoff.getAsBoolean()){fail();return;}handedOff=true;awaitBackdrop(SystemClock.uptimeMillis()+250);
        });});
    }
    private void awaitBackdrop(long deadline){
        if(cancelled||host==null)return;
        if(activity.getWindow().getDecorView().hasWindowFocus()&&SystemClock.uptimeMillis()<deadline){host.postOnAnimation(()->awaitBackdrop(deadline));return;}
        host.postOnAnimation(()->{if(!cancelled)host.postOnAnimation(this::slide);});
    }
    private void slide(){if(cancelled||view==null)return;view.animate().translationY(-height-originY).setDuration(android.animation.ValueAnimator.areAnimatorsEnabled()?650:0).setInterpolator(new android.view.animation.PathInterpolator(.22f,1,.36f,1)).withEndAction(this::finish).start();}
    private void finish(){
        if(cancelled||finishing||host==null)return;finishing=true;view.setVisibility(View.INVISIBLE);
        // Present a completely transparent final buffer before removing its surface.
        afterFrame(()->host.postOnAnimation(()->{if(!cancelled)host.postOnAnimation(()->{
            if(cancelled)return;if((activity.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)Prefs.of(activity).edit().putBoolean("qa_exit_empty_committed",true).putLong("qa_exit_empty_time",SystemClock.uptimeMillis()).apply();cancel();done.run();
        });}));
    }
    private void fail(){cancel();fallback.run();}
    void cancel(){if(cancelled)return;cancelled=true;main.removeCallbacksAndMessages(null);if(view!=null){view.animate().cancel();view.setImageDrawable(null);view=null;}if(host!=null){try{if(host.isAttachedToWindow())manager.removeViewImmediate(host);}catch(IllegalArgumentException ignored){}host=null;}if(!copying)release();}
    private void release(){if(frame!=null){frame.recycle();frame=null;}}
}
