package com.kongye.floatball;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;

/** Keeps the same preview available when the system declines to open its activity. */
final class RecordingPreviewFallback {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static RecordingPreviewFallback pending;
    private final File source;
    private RecordingPreview overlay;

    private RecordingPreviewFallback(File source) {
        this.source = source;
    }

    static void offer(Context context, File source, RecordingSpec spec) {
        if (pending != null) pending.dismiss();
        Context app = context.getApplicationContext();
        RecordingPreviewFallback next = new RecordingPreviewFallback(source);
        pending = next;
        MAIN.postDelayed(() -> {
            if (pending != next) return;
            if (app.getSystemService(KeyguardManager.class).isKeyguardLocked()) {
                next.dismiss();
                return;
            }
            next.overlay = new RecordingPreview(app, source, spec.fps);
            next.overlay.show(spec.gif);
        }, 450);
    }

    static void activityShown(File source) {
        if (pending != null && pending.source.equals(source)) pending.dismiss();
    }

    static void overlayClosed(RecordingPreview overlay) {
        if (pending != null && pending.overlay == overlay) pending.dismiss();
    }

    private void dismiss() {
        if (pending == this) pending = null;
        if (overlay != null) overlay.dispose();
    }
}
