package com.kongye.floatball;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupWindow;
import android.widget.TextView;
import org.json.JSONObject;

/** Synthetic candidate-window focus cycle; never reads or fills credentials. */
final class BrowserImeChecks {
    static void run(Context context, String command) {
        try {
            java.lang.reflect.Field browserField = FloatService.class.getDeclaredField("browser");
            browserField.setAccessible(true);
            Object browser = browserField.get(FloatService.instance);
            View root = (View) field(browser, "root");
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) field(browser, "params");
            WindowManager manager = (WindowManager) field(browser, "wm");
            if(command.equals("stability")){
                sampleStability(context,browser,root);
                return;
            }
            if (command.equals("page")) {
                ((android.webkit.WebView) field(browser, "web")).loadDataWithBaseURL("https://kongye.invalid/",
                    "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='margin:16px'><input aria-label='测试账号' style='width:90%;height:64px;font-size:20px'><p>仅验证输入焦点，不保存账号</p><input type='password' aria-label='测试密码' style='height:64px'></body>",
                    "text/html", "UTF-8", null);
                return;
            }
            if (command.equals("legacy") || command.equals("fixed")) {
                params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING |
                    (command.equals("legacy") ? WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN :
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
                manager.updateViewLayout(root, params);
                return;
            }
            JSONObject result = new JSONObject().put("before", state(browser, root, params));
            TextView content = new TextView(context);
            content.setText("测试候选 · 不含账号密码");
            content.setTextColor(Color.BLACK);
            content.setBackgroundColor(Color.WHITE);
            content.setPadding(24, 24, 24, 24);
            PopupWindow popup = new PopupWindow(content, Ui.dp(context, 240), Ui.dp(context, 72), true);
            // Same focus / IME policy as AOSP AutofillPopupWindow.
            popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            popup.setWindowLayoutType(1005); // AOSP TYPE_APPLICATION_ABOVE_SUB_PANEL (hidden SDK constant).
            popup.showAtLocation(root, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, Ui.dp(context, 220));
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                try {
                    result.put("during", state(browser, root, params));
                    popup.dismiss();
                    handler.postDelayed(() -> {
                        try {
                            result.put("after", state(browser, root, params));
                            save(context, result.toString());
                        } catch (Exception e) { save(context, e.toString()); }
                    }, 1500);
                } catch (Exception e) { popup.dismiss(); save(context, e.toString()); }
            }, 2000);
        } catch (Exception e) { save(context, e.toString()); }
    }

    private static void sampleStability(Context context,Object browser,View root) throws Exception {
        android.webkit.WebView web=(android.webkit.WebView)field(browser,"web");
        int loads=(int)field(browser,"loads");
        long until=android.os.SystemClock.uptimeMillis()+5000;
        android.view.Choreographer.getInstance().postFrameCallback(new android.view.Choreographer.FrameCallback(){
            int frames;
            float minimum=1,maximum=0;
            boolean samePage=true;
            @Override public void doFrame(long time){
                try{
                    frames++;
                    minimum=Math.min(minimum,root.getAlpha());
                    maximum=Math.max(maximum,root.getAlpha());
                    samePage &= root.isAttachedToWindow()&&web==field(browser,"web")&&loads==(int)field(browser,"loads");
                    if(android.os.SystemClock.uptimeMillis()<until){
                        android.view.Choreographer.getInstance().postFrameCallback(this);
                    }else{
                        save(context,new JSONObject().put("frames",frames).put("minAlpha",minimum)
                            .put("maxAlpha",maximum).put("samePage",samePage).toString());
                    }
                }catch(Exception error){save(context,error.toString());}
            }
        });
    }

    private static Object field(Object owner, String name) throws Exception {
        java.lang.reflect.Field field = FloatingBrowser.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static JSONObject state(Object browser, View root, WindowManager.LayoutParams params) throws Exception {
        return new JSONObject().put("imeBottom", field(browser, "imeBottom"))
            .put("windowFocus", root.hasWindowFocus()).put("softInputMode", params.softInputMode);
    }

    private static void save(Context context, String value) {
        try (java.io.FileWriter out = new java.io.FileWriter(new java.io.File(context.getExternalFilesDir(null), "browser-ime-check.json"))) {
            out.write(value);
        } catch (Exception e) { android.util.Log.e("BrowserImeChecks", "Cannot save result", e); }
    }
}
