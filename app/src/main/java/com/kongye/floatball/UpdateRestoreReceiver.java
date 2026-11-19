package com.kongye.floatball;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/** Restores only a ball the user left enabled before an app update. */
public final class UpdateRestoreReceiver extends BroadcastReceiver {
    private static final String ENABLED = "ball_enabled";
    private static final String PENDING = "ball_update_restore_pending";

    static boolean enabled(Context context) {
        return Prefs.of(context).getBoolean(ENABLED, false);
    }

    static void remember(Context context, boolean enabled) {
        var preferences = Prefs.of(context);
        if (preferences.getBoolean(ENABLED, false) != enabled || preferences.getBoolean(PENDING, false)) {
            // Persist before the stop animation: an update can kill the process at any time.
            preferences.edit().putBoolean(ENABLED, enabled).remove(PENDING).commit();
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        if (!enabled(context)) return;
        Prefs.of(context).edit().putBoolean(PENDING, true).commit();
        resumePending(context);
    }

    static void resumePending(Context context) {
        var preferences = Prefs.of(context);
        if (!preferences.getBoolean(PENDING, false) || !preferences.getBoolean(ENABLED, false)) return;
        if (FloatService.running) { remember(context, true); return; }
        if (!Settings.canDrawOverlays(context)) return;
        try {
            context.startForegroundService(new Intent(context, FloatService.class));
        } catch (IllegalStateException | SecurityException restricted) {
            // Retry when the user opens the app if the device blocks background restoration.
            android.util.Log.w("KongyeUpdate", "Floating ball restoration deferred", restricted);
        }
    }

    /** The replacement broadcast can be delayed or missed by a device's installer. */
    static void resumeOnOpen(Context context) {
        if (!enabled(context) || FloatService.running) return;
        Prefs.of(context).edit().putBoolean(PENDING, true).commit();
        resumePending(context);
    }
}
