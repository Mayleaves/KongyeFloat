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
    static void show(Context context,String message){Context app=context.getApplicationContext();if(Looper.myLooper()!=Looper.getMainLooper()){main.post(()->show(app,message));return;}clear();TextView text=Ui.centeredText(app,message,14,Color.WHITE,true);Ui.pad(text,20,12,20,12);text.setBackground(Ui.bg(Ui.toolbar(),22,app));text.setContentDescription("操作反馈："+message);text.setMaxLines(3);text.setElevation(Ui.dp(app,8));android.util.DisplayMetrics metrics=app.getResources().getDisplayMetrics();WindowManager.LayoutParams p=new WindowManager.LayoutParams(Math.min(metrics.widthPixels-Ui.dp(app,32),Ui.dp(app,340)),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;p.y=Ui.dp(app,82);p.alpha=.94f;p.setTitle("空叶操作反馈");if(Build.VERSION.SDK_INT>=30)p.setFitInsetsTypes(0);try{windows=OverlayWindows.get(app);windows.addView(text,p);banner=text;text.announceForAccessibility(message);receipt(app,message,true);main.postDelayed(()->{if(banner==text){clear();receipt(app,message,false);}},2800);}catch(RuntimeException failure){windows=null;Toast.makeText(app,message,Toast.LENGTH_LONG).show();receipt(app,message,false);}}
    private static void clear(){if(banner!=null&&windows!=null)try{windows.removeViewImmediate(banner);}catch(IllegalArgumentException ignored){}banner=null;windows=null;}
    private static void receipt(Context c,String message,boolean visible){if((c.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0)Prefs.of(c).edit().putString("qa_feedback_message",message).putBoolean("qa_feedback_visible",visible).putLong("qa_feedback_time",SystemClock.uptimeMillis()).apply();}
}
