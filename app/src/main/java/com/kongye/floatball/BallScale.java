package com.kongye.floatball;

/** Percentages describe visible art, regardless of transparent image canvas size. */
public final class BallScale {
    private BallScale(){}
    public static int sizePercent(float legacyDp){return Math.max(30,Math.min(500,Math.round(legacyDp*100/36)));}
    public static float dp(int percent){return 36f*Math.max(30,Math.min(500,percent))/100;}
    public static int edgePercent(float legacyInset,float width){return Math.max(30,Math.min(200,Math.round(100+legacyInset*100/Math.max(1,width))));}
    public static int inset(int percent,int extent){return -Math.round(Math.max(0,extent)*(100-Math.max(30,Math.min(200,percent)))/100f);}
}
