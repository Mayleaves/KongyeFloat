package com.kongye.floatball;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.view.View;
import java.util.*;

/** Draws translated blocks at source anchors; glyph colour and paragraph boundaries are independent. */
final class SubtitleCaptionView extends View {
    private final ArrayList<StaticLayout> layouts = new ArrayList<>();
    private final ArrayList<PointF> positions = new ArrayList<>();
    private final ArrayList<RectF> limits = new ArrayList<>();
    private SubtitleFrame frame = SubtitleFrame.EMPTY;
    private List<String> translated = List.of();
    private String language = "zh";
    private int fontScale = 100;
    private int textColor;
    private final Paint fill = new Paint();

    SubtitleCaptionView(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }

    void show(SubtitleFrame frame, List<String> translated, String language, int background, int fontScale, int textColor) {
        boolean changed = this.frame.width != frame.width || this.frame.height != frame.height
                || !this.frame.blocks.equals(frame.blocks) || !this.translated.equals(translated)
                || !this.language.equals(language) || this.fontScale != fontScale || this.textColor != textColor;
        this.frame = frame; this.translated = List.copyOf(translated); this.language = language;
        this.fontScale = fontScale;
        this.textColor = textColor;
        fill.setColor(background);
        if (changed) rebuild();
        invalidate();
    }

    static float fontSize(TextPaint base, String text, float sourceHeight, int percent) {
        TextPaint probe = new TextPaint(base);
        probe.setTextSize(100);
        Rect bounds = new Rect();
        String language = base.getTextLocale().getLanguage();
        String sample = switch (language) {
            case "zh" -> "中字";
            case "ja" -> "日本語かな";
            case "ko" -> "한글";
            default -> OcrPunctuation.containsHan(text) ? "中字" : "Hgp";
        };
        probe.getTextBounds(sample, 0, sample.length(), bounds);
        float measured = bounds.height() > 0 ? bounds.height() : 85;
        return Math.max(6, sourceHeight * percent / measured);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { rebuild(); }

    private void rebuild() {
        layouts.clear(); positions.clear(); limits.clear();
        if (getWidth() <= 0 || getHeight() <= 0 || translated.size() != frame.blocks.size()) return;
        float sx = getWidth() / (float) frame.width, sy = getHeight() / (float) frame.height;
        for (SubtitleLayout.Block block : frame.blocks) limits.add(bounds(block, sx, sy));
        // If one full paragraph needs more room, scale comparable text together.
        for (int zoom = 100; zoom >= 70; zoom -= 5) {
            layouts.clear();
            positions.clear();
            boolean fits = true;
            for (int i = 0; i < frame.blocks.size(); i++) {
                SubtitleLayout.Block block = frame.blocks.get(i);
                RectF area = limits.get(i);
                TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
                paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                paint.setTextLocale(Locale.forLanguageTag(language));
                paint.setColor(textColor == 0 ? block.color() : textColor);
                String text = translated.get(i);
                float size = fontSize(paint, text, block.lineHeight() * sy, Math.round(fontScale * zoom / 100f));
                paint.setTextSize(size);
                StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, Math.max(1, (int) area.width()))
                        .setIncludePad(false).setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
                        .setLineSpacing(size * .12f, 1).build();
                Rect first = new Rect(), last = new Rect();
                int lastLine = layout.getLineCount() - 1;
                paint.getTextBounds(text, 0, layout.getLineVisibleEnd(0), first);
                paint.getTextBounds(text, layout.getLineStart(lastLine), layout.getLineVisibleEnd(lastLine), last);
                float drawTop = area.top - layout.getLineBaseline(0) - first.top;
                boolean hasFullSourceLine = area.height() >= block.lineHeight() * sy * .78f;
                if (hasFullSourceLine && drawTop + layout.getLineBaseline(lastLine) + last.bottom > area.bottom) fits = false;
                layouts.add(layout);
                positions.add(new PointF(area.left, drawTop));
            }
            if (fits) break;
        }
    }

    private RectF bounds(SubtitleLayout.Block block, float sx, float sy) {
        float right = getWidth(), bottom = getHeight();
        for (SubtitleLayout.Block other : frame.blocks) {
            if (other.left() >= block.right() && other.top() < block.bottom() && other.bottom() > block.top())
                right = Math.min(right, other.left() * sx);
            if (other.top() > block.top() && other.top() >= block.bottom() - block.lineHeight() * .25f
                    && other.left() < block.right() && other.right() > block.left())
                bottom = Math.min(bottom, other.top() * sy);
        }
        return new RectF(block.left() * sx, block.top() * sy, right, bottom);
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), fill);
        for (int i = 0; i < layouts.size(); i++) {
            canvas.save();
            canvas.clipRect(limits.get(i));
            PointF at = positions.get(i);
            canvas.translate(at.x, at.y);
            layouts.get(i).draw(canvas);
            canvas.restore();
        }
    }
}
