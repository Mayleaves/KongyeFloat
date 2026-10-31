package com.kongye.floatball;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

/** HTML pulse ring, drawn inside its bounds and stopped with its window. */
final class UpdatePulseView extends FrameLayout {
    private final Paint paint=new Paint(3);private final int fill,accent;private ValueAnimator pulse;private float phase;private boolean active=true;
    UpdatePulseView(Context c,int fill,int accent,int ink){super(c);this.fill=fill;this.accent=accent;setWillNotDraw(false);setContentDescription("更新图标波纹");addView(Ui.icon(c,"ball_leaf",ink,19),new LayoutParams(Ui.dp(c,19),Ui.dp(c,19),Gravity.CENTER));}
    void active(boolean value){active=value;sync();}
    private void stop(){if(pulse!=null){pulse.cancel();pulse=null;}phase=0;invalidate();}
    private void sync(){if(!active||!isAttachedToWindow()||!isShown()||getWindowVisibility()!=VISIBLE||!ValueAnimator.areAnimatorsEnabled()){stop();return;}if(pulse!=null&&pulse.isRunning())return;pulse=ValueAnimator.ofFloat(0,1);pulse.setDuration(2500);pulse.setRepeatCount(ValueAnimator.INFINITE);pulse.setInterpolator(new LinearInterpolator());pulse.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidate();});pulse.start();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float unit=Math.min(getWidth(),getHeight())/48f,x=getWidth()/2f,y=getHeight()/2f;paint.setStyle(Paint.Style.FILL);paint.setColor(fill);c.drawCircle(x,y,16*unit,paint);paint.setStyle(Paint.Style.STROKE);paint.setColor(accent);paint.setStrokeWidth(1.8f*unit);paint.setAlpha(Math.round(153*Math.max(0,1-phase/.7f)));c.drawCircle(x,y,(18+5*Math.min(1,phase/.7f))*unit,paint);paint.setAlpha(255);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();post(this::sync);}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);sync();}
    @Override public void onVisibilityAggregated(boolean visible){super.onVisibilityAggregated(visible);if(paint!=null)sync();}
}
