package com.kongye.floatball;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityWindowInfo;

/** Supplements input-method layers omitted by some devices' full-display projection. */
final class RecordingKeyboard implements AutoCloseable {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final int width, height;
    private volatile boolean closed;
    private volatile int windowId = -1;
    private volatile boolean available;
    private long requestedAt;
    private boolean pending;
    private Bitmap latest;

    RecordingKeyboard(int width, int height) {
        this.width = width;
        this.height = height;
        if (Build.VERSION.SDK_INT >= 34) main.post(poll);
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (closed || Build.VERSION.SDK_INT < 34) return;
            AssistService service = AssistService.instance;
            int visible = -1;
            Rect bounds = new Rect();
            if (service != null) {
                for (AccessibilityWindowInfo window : service.getWindows()) {
                    if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        visible = window.getId();
                        window.getBoundsInScreen(bounds);
                    }
                }
            }
            if (visible != windowId) {
                available = false;
                windowId = visible;
                replace(null);
            }
            long now = SystemClock.uptimeMillis();
            if (visible >= 0 && !pending && now - requestedAt >= 400) {
                pending = true;
                requestedAt = now;
                int requestedWindow = visible;
                try {
                    service.takeScreenshotOfWindow(visible, service.getMainExecutor(),
                        new AccessibilityService.TakeScreenshotCallback() {
                    @Override public void onSuccess(AccessibilityService.ScreenshotResult result) {
                        pending = false;
                        try (HardwareBuffer buffer = result.getHardwareBuffer()) {
                            Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                            if (hardware == null) return;
                            try {
                                if (closed || windowId != requestedWindow) return;
                                Bitmap software = hardware.copy(Bitmap.Config.ARGB_8888, false);
                                if (software == null) return;
                                try {
                                    Bitmap frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                                    // Input-method screenshots include the transparent part of
                                    // their window above the visible keys. Preserve that geometry.
                                    new Canvas(frame).drawBitmap(software, bounds.left,
                                            bounds.bottom - software.getHeight(), null);
                                    replace(frame);
                                    available = true;
                                } finally { software.recycle(); }
                            } finally { hardware.recycle(); }
                        } catch (RuntimeException | OutOfMemoryError unavailable) {
                            available = false;
                            replace(null);
                        }
                    }
                    @Override public void onFailure(int error) {
                        pending = false;
                        if (error == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) return;
                        available = false;
                        replace(null);
                    }
                    });
                } catch (RuntimeException disconnected) {
                    pending = false;
                    available = false;
                    replace(null);
                }
            }
            main.postDelayed(this, 80);
        }
    };

    private synchronized void replace(Bitmap frame) {
        if (closed && frame != null) {
            frame.recycle();
            return;
        }
        if (latest != null) latest.recycle();
        latest = frame;
    }

    synchronized Bitmap take() {
        Bitmap frame = latest;
        latest = null;
        return frame;
    }

    boolean visible() { return !closed && available && windowId >= 0 && AssistService.instance != null; }

    @Override public void close() {
        closed = true;
        main.removeCallbacks(poll);
        replace(null);
    }
}
