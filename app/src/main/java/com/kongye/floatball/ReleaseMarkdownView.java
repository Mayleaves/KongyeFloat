package com.kongye.floatball;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineHeightSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.View;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Native, selectable Markdown body shared by history and the check-update dialog. */
final class ReleaseMarkdownView {
    static TextView create(Context context, String source) {
        return create(context, source, 14.5f, Ui.INK);
    }

    static TextView create(Context context, String source, float size, int color) {
        MarkdownTextView view = new MarkdownTextView(context);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setFontFeatureSettings("kern");
        view.setIncludeFontPadding(false);

        ReleaseMarkdown.Document document = ReleaseMarkdown.parse(
                source == null || source.trim().isEmpty() ? "此版本未提供更新说明。" : source
        );
        Styled styled = transform(context, document);
        view.inlineCodes.addAll(styled.inlineCodes);
        view.codeBlocks.addAll(styled.codeBlocks);
        view.quotes.addAll(styled.quotes);
        view.rules.addAll(styled.rules);
        view.setText(styled.text);
        view.setTextIsSelectable(true);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setLinksClickable(true);
        view.setHighlightColor(Ui.dark ? 0x4483CCA8 : 0x33287A59);
        view.setLineSpacing(Ui.dp(context, 4), 1.08f);
        return view;
    }

    private static final class Range {
        final int start, end;
        Range(int start, int end) { this.start = start; this.end = end; }
    }

    private static final class ListLine {
        final int start, end, indent;
        final int kind; // 0 = bullet, 1 = ordered, 2 = unchecked, 3 = checked
        final String label;
        ListLine(int start, int end, int indent, int kind, String label) {
            this.start = start; this.end = end; this.indent = indent; this.kind = kind; this.label = label;
        }
    }

    private static final class SpacerLine {
        final int start, end, height;
        SpacerLine(int start, int end, int height) { this.start = start; this.end = end; this.height = height; }
    }

    private static final class Styled {
        final SpannableString text;
        final List<Range> inlineCodes = new ArrayList<>();
        final List<Range> codeBlocks = new ArrayList<>();
        final List<Range> quotes = new ArrayList<>();
        final List<Range> rules = new ArrayList<>();
        Styled(SpannableString text) { this.text = text; }
    }

    private static Styled transform(Context context, ReleaseMarkdown.Document doc) {
        String src = doc.text;
        int len = src.length();
        List<ReleaseMarkdown.Mark> rawCodeBlocks = new ArrayList<>();
        List<ReleaseMarkdown.Mark> rawHeadings = new ArrayList<>();
        Map<Integer, List<ReleaseMarkdown.Mark>> inlineStart = new HashMap<>();
        Map<Integer, List<ReleaseMarkdown.Mark>> inlineEnd = new HashMap<>();

        for (ReleaseMarkdown.Mark m : doc.marks) {
            if (m.kind == ReleaseMarkdown.Kind.CODE_BLOCK) rawCodeBlocks.add(m);
            else if (m.kind == ReleaseMarkdown.Kind.HEADING) rawHeadings.add(m);
        }
        for (ReleaseMarkdown.Mark m : doc.marks) {
            if (m.kind == ReleaseMarkdown.Kind.CODE && !insideAny(m.start, rawCodeBlocks) && m.start < m.end) {
                inlineStart.computeIfAbsent(m.start, k -> new ArrayList<>()).add(m);
                inlineEnd.computeIfAbsent(m.end, k -> new ArrayList<>()).add(m);
            }
        }

        StringBuilder out = new StringBuilder(len + 64);
        int[] map = new int[len + 1];
        Map<ReleaseMarkdown.Mark, Range> codePills = new HashMap<>();
        Map<ReleaseMarkdown.Mark, Integer> codeOpen = new HashMap<>();
        List<ListLine> listLines = new ArrayList<>();
        List<SpacerLine> spacers = new ArrayList<>();
        List<Range> rules = new ArrayList<>();

        int lineStart = 0;
        while (lineStart <= len) {
            int lineEnd = src.indexOf('\n', lineStart);
            if (lineEnd < 0) lineEnd = len;
            boolean inBlock = insideAny(lineStart, rawCodeBlocks);

            if (!inBlock && lineStart == lineEnd && lineEnd < len) {
                // Empty spacer paragraph inside \n\n.
                int newStart = out.length();
                map[lineStart] = newStart;
                boolean afterHeading = touchesHeadingEnd(lineStart - 1, rawHeadings);
                boolean beforeHeading = touchesHeadingStart(lineEnd + 1, rawHeadings);
                int gapDp = afterHeading ? 5 : beforeHeading ? 13 : 8;
                out.append('\n');
                spacers.add(new SpacerLine(newStart, out.length(), Ui.dp(context, gapDp)));
                lineStart = lineEnd + 1;
                continue;
            }

            if (!inBlock && lineEnd - lineStart == 8 && "────────".regionMatches(0, src, lineStart, 8)) {
                int newStart = out.length();
                for (int i = lineStart; i <= lineEnd; i++) map[i] = newStart;
                out.append((char) 0x200B);
                int newEnd = out.length();
                if (lineEnd < len) out.append('\n');
                rules.add(new Range(newStart, lineEnd < len ? out.length() : newEnd));
                lineStart = lineEnd + 1;
                continue;
            }

            ParsedPrefix prefix = !inBlock ? parseListPrefix(src, lineStart, lineEnd) : null;
            int copyFrom = prefix != null ? prefix.contentStart : lineStart;
            int newLineStart = out.length();
            if (prefix != null) {
                for (int i = lineStart; i < copyFrom; i++) map[i] = newLineStart;
            }

            for (int pos = copyFrom; pos < lineEnd; pos++) {
                List<ReleaseMarkdown.Mark> opens = inlineStart.get(pos);
                if (opens != null) {
                    for (ReleaseMarkdown.Mark m : opens) {
                        codeOpen.put(m, out.length());
                        out.append((char) 0x2009);
                    }
                }
                map[pos] = out.length();
                out.append(src.charAt(pos));
                List<ReleaseMarkdown.Mark> closes = inlineEnd.get(pos + 1);
                if (closes != null) {
                    map[pos + 1] = out.length();
                    for (ReleaseMarkdown.Mark m : closes) {
                        out.append((char) 0x2009);
                        Integer pillStart = codeOpen.remove(m);
                        if (pillStart != null) codePills.put(m, new Range(pillStart, out.length()));
                    }
                }
            }
            if (!inlineEnd.containsKey(lineEnd)) map[lineEnd] = out.length();

            if (prefix != null && out.length() == newLineStart) out.append((char) 0x200B);
            int newLineEnd = out.length();
            if (lineEnd < len) out.append('\n');
            int paraEnd = out.length();

            if (prefix != null) {
                listLines.add(new ListLine(newLineStart, paraEnd, prefix.indent, prefix.kind, prefix.label));
            }
            if (lineEnd == len) break;
            lineStart = lineEnd + 1;
        }
        map[len] = out.length();

        SpannableString text = new SpannableString(out.toString());
        Styled result = new Styled(text);
        result.rules.addAll(rules);

        for (SpacerLine spacer : spacers) {
            setParagraphSpan(text, new FixedSpacerSpan(spacer.height), spacer.start, spacer.end);
        }
        for (Range rule : rules) {
            setParagraphSpan(text, new FixedSpacerSpan(Ui.dp(context, 14)), rule.start, rule.end);
        }
        for (int i = 0; i < listLines.size(); i++) {
            ListLine item = listLines.get(i);
            setParagraphSpan(text, new ListMarkerSpan(context, item.indent, item.kind, item.label), item.start, item.end);
            boolean consecutiveNext = i + 1 < listLines.size() && listLines.get(i + 1).start == item.end;
            if (consecutiveNext) {
                setParagraphSpan(text, new BlockPadSpan(0, Ui.dp(context, 6)), item.start, item.end);
            }
        }

        for (ReleaseMarkdown.Mark mark : doc.marks) {
            int s = map[Math.max(0, Math.min(len, mark.start))];
            int e = map[Math.max(0, Math.min(len, mark.end))];
            if (s >= e && mark.kind != ReleaseMarkdown.Kind.CODE) continue;
            switch (mark.kind) {
                case HEADING -> {
                    text.setSpan(new StyleSpan(Typeface.BOLD), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    text.setSpan(new ForegroundColorSpan(Ui.INK), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    int level = 2;
                    try { level = Integer.parseInt(mark.value); } catch (NumberFormatException ignored) { }
                    text.setSpan(new RelativeSizeSpan(level == 1 ? 1.28f : level == 2 ? 1.14f : 1.05f), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if (level == 2) {
                        setParagraphSpan(text, new SectionBarSpan(Ui.dp(context, 3.5f), Ui.dp(context, 9), Ui.GREEN), s, e);
                    }
                }
                case BOLD -> text.setSpan(new StyleSpan(Typeface.BOLD), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                case ITALIC -> text.setSpan(new StyleSpan(Typeface.ITALIC), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                case STRIKE -> text.setSpan(new StrikethroughSpan(), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                case CODE -> {
                    Range pill = codePills.get(mark);
                    int cs = pill != null ? pill.start : s;
                    int ce = pill != null ? pill.end : e;
                    if (cs < ce) {
                        text.setSpan(new TypefaceSpan("monospace"), cs, ce, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        text.setSpan(new RelativeSizeSpan(.90f), cs, ce, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        text.setSpan(new ForegroundColorSpan(Ui.dark ? 0xFFA5DFBF : 0xFF1F543C), cs, ce, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        result.inlineCodes.add(new Range(cs, ce));
                    }
                }
                case CODE_BLOCK -> {
                    text.setSpan(new TypefaceSpan("monospace"), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    text.setSpan(new RelativeSizeSpan(.88f), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    text.setSpan(new ForegroundColorSpan(Ui.dark ? 0xFFD5E4DA : 0xFF233A2E), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    setParagraphSpan(text, new LeadingMarginSpan.Standard(Ui.dp(context, 12)), s, e);
                    setParagraphSpan(text, new BlockPadSpan(Ui.dp(context, 8), Ui.dp(context, 8)), s, e);
                    result.codeBlocks.add(new Range(s, trimTrailingNewline(text, s, e)));
                }
                case QUOTE -> {
                    text.setSpan(new ForegroundColorSpan(Ui.MUTED), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    setParagraphSpan(text, new LeadingMarginSpan.Standard(Ui.dp(context, 14)), s, e);
                    setParagraphSpan(text, new BlockPadSpan(Ui.dp(context, 6), Ui.dp(context, 6)), s, e);
                    result.quotes.add(new Range(s, trimTrailingNewline(text, s, e)));
                }
                case LINK -> {
                    final String address = mark.value;
                    text.setSpan(new ClickableSpan() {
                        @Override public void onClick(View widget) {
                            String safe = ReleaseMarkdown.safeLink(address);
                            if (safe == null) return;
                            try {
                                Context target = widget.getContext();
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(safe)).addCategory(Intent.CATEGORY_BROWSABLE);
                                if (!(target instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                target.startActivity(intent);
                            } catch (RuntimeException unavailable) { Feedback.show(widget.getContext(), "无法打开此链接"); }
                        }
                        @Override public void updateDrawState(TextPaint paint) {
                            paint.setColor(Ui.GREEN);
                            paint.setUnderlineText(true);
                        }
                    }, s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
        return result;
    }

    private static final class ParsedPrefix {
        final int contentStart, indent, kind;
        final String label;
        ParsedPrefix(int contentStart, int indent, int kind, String label) {
            this.contentStart = contentStart; this.indent = indent; this.kind = kind; this.label = label;
        }
    }

    private static ParsedPrefix parseListPrefix(String src, int start, int end) {
        int p = start, spaces = 0;
        while (p < end && src.charAt(p) == ' ') { spaces++; p++; }
        int indent = Math.min(6, spaces / 2);
        if (p + 2 <= end) {
            char first = src.charAt(p), second = src.charAt(p + 1);
            if (second == ' ') {
                if (first == '•') return new ParsedPrefix(p + 2, indent, 0, "•");
                if (first == '☐') return new ParsedPrefix(p + 2, indent, 2, "");
                if (first == '☑') return new ParsedPrefix(p + 2, indent, 3, "");
            }
        }
        int d = p;
        while (d < end && d - p < 6 && Character.isDigit(src.charAt(d))) d++;
        if (d > p && d + 1 < end && (src.charAt(d) == '.' || src.charAt(d) == ')') && src.charAt(d + 1) == ' ') {
            return new ParsedPrefix(d + 2, indent, 1, src.substring(p, d + 1));
        }
        return null;
    }

    private static boolean insideAny(int pos, List<ReleaseMarkdown.Mark> blocks) {
        for (ReleaseMarkdown.Mark b : blocks) if (pos >= b.start && pos < b.end) return true;
        return false;
    }

    private static boolean touchesHeadingEnd(int newlineBefore, List<ReleaseMarkdown.Mark> headings) {
        for (ReleaseMarkdown.Mark h : headings) if (h.end == newlineBefore) return true;
        return false;
    }

    private static boolean touchesHeadingStart(int nextLineStart, List<ReleaseMarkdown.Mark> headings) {
        for (ReleaseMarkdown.Mark h : headings) if (h.start == nextLineStart) return true;
        return false;
    }

    private static int trimTrailingNewline(CharSequence text, int start, int end) {
        int e = Math.min(text.length(), end);
        while (e > start && text.charAt(e - 1) == '\n') e--;
        return Math.max(start, e);
    }

    private static void setParagraphSpan(Spannable s, Object span, int start, int end) {
        int len = s.length();
        if (len == 0) return;
        int pStart = Math.max(0, Math.min(len, start));
        while (pStart > 0 && s.charAt(pStart - 1) != '\n') pStart--;
        int pEnd = Math.max(pStart, Math.min(len, end));
        while (pEnd < len && s.charAt(pEnd - 1) != '\n') pEnd++;
        if (pStart < pEnd) s.setSpan(span, pStart, pEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static final class FixedSpacerSpan implements LineHeightSpan {
        private final int height;
        FixedSpacerSpan(int height) { this.height = height; }
        @Override public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight, Paint.FontMetricsInt fm) {
            fm.top = fm.ascent = -height;
            fm.bottom = fm.descent = 0;
        }
    }

    private static final class BlockPadSpan implements LineHeightSpan {
        private final int topPad, bottomPad;
        BlockPadSpan(int topPad, int bottomPad) { this.topPad = topPad; this.bottomPad = bottomPad; }
        @Override public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight, Paint.FontMetricsInt fm) {
            Spanned sp = (Spanned) text;
            if (topPad > 0 && start == sp.getSpanStart(this)) {
                fm.ascent -= topPad;
                fm.top -= topPad;
            }
            if (bottomPad > 0 && end == sp.getSpanEnd(this)) {
                fm.descent += bottomPad;
                fm.bottom += bottomPad;
            }
        }
    }

    private static final class SectionBarSpan implements LeadingMarginSpan {
        private final float barWidth;
        private final int gap, color;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        SectionBarSpan(int barWidth, int gap, int color) {
            this.barWidth = Math.max(2f, barWidth);
            this.gap = gap;
            this.color = color;
        }
        @Override public int getLeadingMargin(boolean first) { return Math.round(barWidth) + gap; }
        @Override public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom,
                                                CharSequence text, int start, int end, boolean first, Layout layout) {
            if (!first) return;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            Paint.FontMetrics fm = p.getFontMetrics();
            float barTop = baseline + fm.ascent * 0.82f;
            float barBottom = baseline + fm.descent * 0.55f;
            float left = dir > 0 ? x : x - barWidth;
            c.drawRoundRect(left, barTop, left + barWidth, barBottom, barWidth / 2f, barWidth / 2f, paint);
        }
    }

    private static final class ListMarkerSpan implements LeadingMarginSpan {
        private final int indentPx, marginPx, kind;
        private final String label;
        private final float dotRadius, strokeWidth, boxSize, boxRadius;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path checkPath = new Path();

        ListMarkerSpan(Context c, int indent, int kind, String label) {
            this.indentPx = indent * Ui.dp(c, 14);
            this.kind = kind;
            this.label = label;
            int markerWidth = kind == 0 ? Ui.dp(c, 16) : kind == 1 && label.length() > 2 ? Ui.dp(c, 28) : Ui.dp(c, 22);
            this.marginPx = indentPx + markerWidth;
            this.dotRadius = Math.max(2f, Ui.dp(c, 2.5f));
            this.strokeWidth = Math.max(1.5f, Ui.dp(c, 1.4f));
            this.boxSize = Ui.dp(c, 13);
            this.boxRadius = Ui.dp(c, 3.5f);
        }

        @Override public int getLeadingMargin(boolean first) { return marginPx; }

        @Override public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom,
                                                CharSequence text, int start, int end, boolean first, Layout layout) {
            if (!first) return;
            Paint.FontMetrics fm = p.getFontMetrics();
            float centerY = baseline + (fm.ascent + fm.descent) * 0.5f;
            float baseLeft = x + dir * indentPx;
            if (kind == 0) {
                float cx = baseLeft + dir * (dotRadius + strokeWidth);
                if (indentPx == 0) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Ui.GREEN);
                    c.drawCircle(cx, centerY, dotRadius, paint);
                } else {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(strokeWidth);
                    paint.setColor(Ui.MUTED);
                    c.drawCircle(cx, centerY, dotRadius * 0.88f, paint);
                }
            } else if (kind == 1) {
                Typeface oldTf = p.getTypeface();
                int oldColor = p.getColor();
                float oldSize = p.getTextSize();
                p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                p.setColor(Ui.GREEN);
                p.setTextSize(oldSize * 0.92f);
                c.drawText(label, baseLeft, baseline, p);
                p.setTypeface(oldTf);
                p.setColor(oldColor);
                p.setTextSize(oldSize);
            } else {
                float left = baseLeft;
                float boxTop = centerY - boxSize * 0.5f;
                if (kind == 3) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Ui.GREEN);
                    c.drawRoundRect(left, boxTop, left + boxSize, boxTop + boxSize, boxRadius, boxRadius, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(strokeWidth);
                    paint.setStrokeCap(Paint.Cap.ROUND);
                    paint.setStrokeJoin(Paint.Join.ROUND);
                    paint.setColor(Ui.dark ? 0xFF102E21 : 0xFFFFFFFF);
                    checkPath.reset();
                    checkPath.moveTo(left + boxSize * 0.24f, centerY + boxSize * 0.02f);
                    checkPath.lineTo(left + boxSize * 0.44f, centerY + boxSize * 0.22f);
                    checkPath.lineTo(left + boxSize * 0.78f, centerY - boxSize * 0.20f);
                    c.drawPath(checkPath, paint);
                } else {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(strokeWidth);
                    paint.setColor(Ui.MUTED);
                    c.drawRoundRect(left, boxTop, left + boxSize, boxTop + boxSize, boxRadius, boxRadius, paint);
                }
            }
        }
    }

    @android.annotation.SuppressLint("AppCompatCustomView")
    private static final class MarkdownTextView extends TextView {
        final List<Range> inlineCodes = new ArrayList<>();
        final List<Range> codeBlocks = new ArrayList<>();
        final List<Range> quotes = new ArrayList<>();
        final List<Range> rules = new ArrayList<>();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        MarkdownTextView(Context context) { super(context); }

        @Override protected void onDraw(Canvas canvas) {
            Layout layout = getLayout();
            CharSequence cs = getText();
            if (layout != null && cs != null && cs.length() > 0) {
                Context c = getContext();
                int len = cs.length();
                float width = layout.getWidth();
                canvas.save();
                canvas.translate(getTotalPaddingLeft(), getTotalPaddingTop());

                // 1. Full-width fenced code blocks
                int blockFill = Ui.dark ? 0xFF131B17 : 0xFFF1F5F1;
                int blockStroke = Ui.LINE;
                float blockRadius = Ui.dp(c, 10);
                for (Range r : codeBlocks) {
                    if (r.start >= len) continue;
                    int firstLine = layout.getLineForOffset(Math.min(len - 1, r.start));
                    int lastLine = layout.getLineForOffset(Math.min(len - 1, Math.max(r.start, r.end)));
                    float top = layout.getLineTop(firstLine) + Ui.dp(c, 2);
                    float bottom = layout.getLineBottom(lastLine) - Ui.dp(c, 2);
                    rect.set(0, top, width, bottom);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(blockFill);
                    canvas.drawRoundRect(rect, blockRadius, blockRadius, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(1f, Ui.dp(c, .8f)));
                    paint.setColor(blockStroke);
                    canvas.drawRoundRect(rect, blockRadius, blockRadius, paint);
                }

                // 2. Blockquotes with soft wash and rounded green rail
                int quoteFill = Ui.dark ? 0xFF16221B : 0xFFF2F7F3;
                float quoteRadius = Ui.dp(c, 8);
                float railWidth = Math.max(2f, Ui.dp(c, 3.5f));
                for (Range r : quotes) {
                    if (r.start >= len) continue;
                    int firstLine = layout.getLineForOffset(Math.min(len - 1, r.start));
                    int lastLine = layout.getLineForOffset(Math.min(len - 1, Math.max(r.start, r.end)));
                    float top = layout.getLineTop(firstLine) + Ui.dp(c, 1);
                    float bottom = layout.getLineBottom(lastLine) - Ui.dp(c, 1);
                    rect.set(0, top, width, bottom);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(quoteFill);
                    canvas.drawRoundRect(rect, quoteRadius, quoteRadius, paint);
                    paint.setColor(Ui.GREEN);
                    canvas.drawRoundRect(0, top + Ui.dp(c, 2), railWidth, bottom - Ui.dp(c, 2), railWidth / 2f, railWidth / 2f, paint);
                }

                // 3. Horizontal rules
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Ui.LINE);
                for (Range r : rules) {
                    if (r.start >= len) continue;
                    int line = layout.getLineForOffset(Math.min(len - 1, r.start));
                    float cy = (layout.getLineTop(line) + layout.getLineBottom(line)) * 0.5f;
                    canvas.drawRect(0, cy, width, cy + Math.max(1f, Ui.dp(c, 1)), paint);
                }

                // 4. Inline code pills
                int codeFill = Ui.dark ? 0xFF223329 : 0xFFEAF2EC;
                int codeBorder = Ui.dark ? 0xFF324A3C : 0xFFD2E2D6;
                float pillRadius = Ui.dp(c, 5.5f);
                Paint.FontMetrics textFm = getPaint().getFontMetrics();
                for (Range r : inlineCodes) {
                    if (r.start >= len || r.start >= r.end) continue;
                    int endIdx = Math.min(len, r.end);
                    int firstLine = layout.getLineForOffset(r.start);
                    int lastLine = layout.getLineForOffset(Math.max(r.start, endIdx - 1));
                    boolean endOnLastLine = endIdx == len || layout.getLineForOffset(endIdx) == lastLine;
                    for (int line = firstLine; line <= lastLine; line++) {
                        float left = line == firstLine ? layout.getPrimaryHorizontal(r.start) : layout.getLineLeft(line);
                        float right = line == lastLine && endOnLastLine ? layout.getPrimaryHorizontal(endIdx) : layout.getLineRight(line);
                        if (right < left) { float t = left; left = right; right = t; }
                        if (right - left < 1f) continue;
                        int base = layout.getLineBaseline(line);
                        float top = base + textFm.ascent * 0.94f - Ui.dp(c, 1);
                        float bottom = base + textFm.descent * 0.94f + Ui.dp(c, 1.5f);
                        rect.set(left, top, right, bottom);
                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(codeFill);
                        canvas.drawRoundRect(rect, pillRadius, pillRadius, paint);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(Math.max(1f, Ui.dp(c, .7f)));
                        paint.setColor(codeBorder);
                        canvas.drawRoundRect(rect, pillRadius, pillRadius, paint);
                    }
                }

                canvas.restore();
            }
            super.onDraw(canvas);
        }
    }

    private ReleaseMarkdownView() { }
}
