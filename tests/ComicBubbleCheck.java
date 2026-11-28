package com.kongye.floatball;
public class ComicBubbleCheck {
 public static void main(String[] args){
  ComicBubbleMap map=new ComicBubbleMap(300,400,(x,y)->(x>20&&x<125&&y>20&&y<180)||(x>160&&x<275&&y>210&&y<380)?0xFFFFFFFF:0xFF333333);
  int a=map.bubble(40,40,65,90),b=map.bubble(80,60,100,140),other=map.bubble(180,240,250,300);
  if(a<=0||a!=b||other<=0||other==a)throw new AssertionError("Bubble separation");
  ComicBubbleMap plain=new ComicBubbleMap(300,400,(x,y)->0xFFFFFFFF);
  if(plain.bubble(30,30,80,80)!=0)throw new AssertionError("Whole page must not merge all text");
  ComicBubbleMap dark=new ComicBubbleMap(300,400,(x,y)->0xFF000000);
  if(dark.bubble(30,30,80,80)!=0)throw new AssertionError("Dark background");
  var spans=java.util.List.of(new int[]{10,0,90,2},new int[]{20,2,80,4},new int[]{30,4,70,6});
  int[] inner=ComicBubbleMap.innerBounds(spans);if(inner==null)throw new AssertionError("No safe rectangle");
  for(int[] row:spans)if(row[1]>=inner[1]&&row[3]<=inner[3]&&(inner[0]<row[0]||inner[2]>row[2]))throw new AssertionError("Text crosses bubble");
  System.out.println("Connected bubbles, independent pages and dark background checks passed");
 }
}
