package com.kongye.floatball;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** High-contrast orbital loading indicator rendered over the floating ball while comic translation is running. */
final class BallLoadingIndicator extends View {
    private static final long MIN_VISIBLE_MS = 450L;
    private final boolean customArt;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Paint platePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ringBounds = new RectF();
    private final Runnable delayedHide = this::hideNow;
    private ValueAnimator spinner;
    private float phase;
    private long shownAt;
    private boolean loading, suppressed;

    BallLoadingIndicator(Context c, boolean customArt) {
        super(c);
        this.customArt = customArt;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(GONE);
        setAlpha(0f);

        // Deep forest green disc so the spinner stands out clearly against both bright manga pages and dark themes.
        platePaint.setStyle(Paint.Style.FILL);
        platePaint.setColor(customArt ? 0xD81B4332 : 0xEB23533D);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(0xE6FFFFFF);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setColor(0x45FFFFFF);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(Color.WHITE);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(0xFFA7E8BE);
    }

    void setLoading(boolean active) {
        if (loading == active) return;
        loading = active;
        main.removeCallbacks(delayedHide);
        if (active) {
            shownAt = SystemClock.uptimeMillis();
            animate().cancel();
            if (!suppressed) setVisibility(VISIBLE);
            startSpinner();
            if (!suppressed) {
                if (ValueAnimator.areAnimatorsEnabled()) {
                    animate().alpha(1f).setDuration(80).setListener(null).start();
                } else {
                    setAlpha(1f);
                }
            }
        } else {
            long elapsed = SystemClock.uptimeMillis() - shownAt;
            long remaining = MIN_VISIBLE_MS - elapsed;
            if (!suppressed && getVisibility() == VISIBLE && remaining > 0 && ValueAnimator.areAnimatorsEnabled()) {
                main.postDelayed(delayedHide, remaining);
            } else {
                hideNow();
            }
        }
    }

    private void hideNow() {
        if (loading) return;
        animate().cancel();
        if (getVisibility() != VISIBLE || !ValueAnimator.areAnimatorsEnabled()) {
            stopImmediate();
            return;
        }
        animate().alpha(0f).setDuration(180).setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (!loading) stopImmediate();
            }
        }).start();
    }

    void setSuppressed(boolean hidden) {
        if (suppressed == hidden) return;
        suppressed = hidden;
        main.removeCallbacks(delayedHide);
        animate().cancel();
        if (hidden) {
            setVisibility(GONE);
            setAlpha(0f);
        } else if (loading) {
            setVisibility(VISIBLE);
            setAlpha(1f);
            startSpinner();
        }
    }

    void stopImmediate() {
        main.removeCallbacks(delayedHide);
        animate().cancel();
        stopSpinner();
        setAlpha(0f);
        setVisibility(GONE);
    }

    private void startSpinner() {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            phase = 0.25f;
            invalidate();
            return;
        }
        if (spinner != null && spinner.isRunning()) return;
        stopSpinner();
        spinner = ValueAnimator.ofFloat(0f, 1f);
        spinner.setDuration(1050);
        spinner.setInterpolator(new LinearInterpolator());
        spinner.setRepeatCount(ValueAnimator.INFINITE);
        spinner.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        spinner.start();
    }

    private void stopSpinner() {
        if (spinner != null) {
            ValueAnimator old = spinner;
            spinner = null;
            old.cancel();
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (loading && !suppressed) startSpinner();
    }

    @Override protected void onDetachedFromWindow() {
        main.removeCallbacks(delayedHide);
        animate().cancel();
        stopSpinner();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float side = Math.min(w, h);
        float cx = w / 2f, cy = h / 2f;

        // Disc backdrop fills the ball (or centered badge on custom art) for instant visibility.
        float plateRadius = customArt ? side * 0.44f : side * 0.49f;
        float borderWidth = Math.max(1f, Ui.dp(getContext(), 1.4f));
        canvas.drawCircle(cx, cy, Math.max(2f, plateRadius - borderWidth * 0.5f), platePaint);
        borderPaint.setStrokeWidth(borderWidth);
        canvas.drawCircle(cx, cy, Math.max(2f, plateRadius - borderWidth * 0.5f), borderPaint);

        // Crisp high-contrast orbital spinner inside the disc.
        float stroke = Math.max(Ui.dp(getContext(), 2.8f), side * 0.11f);
        float ringRadius = Math.max(2f, plateRadius - stroke * 1.15f);
        ringBounds.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius);

        trackPaint.setStrokeWidth(stroke * 0.8f);
        canvas.drawCircle(cx, cy, ringRadius, trackPaint);

        float wave = 0.5f - 0.5f * (float) Math.cos(phase * Math.PI * 2.0);
        float sweep = 95f + 135f * wave;
        float startAngle = phase * 360f - 90f + 55f * wave;
        arcPaint.setStrokeWidth(stroke);
        canvas.drawArc(ringBounds, startAngle, sweep, false, arcPaint);

        // Gentle pulsing core dot so the center feels alive while spinning.
        float coreRadius = Math.max(Ui.dp(getContext(), 2.2f), side * (0.075f + 0.025f * wave));
        canvas.drawCircle(cx, cy, coreRadius, dotPaint);
    }
}
