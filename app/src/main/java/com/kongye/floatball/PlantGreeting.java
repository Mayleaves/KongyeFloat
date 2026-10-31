package com.kongye.floatball;

import android.animation.ValueAnimator;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import java.util.Calendar;

/** apk.html's 2.1-second greeting, anchored beside the perched plant. */
@android.annotation.SuppressLint("AppCompatCustomView") // This framework-only Activity does not use AppCompat.
final class PlantGreeting extends TextView {
    static final String[] LINES={"🌱 早安！新芽正在努力钻出泥土~","🌿 上午好，小叶正在舒展筋骨~","☀️ 午后阳光真暖，光合作用满分！","🌷 花骨朵鼓鼓的，马上就要开花啦~","🌸 晚风轻拂，小花正在温柔呼吸~","💤 嘘…夜深了，小叶正在打瞌睡~"};
    private final View anchor;
    private final java.util.Random random=new java.util.Random();
    private boolean scheduled;
    private long nextGreetingAt;
    private ValueAnimator motion;
    private final Runnable occasional=new Runnable(){public void run(){scheduled=false;if(anchor!=null&&anchor.isShown()&&getWindowVisibility()==VISIBLE)showGreeting();schedule(35000,90000);}};
    PlantGreeting(Context c,View anchor){super(c);this.anchor=anchor;setTextSize(11);setTextColor(0xFFFFFFFF);setBackground(Ui.bg(0xFF23533D,12,c));Ui.pad(this,10,4,10,4);setElevation(Ui.dp(c,3));setSingleLine();setEllipsize(TextUtils.TruncateAt.END);setMaxWidth(c.getResources().getDisplayMetrics().widthPixels-Ui.dp(c,110));setAlpha(0);setVisibility(INVISIBLE);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
    private void schedule(int min,int max){if(scheduled||getWindowVisibility()!=VISIBLE)return;int delay=min+random.nextInt(max-min+1);scheduled=true;nextGreetingAt=android.os.SystemClock.uptimeMillis()+delay;postDelayed(occasional,delay);}
    void maybeGreet(){schedule(8000,24000);}
    void showGreeting(){
        if(motion!=null)motion.cancel();setText(LINES[PlantCycle.stage(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))]);setVisibility(VISIBLE);setPivotX(getWidth());setPivotY(getHeight());
        motion=ValueAnimator.ofFloat(0,1);motion.setDuration(2100);motion.setInterpolator(new android.view.animation.LinearInterpolator());motion.addUpdateListener(a->{float t=(float)a.getAnimatedValue(),alpha,scale,y;if(t<.18f){float u=t/.18f;u=1-(float)Math.pow(1-u,3);alpha=u;scale=.85f+.15f*u;y=6*(1-u);}else if(t<.82f){alpha=1;scale=1;y=0;}else{float u=(t-.82f)/.18f;alpha=1-u;scale=1-.08f*u;y=-5*u;}setAlpha(alpha);setScaleX(scale);setScaleY(scale);setTranslationY(Ui.dp(getContext(),y));});
        motion.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){if(a==motion){setAlpha(0);setVisibility(INVISIBLE);}}});
        if(ValueAnimator.areAnimatorsEnabled())motion.start();else{setAlpha(1);setScaleX(1);setScaleY(1);postDelayed(()->{setAlpha(0);setVisibility(INVISIBLE);},2100);}
    }
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(occasional==null)return;if(visibility==VISIBLE)maybeGreet();else{removeCallbacks(occasional);scheduled=false;if(motion!=null)motion.cancel();setAlpha(0);setVisibility(INVISIBLE);}}
    @Override protected void onDetachedFromWindow(){removeCallbacks(occasional);scheduled=false;if(motion!=null)motion.cancel();super.onDetachedFromWindow();}
}
