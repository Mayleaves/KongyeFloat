package com.kongye.floatball;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.View;
import android.view.animation.PathInterpolator;
import java.util.Calendar;

/** Six time-of-day SVG micro-scenes, with the supplied squash/stretch click jump. */
final class PlantStageView extends View {
    private static final int[] ART={R.drawable.plant_stage_1,R.drawable.plant_stage_2,R.drawable.plant_stage_3,R.drawable.plant_stage_4,R.drawable.plant_stage_5,R.drawable.plant_stage_6};
    static final String[] NAMES={"发芽","成长","茂盛","花苞","开花","休息"};
    private ValueAnimator swayAnimator,jumpAnimator;private float sway,jumpProgress=1;
    private final Handler handler=new Handler(Looper.getMainLooper());private Drawable fallback;private PlantScene scene;private int shown=-1;private final boolean perched;private long epoch=SystemClock.uptimeMillis();private final Paint particle=new Paint(3);
    private final PathInterpolator jumpEase=new PathInterpolator(.28f,.84f,.42f,1);
    private static final float[][] JUMP={{0,0,1,1,0},{.14f,3,1.18f,.82f,-2},{.38f,-18,.88f,1.15f,5},{.56f,-14,.95f,1.06f,-4},{.76f,2,1.12f,.88f,1},{.9f,-4,.97f,1.04f,0},{1,0,1,1,0}};
    private final Runnable tick=new Runnable(){public void run(){update();handler.postDelayed(this,60000-System.currentTimeMillis()%60000);}};
    PlantStageView(Context c){this(c,false);}
    PlantStageView(Context c,boolean perched){super(c);this.perched=perched;setFocusable(true);setOnClickListener(v->jump());update();}
    private void update(){int stage=PlantCycle.stage(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));if(stage!=shown){shown=stage;fallback=getContext().getDrawable(ART[stage]);try{scene=new PlantScene(getContext(),stage);}catch(Exception e){scene=null;}setContentDescription("盆栽："+NAMES[stage]+"，点击起跳");epoch=SystemClock.uptimeMillis();invalidate();}}
    void jump(){if(!ValueAnimator.areAnimatorsEnabled())return;if(jumpAnimator!=null)jumpAnimator.cancel();jumpAnimator=ValueAnimator.ofFloat(0,1);jumpAnimator.setDuration(680);jumpAnimator.setInterpolator(new android.view.animation.LinearInterpolator());jumpAnimator.addUpdateListener(a->{jumpProgress=(float)a.getAnimatedValue();invalidate();});jumpAnimator.start();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();handler.removeCallbacks(tick);tick.run();post(this::motion);}
    @Override protected void onDetachedFromWindow(){handler.removeCallbacks(tick);stopMotion();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(handler==null)return;if(visibility==VISIBLE)update();motion();}
    private void stopMotion(){if(swayAnimator!=null){swayAnimator.cancel();swayAnimator=null;}if(jumpAnimator!=null){jumpAnimator.cancel();jumpAnimator=null;}sway=0;jumpProgress=1;invalidate();}
    private void motion(){if(!perched||!isAttachedToWindow()||!isShown()||getWindowVisibility()!=VISIBLE||!ValueAnimator.areAnimatorsEnabled()){stopMotion();return;}if(swayAnimator!=null&&swayAnimator.isRunning())return;swayAnimator=ValueAnimator.ofFloat(0,1);swayAnimator.setDuration(1000);swayAnimator.setRepeatCount(ValueAnimator.INFINITE);swayAnimator.setInterpolator(new android.view.animation.LinearInterpolator());swayAnimator.addUpdateListener(a->{sway=(SystemClock.uptimeMillis()-epoch)/1000f;invalidate();});swayAnimator.start();}
    @Override public void onVisibilityAggregated(boolean visible){super.onVisibilityAggregated(visible);if(handler!=null)motion();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);int side=Math.min(getWidth(),getHeight());float left=(getWidth()-side)/2f,top=perched?getHeight()-side*53.9f/64:(getHeight()-side)/2f;canvas.save();canvas.translate(left,top);canvas.scale(side/64f,side/64f);
        if(jumpProgress<1){int n=1;while(n<JUMP.length-1&&jumpProgress>JUMP[n][0])n++;float[] a=JUMP[n-1],b=JUMP[n];float t=jumpEase.getInterpolation((jumpProgress-a[0])/(b[0]-a[0]));float y=a[1]+(b[1]-a[1])*t,sx=a[2]+(b[2]-a[2])*t,sy=a[3]+(b[3]-a[3])*t,angle=a[4]+(b[4]-a[4])*t;
            canvas.translate(32,53.76f+y*64/58);canvas.rotate(angle);canvas.scale(sx,sy);canvas.translate(-32,-53.76f);
        }
        long time=swayAnimator!=null&&swayAnimator.isRunning()?SystemClock.uptimeMillis()-epoch:0;
        if(scene!=null)scene.draw(canvas,time);else if(fallback!=null){fallback.setBounds(0,0,64,64);fallback.draw(canvas);}
        if(jumpProgress<.89f){float p=Math.min(1,jumpProgress*680/600);int[] colors={0xFFF6C945,0xFF88B399,0xFFF29F80,0xFF28664B,0xFFE6A940};float[] xs={0,-24,24,-16,18},ys={-26,-18,-16,-30,-28};for(int i=0;i<5;i++){particle.setColor(colors[i]);particle.setAlpha(Math.round(255*(1-p)));canvas.drawCircle(32+xs[i]*p,24.32f+ys[i]*p,2.5f*(.3f+.8f*p),particle);}}
        canvas.restore();
    }
}
