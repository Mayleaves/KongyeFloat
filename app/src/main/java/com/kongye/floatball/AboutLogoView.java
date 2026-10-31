package com.kongye.floatball;

import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;

/** Four fixed quadrants and the HTML's gently floating central leaf. */
final class AboutLogoView extends FrameLayout {
    private final View orb;private ObjectAnimator floating;private boolean active;
    AboutLogoView(Context c){super(c);setContentDescription("空叶动态图标");setClipChildren(false);setClipToPadding(false);
        addView(new View(c){final Paint p=new Paint(3);final Path clip=new Path();protected void onDraw(Canvas canvas){canvas.save();canvas.scale(getWidth()/64f,getHeight()/64f);clip.reset();clip.addRoundRect(0,0,64,64,20,20,Path.Direction.CW);canvas.clipPath(clip);p.setColor(0xFFDCE8C6);canvas.drawRect(0,0,32,32,p);p.setColor(0xFF294233);canvas.drawRect(32,0,64,32,p);p.setColor(0xFF7D9B57);canvas.drawRect(0,32,32,64,p);p.setColor(0xFFB8D293);canvas.drawRect(32,32,64,64,p);p.setColor(0xFFF5D042);canvas.drawCircle(17,15,7,p);canvas.restore();}},new LayoutParams(-1,-1));
        orb=new View(c){final Paint p=new Paint(3);final Path leaf=new Path();protected void onDraw(Canvas canvas){canvas.save();canvas.scale(getWidth()/40f,getHeight()/40f);p.setStyle(Paint.Style.FILL);p.setColor(0xE6294233);canvas.drawCircle(20,20,19,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0xCCE5EDE0);canvas.drawCircle(20,20,19,p);canvas.translate(10,10);canvas.scale(20f/24,20f/24);p.setColor(0xFFB5D68E);p.setStrokeWidth(2.2f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);leaf.reset();leaf.moveTo(17,4);leaf.cubicTo(11.5f,4,6,8,6,15.5f);leaf.cubicTo(6,17.5f,7,19,7,19);leaf.cubicTo(7,19,8.5f,20,10.5f,20);leaf.cubicTo(18,20,22,14.5f,22,9);leaf.cubicTo(22,6.5f,20,4,17,4);leaf.moveTo(5,20);leaf.lineTo(15,10);canvas.drawPath(leaf,p);canvas.restore();}};
        addView(orb,new LayoutParams(Ui.dp(c,40),Ui.dp(c,40),Gravity.CENTER));setElevation(Ui.dp(c,3));setBackground(Ui.bg(0xFF294233,20,c));setOnClickListener(v->{if(floating!=null)floating.cancel();orb.setTranslationY(0);orb.setRotation(0);sync();});
    }
    void active(boolean value){active=value;sync();}
    private void sync(){boolean run=active&&isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&isShown()&&ValueAnimator.areAnimatorsEnabled();if(!run){if(floating!=null){floating.cancel();floating=null;}orb.setTranslationY(0);orb.setRotation(0);return;}if(floating!=null&&floating.isRunning())return;floating=ObjectAnimator.ofPropertyValuesHolder(orb,PropertyValuesHolder.ofFloat(View.TRANSLATION_Y,0,-Ui.dp(getContext(),6)),PropertyValuesHolder.ofFloat(View.ROTATION,0,2));floating.setDuration(2000);floating.setRepeatCount(ValueAnimator.INFINITE);floating.setRepeatMode(ValueAnimator.REVERSE);floating.setInterpolator(new AccelerateDecelerateInterpolator());floating.start();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();post(this::sync);}
    @Override protected void onDetachedFromWindow(){if(floating!=null){floating.cancel();floating=null;}super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(orb!=null)sync();}
}
