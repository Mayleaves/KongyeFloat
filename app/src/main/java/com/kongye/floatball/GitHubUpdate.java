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
    static final class Result {
        final String latest,name,url,digest;final long size;final int comparison;
        Result(String latest,int comparison,String name,String url,long size,String digest){this.latest=latest;this.comparison=comparison;this.name=name;this.url=url;this.size=size;this.digest=digest;}
        boolean downloadable(){return !url.isEmpty();}
    }
    static Result parse(String json,String current)throws IOException{try{
        JSONObject release=new JSONObject(json);if(release.optBoolean("draft",false)||release.optBoolean("prerelease",false))throw new IOException("没有可用的正式版本");
        String tag=release.getString("tag_name");int comparison=ReleaseVersion.compare(tag,current);String name="",url="",digest="";long size=0;JSONArray assets=release.optJSONArray("assets");
        if(assets!=null)for(int i=0;i<assets.length();i++){JSONObject asset=assets.optJSONObject(i);if(asset==null)continue;String candidate=asset.optString("name","");String lower=candidate.toLowerCase(java.util.Locale.ROOT);if(!lower.endsWith(".apk")||lower.contains("debug")||!"uploaded".equals(asset.optString("state","uploaded")))continue;String address=asset.optString("browser_download_url","");try{UpdateTransfer.assetUrl(address);}catch(IOException invalid){continue;}long bytes=asset.optLong("size",0);if(bytes<=0||bytes>UpdateTransfer.LIMIT)continue;name=candidate;url=address;size=bytes;digest=asset.optString("digest","");break;}
        return new Result(tag.replaceFirst("^[vV]",""),comparison,name,url,size,digest);
    }catch(JSONException|IllegalArgumentException e){throw new IOException("发布信息格式无法识别",e);}}
    static Result check(String current)throws IOException{HttpURLConnection conn=(HttpURLConnection)new URL(API).openConnection();conn.setConnectTimeout(8000);conn.setReadTimeout(8000);conn.setInstanceFollowRedirects(false);conn.setRequestProperty("Accept","application/vnd.github+json");conn.setRequestProperty("User-Agent","KongyeFloat-Android");try{int status=conn.getResponseCode();if(status!=200)throw new IOException(status==403||status==429?"GitHub 请求次数受限，请稍后重试":status==404?"暂未找到正式发布版本":"GitHub 暂时无法访问（"+status+"）");try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>256*1024)throw new IOException("发布信息过大");out.write(buffer,0,n);}return parse(new String(out.toByteArray(),StandardCharsets.UTF_8),current);}}catch(SocketTimeoutException|UnknownHostException|ConnectException|javax.net.ssl.SSLException e){throw new IOException("无法连接 GitHub，请检查网络后重试",e);}finally{conn.disconnect();}}
}
