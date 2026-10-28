package com.kongye.floatball;

import android.content.Context;
import android.graphics.*;
import android.view.*;

final class PanZoomImageView extends View {
    private final Bitmap image;private float scale=1,fit=1,x,y,lastX,lastY;private final Paint paint=new Paint(3);private final ScaleGestureDetector pinch;private final GestureDetector taps;
    PanZoomImageView(Context c,Bitmap image){super(c);this.image=image;setLayerType(LAYER_TYPE_SOFTWARE,null);setContentDescription("长截图大图预览，可双指缩放、拖动，双击切换适合屏幕和实际宽度");pinch=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector detector){float old=scale;scale=Math.max(fit,Math.min(Math.max(4,fit*8),scale*detector.getScaleFactor()));x=detector.getFocusX()-(detector.getFocusX()-x)*scale/old;y=detector.getFocusY()-(detector.getFocusY()-y)*scale/old;bound();invalidate();return true;}});taps=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){public boolean onDown(MotionEvent e){return true;}public boolean onDoubleTap(MotionEvent e){scale=Math.abs(scale-fit)<.01?Math.max(fit,getWidth()/(float)image.getWidth()):fit;x=(getWidth()-image.getWidth()*scale)/2;y=0;bound();invalidate();return true;}});}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){fit=Math.min(w/(float)image.getWidth(),h/(float)image.getHeight());scale=fit;x=(w-image.getWidth()*scale)/2;y=(h-image.getHeight()*scale)/2;}
    private void bound(){float w=image.getWidth()*scale,h=image.getHeight()*scale;x=w<=getWidth()?(getWidth()-w)/2:Math.max(getWidth()-w,Math.min(0,x));y=h<=getHeight()?(getHeight()-h)/2:Math.max(getHeight()-h,Math.min(0,y));}
    @Override protected void onDraw(Canvas canvas){if(image.isRecycled())return;canvas.save();canvas.translate(x,y);canvas.scale(scale,scale);canvas.drawBitmap(image,0,0,paint);canvas.restore();}
    @Override public boolean onTouchEvent(MotionEvent e){pinch.onTouchEvent(e);taps.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();}else if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!pinch.isInProgress()&&e.getPointerCount()==1){x+=e.getX()-lastX;y+=e.getY()-lastY;lastX=e.getX();lastY=e.getY();bound();invalidate();}else if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;}
    @Override public boolean performClick(){super.performClick();return true;}
}
