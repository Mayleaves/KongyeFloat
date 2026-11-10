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
        Ui.pad(capsule,11,0,18,0);
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
        boolean success=message.contains("已复制")||message.contains("已保存")||message.contains("已追加")||message.contains("已清空")||message.contains("已填入")||message.contains("已生成")||message.contains("已重置")||message.equals("识别完成")||message.equals("翻译完成");
        badge.setBackground(Ui.bg(success?0xFF85A867:0xFFF6C945,12,app));
        badge.addView(new StatusGlyph(app,success),new FrameLayout.LayoutParams(Ui.dp(app,14),Ui.dp(app,14),Gravity.CENTER));
        capsule.addView(badge,new LinearLayout.LayoutParams(Ui.dp(app,24),Ui.dp(app,24)));
        TextView text=Ui.text(app,message,13.5f,0xFFF4F8F2,true);
        text.setLetterSpacing(.015f);
        text.setMaxWidth(Math.max(Ui.dp(app,60),Math.min(Ui.dp(app,348),metrics.widthPixels-Ui.dp(app,40))-Ui.dp(app,62)));
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
            if(animated&&!success){
                android.animation.ObjectAnimator shake=android.animation.ObjectAnimator.ofPropertyValuesHolder(capsule,
                        android.animation.PropertyValuesHolder.ofFloat(View.TRANSLATION_X,0,-Ui.dp(app,6),Ui.dp(app,6),-Ui.dp(app,3),Ui.dp(app,2),0),
                        android.animation.PropertyValuesHolder.ofFloat(View.ROTATION,0,-1,1,0,0,0));
                shake.setDuration(480);
                shake.setStartDelay(60);
                shake.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                capsule.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
                    public void onViewAttachedToWindow(View v){}
                    public void onViewDetachedFromWindow(View v){shake.cancel();}
                });
                shake.start();
            }
            main.postDelayed(()->{
                if(banner!=root)return;
                root.animate().alpha(0).translationY(-Ui.dp(app,8)).setDuration(animated?180:0).withEndAction(()->{
                    if(banner==root){clear();receipt(app,message,false);}
                }).start();
            },2800);
        }catch(RuntimeException failure){clear();Toast.makeText(app,message,Toast.LENGTH_LONG).show();receipt(app,message,false);}
    }
    private static void clear(){if(banner!=null){banner.animate().cancel();if(windows!=null)try{windows.removeViewImmediate(banner);}catch(IllegalArgumentException ignored){}}banner=null;windows=null;}
    private static final class StatusGlyph extends View {
        private final boolean success;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        StatusGlyph(Context context,boolean success){super(context);this.success=success;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){
            canvas.save();
            canvas.scale(getWidth()/24f,getHeight()/24f);
            paint.setColor(success?0xFF162A1E:0xFF233B2C);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(success?2.4f:2.6f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            if(success){
                Path check=new Path();
                check.moveTo(5,13);check.lineTo(9,17);check.lineTo(19,7);
                canvas.drawPath(check,paint);
            }else{
                canvas.drawLine(12,7,12,13,paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(12,17,1.4f,paint);
            }
            canvas.restore();
        }
    }
    private static void receipt(Context c,String message,boolean visible){if((c.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0)Prefs.of(c).edit().putString("qa_feedback_message",message).putBoolean("qa_feedback_visible",visible).putLong("qa_feedback_time",SystemClock.uptimeMillis()).apply();}
}
