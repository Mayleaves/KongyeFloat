package com.kongye.floatball;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.CompoundButton;
/** 38 x 22 HTML switch: colored track, inset white thumb. */
final class LeafSwitch extends CompoundButton {
    private final Paint paint=new Paint(3);private float position;private ValueAnimator animator;
    LeafSwitch(Context c){super(c);setButtonDrawable(null);setBackground(null);setFocusable(true);setLayerType(LAYER_TYPE_SOFTWARE,null);setPadding(0,0,0,0);}
    @Override public CharSequence getAccessibilityClassName(){return "android.widget.Switch";}
    @Override public void setChecked(boolean checked){boolean changed=checked!=isChecked();super.setChecked(checked);if(animator!=null)animator.cancel();float target=checked?1:0;if(changed&&isAttachedToWindow()&&ValueAnimator.areAnimatorsEnabled()){animator=ValueAnimator.ofFloat(position,target);animator.setDuration(300);animator.setInterpolator(new android.view.animation.PathInterpolator(.2f,.85f,.3f,1));animator.addUpdateListener(a->{position=(float)a.getAnimatedValue();invalidate();});animator.start();}else{position=target;invalidate();}}
    @Override protected void onMeasure(int w,int h){setMeasuredDimension(resolveSize(Ui.dp(getContext(),38),w),resolveSize(Ui.dp(getContext(),32),h));}
    @Override protected void onDraw(Canvas c){float u=getResources().getDisplayMetrics().density,left=(getWidth()-38*u)/2,top=(getHeight()-22*u)/2;int off=Ui.dark?0xFF2C3E31:0xFFD7E0D5,on=Ui.dark?Ui.GREEN:0xFF28664B;paint.clearShadowLayer();paint.setColor((int)new android.animation.ArgbEvaluator().evaluate(position,off,on));c.drawRoundRect(left,top,left+38*u,top+22*u,11*u,11*u,paint);paint.setColor(Color.WHITE);paint.setShadowLayer(u,0,u,0x26000000);c.drawCircle(left+(11+16*position)*u,top+11*u,8*u,paint);paint.clearShadowLayer();}
    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
}
