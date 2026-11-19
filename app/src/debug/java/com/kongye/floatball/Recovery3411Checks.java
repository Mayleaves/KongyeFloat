package com.kongye.floatball;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;

/** Generated emulator states only; never grants an accessibility permission. */
final class Recovery3411Checks {
    static void run(Context context, String command) throws Exception {
        if (command.equals("on")) {
            UpdateRestoreReceiver.remember(context, true);
            UpdateRestoreReceiver.resumeOnOpen(context);
        } else if (command.equals("off")) {
            UpdateRestoreReceiver.remember(context, false);
            if (FloatService.running) context.startService(new Intent(context, FloatService.class).setAction(FloatService.STOP));
        } else if (command.equals("missing")) {
            context.stopService(new Intent(context, FloatService.class));
            Prefs.of(context).edit().putBoolean("ball_enabled", true).remove("ball_update_restore_pending").commit();
        } else if (command.equals("toggle")) {
            MainActivity page = V1231Checks.main();
            if (page == null) throw new IllegalStateException("Open the home page first");
            ((View)V126Checks.get(page, "powerControl")).performClick();
        } else if (!command.equals("report")) {
            throw new IllegalArgumentException(command);
        }
        ComponentName own = new ComponentName(context, AssistService.class);
        JSONArray checks = new JSONArray();
        check(!AccessibilityAccess.contains(null, own), "missing authorization", checks);
        check(!AccessibilityAccess.contains("", own), "empty authorization", checks);
        check(AccessibilityAccess.contains(own.flattenToString(), own), "full component", checks);
        check(AccessibilityAccess.contains(own.flattenToShortString(), own), "short component", checks);
        check(AccessibilityAccess.contains("other.app/.Service:" + own.flattenToShortString(), own), "multiple services", checks);
        check(!AccessibilityAccess.contains(context.getPackageName() + "/.OtherService", own), "different service", checks);
        check(!AccessibilityAccess.contains("bad:" + own.flattenToShortString() + "Extra", own), "no prefix matching", checks);
        JSONObject report = new JSONObject().put("running", FloatService.running)
                .put("enabled", UpdateRestoreReceiver.enabled(context))
                .put("pending", Prefs.of(context).getBoolean("ball_update_restore_pending", false))
                .put("accessibility", AccessibilityAccess.state(context).name()).put("checks", checks);
        if (FloatService.instance != null) {
            View ball = (View)V126Checks.get(FloatService.instance, "ball");
            report.put("attached", ball != null && ball.isAttachedToWindow());
        }
        Prefs.of(context).edit().putString("qa_recovery3411", report.toString()).commit();
    }

    private static void check(boolean value, String label, JSONArray checks) {
        if (!value) throw new AssertionError(label);
        checks.put(label);
    }
}
