package com.kongye.floatball;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Visible above this app's enhanced overlays; never consumes touches. */
final class Feedback {
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static View banner;private static WindowManager windows;
    static void show(Context context,String message){
        Context app=context.getApplicationContext();
        if(Looper.myLooper()!=Looper.getMainLooper()){main.post(()->show(app,message));return;}
        clear();
        windows=OverlayWindows.get(app);
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();
        windows.getDefaultDisplay().getRealMetrics(metrics);
        FrameLayout root=new FrameLayout(app);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        Ui.pad(root,12,12,12,16);
        root.setContentDescription("操作反馈："+message);
        LinearLayout capsule=Ui.row(app);
        Ui.pad(capsule,12,0,18,0);
        capsule.setMinimumHeight(Ui.dp(app,40));
        android.graphics.drawable.GradientDrawable surface=Ui.bg(0xF21E3529,99,app);
        surface.setStroke(Math.max(1,Ui.dp(app,.7f)),0x33FFFFFF);
        capsule.setBackground(surface);
        capsule.setElevation(Ui.dp(app,10));
        if(Build.VERSION.SDK_INT>=28){
            capsule.setOutlineAmbientShadowColor(0x47193424);
            capsule.setOutlineSpotShadowColor(0x47193424);
        }
        FrameLayout badge=new FrameLayout(app);
        badge.setBackground(Ui.bg(0xFF85A867,11,app));
        boolean success=message.contains("已复制")||message.contains("已保存")||message.equals("识别完成");
        badge.addView(Ui.icon(app,success?"check":"info",0xFF162A1E,13),new FrameLayout.LayoutParams(Ui.dp(app,13),Ui.dp(app,13),Gravity.CENTER));
        capsule.addView(badge,new LinearLayout.LayoutParams(Ui.dp(app,22),Ui.dp(app,22)));
        TextView text=Ui.text(app,message,13.5f,0xFFF4F8F2,true);
        text.setLetterSpacing(.025f);
        text.setMaxWidth(Math.max(Ui.dp(app,60),Math.min(Ui.dp(app,340),metrics.widthPixels-Ui.dp(app,40))-Ui.dp(app,61)));
        text.setMaxLines(3);
        Ui.pad(text,0,9,0,9);
        LinearLayout.LayoutParams labelAt=new LinearLayout.LayoutParams(-2,-2);
        labelAt.leftMargin=Ui.dp(app,9);
        capsule.addView(text,labelAt);
        root.addView(capsule,new FrameLayout.LayoutParams(-2,-2));
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
        p.y=Ui.dp(app,28);
        p.setTitle("空叶操作反馈");
        if(Build.VERSION.SDK_INT>=30)p.setFitInsetsTypes(0);
        try{
            windows.addView(root,p);
            banner=root;
            text.announceForAccessibility(message);
            receipt(app,message,true);
            boolean animated=android.animation.ValueAnimator.areAnimatorsEnabled();
            root.setAlpha(animated?0:1);
            root.setScaleX(animated?.88f:1);
            root.setScaleY(animated?.88f:1);
            root.setTranslationY(animated?-Ui.dp(app,18):0);
            root.animate().alpha(1).translationY(0).scaleX(1).scaleY(1).setDuration(animated?360:0).setInterpolator(new android.view.animation.OvershootInterpolator(.7f)).start();
            main.postDelayed(()->{
                if(banner!=root)return;
                root.animate().alpha(0).translationY(-Ui.dp(app,8)).setDuration(animated?180:0).withEndAction(()->{
                    if(banner==root){clear();receipt(app,message,false);}
                }).start();
            },2800);
        }catch(RuntimeException failure){clear();Toast.makeText(app,message,Toast.LENGTH_LONG).show();receipt(app,message,false);}
    }
    private static void clear(){if(banner!=null){banner.animate().cancel();if(windows!=null)try{windows.removeViewImmediate(banner);}catch(IllegalArgumentException ignored){}}banner=null;windows=null;}
    private static void receipt(Context c,String message,boolean visible){if((c.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0)Prefs.of(c).edit().putString("qa_feedback_message",message).putBoolean("qa_feedback_visible",visible).putLong("qa_feedback_time",SystemClock.uptimeMillis()).apply();}
}
