package com.kongye.floatball;

import android.app.*;
import android.content.Context;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

/** Own-session metadata and generated text for one-shot external subtitle tests. */
final class Subtitle344Checks {
    private static Activity bridge;
    private static boolean registered;
    static void run(Context c,String command) throws Exception {
        if(command.startsWith("target_")) {
            String label=command.substring(7);
            if(!java.util.Set.of("测试翻译 App","返回测试译文","复制测试译文","不返回译文","测试分享翻译").contains(label))throw new IllegalArgumentException("Generated controls only");
            var root=AssistService.instance.getRootInActiveWindow();
            if(root==null)throw new IllegalStateException("No active fixture window");
            try {
                for(var node:root.findAccessibilityNodeInfosByText(label)) {
                    try {
                        if(!label.contentEquals(node.getText()==null?"":node.getText()))continue;
                        android.graphics.Rect r=new android.graphics.Rect();node.getBoundsInScreen(r);
                        Prefs.of(c).edit().putString("qa_subtitle344_target",new JSONObject().put("x",r.centerX()).put("y",r.centerY()).put("label",label).toString()).commit();return;
                    }finally{node.recycle();}
                }
            }finally{root.recycle();}
            throw new IllegalStateException("Generated control not visible");
        }
        if(command.equals("register")) {
            if(!registered) {
                registered=true;
                ((Application)c.getApplicationContext()).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
                    public void onActivityCreated(Activity a,Bundle b){if(a instanceof SubtitleExternalActivity)bridge=a;}
                    public void onActivityDestroyed(Activity a){if(bridge==a)bridge=null;}
                    public void onActivityStarted(Activity a){}
                    public void onActivityResumed(Activity a){}
                    public void onActivityPaused(Activity a){}
                    public void onActivityStopped(Activity a){}
                    public void onActivitySaveInstanceState(Activity a,Bundle b){}
                });
            }
            return;
        }
        SubtitleSession s=FloatService.instance.subtitleSession();
        if(command.equals("fixture_next")||command.equals("fixture_changed")||command.equals("fixture_blank")) {
            var field=V141Checks.class.getDeclaredField("line");field.setAccessible(true);field.set(null,command.equals("fixture_next")?"Good morning":command.equals("fixture_changed")?"The next scene is ready.":"");
            var view=V141Checks.class.getDeclaredField("fixture");view.setAccessible(true);((View)view.get(null)).invalidate();return;
        }
        if(command.equals("bridge_rotate")){bridge.recreate();return;}
        if(command.startsWith("bridge_click_")) {
            View v=V124Checks.find(bridge.getWindow().getDecorView(),command.substring(13));
            if(v==null)throw new IllegalStateException("Missing bridge control");v.performClick();return;
        }
        if(command.equals("bridge_edit")){((EditText)V126Checks.get(bridge,"translated")).setText("测试译文：你好，世界。");return;}
        if(command.equals("bridge_focus")) {
            EditText input=(EditText)V126Checks.get(bridge,"translated");input.requestFocus();
            bridge.getSystemService(android.view.inputmethod.InputMethodManager.class).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);return;
        }
        if(command.equals("bridge_paste_probe")){
            android.content.ClipData clip=c.getSystemService(android.content.ClipboardManager.class).getPrimaryClip();
            Prefs.of(c).edit().putBoolean("qa_subtitle344_clipboard_read",clip!=null).commit();return;
        }
        if(command.equals("stop")){s.stop();return;}
        JSONObject report=new JSONObject().put("active",s.isActive()).put("paused",V126Checks.get(s,"externalPaused"))
            .put("fresh",V126Checks.get(s,"freshCaptureRequested")).put("recognized",V126Checks.get(s,"latest"))
            .put("translation",V126Checks.get(s,"translation")).put("override_key",V126Checks.get(s,"overrideKey"))
            .put("override_blocks",new org.json.JSONArray((java.util.List<?>)V126Checks.get(s,"overrideBlocks")))
            .put("pending",V126Checks.get(s,"pendingExternalText")).put("awaiting_package",V126Checks.get(s,"awaitingSourcePackage"))
            .put("frame_busy",V126Checks.get(s,"frameBusy")).put("bridge",bridge!=null&&!bridge.isFinishing());
        SubtitleFrame frame=(SubtitleFrame)V126Checks.get(s,"latestFrame");
        report.put("override_active",V126Checks.get(s,"overrideFrame")!=null&&frame.key.equals(V126Checks.get(s,"overrideKey")))
            .put("status",V126Checks.get(s,"status"));
        if(bridge!=null&&!bridge.isFinishing()) {
            View input=(View)V126Checks.get(bridge,"translated");
            org.json.JSONArray geometry=new org.json.JSONArray();
            for(View view=input;view!=null;view=view.getParent() instanceof View?(View)view.getParent():null){int[] xy=new int[2];view.getLocationOnScreen(xy);geometry.put(new JSONObject().put("type",view.getClass().getSimpleName()).put("x",xy[0]).put("y",xy[1]).put("w",view.getWidth()).put("h",view.getHeight()));}
            report.put("geometry",geometry);
            var insets=input.getRootWindowInsets();report.put("ime",insets.getInsets(WindowInsets.Type.ime()).toString()).put("bars",insets.getInsets(WindowInsets.Type.systemBars()).toString()).put("window",bridge.getWindowManager().getCurrentWindowMetrics().getBounds().toString());
            View card=(View)input.getParent().getParent().getParent().getParent();
            var at=(android.widget.FrameLayout.LayoutParams)card.getLayoutParams();report.put("top_margin",at.topMargin).put("bottom_margin",at.bottomMargin);
            report.put("bridge_translation",((EditText)V126Checks.get(bridge,"translated")).getText())
                .put("bridge_status",((TextView)V126Checks.get(bridge,"status")).getText());
            String id=(String)V126Checks.get(bridge,"requestId");
            var request=s.externalRequest(id);
            report.put("request_valid",request!=null).put("wrong_token_rejected",!s.submitExternal("invalid-test-token","错误文本"));
            if(request!=null)report.put("request_text",request.frame().text);
        }
        Prefs.of(c).edit().remove("qa_overlay_error").putString("qa_subtitle344",report.toString()).commit();
    }
}
