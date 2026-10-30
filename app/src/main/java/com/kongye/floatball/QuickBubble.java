package com.kongye.floatball;

import android.content.Context;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

/** Icon-only circle; content descriptions remain available to accessibility. */
final class QuickBubble {
    static final int[] COLORS={0xFF28765A,0xFF617B4D,0xFF456950,0xFF789261,0xFF527C68,0xFF3C6254};
    static FrameLayout create(Context c,String id,int size,int color){
        FrameLayout bubble=new FrameLayout(c);bubble.setBackground(Ui.bg(color,100,c));bubble.setElevation(Ui.dp(c,3));if(id.isEmpty())return bubble;
        int iconSize=Ui.dp(c,size*.57f);View icon;
        if(id.startsWith("app:")){
            ImageView image=new ImageView(c);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            try{image.setImageDrawable(c.getPackageManager().getApplicationIcon(id.substring(4)));}catch(Exception ignored){}
            icon=image;iconSize=Ui.dp(c,size*.72f);
        }else icon=Ui.icon(c,id.isEmpty()?"plus":id,color==Color.WHITE?Ui.BRAND:Color.WHITE,24);
        bubble.addView(icon,new FrameLayout.LayoutParams(iconSize,iconSize,Gravity.CENTER));
        return bubble;
    }
}
