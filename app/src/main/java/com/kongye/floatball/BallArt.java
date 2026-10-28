package com.kongye.floatball;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;
import java.io.*;

final class BallArt {
    static File file(Context c){return new File(c.getFilesDir(),"ball-art");}
    // Overlay widgets use the platform theme; this app does not use AppCompat activities.
    @android.annotation.SuppressLint("AppCompatCustomView")
    static View create(Context c,int pixels){
        File file=file(c);if(!file.isFile()||!Prefs.of(c).getBoolean("custom_art",false))return null;
        return decode(c,file,pixels);
    }
    static void styleContainer(View view,boolean custom){Context c=view.getContext();view.setBackground(custom?null:Ui.bg(Prefs.of(c).getInt("color",Ui.BRAND),100,c));view.setElevation(custom?0:Ui.dp(c,6));}
    @android.annotation.SuppressLint("AppCompatCustomView")
    static View decode(Context c,File file,int pixels){
        try{
            if(Build.VERSION.SDK_INT>=28){
                Drawable drawable=ImageDecoder.decodeDrawable(ImageDecoder.createSource(file),(decoder,info,source)->{int w=info.getSize().getWidth(),h=info.getSize().getHeight();float scale=Math.min(1f,Math.min(pixels/(float)Math.min(w,h),1024f/Math.max(w,h)));decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});
                return new ImageView(c){
                    {setImageDrawable(drawable);setScaleType(ScaleType.FIT_CENTER);setBackground(null);}
                    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(drawable instanceof AnimatedImageDrawable animated){animated.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);animated.start();}}
                    @Override protected void onDetachedFromWindow(){if(drawable instanceof AnimatedImageDrawable animated)animated.stop();super.onDetachedFromWindow();}
                    @Override protected void onWindowVisibilityChanged(int visible){super.onWindowVisibilityChanged(visible);if(drawable instanceof AnimatedImageDrawable animated){if(visible==VISIBLE)animated.start();else animated.stop();}}
                };
            }
            try(InputStream stream=new FileInputStream(file)){Movie movie=Movie.decodeStream(stream);if(movie!=null)return new MovieView(c,movie);}
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);options.inSampleSize=Math.max(1,Math.max(options.outWidth,options.outHeight)/pixels);options.inJustDecodeBounds=false;
            Bitmap bitmap=BitmapFactory.decodeFile(file.getPath(),options);if(bitmap==null)return null;ImageView image=new ImageView(c);image.setImageBitmap(bitmap);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackground(null);return image;
        }catch(IOException|RuntimeException|OutOfMemoryError e){return null;}
    }
    static void importFile(Context c,android.net.Uri uri)throws IOException{
        File pending=new File(c.getFilesDir(),"ball-art.pending");
        try{
            try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(pending)){
                if(in==null)throw new IOException("无法读取所选图片");byte[] bytes=new byte[8192];int total=0,n;
                while((n=in.read(bytes))!=-1){total+=n;if(total>20*1024*1024)throw new IOException("请选择小于 20 MB 的图片或 GIF");out.write(bytes,0,n);}
            }
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(pending.getPath(),bounds);
            if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>4096||bounds.outHeight>4096||(long)bounds.outWidth*bounds.outHeight>16000000)throw new IOException("图片格式不支持，或尺寸超过 4096 像素");
            if(Build.VERSION.SDK_INT>=28){Drawable check=ImageDecoder.decodeDrawable(ImageDecoder.createSource(pending),(d,i,s)->d.setTargetSize(96,96));if(check instanceof AnimatedImageDrawable animation)animation.stop();}
            java.nio.file.Files.move(pending.toPath(),file(c).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Prefs.of(c).edit().putBoolean("custom_art",true).apply();
        }catch(RuntimeException|OutOfMemoryError e){throw new IOException("无法解码这张图片，请换一张图片",e);}finally{pending.delete();}
    }
    private static class MovieView extends View {
        final Movie movie;long start=android.os.SystemClock.uptimeMillis();
        MovieView(Context c,Movie movie){super(c);this.movie=movie;setLayerType(LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas canvas){canvas.save();int duration=movie.duration();movie.setTime((int)((android.os.SystemClock.uptimeMillis()-start)%(duration>0?duration:1000)));float scale=Math.min(getWidth()/(float)movie.width(),getHeight()/(float)movie.height());canvas.translate((getWidth()-movie.width()*scale)/2,(getHeight()-movie.height()*scale)/2);canvas.scale(scale,scale);movie.draw(canvas,0,0);canvas.restore();if(isShown())postInvalidateDelayed(33);}
    }
}
