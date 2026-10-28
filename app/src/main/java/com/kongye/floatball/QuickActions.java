package com.kongye.floatball;

import android.content.Context;
import org.json.JSONArray;
import java.util.*;

final class QuickActions {
    static final int LIMIT=12;
    static ArrayList<String> load(Context c){
        String stored=Prefs.of(c).getString("quick_actions",null);ArrayList<String> actions=new ArrayList<>();
        if(stored==null)actions.addAll(Arrays.asList("region","longshot","record","notes","browser","favorite_apps","recent_apps","screenshot"));
        else try{JSONArray values=new JSONArray(stored);for(int i=0;i<values.length()&&actions.size()<LIMIT;i++){String id=Prefs.canonicalAction(values.getString(i));if("record".equals(id)&&actions.contains(id))id="";actions.add(id.equals("quick")||id.equals("none")?"":id);}}catch(Exception ignored){}
        while(actions.size()<LIMIT)actions.add("");
        return actions;
    }
    static void save(Context c,List<String> actions){JSONArray values=new JSONArray();for(String action:actions)values.put(action);Prefs.of(c).edit().putString("quick_actions",values.toString()).apply();}
    static ArrayList<String> recent(Context c){
        ArrayList<String> result=new ArrayList<>();Map<String,Long> times=AppCatalog.recent(c);ArrayList<String> packages=new ArrayList<>(times.keySet());packages.sort((a,b)->Long.compare(times.get(b),times.get(a)));
        for(String pkg:packages)if(c.getPackageManager().getLaunchIntentForPackage(pkg)!=null){result.add("app:"+pkg);if(result.size()==1)break;}
        while(result.size()<1)result.add("");return result;
    }
    static void migrate(Context c){if(!Prefs.of(c).getBoolean("edge_interaction_v2",false))Prefs.of(c).edit().putString("gesture_long","quick").putBoolean("drag_center_region",true).putBoolean("edge_interaction_v2",true).apply();}
}
