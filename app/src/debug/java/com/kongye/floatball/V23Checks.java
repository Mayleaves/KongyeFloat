package com.kongye.floatball;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.*;
import java.util.*;

final class V23Checks {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    static void run(Activity c){SharedPreferences prefs=Prefs.of(c);String home=prefs.getString("home_tools",null),up=prefs.getString("gesture_up",null),tap=prefs.getString("gesture_tap",null);count=0;String error="";try{
        prefs.edit().remove("home_tools").remove("gesture_up").apply();check(HomeTools.load(c).equals(Arrays.asList(HomeTools.DEFAULT)),"default home tools stay compatible");check(Prefs.action(c,"up").equals("settings"),"fresh and reset up gesture opens app");
        prefs.edit().putString("gesture_up","notifications").apply();check(Prefs.action(c,"up").equals("notifications"),"existing custom gesture is preserved");
        HomeTools.save(c,Arrays.asList(HomeTools.AVAILABLE));check(HomeTools.load(c).size()==18,"all tools can be selected beyond eight");HomeTools.save(c,Arrays.asList("browser","browser","unknown","region"));check(HomeTools.load(c).equals(Arrays.asList("browser","region")),"invalid and duplicate tools removed in stable order");HomeTools.save(c,List.of());check(HomeTools.load(c).isEmpty(),"empty tool layout stays empty");prefs.edit().putString("home_tools","invalid json").apply();check(HomeTools.load(c).equals(Arrays.asList(HomeTools.DEFAULT)),"malformed storage restores defaults");
        List<String> gestures=Arrays.asList(Prefs.gestureActions());check(!gestures.contains("manage_quick")&&!gestures.contains("record_stop"),"removed gesture choices absent");for(String id:Prefs.gestureActions())check(!Prefs.label(id).isBlank(),"filtered action keeps matching label");prefs.edit().putString("gesture_tap","record_stop").apply();check(Prefs.action(c,"tap").equals("none"),"legacy stop binding becomes no action");prefs.edit().putString("gesture_tap","manage_quick").apply();check(Prefs.action(c,"tap").equals("none"),"legacy management binding becomes no action");
        java.lang.reflect.Field icons=MainActivity.class.getDeclaredField("TAB_ICONS");icons.setAccessible(true);check(((String[])icons.get(null))[0].equals("leaf"),"home tab uses leaf icon");Bitmap a=icon(c,"auto_longshot"),b=icon(c,"reorder");check(!a.sameAs(b),"automatic scroll has distinct icon");a.recycle();b.recycle();
    }catch(Throwable t){error=t.toString();}finally{SharedPreferences.Editor edit=prefs.edit();if(home==null)edit.remove("home_tools");else edit.putString("home_tools",home);if(up==null)edit.remove("gesture_up");else edit.putString("gesture_up",up);if(tap==null)edit.remove("gesture_tap");else edit.putString("gesture_tap",tap);edit.putInt("qa_v23_checks",count).putBoolean("qa_v23_pass",error.isEmpty()).putString("qa_v23_error",error).apply();}}
    private static Bitmap icon(Activity c,String id){Bitmap result=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);Ui.Icon icon=new Ui.Icon(c,id,Color.WHITE);icon.layout(0,0,96,96);icon.draw(new Canvas(result));return result;}
}
