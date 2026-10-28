package com.kongye.floatball;

import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

final class MediaFiles {
    static String name(String prefix,String extension){return "Kongye_"+prefix+"_"+new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date())+extension;}
    static Uri saveBitmap(Context c,Bitmap bitmap,String prefix)throws IOException{
        if(Build.VERSION.SDK_INT<29)throw new IOException("此截图功能需要 Android 10 或以上");
        ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,name(prefix,".png"));values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Kongye");values.put(MediaStore.Images.Media.IS_PENDING,1);
        Uri uri=c.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("无法创建相册文件");
        try{
            try(OutputStream out=c.getContentResolver().openOutputStream(uri)){if(out==null||!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("无法写入截图");}
            values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);c.getContentResolver().update(uri,values,null,null);
            Prefs.of(c).edit().putString("last_capture_uri",uri.toString()).putString("last_capture_kind",prefix).putInt("last_capture_width",bitmap.getWidth()).putInt("last_capture_height",bitmap.getHeight()).apply();return uri;
        }catch(IOException|RuntimeException e){c.getContentResolver().delete(uri,null,null);throw e;}
    }
    static final class VideoTarget implements AutoCloseable {
        final Context context;final Uri uri;final File file;final ParcelFileDescriptor descriptor;boolean published;
        VideoTarget(Context c)throws IOException{
            context=c;
            if(Build.VERSION.SDK_INT>=29){ContentValues values=new ContentValues();values.put(MediaStore.Video.Media.DISPLAY_NAME,name("Record",".mp4"));values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");values.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/Kongye");values.put(MediaStore.Video.Media.IS_PENDING,1);uri=c.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);file=null;if(uri==null)throw new IOException("无法创建录屏文件");ParcelFileDescriptor opened=null;try{opened=c.getContentResolver().openFileDescriptor(uri,"rw");if(opened==null)throw new IOException("无法写入录屏文件");}catch(IOException|RuntimeException e){c.getContentResolver().delete(uri,null,null);throw e;}descriptor=opened;}
            else{uri=null;File folder=c.getExternalFilesDir(Environment.DIRECTORY_MOVIES);if(folder==null)throw new IOException("存储不可用");file=new File(folder,name("Record",".mp4"));descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_READ_WRITE|ParcelFileDescriptor.MODE_TRUNCATE);}
        }
        void publish(){if(uri!=null&&Build.VERSION.SDK_INT>=29){ContentValues values=new ContentValues();values.put(MediaStore.Video.Media.IS_PENDING,0);context.getContentResolver().update(uri,values,null,null);}published=true;Prefs.of(context).edit().putString("last_record_uri",uri==null?file.getAbsolutePath():uri.toString()).apply();}
        @Override public void close(){try{descriptor.close();}catch(IOException ignored){}if(!published){if(uri!=null)context.getContentResolver().delete(uri,null,null);else file.delete();}}
    }
}
