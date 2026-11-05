package com.kongye.floatball;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.function.Consumer;

/** Four edges, four corners and a move handle. Background can be frozen or live. */
final class ResizableCropView extends View {
    private final Bitmap background;private final boolean live;
    final RectF area=new RectF();private final Paint paint=new Paint(3);private final Path dash=new Path();
    private int handle=-1;private float downX,downY;private float[] original;private boolean creating;
    private Consumer<RectF> changed;
    private boolean subtitleFrame,regionFrame;
    private final android.graphics.drawable.Drawable moveIcon;
    ResizableCropView(Context c,Bitmap background,boolean live){super(c);this.background=background;this.live=live;moveIcon=c.getDrawable(R.drawable.ic_move_area).mutate();moveIcon.setTint(Color.WHITE);setContentDescription("截图选择框，可拖动四边和四角调整大小，拖动中心移动");}
    void setChanged(Consumer<RectF> callback){changed=callback;}
    void useSubtitleFrame(){subtitleFrame=true;}
    void useRegionFrame(){regionFrame=true;}
    void resetSubtitleFrame(){
        area.set(getWidth()*.1f,getHeight()*.35f,getWidth()*.9f,getHeight()*.58f);
        notifyChange();
    }
    void setInitial(Rect source,int sourceW,int sourceH){if(source!=null){area.set(source);initialW=sourceW;initialH=sourceH;}}
    private int initialW,initialH;
    private float d(float n){return Ui.dp(getContext(),n);}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        if(area.isEmpty()){
            if(subtitleFrame)area.set(w*.1f,h*.35f,w*.9f,h*.58f);
            else area.set(w*.1f,h*.3f,w*.9f,h*.7f);
        }
        else if(oldW==0&&initialW>0){area.left*=w/(float)initialW;area.right*=w/(float)initialW;area.top*=h/(float)initialH;area.bottom*=h/(float)initialH;}
        else if(oldW>0&&oldH>0){area.left*=w/(float)oldW;area.right*=w/(float)oldW;area.top*=h/(float)oldH;area.bottom*=h/(float)oldH;}
        notifyChange();
    }
    Rect sourceRect(int width,int height){return new Rect(Math.max(0,Math.round(area.left*width/Math.max(1,getWidth()))),Math.max(0,Math.round(area.top*height/Math.max(1,getHeight()))),Math.min(width,Math.round(area.right*width/Math.max(1,getWidth()))),Math.min(height,Math.round(area.bottom*height/Math.max(1,getHeight()))));}
    void addTouchRegion(Region region){
        int radius=(int)d(25);float[][] points=points();for(float[] p:points)region.op(new Rect((int)p[0]-radius,(int)p[1]-radius,(int)p[0]+radius,(int)p[1]+radius),Region.Op.UNION);
        region.op(new Rect((int)area.centerX()-radius,(int)area.centerY()-radius,(int)area.centerX()+radius,(int)area.centerY()+radius),Region.Op.UNION);
    }
    float[][] points(){
        float inset=live?Math.min(d(32),Math.min(area.width(),area.height())/4):0;
        float bottom=live?Math.min(area.bottom-inset,getHeight()-d(84)):area.bottom;
        return new float[][]{{area.left,area.top},{area.centerX(),area.top+inset},{area.right,area.top},{area.right-inset,area.centerY()},{area.right,area.bottom},{area.centerX(),bottom},{area.left,area.bottom},{area.left+inset,area.centerY()}};
    }
    private int hit(float x,float y){float edge=d(25);boolean withinY=y>=area.top-edge&&y<=area.bottom+edge,withinX=x>=area.left-edge&&x<=area.right+edge;boolean left=withinY&&Math.abs(x-area.left)<edge,right=withinY&&Math.abs(x-area.right)<edge,top=withinX&&Math.abs(y-area.top)<edge,bottom=withinX&&Math.abs(y-area.bottom)<edge;int mode=0;if(left)mode|=CropGeometry.LEFT;else if(right)mode|=CropGeometry.RIGHT;if(top)mode|=CropGeometry.TOP;else if(bottom)mode|=CropGeometry.BOTTOM;if(mode!=0)return mode;if((!live&&area.contains(x,y))||Math.hypot(x-area.centerX(),y-area.centerY())<edge)return CropGeometry.MOVE;return -1;}
    @Override public boolean onTouchEvent(MotionEvent event){float x=clamp(event.getX(),0,getWidth()),y=clamp(event.getY(),0,getHeight());switch(event.getActionMasked()){
        case MotionEvent.ACTION_DOWN->{handle=hit(x,y);creating=handle<0&&!live;if(handle<0&&!creating)return false;downX=x;downY=y;original=new float[]{area.left,area.top,area.right,area.bottom};if(creating)area.set(x,y,x,y);return true;}
        case MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP->{if(creating)area.set(Math.min(downX,x),Math.min(downY,y),Math.max(downX,x),Math.max(downY,y));else if(handle>=0){float[] r=CropGeometry.adjust(original,handle,x-downX,y-downY,getWidth(),getHeight(),d(36));area.set(r[0],r[1],r[2],r[3]);}if(event.getActionMasked()==MotionEvent.ACTION_UP){if(area.width()<d(20)||area.height()<d(20)){if(subtitleFrame)area.set(original[0],original[1],original[2],original[3]);else area.set(getWidth()*.1f,getHeight()*.3f,getWidth()*.9f,getHeight()*.7f);}performClick();handle=-1;}notifyChange();return true;}
        case MotionEvent.ACTION_CANCEL->{handle=-1;return true;}
    }return false;}
    private static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
    private void notifyChange(){
        if(android.os.Build.VERSION.SDK_INT>=29){java.util.ArrayList<Rect> exclusions=new java.util.ArrayList<>();int radius=(int)d(24);for(float[] point:points())exclusions.add(new Rect((int)point[0]-radius,(int)point[1]-radius,(int)point[0]+radius,(int)point[1]+radius));setSystemGestureExclusionRects(exclusions);}
        invalidate();if(changed!=null)changed.accept(new RectF(area));
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDraw(Canvas canvas){
        if(background!=null&&!background.isRecycled())canvas.drawBitmap(background,null,new Rect(0,0,getWidth(),getHeight()),null);
        if(regionFrame){drawRegionFrame(canvas);return;}
        paint.setStyle(Paint.Style.FILL);paint.setColor(live?0xCC000000:0x88000000);canvas.drawRect(0,0,getWidth(),area.top,paint);canvas.drawRect(0,area.bottom,getWidth(),getHeight(),paint);canvas.drawRect(0,area.top,area.left,area.bottom,paint);canvas.drawRect(area.right,area.top,getWidth(),area.bottom,paint);
        if(subtitleFrame){
            paint.setColor(Ui.GREEN);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d(1.5f));
            canvas.drawRect(area,paint);
            paint.setStrokeWidth(d(2.5f));
            float inset=d(3), length=Math.min(d(10),Math.min(area.width(),area.height())/3);
            dash.reset();
            for(int i=0;i<4;i++){
                boolean left=i%2==0, top=i<2;
                float x=left?area.left+inset:area.right-inset;
                float y=top?area.top+inset:area.bottom-inset;
                dash.moveTo(x+(left?length:-length),y);
                dash.lineTo(x,y);
                dash.lineTo(x,y+(top?length:-length));
            }
            canvas.drawPath(dash,paint);
            return;
        }
        paint.setColor(Ui.GREEN);paint.setStrokeWidth(d(2));paint.setStyle(Paint.Style.STROKE);paint.setPathEffect(live?new DashPathEffect(new float[]{d(5),d(4)},0):null);canvas.drawRect(area,paint);paint.setPathEffect(null);paint.setStyle(Paint.Style.FILL);
        float[][] handles=points();
        if(live){paint.setStrokeWidth(d(1));canvas.drawLine(area.centerX(),area.top,handles[1][0],handles[1][1],paint);canvas.drawLine(area.right,area.centerY(),handles[3][0],handles[3][1],paint);canvas.drawLine(area.centerX(),area.bottom,handles[5][0],handles[5][1],paint);canvas.drawLine(area.left,area.centerY(),handles[7][0],handles[7][1],paint);}
                for(int i=0;i<handles.length;i++){if(live&&i%2==0)continue;float[] point=handles[i];canvas.drawCircle(point[0],point[1],d(live?10:7),paint);if(live){paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(2));float cx=point[0],cy=point[1],r=d(3);if(i==1){canvas.drawLine(cx-r,cy-r,cx,cy+r,paint);canvas.drawLine(cx,cy+r,cx+r,cy-r,paint);}else if(i==5){canvas.drawLine(cx-r,cy+r,cx,cy-r,paint);canvas.drawLine(cx,cy-r,cx+r,cy+r,paint);}else if(i==3){canvas.drawLine(cx+r,cy-r,cx-r,cy,paint);canvas.drawLine(cx-r,cy,cx+r,cy+r,paint);}else{canvas.drawLine(cx-r,cy-r,cx+r,cy,paint);canvas.drawLine(cx+r,cy,cx-r,cy+r,paint);}paint.setStyle(Paint.Style.FILL);paint.setColor(Ui.GREEN);}}
        float cx=area.centerX(),cy=area.centerY(),radius=d(13);
        paint.setColor(Ui.toolbar());
        canvas.drawCircle(cx,cy,d(19),paint);
        moveIcon.setBounds(Math.round(cx-radius),Math.round(cy-radius),Math.round(cx+radius),Math.round(cy+radius));
        moveIcon.draw(canvas);
    }
    private void drawRegionFrame(Canvas canvas){
        float radius=Math.min(d(18),Math.min(area.width(),area.height())/3);
        Path cutout=new Path();
        cutout.addRoundRect(area,radius,radius,Path.Direction.CW);
        Path shade=new Path();
        shade.addRect(0,0,getWidth(),getHeight(),Path.Direction.CW);
        shade.addRoundRect(area,radius,radius,Path.Direction.CW);
        shade.setFillType(Path.FillType.EVEN_ODD);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x9E09100D);
        canvas.drawPath(shade,paint);
        canvas.save();
        canvas.clipPath(cutout);
        paint.setStrokeWidth(d(.7f));
        paint.setColor(0x33FFFFFF);
        for(int part=1;part<=2;part++){
            float x=area.left+area.width()*part/3,y=area.top+area.height()*part/3;
            canvas.drawLine(x,area.top,x,area.bottom,paint);
            canvas.drawLine(area.left,y,area.right,y,paint);
        }
        canvas.restore();
        float length=Math.min(d(24),Math.min(area.width(),area.height())/3);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xD1FFFFFF);
        paint.setStrokeWidth(d(1.5f));
        canvas.drawLine(area.left+length,area.top,area.right-length,area.top,paint);
        canvas.drawLine(area.left+length,area.bottom,area.right-length,area.bottom,paint);
        canvas.drawLine(area.left,area.top+length,area.left,area.bottom-length,paint);
        canvas.drawLine(area.right,area.top+length,area.right,area.bottom-length,paint);
        paint.setColor(0xFFB5D68E);
        paint.setStrokeWidth(d(4));
        paint.setStrokeCap(Paint.Cap.ROUND);
        for(int index=0;index<4;index++){
            boolean left=index%2==0,top=index<2;
            float x=left?area.left:area.right,y=top?area.top:area.bottom;
            float sx=left?1:-1,sy=top?1:-1;
            canvas.save();
            canvas.translate(x,y);
            canvas.scale(sx,sy);
            dash.reset();
            dash.moveTo(length,0);
            dash.lineTo(radius,0);
            dash.arcTo(0,0,radius*2,radius*2,270,-90,false);
            dash.lineTo(0,length);
            canvas.drawPath(dash,paint);
            canvas.restore();
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        float half=Math.min(d(13),Math.min(area.width(),area.height())/4),thickness=d(2);
        canvas.drawRoundRect(area.centerX()-half,area.top-thickness,area.centerX()+half,area.top+thickness,thickness,thickness,paint);
        canvas.drawRoundRect(area.centerX()-half,area.bottom-thickness,area.centerX()+half,area.bottom+thickness,thickness,thickness,paint);
        canvas.drawRoundRect(area.left-thickness,area.centerY()-half,area.left+thickness,area.centerY()+half,thickness,thickness,paint);
        canvas.drawRoundRect(area.right-thickness,area.centerY()-half,area.right+thickness,area.centerY()+half,thickness,thickness,paint);
        float cx=area.centerX(),cy=area.centerY();
        paint.setColor(0x851E3529);
        canvas.drawCircle(cx,cy,d(19),paint);
        paint.setColor(0xBFFFFFFF);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(d(1.5f));
        canvas.drawCircle(cx,cy,d(19),paint);
        paint.setStyle(Paint.Style.FILL);
        moveIcon.setBounds(Math.round(cx-d(9)),Math.round(cy-d(9)),Math.round(cx+d(9)),Math.round(cy+d(9)));
        moveIcon.draw(canvas);
        drawRegionSize(canvas);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }
    private void drawRegionSize(Canvas canvas){
        Rect source=sourceRect(background==null?getWidth():background.getWidth(),background==null?getHeight():background.getHeight());
        String label="区域截图 · "+source.width()+" × "+source.height()+" px";
        paint.setTypeface(Typeface.create("monospace",Typeface.NORMAL));
        paint.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);
        float width=paint.measureText(label)+d(30),height=d(24);
        float x=Math.max(d(6),Math.min(getWidth()-width-d(6),area.left+d(2)));
        float y=area.top-d(30);
        if(y<d(6))y=area.top+d(8);
        paint.setColor(0xE6182A20);
        canvas.drawRoundRect(x,y,x+width,y+height,d(9),d(9),paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(d(.7f));
        paint.setColor(0x59B5D68E);
        canvas.drawRoundRect(x,y,x+width,y+height,d(9),d(9),paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFF6C945);
        canvas.drawCircle(x+d(10),y+height/2,d(3),paint);
        paint.setColor(0xFFE2F0E4);
        Paint.FontMetrics font=paint.getFontMetrics();
        canvas.drawText(label,x+d(20),y+height/2-(font.ascent+font.descent)/2,paint);
    }
}
