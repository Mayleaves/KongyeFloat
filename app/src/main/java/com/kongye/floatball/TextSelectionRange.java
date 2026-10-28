package com.kongye.floatball;

/** UTF-16 offsets are clamped to complete code points before copying a range. */
public final class TextSelectionRange {
    private TextSelectionRange(){}
    public static int[] normalize(String text,int a,int b){int start=Math.max(0,Math.min(text.length(),Math.min(a,b))),end=Math.max(0,Math.min(text.length(),Math.max(a,b)));if(start>0&&start<text.length()&&Character.isLowSurrogate(text.charAt(start))&&Character.isHighSurrogate(text.charAt(start-1)))start--;if(end>0&&end<text.length()&&Character.isLowSurrogate(text.charAt(end))&&Character.isHighSurrogate(text.charAt(end-1)))end++;return new int[]{start,end};}
    public static int[] word(String text,int at){if(text.isEmpty())return new int[]{0,0};at=Math.max(0,Math.min(text.length()-1,at));int start=at,end=at+1;char c=text.charAt(at);if(c<128&&Character.isLetterOrDigit(c)){while(start>0&&text.charAt(start-1)<128&&Character.isLetterOrDigit(text.charAt(start-1)))start--;while(end<text.length()&&text.charAt(end)<128&&Character.isLetterOrDigit(text.charAt(end)))end++;}return normalize(text,start,end);}
}
