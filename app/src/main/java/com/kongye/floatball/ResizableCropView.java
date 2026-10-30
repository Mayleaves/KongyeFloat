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
    ResizableCropView(Context c,Bitmap background,boolean live){super(c);this.background=background;this.live=live;setContentDescription("截图选择框，可拖动四边和四角调整大小，拖动中心移动");}
    void setChanged(Consumer<RectF> callback){changed=callback;}
    void setInitial(Rect source,int sourceW,int sourceH){if(source!=null){area.set(source);initialW=sourceW;initialH=sourceH;}}
    private int initialW,initialH;
    private float d(float n){return Ui.dp(getContext(),n);}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        if(area.isEmpty())area.set(w*.1f,h*.3f,w*.9f,h*.7f);
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
        case MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP->{if(creating)area.set(Math.min(downX,x),Math.min(downY,y),Math.max(downX,x),Math.max(downY,y));else if(handle>=0){float[] r=CropGeometry.adjust(original,handle,x-downX,y-downY,getWidth(),getHeight(),d(36));area.set(r[0],r[1],r[2],r[3]);}if(event.getActionMasked()==MotionEvent.ACTION_UP){if(area.width()<d(20)||area.height()<d(20))area.set(getWidth()*.1f,getHeight()*.3f,getWidth()*.9f,getHeight()*.7f);performClick();handle=-1;}notifyChange();return true;}
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
        paint.setStyle(Paint.Style.FILL);paint.setColor(live?0xCC000000:0x88000000);canvas.drawRect(0,0,getWidth(),area.top,paint);canvas.drawRect(0,area.bottom,getWidth(),getHeight(),paint);canvas.drawRect(0,area.top,area.left,area.bottom,paint);canvas.drawRect(area.right,area.top,getWidth(),area.bottom,paint);
        paint.setColor(Ui.GREEN);paint.setStrokeWidth(d(2));paint.setStyle(Paint.Style.STROKE);paint.setPathEffect(live?new DashPathEffect(new float[]{d(5),d(4)},0):null);canvas.drawRect(area,paint);paint.setPathEffect(null);paint.setStyle(Paint.Style.FILL);
        float[][] handles=points();
        if(live){paint.setStrokeWidth(d(1));canvas.drawLine(area.centerX(),area.top,handles[1][0],handles[1][1],paint);canvas.drawLine(area.right,area.centerY(),handles[3][0],handles[3][1],paint);canvas.drawLine(area.centerX(),area.bottom,handles[5][0],handles[5][1],paint);canvas.drawLine(area.left,area.centerY(),handles[7][0],handles[7][1],paint);}
                for(int i=0;i<handles.length;i++){if(live&&i%2==0)continue;float[] point=handles[i];canvas.drawCircle(point[0],point[1],d(live?10:7),paint);if(live){paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(2));float cx=point[0],cy=point[1],r=d(3);if(i==1){canvas.drawLine(cx-r,cy-r,cx,cy+r,paint);canvas.drawLine(cx,cy+r,cx+r,cy-r,paint);}else if(i==5){canvas.drawLine(cx-r,cy+r,cx,cy-r,paint);canvas.drawLine(cx,cy-r,cx+r,cy+r,paint);}else if(i==3){canvas.drawLine(cx+r,cy-r,cx-r,cy,paint);canvas.drawLine(cx-r,cy,cx+r,cy+r,paint);}else{canvas.drawLine(cx-r,cy-r,cx+r,cy,paint);canvas.drawLine(cx+r,cy,cx-r,cy+r,paint);}paint.setStyle(Paint.Style.FILL);paint.setColor(Ui.GREEN);}}
        paint.setColor(Ui.toolbar());canvas.drawCircle(area.centerX(),area.centerY(),d(19),paint);paint.setColor(Color.WHITE);paint.setStrokeWidth(d(2));canvas.drawLine(area.centerX()-d(10),area.centerY(),area.centerX()+d(10),area.centerY(),paint);canvas.drawLine(area.centerX(),area.centerY()-d(10),area.centerX(),area.centerY()+d(10),paint);
    }
}
