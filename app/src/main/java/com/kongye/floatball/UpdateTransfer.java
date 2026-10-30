package com.kongye.floatball;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.function.*;

/** Download policy and streaming integrity checks, independent of Android UI. */
final class UpdateTransfer {
    static final long LIMIT=512L*1024*1024;
    static URL assetUrl(String value)throws IOException{
        URL u=new URL(value);if(!"https".equalsIgnoreCase(u.getProtocol())||!"github.com".equalsIgnoreCase(u.getHost())||!u.getPath().startsWith("/Mayleaves/KongyeFloat/releases/download/")||u.getUserInfo()!=null||u.getPort()!=-1)throw new IOException("安装包下载地址无效");try{for(String segment:u.toURI().getPath().split("/"))if(segment.equals(".")||segment.equals(".."))throw new IOException("安装包下载地址无效");}catch(URISyntaxException e){throw new IOException("安装包下载地址无效",e);}return u;
    }
    static URL redirect(URL base,String location)throws IOException{
        if(location==null)throw new IOException("下载跳转缺少地址");URL u=new URL(base,location);String host=u.getHost().toLowerCase(java.util.Locale.ROOT);
        if(!"https".equalsIgnoreCase(u.getProtocol())||u.getUserInfo()!=null||u.getPort()!=-1||!(host.equals("github.com")||host.equals("githubusercontent.com")||host.endsWith(".githubusercontent.com")))throw new IOException("安装包下载跳转无效");return u;
    }
    static long copy(InputStream in,OutputStream out,long expected,String digest,BooleanSupplier cancelled,LongConsumer progress)throws IOException{
        if(expected<0||expected>LIMIT)throw new IOException("安装包大小无效");MessageDigest hash;try{hash=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IOException(e);}
        byte[] block=new byte[32768];long count=0,next=0;
        while(true){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("已取消下载");int n=in.read(block);if(n<0)break;if(n==0)continue;count+=n;if(count>LIMIT||expected>0&&count>expected)throw new IOException("安装包大小不符");out.write(block,0,n);hash.update(block,0,n);if(count>=next){progress.accept(count);next=count+128*1024;}}
        if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("已取消下载");if(count==0||expected>0&&count!=expected)throw new IOException("下载未完成，请重试");
        if(digest!=null&&!digest.isBlank()){if(!digest.matches("(?i)sha256:[0-9a-f]{64}"))throw new IOException("安装包校验信息无效");StringBuilder actual=new StringBuilder();for(byte b:hash.digest())actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(!actual.toString().equalsIgnoreCase(digest.substring(7)))throw new IOException("安装包校验失败，请重新下载");}
        progress.accept(count);return count;
    }
}
