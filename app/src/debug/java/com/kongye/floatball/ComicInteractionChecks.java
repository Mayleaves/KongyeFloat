package com.kongye.floatball;
import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.*;
import org.json.JSONObject;
final class ComicInteractionChecks {
 static JSONObject run(Context c)throws Exception{
  int passed=0;
  LeafSwitch sw=new LeafSwitch(c);sw.layout(0,0,120,120);
  if(!sw.isClickable())throw new IllegalStateException("switch not clickable");
  sw.setChecked(false);sw.performClick();if(!sw.isChecked())throw new IllegalStateException("switch won't open");passed++;
  sw.performClick();if(sw.isChecked())throw new IllegalStateException("switch won't close");passed++;
  ResizableCropView crop=new ResizableCropView(c,null,false);crop.useLongshotControls();crop.setInitial(new Rect(100,200,980,1700),1080,1920);crop.layout(0,0,1080,1920);
  int[] indices={1,3,5,7};
  for(int i:indices){crop.area.set(100,200,980,1700);float[] point=crop.points()[i];float dx=i==3?-70:i==7?70:0,dy=i==1?70:i==5?-70:0;
   long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point[0],point[1],0),up=MotionEvent.obtain(now,now+100,MotionEvent.ACTION_UP,point[0]+dx,point[1]+dy,0);
   crop.dispatchTouchEvent(down);crop.dispatchTouchEvent(up);down.recycle();up.recycle();
   if(i==1&&(Math.abs(crop.area.top-270)>1||crop.area.bottom!=1700)||i==3&&(Math.abs(crop.area.right-910)>1||crop.area.left!=100)||i==5&&(Math.abs(crop.area.bottom-1630)>1||crop.area.top!=200)||i==7&&(Math.abs(crop.area.left-170)>1||crop.area.right!=980))throw new IllegalStateException("direction arrow "+i);
   passed++;
  }
  for(float[] point:new float[][]{{300,450},{100,200},{100,650},{40,80},{980,1700}}){
   crop.area.set(100,200,980,1700);android.graphics.RectF before=new android.graphics.RectF(crop.area);
   long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,0,point[0],point[1],0),up=MotionEvent.obtain(now,now+100,1,point[0]+60,point[1]+60,0);
   boolean accepted=crop.dispatchTouchEvent(down);if(accepted)crop.dispatchTouchEvent(up);down.recycle();up.recycle();
   if(accepted||!before.equals(crop.area))throw new IllegalStateException("non-control moved crop");passed++;
  }
  crop.area.set(100,200,980,1700);long now=SystemClock.uptimeMillis();
  MotionEvent down=MotionEvent.obtain(now,now,0,540,950,0),up=MotionEvent.obtain(now,now+100,1,560,980,0);crop.dispatchTouchEvent(down);crop.dispatchTouchEvent(up);down.recycle();up.recycle();
  if(!crop.area.equals(new android.graphics.RectF(120,230,1000,1730)))throw new IllegalStateException("center move");passed++;
  return new JSONObject().put("passed",passed).put("phase","complete");
 }
}
