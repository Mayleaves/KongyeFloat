package com.kongye.floatball;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import java.io.File;
final class V18Checks {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    static void run(Context c){count=0;String error="";Bitmap source=null,image=null;ImageEditView editor=null;RecordingPlayer player=null;
        try{
            String value="[{\"text\":\"KY\",\"x\":0.25,\"y\":0.5,\"size\":0.1,\"color\":-65536}]";
            image=RecordingWatermark.render(value,400,600);int pixels=0;boolean misplaced=false;for(int y=0;y<600;y++)for(int x=0;x<400;x++)if(Color.alpha(image.getPixel(x,y))>0){pixels++;if(x<98||x>260||y<230||y>330)misplaced=true;}
            check(pixels>100,"normalized watermark renders text");check(!misplaced,"watermark stays at chosen source position");image.recycle();image=null;
            check(RecordingWatermark.parse("").length()==0,"empty watermark remains disabled");check(RecordingWatermark.parse("legacy").length()==1,"old watermark text migrates");
            source=Bitmap.createBitmap(400,600,Bitmap.Config.ARGB_8888);editor=new ImageEditView(c,source);editor.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));editor.layout(0,0,400,600);editor.loadWatermark(value);
            org.json.JSONObject mark=new org.json.JSONArray(editor.watermark()).getJSONObject(0);check(mark.getString("text").equals("KY"),"watermark editor round-trips text");check(Math.abs(mark.getDouble("x")-.25)<.001&&Math.abs(mark.getDouble("size")-.1)<.001,"watermark editor preserves relative placement and size");editor.reset();check(editor.watermark().isEmpty(),"delete all watermark objects disables watermark");
            player=new RecordingPlayer(c,new File(c.getCacheDir(),"not-opened.mp4"),true,0,false,()->{});player.measure(View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1200,View.MeasureSpec.EXACTLY));player.layout(0,0,800,1200);View texture=((ViewGroup)player.getChildAt(0)).getChildAt(0);
            long now=SystemClock.uptimeMillis();int[] actions={MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_DOWN|256,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_POINTER_UP|256,MotionEvent.ACTION_UP};
            for(int i=0;i<actions.length;i++){int n=i==0||i==actions.length-1?1:2;MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[n];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[n];float span=i<2?80:i==2?120:200;for(int p=0;p<n;p++){props[p]=new MotionEvent.PointerProperties();props[p].id=p;props[p].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[p]=new MotionEvent.PointerCoords();coords[p].x=400+(p==0?-span:span);coords[p].y=500;coords[p].pressure=1;coords[p].size=1;}MotionEvent event=MotionEvent.obtain(now,now+i*40,actions[i],n,props,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);texture.dispatchTouchEvent(event);event.recycle();}
            check(Prefs.of(c).getFloat("qa_play_zoom",0)>2,"two-finger video gesture expands texture");check(!player.playing(),"video pinch does not start playback");
        }catch(Throwable t){error=t.getClass().getSimpleName()+": "+t.getMessage();}finally{if(player!=null)player.release();if(editor!=null)editor.dispose();if(source!=null)source.recycle();if(image!=null)image.recycle();}
        Prefs.of(c).edit().putInt("qa_v18_checks",count).putBoolean("qa_v18_pass",error.isEmpty()).putString("qa_v18_error",error).apply();
    }
}
