package com.kongye.floatball;

/** UTF-16 offsets are clamped to complete code points before copying a range. */
public final class TextSelectionRange {
    private TextSelectionRange(){}
    public static int[] normalize(CharSequence text,int a,int b){int start=Math.max(0,Math.min(text.length(),Math.min(a,b))),end=Math.max(0,Math.min(text.length(),Math.max(a,b)));if(start>0&&start<text.length()&&Character.isLowSurrogate(text.charAt(start))&&Character.isHighSurrogate(text.charAt(start-1)))start--;if(end>0&&end<text.length()&&Character.isLowSurrogate(text.charAt(end))&&Character.isHighSurrogate(text.charAt(end-1)))end++;return new int[]{start,end};}
    public static int[] character(CharSequence text, int offset) {
        if (text.length() == 0) return new int[]{0, 0};
        int start = Math.max(0, Math.min(text.length() - 1, offset));
        return normalize(text, start, start + 1);
    }
}
