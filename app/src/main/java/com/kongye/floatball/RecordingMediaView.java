package com.kongye.floatball;
import android.content.Context;import android.graphics.*;import android.graphics.drawable.*;import android.os.Build;import android.view.View;import android.widget.ImageView;import java.io.*;
/** Media preview must not use the floating ball's stored alpha crop or scale. */
@android.annotation.SuppressLint("AppCompatCustomView") // Framework Activity: no AppCompat replacement/tinting is used.
final class RecordingMediaView {
 static View decode(Context c,File file,int pixels){try{
  if(Build.VERSION.SDK_INT>=28){Drawable drawable=ImageDecoder.decodeDrawable(ImageDecoder.createSource(file),(decoder,info,source)->{int longest=Math.max(info.getSize().getWidth(),info.getSize().getHeight());if(longest>pixels*2)decoder.setTargetSampleSize(Math.max(1,longest/(pixels*2)));});return new ImageView(c){ {setImageDrawable(drawable);setScaleType(ScaleType.FIT_CENTER);} @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(drawable instanceof AnimatedImageDrawable a){a.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);a.start();}}@Override protected void onDetachedFromWindow(){if(drawable instanceof AnimatedImageDrawable a)a.stop();super.onDetachedFromWindow();}};}
  Movie movie;try(InputStream in=new FileInputStream(file)){movie=Movie.decodeStream(in);}if(movie==null)return null;return new View(c){final long began=android.os.SystemClock.uptimeMillis();final Paint paint=new Paint(3);@Override protected void onDraw(Canvas canvas){int duration=Math.max(100,movie.duration());movie.setTime((int)((android.os.SystemClock.uptimeMillis()-began)%duration));float fit=Math.min(getWidth()/(float)movie.width(),getHeight()/(float)movie.height());canvas.save();canvas.translate((getWidth()-movie.width()*fit)/2,(getHeight()-movie.height()*fit)/2);canvas.scale(fit,fit);movie.draw(canvas,0,0,paint);canvas.restore();if(isShown())postInvalidateDelayed(33);}};
 }catch(Exception|OutOfMemoryError error){return null;}}
}
