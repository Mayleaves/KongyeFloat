package com.kongye.floatball;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Code-drawn botanical patchwork. No raster or network asset is required. */
final class BotanicalPanel extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    BotanicalPanel(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onMeasure(int width,int height){int w=MeasureSpec.getSize(width);setMeasuredDimension(w,Math.round(w*.4f));}
    private void fill(Canvas c,int color,float l,float t,float r,float b){paint.setColor(color);paint.setStyle(Paint.Style.FILL);c.drawRect(l,t,r,b,paint);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);c.save();c.scale(getWidth()/400f,getHeight()/160f);
        int forest=0xFF2E4D29,light=0xFFDDE8AE,olive=0xFF879F46,sage=0xFFB4C67B;
        fill(c,light,0,0,100,80);fill(c,forest,100,0,200,80);fill(c,sage,200,0,300,80);fill(c,0xFFD6E1A5,300,0,400,80);
        fill(c,olive,0,80,100,160);fill(c,0xFFD9E3AD,100,80,200,160);fill(c,0xFFB2C56D,200,80,300,160);fill(c,forest,300,80,400,160);
        paint.setColor(0xFFF4D84C);c.drawCircle(87,14,25,paint);
        paint.setColor(0xFF99AD50);path.reset();path.moveTo(400,0);path.cubicTo(400,39,384,59,350,80);path.lineTo(400,80);path.close();c.drawPath(path,paint);
        paint.setColor(0xFFABC65A);path.reset();path.moveTo(300,160);path.cubicTo(315,112,360,84,400,80);path.lineTo(400,160);path.close();c.drawPath(path,paint);
        leaf(c,130,15,0xFFC6D97A);leaf(c,130,96,0xFF587748);
        plant(c,35,97);plant(c,235,17);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xFFC1CE65);c.drawOval(231,95,283,147,paint);paint.setColor(0xFFF2D74B);c.drawOval(239,102,276,139,paint);
        c.restore();
    }
    private void leaf(Canvas c,float x,float y,int color){
        c.save();c.translate(x,y);paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        path.reset();path.moveTo(5,30);path.cubicTo(-2,9,18,4,40,4);path.cubicTo(39,24,22,38,8,32);c.drawPath(path,paint);c.drawLine(0,38,32,12,paint);c.restore();
    }
    private void plant(Canvas c,float x,float y){
        c.save();c.translate(x,y);paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF2C473E);
        path.reset();path.moveTo(0,36);path.lineTo(35,36);path.lineTo(30,56);path.lineTo(5,56);path.close();c.drawPath(path,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.7f);c.drawLine(19,37,16,12,paint);c.drawLine(21,36,27,22,paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF4C6D2D);
        path.reset();path.moveTo(17,24);path.cubicTo(3,27,-2,21,2,20);path.cubicTo(10,16,16,18,17,24);path.close();c.drawPath(path,paint);
        path.reset();path.moveTo(23,32);path.cubicTo(22,21,29,19,32,22);path.cubicTo(34,29,28,34,23,32);path.close();c.drawPath(path,paint);c.restore();
    }
}
