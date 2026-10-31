package com.kongye.floatball;

import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.*;

/** Native transcription of branding/launch-animation.svg. No WebView or network. */
final class LaunchIntroView extends View {
    private static final int BG=0xFFF4F7F0,INK=0xFF294233,ORB=0xFF85A867;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path leaf=new Path();private final Runnable finished;private ValueAnimator animator;private float elapsed;private boolean delivered;
    private final TimeInterpolator smooth=new PathInterpolator(.22f,1,.36f,1),pop=new PathInterpolator(.34f,1.56f,.64f,1),easeOut=new PathInterpolator(0,0,.58f,1);
    LaunchIntroView(Context c,Runnable finished){super(c);this.finished=finished;setContentDescription("空叶开屏动画");setBackgroundColor(BG);leaf.moveTo(540,970);leaf.cubicTo(595,970,610,1050,540,1120);leaf.cubicTo(470,1050,485,970,540,970);leaf.close();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();animator=ValueAnimator.ofFloat(0,1600);animator.setInterpolator(new LinearInterpolator());animator.setDuration(ValueAnimator.areAnimatorsEnabled()?1600:0);animator.addUpdateListener(a->{elapsed=(float)a.getAnimatedValue();invalidate();});animator.addListener(new AnimatorListenerAdapter(){private boolean cancelled;@Override public void onAnimationCancel(Animator a){cancelled=true;}@Override public void onAnimationEnd(Animator a){if(!cancelled&&!delivered){delivered=true;post(finished);}}});animator.start();}
    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
    private float progress(float delay,float duration,TimeInterpolator curve){return curve.getInterpolation(Math.max(0,Math.min(1,(elapsed-delay)/duration)));}
    private float keys(float t,float[] times,float[] values){for(int i=1;i<times.length;i++)if(t<=times[i]){float f=smooth.getInterpolation((t-times[i-1])/(times[i]-times[i-1]));return values[i-1]+(values[i]-values[i-1])*f;}return values[values.length-1];}
    private void fill(int color,float alpha){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setAlpha(Math.round(255*Math.max(0,Math.min(1,alpha))));}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float scale=Math.min(getWidth()/1080f,getHeight()/2400f);c.save();c.translate((getWidth()-1080*scale)/2,(getHeight()-2400*scale)/2);c.scale(scale,scale);
        float reveal=progress(600,800,pop);c.save();c.scale(.7f+.3f*reveal,.7f+.3f*reveal,540,1050);c.rotate(-8+8*reveal,540,1050);fill(0xFFD5E3B8,reveal);c.drawRoundRect(360,870,540,1050,40,40,p);fill(0xFFDCE8C6,reveal);c.drawRoundRect(540,1050,720,1230,40,40,p);fill(0xFFF5D042,reveal);c.drawCircle(430,940,45,p);c.restore();
        float t=Math.min(1,elapsed/1400);float shift=keys(t,new float[]{0,.6f,.8f,1},new float[]{600,-30,10,0}),orbScale=keys(t,new float[]{0,.6f,.8f,1},new float[]{.4f,1.05f,.98f,1}),opacity=keys(t,new float[]{0,.6f,1},new float[]{.5f,1,1});c.save();c.translate(shift,0);c.scale(orbScale,orbScale,540,1050);fill(ORB,.35f*opacity);c.drawCircle(540,1050,145,p);fill(ORB,opacity);c.drawCircle(540,1050,115,p);
        float angle=keys(t,new float[]{0,.6f,1},new float[]{-140,50,40}),leafScale=keys(t,new float[]{0,.6f,1},new float[]{.5f,1.05f,1});c.save();c.rotate(angle,540,1050);c.scale(leafScale,leafScale,540,1050);p.setStyle(Paint.Style.STROKE);p.setColor(BG);p.setAlpha(Math.round(255*opacity));p.setStrokeWidth(14);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);c.drawPath(leaf,p);c.drawLine(540,1140,540,1015,p);c.restore();c.restore();
        float fade=progress(900,700,easeOut);c.save();c.translate(0,30*(1-fade));text(c,"空叶悬浮球",540,2020,44,6,INK,true,fade);text(c,"LIGHTWEIGHT FLOATING ASSISTANT",540,2070,22,4,0xFF7F9686,false,fade);c.restore();c.restore();
    }
    private void text(Canvas c,String text,float x,float y,float size,float space,int color,boolean bold,float alpha){fill(color,alpha);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));float width=p.measureText(text)+space*(text.length()-1),left=x-width/2;for(int i=0;i<text.length();i++){String letter=text.substring(i,i+1);c.drawText(letter,left,y,p);left+=p.measureText(letter)+space;}}
}
