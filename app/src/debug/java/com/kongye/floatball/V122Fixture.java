package com.kongye.floatball;
import android.content.Context;
import android.graphics.*;
import java.util.concurrent.*;
/** Generated document only: no capture of the phone. */
final class V122Fixture {
    static RegionResult result;
    static void show(Context c,String kind){
        close();ThemePolicy.apply(c);Bitmap b=Bitmap.createBitmap(720,480,Bitmap.Config.ARGB_8888);b.eraseColor(0xFFF5F8F5);Canvas canvas=new Canvas(b);Paint p=new Paint(3);p.setColor(0xFF23533D);p.setTextSize(42);canvas.drawText("Kongye UI test 123",40,100,p);canvas.drawText("Generated local document",40,170,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);canvas.drawRect(40,220,680,420,p);
        ExecutorService worker=Executors.newSingleThreadExecutor();result=new RegionResult(c,b,worker,()->{worker.shutdownNow();result=null;},on->{},kind);
    }
    static void close(){if(result!=null){RegionResult old=result;result=null;old.close();}}
}
