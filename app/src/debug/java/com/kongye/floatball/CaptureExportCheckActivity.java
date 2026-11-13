package com.kongye.floatball;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.*;
import android.widget.TextView;
import java.io.InputStream;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Generated pixels only. Hold OCR indefinitely and prove that saving still finishes. */
public final class CaptureExportCheckActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService images=Executors.newSingleThreadExecutor();
    private final CountDownLatch releaseOcr=new CountDownLatch(1),releaseImage=new CountDownLatch(1);
    private RegionResult result;
    private Bitmap image;
    private Uri saved;
    private Map<String,?> before;
    private long started;
    private int stage;
    private boolean done;
    private TextView status;
    private static final String[] KEYS={"document_kind","document_width","document_height","last_capture_uri","last_capture_kind","last_capture_width","last_capture_height","last_ocr_state","result_pinned","result_all_folded","result_text_folded","result_image_folded"};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);status=new TextView(this);status.setText("测试保存图片，不读取屏幕内容");setContentView(status);
        before=new HashMap<>(Prefs.of(this).getAll());report().edit().clear().putString("state","running").commit();
        main.post(()->begin(false));
    }
    private SharedPreferences report(){return getSharedPreferences("capture_export_check",MODE_PRIVATE);}
    private static Object get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void call(Object o,String name,Class<?>[] types,Object... args)throws Exception{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(o,args);}
    private void require(boolean ok,String error){if(!ok)throw new IllegalStateException(error);}
    private void begin(boolean closeEarly){
        try{
            stage=closeEarly?1:0;image=Bitmap.createBitmap(960,640,Bitmap.Config.ARGB_8888);int[] pixels=new int[960*640];Random random=new Random(13);
            for(int i=0;i<pixels.length;i++)pixels[i]=0xFF000000|random.nextInt(0x1000000);image.setPixels(pixels,0,960,0,0,960,640);
            result=new RegionResult(this,image,images,()->{},pin->{},"Screen");
            ExecutorService ocr=(ExecutorService)get(result,"recognitionWorker");
            ocr.execute(()->{try{releaseOcr.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            // Use the real recognize method and bitmap ownership, with its queue deliberately held.
            call(result,"recognize",new Class<?>[0]);
            if(closeEarly)images.execute(()->{try{releaseImage.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            started=SystemClock.elapsedRealtime();call(result,"save",new Class<?>[]{boolean.class},false);
            call(result,"save",new Class<?>[]{boolean.class},false); // repeated taps must not enqueue a second export
            if(closeEarly){result.close();require(!image.isRecycled(),"bitmap recycled before queued export");releaseImage.countDown();}
            poll();
        }catch(Throwable e){finishCheck(e);}
    }
    private void poll(){
        try{
            if(SystemClock.elapsedRealtime()-started>7000)throw new IllegalStateException("export blocked by held OCR");
            if(!(boolean)get(result,"closed")||(boolean)get(result,"saving")){main.postDelayed(this::poll,30);return;}
            saved=Uri.parse(Prefs.of(this).getString("last_capture_uri",""));
            require(!saved.toString().isEmpty(),"missing saved image");
            require(releaseOcr.getCount()==1,"OCR released before export finished");
            try(InputStream in=getContentResolver().openInputStream(saved)){
                Bitmap b=BitmapFactory.decodeStream(in);require(b!=null,"PNG not readable");
                try{require(b.getWidth()==960&&b.getHeight()==640,"wrong dimensions");require(b.getPixel(0,0)==(0xFF000000|new Random(13).nextInt(0x1000000)),"PNG changed pixels");}finally{b.recycle();}
            }
            require(image.isRecycled(),"closed result retains bitmap after export");
            ExecutorService ocr=(ExecutorService)get(result,"recognitionWorker");
            require(ocr.isShutdown(),"closed result retains OCR executor");
            long ms=SystemClock.elapsedRealtime()-started;
            report().edit().putLong(stage==0?"save_while_ocr_held_ms":"close_during_save_ms",ms).commit();
            getContentResolver().delete(saved,null,null);saved=null;
            Prefs.of(this).edit().remove("last_capture_uri").apply();
            if(stage==0){result=null;image=null;begin(true);}else finishCheck(null);
        }catch(Throwable e){finishCheck(e);}
    }
    private void finishCheck(Throwable error){
        if(done)return;done=true;
        if(result!=null)result.close();releaseImage.countDown();releaseOcr.countDown();images.shutdown();
        if(saved!=null)getContentResolver().delete(saved,null,null);
        SharedPreferences.Editor restore=Prefs.of(this).edit();
        if(before!=null)for(String key:KEYS){Object value=before.get(key);if(value==null)restore.remove(key);else if(value instanceof String s)restore.putString(key,s);else if(value instanceof Integer i)restore.putInt(key,i);else if(value instanceof Boolean b)restore.putBoolean(key,b);}
        restore.commit();String message=error==null?"passed":error.toString();report().edit().putString("state",message).commit();status.setText(message);
    }
    @Override protected void onDestroy(){if(!done)finishCheck(new IllegalStateException("test interrupted"));super.onDestroy();}
}
