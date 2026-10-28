package com.kongye.floatball;
import android.app.Activity;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
final class V19Checks {
    static int count;static void check(boolean value,String label){count++;if(!value)throw new AssertionError(label);}
    static void run(Activity c){count=0;String error="";try{
        int[] taps={0},moves={0};DragOverlay root=new DragOverlay(c,()->{},(x,y)->moves[0]++);View child=new View(c);child.setOnClickListener(v->{});child.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_UP)taps[0]++;return false;});root.addView(child,new FrameLayout.LayoutParams(200,200));root.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY));root.layout(0,0,400,400);
        for(int i=0;i<3;i++){long now=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(now,now,new int[]{0,2,1}[i],i==0?50:150,i==0?50:150,0);root.dispatchTouchEvent(e);e.recycle();}check(moves[0]>0,"drag beginning on child reaches window mover");check(taps[0]==0,"drag cancels child click");
        for(int action:new int[]{0,1}){long now=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(now,now,action,50,50,0);root.dispatchTouchEvent(e);e.recycle();}check(taps[0]==1,"tap still reaches child control");
        Prefs.of(c).edit().putString("record_watermark","legacy test").putString("gif_watermark","legacy test").apply();RecordingSetup first=new RecordingSetup(c,false,()->{},()->{});check(first.spec.watermark.isEmpty(),"new video session has no saved watermark");first.spec.watermark="session only";RecordingSetup second=new RecordingSetup(c,true,()->{},()->{});check(second.spec.watermark.isEmpty(),"next GIF session has no watermark");check(!Prefs.of(c).contains("record_watermark")&&!Prefs.of(c).contains("gif_watermark"),"legacy watermark settings removed");
    }catch(Throwable t){error=t.getClass().getSimpleName()+": "+t.getMessage();}Prefs.of(c).edit().putInt("qa_v19_checks",count).putBoolean("qa_v19_pass",error.isEmpty()).putString("qa_v19_error",error).apply();}
    static void dropWindow(){try{java.lang.reflect.Field f=FloatService.class.getDeclaredField("ball");f.setAccessible(true);View ball=(View)f.get(FloatService.instance);if(ball!=null)FloatService.instance.getSystemService(WindowManager.class).removeView(ball);}catch(Exception e){throw new RuntimeException(e);}}
}
