package com.kongye.floatball;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/** One stroked eye changes shape without replacing the view during a toggle. */
final class PrivacyEyeView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path eyelid = new Path();
    private final Path bottom = new Path();
    private ValueAnimator animation;
    private float openness;

    PrivacyEyeView(Context context, boolean hidden) {
        super(context);
        openness = hidden ? 0 : 1;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setHidden(boolean hidden) {
        if (animation != null) animation.cancel();
        float target = hidden ? 0 : 1;
        if (!isAttachedToWindow() || !ValueAnimator.areAnimatorsEnabled()) {
            openness = target;
            invalidate();
            return;
        }
        animation = ValueAnimator.ofFloat(openness, target);
        animation.setDuration(260);
        animation.setInterpolator(new AccelerateDecelerateInterpolator());
        animation.addUpdateListener(value -> {
            openness = (float) value.getAnimatedValue();
            invalidate();
        });
        animation.start();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(getWidth() / 24f, getHeight() / 24f);
        paint.setColor(Ui.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.7f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        float edge = 8 + 4 * openness;
        float upper = 16 - 15 * openness;
        float lower = 16 + 7 * openness;
        eyelid.reset();
        eyelid.moveTo(2, edge);
        eyelid.quadTo(12, upper, 22, edge);
        canvas.drawPath(eyelid, paint);
        if (openness > 0) {
            eyelid.quadTo(12, lower, 2, edge);
            eyelid.close();
            canvas.save();
            canvas.clipPath(eyelid);
            canvas.drawCircle(12, 12, 4.2f, paint);
            canvas.restore();
            paint.setAlpha(Math.round(255 * openness));
            bottom.reset();
            bottom.moveTo(2, edge);
            bottom.quadTo(12, lower, 22, edge);
            canvas.drawPath(bottom, paint);
            paint.setAlpha(255);
        }

        // The same three lashes rotate from beneath the closed lid to above the open eye.
        float lashY = edge + .28875f * (upper - edge);
        float lashTip = 15 - 11 * openness;
        canvas.drawLine(5.5f, lashY, 4, lashTip, paint);
        canvas.drawLine(12, 12 - 5.5f * openness, 12, 16.5f - 13 * openness, paint);
        canvas.drawLine(18.5f, lashY, 20, lashTip, paint);
        canvas.restore();
    }

    @Override protected void onDetachedFromWindow() {
        if (animation != null) animation.cancel();
        super.onDetachedFromWindow();
    }
}
