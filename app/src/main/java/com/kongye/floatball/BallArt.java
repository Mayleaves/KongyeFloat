package com.kongye.floatball;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;
import java.io.*;

final class BallArt {
    static final int DEFAULT_SIZE=36;
    static final float LEAF_SCALE=20f/44;
    static final int DEFAULT_COLOR=0xFF85A867,FOREGROUND=Color.WHITE;
    static int color(Context c){int value=Prefs.of(c).getInt("color",DEFAULT_COLOR);return value==Ui.BRAND||value==0xFF287A59||value==0xFF28765A||value==0xFF86A26D?DEFAULT_COLOR:value;}
    static File file(Context c){return new File(c.getFilesDir(),"ball-art");}
    private static boolean preparing;private static final java.util.ArrayList<Runnable> prepared=new java.util.ArrayList<>();
    private static String signature(File f){return f.length()+":"+f.lastModified();}
    static RectF bounds(Context c){
        if(!signature(file(c)).equals(Prefs.of(c).getString("art_bounds_file","")))return new RectF(0,0,1,1);
        try{String[] a=Prefs.of(c).getString("art_bounds","").split(",");RectF r=new RectF(Float.parseFloat(a[0]),Float.parseFloat(a[1]),Float.parseFloat(a[2]),Float.parseFloat(a[3]));if(r.left>=0&&r.top>=0&&r.right<=1&&r.bottom<=1&&!r.isEmpty())return r;}catch(RuntimeException ignored){}return new RectF(0,0,1,1);
    }
    static int[] dimensions(Context c,int pixels){RectF b=bounds(c);if(!Prefs.of(c).getBoolean("custom_art",false)||!file(c).isFile())return new int[]{pixels,pixels};float aspect=Prefs.of(c).getFloat("art_canvas_aspect",1)*b.width()/b.height();return aspect>=1?new int[]{pixels,Math.max(1,Math.round(pixels/aspect))}:new int[]{Math.max(1,Math.round(pixels*aspect)),pixels};}
    /** Upgrade existing art on a worker; no media leaves the device. */
    static synchronized void prepare(Context c,Runnable done){
        File f=file(c);if(preparing){prepared.add(done);return;}if(!f.isFile()||!Prefs.of(c).getBoolean("custom_art",false)||signature(f).equals(Prefs.of(c).getString("art_bounds_file","")))return;
        preparing=true;prepared.add(done);Context app=c.getApplicationContext();String before=signature(f);
        new Thread(()->{try{Analysis result=analyze(f);if(before.equals(signature(f))){saveBounds(app,f,result,true);}}finally{java.util.ArrayList<Runnable> callbacks;synchronized(BallArt.class){preparing=false;callbacks=new java.util.ArrayList<>(prepared);prepared.clear();}android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());for(Runnable callback:callbacks)main.post(callback);}},"Kongye-Art-Bounds").start();
    }
    private record Analysis(RectF area,int width,int height){}
    private static Analysis analyze(File f){
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);int w=Math.max(1,o.outWidth),h=Math.max(1,o.outHeight);RectF full=new RectF(0,0,1,1);
        try{
            byte[] header=new byte[6];try(InputStream in=new FileInputStream(f)){if(in.read(header)!=6)return new Analysis(full,w,h);}
            if(new String(header,java.nio.charset.StandardCharsets.US_ASCII).startsWith("GIF")){GifBounds.Bounds b=GifBounds.read(java.nio.file.Files.readAllBytes(f.toPath()));return new Analysis(new RectF(b.left()/(float)b.width(),b.top()/(float)b.height(),b.right()/(float)b.width(),b.bottom()/(float)b.height()),b.width(),b.height());}
            // Animated WebP/APNG keep their full canvas rather than risk clipping later frames.
            String mime=o.outMimeType;if(!"image/png".equals(mime))return new Analysis(full,w,h);
            byte[] bytes=java.nio.file.Files.readAllBytes(f.toPath());for(int i=8;i+12<=bytes.length;){int n=java.nio.ByteBuffer.wrap(bytes,i,4).getInt();if(n<0||n>bytes.length-i-12)break;if(bytes[i+4]=='a'&&bytes[i+5]=='c'&&bytes[i+6]=='T'&&bytes[i+7]=='L')return new Analysis(full,w,h);i+=12+n;}
            o.inJustDecodeBounds=false;o.inSampleSize=Math.max(1,Math.max(w,h)/1024);Bitmap image=BitmapFactory.decodeFile(f.getPath(),o);if(image==null)return new Analysis(full,w,h);
            try{int iw=image.getWidth(),ih=image.getHeight(),left=iw,top=ih,right=0,bottom=0;int[] row=new int[iw];for(int y=0;y<ih;y++){image.getPixels(row,0,iw,0,y,iw,1);for(int x=0;x<iw;x++)if((row[x]>>>24)>0){left=Math.min(left,x);right=Math.max(right,x+1);top=Math.min(top,y);bottom=Math.max(bottom,y+1);}}if(right>left&&bottom>top)full.set(Math.max(0,left-1)/(float)iw,Math.max(0,top-1)/(float)ih,Math.min(iw,right+1)/(float)iw,Math.min(ih,bottom+1)/(float)ih);}finally{image.recycle();}
        }catch(IOException|RuntimeException|OutOfMemoryError ignored){}return new Analysis(full,w,h);
    }
    private static void saveBounds(Context c,File f,Analysis a,boolean migrate){
        RectF r=a.area;android.content.SharedPreferences.Editor e=Prefs.of(c).edit().putString("art_bounds_file",signature(f)).putString("art_bounds",r.left+","+r.top+","+r.right+","+r.bottom).putFloat("art_canvas_aspect",a.width/(float)a.height);
        if(migrate){float ratio=Math.max(a.width*r.width(),a.height*r.height())/Math.max(a.width,a.height);float old=BallSettings.dp(c);int percent=BallScale.sizePercent(old*ratio);e.putInt("size_percent",percent).putInt("size",Math.round(BallScale.dp(percent)));}e.apply();
    }
    // Overlay widgets use the platform theme; this app does not use AppCompat activities.
    @android.annotation.SuppressLint("AppCompatCustomView")
    static View create(Context c,int pixels){
        File file=file(c);if(!file.isFile()||!Prefs.of(c).getBoolean("custom_art",false))return null;
        return decode(c,file,pixels);
    }
    static void styleContainer(View view,boolean custom){Context c=view.getContext();if(custom)view.setBackground(null);else{GradientDrawable circle=Ui.bg((color(c)&0x00FFFFFF)|0xA6000000,100,c);circle.setStroke(Math.max(1,Ui.dp(c,1.6f)),0xB3FFFFFF);view.setBackground(circle);}view.setElevation(custom?0:Ui.dp(c,3));}
    @android.annotation.SuppressLint("AppCompatCustomView")
    static View decode(Context c,File file,int pixels){
        try{
            if(Build.VERSION.SDK_INT>=28){
                RectF visible=bounds(c);Drawable drawable=ImageDecoder.decodeDrawable(ImageDecoder.createSource(file),(decoder,info,source)->{int w=info.getSize().getWidth(),h=info.getSize().getHeight();float scale=Math.min(1f,Math.min(pixels/Math.max(w*visible.width(),h*visible.height()),2048f/Math.max(w,h)));decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});
                return new ImageView(c){
                    {setImageDrawable(drawable);setScaleType(ScaleType.MATRIX);setBackground(null);}
                    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);float vw=drawable.getIntrinsicWidth()*visible.width(),vh=drawable.getIntrinsicHeight()*visible.height(),scale=Math.min(w/vw,h/vh);Matrix matrix=new Matrix();matrix.setScale(scale,scale);matrix.postTranslate((w-vw*scale)/2-visible.left*drawable.getIntrinsicWidth()*scale,(h-vh*scale)/2-visible.top*drawable.getIntrinsicHeight()*scale);setImageMatrix(matrix);}
                    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(drawable instanceof AnimatedImageDrawable animated){animated.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);animated.start();}}
                    @Override protected void onDetachedFromWindow(){if(drawable instanceof AnimatedImageDrawable animated)animated.stop();super.onDetachedFromWindow();}
                    @Override protected void onWindowVisibilityChanged(int visible){super.onWindowVisibilityChanged(visible);if(drawable instanceof AnimatedImageDrawable animated){if(visible==VISIBLE)animated.start();else animated.stop();}}
                };
            }
            try(InputStream stream=new FileInputStream(file)){Movie movie=Movie.decodeStream(stream);if(movie!=null)return new MovieView(c,movie,bounds(c));}
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);options.inSampleSize=Math.max(1,Math.max(options.outWidth,options.outHeight)/pixels);options.inJustDecodeBounds=false;
            Bitmap bitmap=BitmapFactory.decodeFile(file.getPath(),options);if(bitmap==null)return null;RectF b=bounds(c);int x=Math.min(bitmap.getWidth()-1,Math.round(b.left*bitmap.getWidth())),y=Math.min(bitmap.getHeight()-1,Math.round(b.top*bitmap.getHeight()));Bitmap clipped=Bitmap.createBitmap(bitmap,x,y,Math.max(1,Math.min(bitmap.getWidth()-x,Math.round(b.right*bitmap.getWidth())-x)),Math.max(1,Math.min(bitmap.getHeight()-y,Math.round(b.bottom*bitmap.getHeight())-y)));ImageView image=new ImageView(c);image.setImageBitmap(clipped);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackground(null);return image;
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
            Analysis analysis=analyze(pending);java.nio.file.Files.move(pending.toPath(),file(c).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);saveBounds(c,file(c),analysis,false);
            Prefs.of(c).edit().putBoolean("custom_art",true).apply();
        }catch(RuntimeException|OutOfMemoryError e){throw new IOException("无法解码这张图片，请换一张图片",e);}finally{pending.delete();}
    }
    private static class MovieView extends View {
        final Movie movie;final RectF visible;long start=android.os.SystemClock.uptimeMillis();
        MovieView(Context c,Movie movie,RectF visible){super(c);this.movie=movie;this.visible=visible;setLayerType(LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas canvas){canvas.save();int duration=movie.duration();movie.setTime((int)((android.os.SystemClock.uptimeMillis()-start)%(duration>0?duration:1000)));float w=movie.width()*visible.width(),h=movie.height()*visible.height(),scale=Math.min(getWidth()/w,getHeight()/h);canvas.translate((getWidth()-w*scale)/2-visible.left*movie.width()*scale,(getHeight()-h*scale)/2-visible.top*movie.height()*scale);canvas.scale(scale,scale);movie.draw(canvas,0,0);canvas.restore();if(isShown())postInvalidateDelayed(33);}
    }
}
