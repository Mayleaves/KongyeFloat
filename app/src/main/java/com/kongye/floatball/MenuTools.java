package com.kongye.floatball;
import android.content.Context;import java.util.*;import org.json.*;
/** Menu selection/order is separate from home; both offer the complete same tool catalogue. */
final class MenuTools {
 static final String[] DEFAULT={"back","home","recents","notifications","region","longshot","screenshot","auto_longshot","record","gif_record","subtitle","recent_apps","favorite_apps","notes","browser","quick_settings","lock","apps","files","settings","password_fill"};
 static ArrayList<String> load(Context c){try{JSONArray a=new JSONArray(Prefs.of(c).getString("menu_tools",new JSONArray(Arrays.asList(DEFAULT)).toString()));ArrayList<String> ids=new ArrayList<>();for(int i=0;i<a.length();i++){String id=a.getString(i);if(Arrays.asList(HomeTools.AVAILABLE).contains(id)&&!ids.contains(id))ids.add(id);}if(ids.equals(Arrays.asList(HomeTools.AVAILABLE)))return new ArrayList<>(Arrays.asList(DEFAULT));return addPassword(c,Prefs.addSubtitleTool(c,"menu_tools",ids));}catch(Exception e){return new ArrayList<>(Arrays.asList(DEFAULT));}}
 static ArrayList<String> addPassword(Context c,ArrayList<String> ids){if(!Prefs.of(c).getBoolean("menu_password_added_v3",false)){if(!ids.contains("password_fill"))ids.add("password_fill");Prefs.of(c).edit().putString("menu_tools",new JSONArray(ids).toString()).putBoolean("menu_password_added_v3",true).apply();}return ids;}
 static ArrayList<String> order(Context c){ArrayList<String> ids=new ArrayList<>();try{JSONArray a=new JSONArray(Prefs.of(c).getString("menu_order","[]"));for(int i=0;i<a.length();i++){String id=a.getString(i);if(Arrays.asList(DEFAULT).contains(id)&&!ids.contains(id))ids.add(id);}}catch(Exception ignored){}if(ids.isEmpty())ids.addAll(load(c));for(String id:DEFAULT)if(!ids.contains(id))ids.add(id);return ids;}
 static void saveOrder(Context c,List<String> order){Prefs.of(c).edit().putString("menu_order",new JSONArray(order).toString()).apply();}
 static void save(Context c,List<String> ids){Prefs.of(c).edit().putString("menu_tools",new JSONArray(ids).toString()).putBoolean("menu_tools_subtitle_added",true).putBoolean("menu_password_added_v3",true).apply();}
}
