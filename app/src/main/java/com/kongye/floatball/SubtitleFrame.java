package com.kongye.floatball;

import android.graphics.Bitmap;
import android.graphics.Rect;
import com.google.mlkit.vision.text.Text;
import java.util.*;

final class SubtitleFrame {
    static final SubtitleFrame EMPTY = new SubtitleFrame(1, 1, List.of());
    final int width, height, background;
    final List<SubtitleLayout.Block> blocks;
    final String text, key;

    SubtitleFrame(int width, int height, List<SubtitleLayout.Block> blocks) {
        this(width, height, blocks, 0);
    }

    private SubtitleFrame(int width, int height, List<SubtitleLayout.Block> blocks, int background) {
        this.width = width; this.height = height; this.blocks = List.copyOf(blocks);
        this.background = background;
        StringJoiner text = new StringJoiner("\n");
        StringBuilder key = new StringBuilder();
        for (SubtitleLayout.Block block : blocks) {
            text.add(block.text()); key.append(block.text().length()).append(':').append(block.text());
        }
        this.text = text.toString(); this.key = key.toString();
    }

    static SubtitleFrame read(Text text, Bitmap bitmap) {
        ArrayList<SubtitleLayout.Line> lines = new ArrayList<>();
        outer: for (Text.TextBlock block : text.getTextBlocks()) for (Text.Line line : block.getLines()) {
            Rect bounds = line.getBoundingBox();
            if (bounds == null) continue;
            ArrayList<TextRegion> words = new ArrayList<>();
            for (Text.Element element : line.getElements()) {
                Rect r = element.getBoundingBox();
                if (r != null) words.add(new TextRegion(element.getText(), r.left, r.top, r.right, r.bottom));
            }
            TextRegion region = new TextRegion(line.getText(), bounds.left, bounds.top, bounds.right, bounds.bottom, words);
            int color = SubtitleLayout.foreground(bitmap::getPixel, bitmap.getWidth(), bitmap.getHeight(), region);
            lines.add(new SubtitleLayout.Line(region, color));
            if (lines.size() >= 128) break outer;
        }
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        return new SubtitleFrame(width, height, SubtitleLayout.blocks(lines, width, height),
                SubtitleLayout.background(bitmap::getPixel, width, height, lines));
    }
}
