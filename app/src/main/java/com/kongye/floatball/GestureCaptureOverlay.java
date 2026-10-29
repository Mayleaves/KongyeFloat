package com.kongye.floatball;
import android.content.Context;
import android.graphics.*;
import android.view.View;
/** The cross follows the ball; yellow locks a first corner for a drag-out region. */
final class GestureCaptureOverlay extends View {
    final Paint paint=new Paint(3);float x,y,anchorX,anchorY;boolean ready,drawing;
    java.util.function.Supplier<PointF> position;
    GestureCaptureOverlay(Context c){super(c);setContentDescription("区域截图红黄十字提示");setLayerType(LAYER_TYPE_SOFTWARE,null);}
    @Override protected void onDraw(Canvas canvas){if(position!=null){PointF screen=position.get();x=screen.x;y=screen.y;}int[] origin=new int[2];getLocationOnScreen(origin);drawAt(canvas,origin[0],origin[1]);}
    void drawAt(Canvas canvas,int left,int top){
        canvas.save();canvas.translate(-left,-top);paint.clearShadowLayer();
        paint.setStrokeWidth(Ui.dp(getContext(),2));paint.setStyle(Paint.Style.STROKE);
        if(drawing){paint.setColor(ready?0xB3FFDF38:0xB3F34242);canvas.drawRect(Math.min(anchorX,x),Math.min(anchorY,y),Math.max(anchorX,x),Math.max(anchorY,y),paint);}
        paint.setColor(ready?0xFFC49A00:0xFFC62828);float r=Ui.dp(getContext(),6);
        canvas.drawLine(x-r,y,x+r,y,paint);canvas.drawLine(x,y-r,x,y+r,paint);canvas.restore();
    }
}
