package com.kongye.floatball;

import android.content.Context;
import android.os.SystemClock;
import android.view.*;
import org.json.*;

/** Own ball touch and window metadata only; no device images or other app data. */
final class BallDragChecks {
    static void run(Context c,String command)throws Exception{
        if(command.equals("state")){Prefs.of(c).edit().putString("qa_drag_state",state().toString()).commit();return;}
        if(command.equals("cancel")){FloatService s=FloatService.instance;if(s!=null){V126Checks.call(s,"closePanel");V126Checks.call(s,"hideCaptureTarget");}return;}
        if(command.equals("monitor")){
            JSONArray frames=new JSONArray();long started=SystemClock.uptimeMillis();
            Prefs.of(c).edit().remove("qa_drag_frames").remove("qa_drag_error").commit();
            Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback(){public void doFrame(long nanos){try{
                frames.put(state());if(SystemClock.uptimeMillis()-started<1800)Choreographer.getInstance().postFrameCallback(this);
                else Prefs.of(c).edit().putString("qa_drag_frames",frames.toString()).apply();
            }catch(Exception e){Prefs.of(c).edit().putString("qa_drag_error",e.getClass().getSimpleName()).apply();}}});
        }
    }
    private static JSONObject state()throws Exception{
        JSONObject out=new JSONObject();FloatService s=FloatService.instance;if(s==null)return out;
        View ball=(View)V126Checks.get(s,"ball");Object touch=V126Checks.get(s,"touch");if(ball==null||touch==null)return out;
        WindowManager.LayoutParams p=(WindowManager.LayoutParams)V126Checks.get(s,"ballParams");int[] at=new int[2];ball.getLocationOnScreen(at);
        out.put("x",p.x).put("y",p.y).put("w",p.width).put("h",p.height).put("actualX",at[0]).put("actualY",at[1]).put("scale",ball.getScaleX());
        for(String name:new String[]{"downX","downY","pointerX","pointerY","initialX","initialY","pressed","dragging","armed","holdTriggered"})out.put(name,V126Checks.get(touch,name));
        out.put("age",SystemClock.uptimeMillis()-(long)V126Checks.get(touch,"downAt"));return out;
    }
}
