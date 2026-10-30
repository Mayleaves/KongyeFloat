package com.kongye.floatball;

/** Pure gesture classification, shared by the overlay and standalone regression checks. */
public final class GestureRules {
    private GestureRules() {}
    public static int dragPosition(int initial,float pointer,float down){return initial+Math.round(pointer-down);}
    /** Keep the cross independent of artwork size, easing toward the far edge as the finger approaches it. */
    public static float capturePointer(float pointer,int extent,float offset,float reach,float edgeInset){
        if(extent<=0)return 0;edgeInset=Math.max(0,edgeInset);offset=Math.max(0,offset);float end=Math.max(0,extent-edgeInset);
        float start=Math.max(0,end-Math.max(1,reach));float progress=Math.max(0,Math.min(1,(pointer-start)/Math.max(1,end-start)));
        return Math.max(0,Math.min(extent,pointer-offset+(offset+edgeInset)*progress));
    }
    public static boolean isDrag(float dx, float dy, long duration, float slop) {
        return Math.hypot(dx, dy) > slop && duration >= 280;
    }
    public static String swipe(float dx, float dy, long duration, float threshold) {
        if (duration >= 280 || Math.abs(dy) < threshold || Math.abs(dy) < Math.abs(dx) * 1.4f) return null;
        return dy < 0 ? "up" : "down";
    }
    public static int clamp(int value, int min, int max) { return Math.max(min, Math.min(Math.max(min, max), value)); }
    public static int edge(int x, int width, int size) { return x + size / 2 < width / 2 ? 0 : Math.max(0, width - size); }
    public static int edgeWithInset(int x,int width,int size,int inset){int gap=Math.min(inset,Math.max(0,(width-size)/2));return x+size/2<width/2?gap:Math.max(gap,width-size-gap);}
    public static int visibleInset(int requested,int size,int visible){return Math.max(Math.min(0,requested),Math.min(0,visible-size));}
    public static float followOffset(float delta,float maximum){return Math.max(-maximum,Math.min(maximum,delta*.45f));}
}
