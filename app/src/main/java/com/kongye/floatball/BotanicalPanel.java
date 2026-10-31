package com.kongye.floatball;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Native geometry from assets/branding/home-banner.svg, driven by the service state. */
final class BotanicalPanel extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable artwork,moon;
    private ValueAnimator transition;
    private float awake;
    BotanicalPanel(Context c){super(c);artwork=c.getDrawable(R.drawable.home_banner);moon=c.getDrawable(R.drawable.banner_moon);awake=FloatService.running?1:0;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void running(boolean on,boolean animate){
        if(transition!=null)transition.cancel();float target=on?1:0;
        if(!animate||!ValueAnimator.areAnimatorsEnabled()){awake=target;invalidate();return;}
        transition=ValueAnimator.ofFloat(awake,target);transition.setDuration(900);transition.setInterpolator(new PathInterpolator(.4f,0,.2f,1));transition.addUpdateListener(a->{awake=(float)a.getAnimatedValue();invalidate();});transition.start();
    }
    @Override protected void onMeasure(int width,int height){int w=MeasureSpec.getSize(width);setMeasuredDimension(w,Math.round(w*.4375f));}
    @Override protected void onDetachedFromWindow(){if(transition!=null)transition.cancel();super.onDetachedFromWindow();}
    private void moon(Canvas c,float x,float y,float scale,float alpha){c.save();c.translate(x,y);c.scale(scale,scale);moon.setBounds(-24,-24,24,24);moon.setAlpha(Math.round(alpha*255));moon.draw(c);c.restore();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        int[] colors={0xFFDBE7B8,0xFF284E33,0xFFB5CF83,0xFFDCE7C6,0xFF88AA50,0xFFCFE0AD,0xFFB2CF78,0xFF284E33};
        paint.setAlpha(255);paint.setAntiAlias(false);
        for(int row=0;row<2;row++)for(int col=0;col<4;col++){paint.setColor(colors[row*4+col]);c.drawRect(Math.round(col*getWidth()/4f),Math.round(row*getHeight()/2f),Math.round((col+1)*getWidth()/4f),Math.round((row+1)*getHeight()/2f),paint);}
        paint.setAntiAlias(true);c.save();c.scale(getWidth()/400f,getHeight()/175f);artwork.setBounds(0,0,400,175);artwork.draw(c);
        float rest=1-awake;
        c.save();c.clipRect(0,0,100,87.5f);float x=22+56*awake,y=66-50*awake;
        paint.setColor(0xFFF6C945);paint.setAlpha(Math.round(255*awake));c.drawCircle(x,y,27*(.75f+.25f*awake),paint);moon(c,x,y,.7f+.38f*rest,rest);c.restore();
        c.save();c.clipRect(200,87.5f,300,175);paint.setAlpha(Math.round(255*awake));float sunScale=.55f+.45f*awake;c.drawCircle(298-48*awake,84+47.25f*awake,22*sunScale,paint);
        moon(c,202+48*rest,180-48.75f*rest,.65f+.43f*rest,rest);c.restore();c.restore();paint.setAlpha(255);
    }
}
