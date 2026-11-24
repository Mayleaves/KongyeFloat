package com.kongye.floatball;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

/** Visible capture boundary, drawn outside the pixels sent to the encoder. */
final class RecordingRegionView extends View {
    private final Rect area;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF outline;

    RecordingRegionView(Context context, Rect crop) {
        super(context);
        area = new Rect(crop);
        float stroke = Ui.dp(context, 2);
        outline = new RectF(area);
        outline.inset(-stroke / 2, -stroke / 2);
        paint.setColor(Ui.GREEN);
        paint.setStrokeWidth(stroke);
        paint.setStyle(Paint.Style.STROKE);
        paint.setPathEffect(new DashPathEffect(new float[]{Ui.dp(context, 6), Ui.dp(context, 4)}, 0));
        setContentDescription("正在录制的选区边框");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.clipOutRect(area);
        canvas.drawRect(outline, paint);
        canvas.restore();
    }
}
