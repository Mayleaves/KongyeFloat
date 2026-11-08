package com.kongye.floatball;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

/** Generated note and own-window touch checks. Never exports screen or clipboard content. */
final class V205Checks {
    private static String originalNote;
    private static boolean noteSaved,notePresent;
    static void run(Context context,String command)throws Exception{
        MainActivity page=V1231Checks.main();
        if(command.equals("touch")){
            int[] clicks={0};
            FrameLayout root=new FrameLayout(page);
            View button=new View(page){
                @Override public boolean onTouchEvent(MotionEvent event){
                    if(event.getActionMasked()==MotionEvent.ACTION_UP){clicks[0]++;performClick();}
                    return true;
                }
                @Override public boolean performClick(){super.performClick();return true;}
            };
            root.addView(button,new FrameLayout.LayoutParams(-1,-1));
            page.setContentView(root);
            int width=page.getResources().getDisplayMetrics().widthPixels,height=page.getResources().getDisplayMetrics().heightPixels;
            root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
            root.layout(0,0,width,height);
            View decor=page.getWindow().getDecorView();
            decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
            decor.layout(0,0,width,height);
            LaunchIntroView marker=new LaunchIntroView(page,()->{});
            try{
                V126Checks.set(page,"intro",marker);
                send(page,MotionEvent.ACTION_DOWN);send(page,MotionEvent.ACTION_UP);
                require(clicks[0]==0,"tap during intro");
                require(V126Checks.get(page,"intro")==null,"tap skips intro");
                V126Checks.set(page,"intro",marker);
                send(page,MotionEvent.ACTION_DOWN);
                V126Checks.set(page,"intro",null);
                send(page,MotionEvent.ACTION_UP);
                require(clicks[0]==0,"touch held across intro dismissal");
                V126Checks.set(page,"intro",marker);
                send(page,MotionEvent.ACTION_DOWN);send(page,MotionEvent.ACTION_CANCEL);
                V126Checks.set(page,"intro",null);
                send(page,MotionEvent.ACTION_DOWN);send(page,MotionEvent.ACTION_UP);
                require(clicks[0]==1,"normal tap after dismissal");
                Prefs.of(context).edit().putBoolean("qa_v205_touch",true).apply();
            }finally{
                V126Checks.set(page,"intro",null);
                V126Checks.set(page,"introTouchActive",false);
                V126Checks.call(page,"render");
            }
            return;
        }
        if(command.equals("note_fixture")){
            SharedPreferences prefs=Prefs.of(context);
            if(!noteSaved){notePresent=prefs.contains("note");originalNote=prefs.getString("note","");noteSaved=true;}
            prefs.edit().putString("note","Generated clear-note sample 205").apply();
            FloatService.instance.perform("notes");
            return;
        }
        if(command.equals("notes")){FloatService.instance.perform("notes");return;}
        if(command.equals("restore")){
            V126Checks.call(FloatService.instance,"closePanel");
            if(noteSaved){
                SharedPreferences.Editor edit=Prefs.of(context).edit();
                if(notePresent)edit.putString("note",originalNote);else edit.remove("note");
                edit.apply();originalNote=null;noteSaved=false;
            }
            return;
        }
        if(command.equals("state")){
            JSONObject out=new JSONObject();
            out.put("intro",V126Checks.get(page,"intro")!=null);
            if(V126Checks.get(page,"intro") instanceof View intro)out.put("intro_alpha",intro.getAlpha());
            if(FloatService.instance!=null)out.put("panel",V126Checks.get(FloatService.instance,"panel")!=null);
            out.put("note_empty",Prefs.of(context).getString("note","").isEmpty());
            java.lang.reflect.Field field=Feedback.class.getDeclaredField("banner");
            field.setAccessible(true);
            if(field.get(null) instanceof ViewGroup banner){
                ViewGroup capsule=(ViewGroup)banner.getChildAt(0),badge=(ViewGroup)capsule.getChildAt(0);
                out.put("badge_color",((GradientDrawable)badge.getBackground()).getColor().getDefaultColor());
                out.put("badge_width",badge.getWidth());
                out.put("success",V126Checks.get(badge.getChildAt(0),"success"));
                out.put("shake",capsule.getTranslationX());
            }
            Prefs.of(context).edit().putString("qa_v205_state",out.toString()).apply();
        }
    }
    private static void send(MainActivity page,int action){
        long now=SystemClock.uptimeMillis();
        MotionEvent event=MotionEvent.obtain(now,now,action,120,200,0);
        page.dispatchTouchEvent(event);
        event.recycle();
    }
    private static void require(boolean value,String label){if(!value)throw new IllegalStateException(label);}
}
