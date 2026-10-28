package com.kongye.floatball;

/** Natural-size, centered concatenation; independent from Android for regression checks. */
public final class CompositionLayout {
    public static final long MAX_PIXELS=12_000_000;
    public static final int MAX_SIDE=20_000;
    public final int width,height;
    public final int[] left,top;
    private CompositionLayout(int w,int h,int[] x,int[] y){width=w;height=h;left=x;top=y;}
    public static CompositionLayout create(int[] widths,int[] heights,boolean horizontal){
        if(widths.length!=heights.length||widths.length==0)throw new IllegalArgumentException("No images");
        long w=0,h=0;
        for(int i=0;i<widths.length;i++){
            if(widths[i]<1||heights[i]<1)throw new IllegalArgumentException("Invalid image size");
            w=horizontal?w+widths[i]:Math.max(w,widths[i]);h=horizontal?Math.max(h,heights[i]):h+heights[i];
        }
        if(w>MAX_SIDE||h>MAX_SIDE||w*h>MAX_PIXELS)throw new IllegalArgumentException("Composition is too large");
        int[] x=new int[widths.length],y=new int[widths.length];int cursor=0;
        for(int i=0;i<x.length;i++){x[i]=horizontal?cursor:((int)w-widths[i])/2;y[i]=horizontal?((int)h-heights[i])/2:cursor;cursor+=horizontal?widths[i]:heights[i];}
        return new CompositionLayout((int)w,(int)h,x,y);
    }
    public static <T> void move(java.util.List<T> items,int from,int to){
        if(from<0||to<0||from>=items.size()||to>=items.size())return;
        T value=items.remove(from);items.add(to,value);
    }
}
