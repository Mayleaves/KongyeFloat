package com.kongye.floatball;
import java.util.*;
public final class EnhancedOcrCheck {
 static int count;
 static void eq(String a,String b,String name){if(!a.equals(b))throw new AssertionError(name+": "+b);count++;}
 static void yes(boolean b,String name){if(!b)throw new AssertionError(name);count++;}
 static TextRegion r(String s,int l,int t,int right,int bottom,float confidence){return new TextRegion(s,l,t,right,bottom,Collections.emptyList(),false,confidence);}
 public static void main(String[] args){
  eq("你好，世界！今天好吗？",OcrPunctuation.normalize("你好,世界!今天好吗?"),"Chinese comma exclamation question");
  eq("他说：“春风又绿江南岸。”",OcrPunctuation.normalize("他说:\"春风又绿江南岸.\""),"paired existing Chinese quote marks");
  eq("他说：“春风又绿江南岸。”",OcrPunctuation.normalize("他说：“春风又绿江南岸。“"),"correct two opening quotes into opening and closing");
  eq("他说：“春风又绿江南岸。",OcrPunctuation.normalize("他说：“春风又绿江南岸。"),"never fabricate missing quote");
  eq("金额 3.14 元，数量 1,234，时间 12:30",OcrPunctuation.normalize("金额 3.14 元,数量 1,234,时间 12：30"),"decimal thousands separator timestamp preserved");
  eq("网址 example.com，邮件 a@b.com",OcrPunctuation.normalize("网址 example.com,邮件 a@b.com"),"URL and email periods preserved");
  eq("English: Hello, world!",OcrPunctuation.normalize("English： Hello， world！"),"Latin line uses Latin punctuation");
  eq("中文 English, sentence! URL https://example.com/a?b=1&c=2",OcrPunctuation.normalize("中文 English, sentence! URL https://example.com/a?b=1&c=2"),"English phrase and URL query in mixed line preserved");
  eq("等等...",OcrPunctuation.normalize("等等..."),"ellipsis is not split");
  eq("123.45",OcrPunctuation.normalize("123.45"),"number-only input");
  TextRegion old=r("吴致正在阅读,今天心情很好。",0,0,400,35,.98f),pp=r("吴玫正在阅读，今天心情很好。",0,0,400,35,.94f);
  eq(pp.text,OcrEngineFusion.merge(List.of(pp),List.of(old)).get(0).text,"strong ML confidence does not overwrite Paddle Chinese repair");
  TextRegion english=r("Hello, world!",0,50,300,80,.98f),englishPp=r("Hello，world！",0,50,300,80,.9f);
  eq(english.text,OcrEngineFusion.merge(List.of(englishPp),List.of(english)).get(0).text,"strong agreeing Latin text keeps ML spacing");
  yes(OcrEngineFusion.merge(List.of(pp),List.of(old,english)).size()==2,"ML-only area survives");
  yes(OcrEngineFusion.merge(Collections.emptyList(),List.of(old)).get(0)==old,"Paddle empty fallback");
  TextRegion positioned=new TextRegion("原文 ABC Image 246",0,0,360,30,List.of(r("原文",0,0,60,30,.9f),r("ABC",75,0,130,30,.9f),r("Image",160,0,270,30,.9f),r("246",290,0,360,30,.9f)));
  TextRegion improved=OcrTextAlignment.apply(r("原文 ABC Image 246",0,0,365,32,.95f),positioned);
  eq("原文 ABC Image 246",RegionTextFusion.text(RegionTextFusion.merge(List.of(r("原文 ABC",0,0,130,30,1)),List.of(improved))),"native plus image words after Paddle alignment");
  TextRegion nativeExact=r("原文 ABC",0,0,130,30,1);TextRegion corrected=OcrTextAlignment.apply(r("原文 ABC Image 248",0,0,365,32,.95f),positioned);
  eq("原文 ABC Image 248",RegionTextFusion.text(RegionTextFusion.merge(List.of(nativeExact),List.of(corrected))),"second engine corrected suffix survives native fusion");
  eq("原文,不改!",RegionTextFusion.text(RegionTextFusion.merge(List.of(r("原文,不改!",0,0,200,30,1)),List.of(OcrPunctuation.normalize(r("原文,不改!",0,0,200,30,.9f))))),"exact accessible ASCII punctuation untouched");
  System.out.println(count+" enhanced OCR selection, alignment and punctuation checks passed");
 }
}
