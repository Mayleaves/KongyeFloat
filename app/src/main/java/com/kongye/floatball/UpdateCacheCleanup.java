package com.kongye.floatball;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/** Remove only completed private update packages that are already installed/obsolete. */
final class UpdateCacheCleanup {
    private static final AtomicBoolean started=new AtomicBoolean();
    private UpdateCacheCleanup(){}
    static void schedule(Context context){
        if(!started.compareAndSet(false,true))return;
        Context app=context.getApplicationContext();
        new Thread(()->clean(app),"Kongye-UpdateCache").start();
    }
    static int clean(Context context){return clean(context,new File(context.getCacheDir(),"updates"));}
    static int clean(Context context,File folder){
        int removed=0;
        try{
            PackageInfo installed=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
            long current=version(installed);
            File[] files=folder.listFiles();if(files==null)return 0;
            String parent=folder.getCanonicalPath();
            for(File file:files){
                if(!file.getName().matches("[a-f0-9-]{36}\\.apk")||!file.isFile()||!parent.equals(file.getCanonicalFile().getParent()))continue;
                PackageInfo archive=context.getPackageManager().getPackageArchiveInfo(file.getPath(),0);
                if(archive!=null&&context.getPackageName().equals(archive.packageName)&&version(archive)<=current&&file.delete())removed++;
            }
        }catch(Exception ignored){/* Cleanup cannot block startup or touch media/settings/models. */}
        return removed;
    }
    private static long version(PackageInfo info){return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
}
