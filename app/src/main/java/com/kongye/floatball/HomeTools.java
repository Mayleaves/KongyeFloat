package com.kongye.floatball;

import android.content.Context;
import java.util.*;
import org.json.JSONArray;

final class HomeTools {
    static final String[] AVAILABLE={"region","longshot","auto_longshot","screenshot","record","gif_record","recent_apps","favorite_apps","apps","notes","browser","files","back","home","recents","notifications","quick_settings","lock"};
    static final String[] DEFAULT={"region","longshot","record","gif_record","recent_apps","favorite_apps","notes","browser"};
    static ArrayList<String> load(Context c){String value=Prefs.of(c).getString("home_tools",null);if(value==null)return new ArrayList<>(Arrays.asList(DEFAULT));try{JSONArray array=new JSONArray(value);ArrayList<String> values=new ArrayList<>();for(int i=0;i<array.length();i++)values.add(array.getString(i));return clean(values);}catch(Exception ignored){return new ArrayList<>(Arrays.asList(DEFAULT));}}
    private static ArrayList<String> clean(List<String> source){ArrayList<String> result=new ArrayList<>();List<String> allowed=Arrays.asList(AVAILABLE);for(String id:source)if(allowed.contains(id)&&!result.contains(id))result.add(id);return result;}
    static void save(Context c,List<String> source){Prefs.of(c).edit().putString("home_tools",new JSONArray(clean(source)).toString()).apply();}
    static String label(String id){return switch(id){case "browser"->"浏览器";case "recent_apps"->"最近软件";case "favorite_apps"->"常用软件";default->Prefs.label(id);};}
}
