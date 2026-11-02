package com.kongye.floatball;

import android.content.Context;
import java.io.File;
import org.json.*;

/** Owned update/cache fixtures only; no screen or media access. */
final class V139Checks {
    static void run(Context c,String command){try{
        JSONObject report=new JSONObject();
        if(command.equals("cache")){
            File folder=new File(c.getFilesDir(),"qa-v139-update-fixtures");
            report.put("removed",UpdateCacheCleanup.clean(c,folder));
            JSONArray remaining=new JSONArray();File[] files=folder.listFiles();if(files!=null)for(File f:files)remaining.put(f.getName());report.put("remaining",remaining);
        }else if(command.equals("assets")){
            JSONArray assets=new JSONArray();
            for(String name:new String[]{"KongyeFloat-1.30.9-x86.apk","KongyeFloat-1.30.9.apk","KongyeFloat-1.30.9-universal.apk","KongyeFloat-1.30.9-arm64-v8a.apk","KongyeFloat-1.30.9-armeabi-v7a.apk"})
                assets.put(new JSONObject().put("name",name).put("size",100).put("state","uploaded").put("browser_download_url","https://github.com/Mayleaves/KongyeFloat/releases/download/v1.30.9/"+name));
            JSONObject release=new JSONObject().put("tag_name","v1.30.9").put("assets",assets);
            report.put("arm64",GitHubUpdate.parse(release.toString(),"1.30.8",new String[]{"arm64-v8a","armeabi-v7a"}).name);
            report.put("arm32",GitHubUpdate.parse(release.toString(),"1.30.8",new String[]{"armeabi-v7a"}).name);
            report.put("x86",GitHubUpdate.parse(release.toString(),"1.30.8",new String[]{"x86"}).name);
            report.put("unknown",GitHubUpdate.parse(release.toString(),"1.30.8",new String[]{"other"}).name);
            report.put("newVersion",GitHubUpdate.parse(release.toString(),"1.30.8",new String[]{"arm64-v8a"}).comparison);
            assets.getJSONObject(3).put("browser_download_url","https://invalid.example/test.apk");
            report.put("invalidPreferred",GitHubUpdate.parse(release.toString(),"1.30.8",new String[]{"arm64-v8a"}).name);
        }
        Prefs.of(c).edit().putString("qa_v139_"+command,report.toString()).commit();
    }catch(Exception error){Prefs.of(c).edit().putString("qa_v139_error",error.toString()).commit();}}
}
