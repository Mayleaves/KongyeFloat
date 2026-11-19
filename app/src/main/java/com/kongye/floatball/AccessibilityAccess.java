package com.kongye.floatball;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.provider.Settings;

/** Distinguishes system authorization from the asynchronous service connection. */
final class AccessibilityAccess {
    enum State { OFF, WAITING, CONNECTED }

    static State state(Context context) {
        if (AssistService.instance != null) return State.CONNECTED;
        String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return contains(enabled, new ComponentName(context, AssistService.class)) ? State.WAITING : State.OFF;
    }

    static boolean contains(String enabled, ComponentName ownService) {
        if (enabled == null || enabled.isEmpty()) return false;
        for (String entry : enabled.split(":")) {
            if (ownService.equals(ComponentName.unflattenFromString(entry))) return true;
        }
        return false;
    }

    static String guidance(State state) {
        return state == State.WAITING
                ? "无障碍已开启，正在等待系统连接。长时间未恢复时，请在系统设置中关闭后重新开启。"
                : "请在系统无障碍设置中开启空叶悬浮球。\n\n如果开关是灰色，或提示“受限制的设置”：先打开应用信息，点击右上角菜单中的“允许受限制的设置”，再返回开启无障碍。";
    }

    static void openSettings(Activity activity) {
        State state = state(activity);
        Runnable settings = () -> {
            try { activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (ActivityNotFoundException | SecurityException unavailable) { Feedback.show(activity, "请在系统设置中检查空叶悬浮球的无障碍权限"); }
        };
        if (state == State.CONNECTED) { settings.run(); return; }
        new UiDialog(activity).setTitle(state == State.WAITING ? "无障碍尚未连接" : "开启无障碍服务")
                .setMessage(guidance(state) + "\n\n悬浮球开关独立保留；返回、截图和字幕等功能需要无障碍连接后才能使用。")
                .setPositiveButton("去开启", (dialog, which) -> settings.run())
                .setNeutralButton("应用信息", (dialog, which) -> {
                    try { activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName()))); }
                    catch (ActivityNotFoundException | SecurityException unavailable) { Feedback.show(activity, "请在系统设置中打开空叶悬浮球的应用信息"); }
                })
                .setNegativeButton("关闭", null).show();
    }
}
