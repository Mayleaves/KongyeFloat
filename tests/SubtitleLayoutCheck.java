package com.kongye.floatball;

import java.util.*;

public final class SubtitleLayoutCheck {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static SubtitleLayout.Line line(String text,int left,int top,int right,int bottom,int color) {
        return new SubtitleLayout.Line(new TextRegion(text,left,top,right,bottom),color);
    }
    public static void main(String[] args) {
        int pink=0xFFD45CC5, white=0xFFF4F4F4;
        var lines=List.of(line("Sarah",10,4,75,29,pink),line("Now...? It's too late isn't it...?",12,39,390,63,white),
                line("And we've just known each other.",12,70,385,94,white));
        var blocks=SubtitleLayout.blocks(lines,450,120);
        check(blocks.size()==2,"name stays separate; wrapped dialogue is translated together");
        check(blocks.get(0).text().equals("Sarah")&&blocks.get(0).color()==pink,"name retains its pink colour");
        check(blocks.get(1).text().contains("...? And"),"wrapped English keeps word spacing");
        check(blocks.get(0).left()==10&&blocks.get(1).top()==39,"source anchors retained");
        var sameColor=SubtitleLayout.blocks(List.of(line("Sarah",10,4,75,29,white),lines.get(1)),450,120);
        check(sameColor.size()==2,"short name stays separate even when both rows are white");
        var columns=SubtitleLayout.blocks(List.of(line("Left",10,10,70,30,white),line("Right",250,10,330,30,white)),400,100);
        check(columns.size()==2,"separate columns are never joined");
        var chinese=SubtitleLayout.blocks(List.of(line("今天",10,2,80,24,white),line("天气很好",10,29,100,51,white)),150,90);
        check(chinese.size()==1&&chinese.get(0).text().equals("今天天气很好"),"Chinese wrapping does not add spaces");
        check(SubtitleLayout.blocks(List.of(line("bad",-5,0,10,20,white)),100,100).isEmpty(),"invalid OCR geometry ignored");
        int black=0xFF202020;
        var exam = SubtitleLayout.blocks(List.of(
                line("Questions 1 to 4 are based on the",24,0,390,26,black),
                line("conversation you have just heard.",24,38,406,63,black),
                line("1. A) How to run one's business",24,95,390,122,black),
                line("successfully.",91,134,240,159,black),
                line("B) How to make progress in one's career.",52,177,440,199,black),
                line("C) Why few people can fully display their",52,215,440,245,black),
                line("capabilities.",90,252,235,280,black),
                line("D) Why young people seldom get the",52,293,430,320,black),
                line("expected promotion.",90,333,350,360,black)),450,400);
        check(exam.size()==5,"the heading and each answer choice form separate translation paragraphs");
        check(exam.get(1).text().endsWith("business successfully.") && exam.get(3).text().endsWith("their capabilities.")
                && exam.get(4).text().endsWith("the expected promotion."),"indented continuations join their own answer");
        check(exam.stream().allMatch(b -> b.lineHeight()==exam.get(1).lineHeight()),"similar source sizes normalize to a shared glyph height");
        int width=120,height=50;
        int[] pixels=new int[width*height]; Arrays.fill(pixels,0xFF171217);
        for(int y=14;y<33;y++)for(int x=14;x<99;x++)if(x%8<4)pixels[y*width+x]=pink;
        TextRegion region=new TextRegion("Sarah",10,10,105,37);
        check(SubtitleLayout.distance(SubtitleLayout.foreground((x,y)->pixels[y*width+x],width,height,region),pink)<2,"pink glyphs sampled instead of the dark background");
        Arrays.fill(pixels,0xFFEEEEEE);
        for(int y=14;y<33;y++)for(int x=14;x<99;x++)if(x%8<4)pixels[y*width+x]=0xFF101010;
        check(SubtitleLayout.distance(SubtitleLayout.foreground((x,y)->pixels[y*width+x],width,height,region),0xFF101010)<2,"dark glyphs on a light background retained");
        var caption = List.of(line("Caption", 10, 14, 110, 34, white));
        for (int color : new int[]{0xFF624827, 0xFF244D69}) {
            Arrays.fill(pixels, 0xFF171217);
            for (int y = 8; y < 42; y++) Arrays.fill(pixels, y * width, (y + 1) * width, color);
            for (int y = 14; y < 34; y++) Arrays.fill(pixels, y * width + 10, y * width + 110, white);
            check(SubtitleLayout.background((x,y)->pixels[y*width+x],width,height,caption)==color,
                    "sample the dialogue strip around the glyphs instead of the rendered letters or outer scene");
        }
        check(SubtitleLayout.background((x,y)->pixels[y*width+x],width,height,List.of())==0,
                "return no sampled colour when no subtitle is detected");
        check(OcrPunctuation.formatTranslatedSubtitle("闲逛 \t\n酒店", "zh").equals("闲逛酒店"), "Chinese paragraph wraps at the display width without OCR or model gaps");
        check(OcrPunctuation.formatTranslatedSubtitle("在 New York\n见面", "zh").equals("在 New York 见面"), "English word spaces survive in mixed text");
        System.out.println(checks+" subtitle grouping, colour and text checks passed.");
    }
}
