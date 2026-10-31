package com.kongye.floatball;

import android.content.Context;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

/** Icon-only circle; content descriptions remain available to accessibility. */
final class QuickBubble {
    static final int[] COLORS={0xFF28765A,0xFF617B4D,0xFF456950,0xFF789261,0xFF527C68,0xFF3C6254};
    static FrameLayout create(Context c,String id,int size,int color){return create(c,id,size,color,false);}
    static FrameLayout create(Context c,String id,int size,int color,boolean recent){
        FrameLayout bubble=new FrameLayout(c);bubble.setBackground(Ui.bg(color,100,c));bubble.setElevation(Ui.dp(c,3));bubble.setClipToOutline(true);if(id.isEmpty())return bubble;
        int iconSize=Ui.dp(c,size*.57f);View icon;
        if(id.startsWith("app:")){
            ImageView image=new ImageView(c);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try{android.graphics.drawable.Drawable art=c.getPackageManager().getApplicationIcon(id.substring(4));image.setImageDrawable(art);int fill=dominant(art);if(recent){float[] hsv=new float[3];Color.colorToHSV(fill,hsv);hsv[1]*=.3f;hsv[2]=Math.min(1,hsv[2]*.8f+.2f);fill=Color.HSVToColor(hsv);android.graphics.ColorMatrix matrix=new android.graphics.ColorMatrix();matrix.setSaturation(.3f);android.graphics.ColorMatrix lighter=new android.graphics.ColorMatrix(new float[]{.8f,0,0,0,51,0,.8f,0,0,51,0,0,.8f,0,51,0,0,0,1,0});lighter.postConcat(matrix);image.setColorFilter(new android.graphics.ColorMatrixColorFilter(lighter));}bubble.setBackground(Ui.bg(fill,100,c));}catch(Exception ignored){}
            icon=image;iconSize=Ui.dp(c,size);
        }else icon=Ui.icon(c,id.isEmpty()?"plus":id,color==Color.WHITE?Ui.BRAND:Color.WHITE,24);
        bubble.addView(icon,new FrameLayout.LayoutParams(iconSize,iconSize,Gravity.CENTER));
        return bubble;
    }
    private static int dominant(android.graphics.drawable.Drawable art){android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(32,32,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Rect previous=new android.graphics.Rect(art.getBounds());art.setBounds(0,0,32,32);art.draw(new android.graphics.Canvas(b));art.setBounds(previous);java.util.HashMap<Integer,Integer> counts=new java.util.HashMap<>();int winner=Color.WHITE,max=0;for(int y=0;y<32;y++)for(int x=0;x<32;x++){int pixel=b.getPixel(x,y);if(Color.alpha(pixel)<180)continue;int bucket=0xff000000|(Color.red(pixel)&240)<<16|(Color.green(pixel)&240)<<8|(Color.blue(pixel)&240);int n=counts.getOrDefault(bucket,0)+1;counts.put(bucket,n);if(n>max){max=n;winner=pixel;}}b.recycle();return winner;}
}
