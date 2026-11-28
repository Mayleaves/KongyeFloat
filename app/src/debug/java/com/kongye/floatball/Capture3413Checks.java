package com.kongye.floatball;

import android.content.Context;
import android.view.View;
import android.view.WindowManager;
import java.util.List;
import org.json.JSONObject;

/** Uses generated parts and this app's own windows only. */
final class Capture3413Checks {
    static void run(Context context,String command)throws Exception {
        if(command.equals("dialog")){AccessibilityAccess.openSettings(V1231Checks.main());return;}
        if(command.equals("start_empty")){
            V125Checks.run(context,"stitch");
            for(Object part:(List<?>)V126Checks.get(V125Checks.stitch,"parts"))((android.graphics.Bitmap)V126Checks.get(part,"image")).recycle();
            ((List<?>)V126Checks.get(V125Checks.stitch,"parts")).clear();
            V126Checks.call(V125Checks.stitch,"showLive");return;
        }
        if(command.equals("start")){V125Checks.run(context,"stitch");return;}
        if(command.equals("close")){V125Checks.close();return;}
        StitchSession session=V125Checks.stitch;
        if(session==null)throw new IllegalStateException("Start generated stitch session first");
        View plus=(View)V126Checks.get(session,"compactPlus");
        if(command.equals("expand"))plus.performLongClick();
        else if(command.equals("capture"))plus.performClick();
        else if(command.equals("auto"))V126Checks.call(session,"startAutoScroll");
        else if(command.equals("auto_stop")){
            Object auto=V126Checks.get(session,"autoScroll");
            if(auto!=null)((View)V126Checks.get(auto,"stop")).performClick();
        }
        else if(command.equals("preview"))((View)V126Checks.get(session,"done")).performClick();
        JSONObject state=new JSONObject().put("expanded",V126Checks.get(session,"toolsExpanded"))
                .put("busy",V126Checks.get(session,"busy"))
                .put("closed",V126Checks.get(session,"closed"))
                .put("parts",((List<?>)V126Checks.get(session,"parts")).size());
        int visibleControls=0;
        for(Object item:(List<?>)V126Checks.get(session,"liveViews")){
            View view=(View)item;
            WindowManager.LayoutParams at=(WindowManager.LayoutParams)view.getLayoutParams();
            if(view.getVisibility()==View.VISIBLE&&(at.flags&WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)==0)visibleControls++;
        }
        state.put("visible_controls",visibleControls);
        for(String field:new String[]{"toolbar","crop","done","thumbnailBox"}){
            View view=(View)V126Checks.get(session,field);state.put(field+"_visible",view!=null&&view.getVisibility()==View.VISIBLE);
        }
        if(plus!=null)state.put("plus_visible",plus.getVisibility()==View.VISIBLE)
                .put("plus_alpha",((WindowManager.LayoutParams)plus.getLayoutParams()).alpha);
        state.put("result",V122Fixture.result!=null);
        Object auto=V126Checks.get(session,"autoScroll");state.put("automatic",auto!=null);
        if(auto!=null){View stop=(View)V126Checks.get(auto,"stop");if(stop!=null)state.put("automatic_alpha",((WindowManager.LayoutParams)stop.getLayoutParams()).alpha);}
        Prefs.of(context).edit().putString("qa_capture3413",state.toString()).commit();
    }
}
