package com.kongye.floatball;
import java.util.*;

/** Geometry and expected user text are independent of ML Kit's model output. */
public final class RegionTextFusionCheck {
    static int checks;
    static TextRegion r(String s,int x,int y,int w,int h){return new TextRegion(s,x,y,x+w,y+h);}
    static List<TextRegion> list(TextRegion... r){return Arrays.asList(r);}
    static String merge(List<TextRegion> a,List<TextRegion> b){return RegionTextFusion.text(RegionTextFusion.merge(a,b));}
    static void eq(String expected,String actual,String name){if(!expected.equals(actual))throw new AssertionError(name+": "+actual);checks++;}
    static void yes(boolean condition,String name){if(!condition)throw new AssertionError(name);checks++;}
    public static void main(String[] args){
        List<TextRegion> nativeText=list(r("原生标题",0,0,100,24),r("正文 ￥1,234.50",0,120,200,28));
        List<TextRegion> ocr=list(r("原生标廷",2,1,96,22),r("图片独有文字",0,60,180,24),r("正文 ￥1,234.5O",2,121,196,26));
        eq("原生标题\n图片独有文字\n正文 ￥1,234.50",merge(nativeText,ocr),"original spelling and price win; image text is added between them");
        eq("原生标题\n正文 ￥1,234.50",merge(nativeText,Collections.emptyList()),"source survives empty OCR");
        eq("原生标廷\n图片独有文字\n正文 ￥1,234.5O",merge(Collections.emptyList(),ocr),"image-only recognition is retained");
        eq("",merge(Collections.emptyList(),Collections.emptyList()),"empty image and no nodes");
        eq("标签\n标签\n标签",merge(list(r("标签",0,0,40,20),r("标签",0,70,40,20)),list(r("标签",0,0,40,20),r("标签",0,35,40,20),r("标签",0,70,40,20))),"repeated text in three places is not globally deduplicated");
        eq("A B",merge(list(r("A",0,0,20,20)),list(r("B",50,0,20,20))),"same row reads left to right");
        TextRegion sameLine=new TextRegion("Native Image",0,0,240,28,list(r("Native",0,0,110,28),r("Image",135,0,105,28)));
        eq("Native Image",merge(list(r("Native",0,0,110,28)),list(sameLine)),"native and image words share one OCR line without duplicating native prefix");
        TextRegion mixedWord=new TextRegion("原文图片字",0,0,100,20,list(r("原",0,0,20,20),r("文",20,0,20,20),r("图",40,0,20,20),r("片",60,0,20,20),r("字",80,0,20,20)),true);
        eq("原文图片字",merge(list(r("原文",0,0,40,20)),list(mixedWord)),"image suffix survives inside one OCR element");
        eq("正确图片字",merge(list(r("正确",0,0,40,20)),list(mixedWord)),"incorrect OCR characters are replaced by exact source in a mixed element");
        eq("长段原文\n图片文字",merge(list(r("长段原文",0,0,800,500)),list(r("图片文字",400,300,100,20))),"large mostly empty source bounds do not swallow image text");
        eq("你好，世界！",merge(list(r("你好，世界！",0,0,150,25)),list(r("你好,世界!",0,0,150,25))),"original punctuation preserved");
        eq("标题\n副标题",merge(list(r("标题\n副标题",0,0,100,60)),list(r("标题",0,0,100,20),r("副标题",0,35,100,20))),"native multiline block suppresses OCR copies of each line");
        eq("父标题 子标题",merge(list(r("父标题 子标题",0,0,180,30),r("子标题",80,0,100,30)),Collections.emptyList()),"nested accessible labels appear once");
        eq("金额 100",merge(list(r("金额 100",0,0,150,30),r("金额 100",2,1,145,28)),Collections.emptyList()),"overlapping duplicate nodes");
        eq("A\nB",RegionTextFusion.text(list(r("B",0,50,10,20),r("A",0,0,10,20))),"reading order does not depend on traversal order");
        List<TextRegion> mapped=RegionTextFusion.inCrop(list(r("inside",200,400,100,80),r("outside",10,10,30,30),r("cut",190,390,20,20)),1000,2000,2000,4000,400,800,1000,1400);
        yes(mapped.size()==1,"outside and partly cut nodes excluded");TextRegion mappedText=mapped.get(0);yes(mappedText.left==0&&mappedText.top==0&&mappedText.right==200&&mappedText.bottom==160,"screen coordinates scale to screenshot then subtract crop offset");
        mapped=RegionTextFusion.inCrop(list(r("inside",200,400,100,80)),1000,2000,500,1000,100,200,400,500);yes(mapped.size()==1&&mapped.get(0).right==50&&mapped.get(0).bottom==40,"lower-resolution screenshot mapping");
        yes(RegionTextFusion.inCrop(nativeText,0,0,100,100,0,0,100,100).isEmpty(),"invalid display metrics do not leak nodes");
        eq("",merge(list(r("",0,0,50,20),r("bad",0,0,0,10)),Collections.emptyList()),"empty and degenerate nodes ignored");
        System.out.println(checks+" region text fusion checks passed");
    }
}
