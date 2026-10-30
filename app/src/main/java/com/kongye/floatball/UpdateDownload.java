package com.kongye.floatball;

import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.LongConsumer;

/** Private-cache download, checksum, package and signing certificate verification. */
final class UpdateDownload {
    private volatile boolean cancelled;private volatile HttpURLConnection connection;
    void cancel(){cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    File download(Context context,GitHubUpdate.Result release,LongConsumer progress)throws IOException{
        File folder=new File(context.getCacheDir(),"updates");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("无法创建更新缓存");
        for(File old:Objects.requireNonNullElse(folder.listFiles(),new File[0]))if(old.lastModified()<System.currentTimeMillis()-3L*24*3600000)old.delete();
        String id=UUID.randomUUID().toString();File partial=new File(folder,id+".part.apk"),target=new File(folder,id+".apk");boolean complete=false;
        try{URL address=UpdateTransfer.assetUrl(release.url);HttpURLConnection c=null;
            for(int hops=0;hops<8;hops++){if(cancelled)throw new InterruptedIOException("已取消下载");c=(HttpURLConnection)address.openConnection();connection=c;c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","KongyeFloat-Android");c.setRequestProperty("Accept","application/octet-stream");int status=c.getResponseCode();if(status==200)break;if(status==301||status==302||status==303||status==307||status==308){URL next=UpdateTransfer.redirect(address,c.getHeaderField("Location"));c.disconnect();c=null;address=next;continue;}throw new IOException("下载失败（"+status+"），请稍后重试");}
            if(c==null||c.getResponseCode()!=200)throw new IOException("下载跳转过多");
            try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(partial)){UpdateTransfer.copy(in,out,release.size,release.digest,()->cancelled,progress);}
            verify(context,partial);if(cancelled)throw new InterruptedIOException("已取消下载");if(!partial.renameTo(target))throw new IOException("无法保存更新安装包");complete=true;return target;
        }finally{HttpURLConnection c=connection;if(c!=null)c.disconnect();connection=null;partial.delete();if(!complete)target.delete();}
    }
    @SuppressWarnings("deprecation") static void verify(Context c,File file)throws IOException{
        PackageManager pm=c.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo incoming=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags);if(incoming==null||!c.getPackageName().equals(incoming.packageName))throw new IOException("不是空叶悬浮球安装包");
        try{PackageInfo installed=pm.getPackageInfo(c.getPackageName(),flags);long next=Build.VERSION.SDK_INT>=28?incoming.getLongVersionCode():incoming.versionCode,current=Build.VERSION.SDK_INT>=28?installed.getLongVersionCode():installed.versionCode;
            Signature[] a=Build.VERSION.SDK_INT>=28&&incoming.signingInfo!=null?incoming.signingInfo.getApkContentsSigners():incoming.signatures,b=Build.VERSION.SDK_INT>=28&&installed.signingInfo!=null?installed.signingInfo.getApkContentsSigners():installed.signatures;
            if(a==null||b==null||a.length==0||a.length!=b.length||!new HashSet<>(Arrays.asList(a)).equals(new HashSet<>(Arrays.asList(b))))throw new IOException("安装包签名与当前版本不一致");
            if(next<=current)throw new IOException("安装包版本不高于当前版本");
        }catch(PackageManager.NameNotFoundException e){throw new IOException("无法校验当前版本",e);}
    }
}
