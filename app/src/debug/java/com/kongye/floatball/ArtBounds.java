package com.kongye.floatball;
import android.content.Context;import android.graphics.*;import java.io.*;import org.json.JSONObject;
/** Metadata only; the imported file and decoded pixels stay on the phone. */
final class ArtBounds {
 static void inspect(Context c){try{
  File file=BallArt.file(c);JSONObject result=new JSONObject();if(!file.isFile()){result.put("state","no_imported_art");save(c,result);return;}
  BitmapFactory.Options info=new BitmapFactory.Options();info.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),info);result.put("canvas_width",info.outWidth).put("canvas_height",info.outHeight).put("mime",info.outMimeType);
  Movie movie;try(InputStream in=new FileInputStream(file)){movie=Movie.decodeStream(in);}Bitmap bitmap;int frames,duration=0;
  if(movie!=null){int w=movie.width(),h=movie.height();float factor=Math.min(1,256f/Math.max(w,h));bitmap=Bitmap.createBitmap(Math.max(1,Math.round(w*factor)),Math.max(1,Math.round(h*factor)),Bitmap.Config.ARGB_8888);frames=12;duration=Math.max(1,movie.duration());}
  else{BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=Math.max(1,Math.max(info.outWidth,info.outHeight)/256);bitmap=BitmapFactory.decodeFile(file.getPath(),options);frames=1;}
  if(bitmap==null){result.put("state","decode_unavailable");save(c,result);return;}int left=bitmap.getWidth(),top=bitmap.getHeight(),right=-1,bottom=-1;int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];
  for(int frame=0;frame<frames;frame++){
   if(movie!=null){bitmap.eraseColor(Color.TRANSPARENT);movie.setTime(duration*frame/frames);Canvas canvas=new Canvas(bitmap);canvas.scale(bitmap.getWidth()/(float)movie.width(),bitmap.getHeight()/(float)movie.height());movie.draw(canvas,0,0);}
   bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)if(Color.alpha(pixels[y*bitmap.getWidth()+x])>8){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}
  }
  float span=right>=left?Math.max(right-left+1,bottom-top+1)/(float)Math.max(bitmap.getWidth(),bitmap.getHeight()):0;
  result.put("state","sampled").put("sample_frames",frames).put("sample_width",bitmap.getWidth()).put("sample_height",bitmap.getHeight()).put("content_width",Math.max(0,right-left+1)).put("content_height",Math.max(0,bottom-top+1)).put("visible_long_edge_fraction",span).put("visible_at_130_dp",130*span);bitmap.recycle();save(c,result);
 }catch(Exception|OutOfMemoryError e){try{save(c,new JSONObject().put("state","error").put("error",e.getClass().getSimpleName()));}catch(Exception ignored){}}}
 private static void save(Context c,JSONObject result){Prefs.of(c).edit().putString("qa_art_bounds",result.toString()).commit();}
}
