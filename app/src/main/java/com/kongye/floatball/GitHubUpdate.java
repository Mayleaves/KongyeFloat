package com.kongye.floatball;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** On-demand check of this project's published, non-prerelease GitHub release. */
final class GitHubUpdate {
    static final String PROJECT="https://github.com/Mayleaves/KongyeFloat";
    static final String RELEASES=PROJECT+"/releases/latest";
    private static final String API="https://api.github.com/repos/Mayleaves/KongyeFloat/releases/latest";
    static final class Result {final String latest;final int comparison;Result(String value,int comparison){latest=value;this.comparison=comparison;}}
    static Result parse(String json,String current)throws IOException{try{JSONObject release=new JSONObject(json);if(release.optBoolean("draft",false)||release.optBoolean("prerelease",false))throw new IOException("没有可用的正式版本");String tag=release.getString("tag_name");int comparison=ReleaseVersion.compare(tag,current);return new Result(tag.replaceFirst("^[vV]",""),comparison);}catch(JSONException|IllegalArgumentException e){throw new IOException("发布信息格式无法识别",e);}}
    static Result check(String current)throws IOException{HttpURLConnection conn=(HttpURLConnection)new URL(API).openConnection();conn.setConnectTimeout(8000);conn.setReadTimeout(8000);conn.setInstanceFollowRedirects(false);conn.setRequestProperty("Accept","application/vnd.github+json");conn.setRequestProperty("User-Agent","KongyeFloat-Android");try{int status=conn.getResponseCode();if(status!=200)throw new IOException(status==403||status==429?"GitHub 请求次数受限，请稍后重试":status==404?"暂未找到正式发布版本":"GitHub 暂时无法访问（"+status+"）");try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>256*1024)throw new IOException("发布信息过大");out.write(buffer,0,n);}return parse(new String(out.toByteArray(),StandardCharsets.UTF_8),current);}}catch(SocketTimeoutException|UnknownHostException|ConnectException|javax.net.ssl.SSLException e){throw new IOException("无法连接 GitHub，请检查网络后重试",e);}finally{conn.disconnect();}}
}
