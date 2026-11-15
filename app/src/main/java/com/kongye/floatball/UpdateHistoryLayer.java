package com.kongye.floatball;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Temporarily supplies the live modal page with its settings backdrop during a page slide. */
final class UpdateHistoryLayer {
    private final View backdrop;
    private final float backdropAlpha;
    private final ImageView image;

    private UpdateHistoryLayer(View backdrop, ImageView image) {
        this.backdrop = backdrop;
        this.backdropAlpha = backdrop.getAlpha();
        this.image = image;
    }

    static UpdateHistoryLayer show(Activity owner, FrameLayout host) {
        if (host == null || host.getWidth() <= 0 || host.getHeight() <= 0) return null;
        View backdrop = AppMotion.backdropContent();
        if (backdrop == null) return null;
        Bitmap pixels;
        try {
            pixels = Bitmap.createBitmap(host.getWidth(), host.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(pixels);
            int[] origin = new int[2];
            host.getLocationOnScreen(origin);
            drawAt(canvas, backdrop, origin);
        } catch (RuntimeException | OutOfMemoryError unavailable) {
            return null;
        }
        ImageView image = new ImageView(owner);
        image.setImageBitmap(pixels);
        image.setScaleType(ImageView.ScaleType.FIT_XY);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        UpdateHistoryLayer layer = new UpdateHistoryLayer(backdrop, image);
        host.addView(image, 0, new FrameLayout.LayoutParams(-1, -1));
        backdrop.setAlpha(0);
        return layer;
    }

    private static void drawAt(Canvas canvas, View view, int[] origin) {
        int[] position = new int[2];
        view.getLocationOnScreen(position);
        canvas.save();
        canvas.translate(position[0] - origin[0], position[1] - origin[1]);
        view.draw(canvas);
        canvas.restore();
    }

    void release() {
        backdrop.setAlpha(backdropAlpha);
        Runnable remove = () -> {
            image.setImageDrawable(null);
            if (image.getParent() instanceof ViewGroup parent) parent.removeView(image);
        };
        if (image.isAttachedToWindow() && ValueAnimator.areAnimatorsEnabled()) {
            image.animate().alpha(0).setDuration(120).withEndAction(remove).start();
        } else remove.run();
    }
}
