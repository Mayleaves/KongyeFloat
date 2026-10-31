package com.kongye.floatball;

import android.content.Context;
import android.graphics.*;
import android.view.View;

final class RecoveryPulse extends View {
    float x,y,radius,phase;View target;private final int[] ballAt=new int[2],pulseAt=new int[2];private final Paint paint=new Paint(3);
    RecoveryPulse(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setLayerType(LAYER_TYPE_SOFTWARE,null);}
    @Override protected void onDraw(Canvas c){if(target==null||!target.isAttachedToWindow())return;
        target.getLocationOnScreen(ballAt);getLocationOnScreen(pulseAt);
        x=ballAt[0]+target.getWidth()*target.getScaleX()/2f-pulseAt[0];
        y=ballAt[1]+target.getHeight()*target.getScaleY()/2f-pulseAt[1];
        float spread=Ui.dp(getContext(),18)*phase;paint.setColor(Color.TRANSPARENT);paint.setShadowLayer(Math.max(1,Ui.dp(getContext(),2)),0,0,Color.argb(Math.round(204*(1-phase)),245,208,66));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,spread));paint.setColor(Color.argb(Math.round(204*(1-phase)),245,208,66));c.drawCircle(x,y,radius+spread/2,paint);}
}
