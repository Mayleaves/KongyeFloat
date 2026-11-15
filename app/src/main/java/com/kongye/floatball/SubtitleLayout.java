package com.kongye.floatball;

import java.util.*;
import java.util.regex.Pattern;

/** Image coordinates and foreground colours survive OCR and translation as separate text blocks. */
final class SubtitleLayout {
    interface Pixels { int color(int x, int y); }
    record Line(TextRegion region, int color) { }
    record Block(String text, int left, int top, int right, int bottom, int color, float lineHeight) { }
    static final int MAX_BLOCKS = 24;
    private static final Pattern LIST_ITEM = Pattern.compile(
            "^(?:(?:\\d+[.．、]\\s*)?[A-Za-z][)）.．]|\\d+[.)．、）]|[•●▪])\\s*");
    private SubtitleLayout() { }

    static List<Block> blocks(List<Line> source, int width, int height) {
        ArrayList<Line> lines = new ArrayList<>();
        for (Line line : source) {
            TextRegion r = line.region;
            if (r.text != null && !r.text.isBlank() && r.width() > 0 && r.height() > 0
                    && r.left >= 0 && r.top >= 0 && r.right <= width && r.bottom <= height) lines.add(line);
        }
        lines.sort(Comparator.comparingInt((Line l) -> l.region.top).thenComparingInt(l -> l.region.left));
        ArrayList<Block> blocks = new ArrayList<>();
        ArrayList<TextRegion> tails = new ArrayList<>();
        int characters = 0;
        for (Line line : lines) {
            if (characters + line.region.text.length() > 6000) break;
            characters += line.region.text.length();
            TextRegion r = line.region;
            int match = -1;
            if (!startsListItem(r.text)) {
                for (int i = blocks.size() - 1; i >= 0; i--) {
                    if (continues(blocks.get(i), tails.get(i), line)) { match = i; break; }
                }
            }
            if (match < 0) {
                if (blocks.size() >= MAX_BLOCKS) break;
                blocks.add(new Block(r.text.trim(), r.left, r.top, r.right, r.bottom, line.color, r.height()));
                tails.add(r);
            } else {
                Block old = blocks.get(match);
                String separator = needsSpace(old.text, r.text) ? " " : "";
                int weight = Math.min(80, old.text.length());
                int nextWeight = Math.min(80, r.text.length());
                float heightEstimate = (old.lineHeight * weight + r.height() * nextWeight) / (weight + nextWeight);
                blocks.set(match, new Block(old.text + separator + r.text.trim(), Math.min(old.left, r.left), old.top,
                        Math.max(old.right, r.right), Math.max(old.bottom, r.bottom), old.color, heightEstimate));
                tails.set(match, r);
            }
        }
        return normalizeLineHeights(blocks);
    }

    private static boolean startsListItem(String text) {
        return LIST_ITEM.matcher(text.stripLeading()).find();
    }

    private static boolean continues(Block previous, TextRegion last, Line line) {
        TextRegion next = line.region;
        float size = previous.lineHeight;
        int gap = next.top - last.bottom;
        if (gap < -size * .25f || gap > size * .95f
                || next.height() < size * .60f || next.height() > size * 1.58f
                || distance(line.color, previous.color) >= 60) return false;

        int indent = next.left - last.left;
        if (startsListItem(previous.text)) {
            return indent >= -size * .8f && indent <= size * 4
                    && next.left < last.right + size * .25f;
        }
        boolean shortHeading = previous.text.length() <= 32 && previous.text.split("\\s+").length <= 4
                && next.text.length() > previous.text.length() * 2
                && previous.right - previous.left < next.width() * .5f;
        if (shortHeading) return false;
        // Follow the adjacent line, allowing the first line to start at a paragraph indent.
        if (indent < -size * 2.5f || indent > size * 1.15f) return false;
        return Math.min(last.right, next.right) > Math.max(last.left, next.left);
    }

    private static List<Block> normalizeLineHeights(List<Block> blocks) {
        ArrayList<Block> normalized = new ArrayList<>(blocks);
        ArrayList<Integer> byHeight = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) byHeight.add(i);
        byHeight.sort(Comparator.comparingDouble(i -> blocks.get(i).lineHeight));
        while (!byHeight.isEmpty()) {
            int first = byHeight.remove(0);
            Block seed = blocks.get(first);
            ArrayList<Integer> peers = new ArrayList<>();
            peers.add(first);
            for (Iterator<Integer> at = byHeight.iterator(); at.hasNext();) {
                int index = at.next();
                Block candidate = blocks.get(index);
                if (candidate.lineHeight > seed.lineHeight * 1.42f) break;
                if (distance(candidate.color, seed.color) < 60) {
                    peers.add(index);
                    at.remove();
                }
            }
            float height = blocks.get(peers.get(peers.size() / 2)).lineHeight;
            for (int index : peers) {
                Block block = blocks.get(index);
                normalized.set(index, new Block(block.text, block.left, block.top, block.right, block.bottom, block.color, height));
            }
        }
        return Collections.unmodifiableList(normalized);
    }

    private static boolean needsSpace(String before, String after) {
        int last = before.codePointBefore(before.length()), first = after.strip().codePointAt(0);
        return Character.UnicodeScript.of(last) != Character.UnicodeScript.HAN
                && Character.UnicodeScript.of(first) != Character.UnicodeScript.HAN;
    }

    static int distance(int a, int b) {
        int r = (a >> 16 & 255) - (b >> 16 & 255), g = (a >> 8 & 255) - (b >> 8 & 255), bl = (a & 255) - (b & 255);
        return (int) Math.sqrt(r * r + g * g + bl * bl);
    }

    /** Samples around the detected lines so surrounding scenery and glyphs do not set the fill. */
    static int background(Pixels pixels, int width, int height, List<Line> lines) {
        int count = Math.min(lines.size(), MAX_BLOCKS);
        int[] samples = new int[count * 24];
        int size = 0;
        for (int i = 0; i < count; i++) {
            TextRegion line = lines.get(i).region;
            int offset = Math.max(2, line.height() / 7);
            int above = line.top - offset, below = line.bottom + offset;
            for (int column = 0; column < 12; column++) {
                int x = line.left + (int) ((long) line.width() * (2 * column + 1) / 24);
                if (x < 0 || x >= width) continue;
                if (above >= 0 && above < height && !insideText(x, above, lines)) samples[size++] = pixels.color(x, above);
                if (below >= 0 && below < height && !insideText(x, below, lines)) samples[size++] = pixels.color(x, below);
            }
        }
        if (size == 0) return 0;
        int[] channels = new int[size];
        int median = 0xFF000000;
        for (int shift : new int[]{16, 8, 0}) {
            for (int i = 0; i < size; i++) channels[i] = samples[i] >> shift & 255;
            Arrays.sort(channels);
            median |= channels[size / 2] << shift;
        }
        int closest = samples[0], best = distance(closest, median);
        for (int i = 1; i < size && best > 0; i++) {
            int delta = distance(samples[i], median);
            if (delta < best) {
                best = delta;
                closest = samples[i];
            }
        }
        return closest | 0xFF000000;
    }

    private static boolean insideText(int x, int y, List<Line> lines) {
        for (Line line : lines) {
            TextRegion box = line.region;
            if (x >= box.left - 1 && x <= box.right && y >= box.top - 1 && y <= box.bottom) return true;
        }
        return false;
    }

    static int foreground(Pixels pixels, int width, int height, TextRegion line) {
        int[] border = new int[96];
        int n = 0;
        int left = Math.max(0, line.left), right = Math.min(width - 1, line.right - 1);
        int top = Math.max(0, line.top), bottom = Math.min(height - 1, line.bottom - 1);
        if (right <= left || bottom <= top) return 0xFFFFFFFF;
        for (int i = 0; i < 24; i++) {
            int x = left + (right - left) * i / 23, y = top + (bottom - top) * i / 23;
            border[n++] = pixels.color(x, Math.max(0, top - 2));
            border[n++] = pixels.color(x, Math.min(height - 1, bottom + 2));
            border[n++] = pixels.color(Math.max(0, left - 2), y);
            border[n++] = pixels.color(Math.min(width - 1, right + 2), y);
        }
        int[] channels = new int[n];
        int background = 0xFF000000;
        for (int shift : new int[]{16, 8, 0}) {
            for (int i = 0; i < n; i++) channels[i] = border[i] >> shift & 255;
            Arrays.sort(channels);
            background |= channels[n / 2] << shift;
        }
        int[] counts = new int[4096], reds = new int[4096], greens = new int[4096], blues = new int[4096];
        List<TextRegion> regions = line.parts.isEmpty() ? List.of(line) : line.parts;
        int step = Math.max(1, (int) Math.ceil(Math.sqrt(line.area() / 16000.0)));
        for (TextRegion word : regions) {
            for (int y = Math.max(top, word.top); y <= Math.min(bottom, word.bottom - 1); y += step) {
                for (int x = Math.max(left, word.left); x <= Math.min(right, word.right - 1); x += step) {
                    int c = pixels.color(x, y);
                    if (distance(c, background) < 65) continue;
                    int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
                    int bin = (r >> 4) << 8 | (g >> 4) << 4 | (b >> 4);
                    counts[bin]++; reds[bin] += r; greens[bin] += g; blues[bin] += b;
                }
            }
        }
        double best = 0;
        int color = 0xFFFFFFFF;
        for (int i = 0; i < counts.length; i++) if (counts[i] >= 3) {
            int c = 0xFF000000 | (reds[i] / counts[i]) << 16 | (greens[i] / counts[i]) << 8 | blues[i] / counts[i];
            double score = counts[i] * Math.pow(distance(c, background), 1.5);
            if (score > best) { best = score; color = c; }
        }
        return color;
    }
}
