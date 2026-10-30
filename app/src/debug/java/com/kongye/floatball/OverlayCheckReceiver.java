package com.kongye.floatball;

import android.content.*;
import android.graphics.Rect;
import android.view.*;
import android.widget.TextView;
import java.util.*;
import org.json.*;

/** Own-view metadata for tests which must keep the real accessibility service connected. */
public class OverlayCheckReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){try{
        String command=intent.getStringExtra("command");
        if("qa_preferences".equals(command)){
            JSONObject values=new JSONObject(intent.getStringExtra("values"));android.content.SharedPreferences.Editor edit=Prefs.of(c).edit();
            for(String key:new String[]{"size","opacity","edge_gap","x","y"})if(values.has(key)){if(values.isNull(key))edit.remove(key);else edit.putInt(key,Integer.parseInt(values.getString(key)));}
            for(String key:new String[]{"snap","fade","haptic","enhanced_overlay","custom_art"})if(values.has(key)){if(values.isNull(key))edit.remove(key);else edit.putBoolean(key,Boolean.parseBoolean(values.getString(key)));}
            for(String key:new String[]{"theme","home_tools","gesture_up","gesture_down","gesture_tap","gesture_double","gesture_long"})if(values.has(key)){if(values.isNull(key))edit.remove(key);else edit.putString(key,values.getString(key));}
            edit.commit();if(FloatService.instance!=null)c.startService(new Intent(c,FloatService.class).setAction(FloatService.REFRESH));return;
        }
        if("main_snapshot".equals(command)){
            java.lang.reflect.Field visible=MainActivity.class.getDeclaredField("visible");visible.setAccessible(true);MainActivity page=(MainActivity)((java.lang.ref.WeakReference<?>)visible.get(null)).get();if(page==null)throw new IllegalStateException("Main page is not visible");
            JSONArray nodes=new JSONArray();append(page.getWindow().getDecorView(),"MainActivity",nodes);Prefs.of(c).edit().putString("qa_main_nodes",nodes.toString()).commit();
            java.lang.reflect.Field preview=MainActivity.class.getDeclaredField("preview");preview.setAccessible(true);View view=(View)preview.get(page);if(view!=null){java.lang.reflect.Field ball=view.getClass().getDeclaredField("ball");ball.setAccessible(true);View b=(View)ball.get(view);Prefs.of(c).edit().putInt("qa_preview_width",b.getWidth()).putFloat("qa_preview_alpha",b.getAlpha()).putFloat("qa_preview_shift",b.getTranslationX()).commit();}return;
        }
        if("home".equals(command)){HomeTools.save(c,Arrays.asList(intent.getStringExtra("tools").split(",")));return;}
        if("position".equals(command)){Prefs.of(c).edit().putInt("x",intent.getIntExtra("x",1118)).putInt("y",intent.getIntExtra("y",1129)).apply();c.startService(new Intent(c,FloatService.class).setAction(FloatService.REFRESH));return;}
        if("cross".equals(command)){java.lang.reflect.Field ball=FloatService.class.getDeclaredField("ball"),cross=FloatService.class.getDeclaredField("captureTarget");ball.setAccessible(true);cross.setAccessible(true);View b=(View)ball.get(FloatService.instance);GestureCaptureOverlay target=(GestureCaptureOverlay)cross.get(FloatService.instance);int[] at=new int[2];b.getLocationOnScreen(at);Prefs.of(c).edit().putInt("qa_cross_ball_x",at[0]).putInt("qa_cross_ball_y",at[1]).putFloat("qa_cross_x",target.x).putFloat("qa_cross_y",target.y).apply();return;}
        if("cleanup_saved_fixture".equals(command)){String uri=Prefs.of(c).getString("last_record_saved_uri","");String file=Prefs.of(c).getString("qa_v24_fixture","");if(!uri.isEmpty()&&!file.isEmpty()){c.getContentResolver().delete(android.net.Uri.parse(uri),null,null);Prefs.of(c).edit().remove("last_record_saved_uri").apply();}return;}
        if("enhanced".equals(command)||"ordinary".equals(command)){Prefs.of(c).edit().putBoolean("enhanced_overlay",command.equals("enhanced")).apply();OverlayWindows.refreshAll();return;}
        java.lang.reflect.Field field=OverlayWindows.class.getDeclaredField("hosts");field.setAccessible(true);JSONArray nodes=new JSONArray();
        for(Object host:new ArrayList<>((Set<?>)field.get(null))){java.lang.reflect.Field entries=OverlayWindows.class.getDeclaredField("entries");entries.setAccessible(true);for(Object root:((Map<?,?>)entries.get(host)).keySet())if(root instanceof View view&&view.isShown())append(view,((WindowManager.LayoutParams)view.getLayoutParams()).getTitle().toString(),nodes);}
        Prefs.of(c).edit().putString("qa_overlay_nodes",nodes.toString()).apply();
    }catch(Exception e){Prefs.of(c).edit().putString("qa_overlay_error",e.toString()).apply();}}
    private void append(View view,String window,JSONArray nodes)throws JSONException{if(!view.isShown())return;Rect box=new Rect();if(!view.getLocalVisibleRect(box)||box.isEmpty())return;int[] at=new int[2];view.getLocationOnScreen(at);box.offset(at[0],at[1]);CharSequence desc=view.getContentDescription();String label=desc==null?view instanceof TextView text&&!(text.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod)?text.getText().toString():"":desc.toString();if(!label.isEmpty()){
            JSONObject node=new JSONObject().put("label",label).put("window",window).put("x1",box.left).put("y1",box.top).put("x2",box.right).put("y2",box.bottom).put("clickable",view.isClickable()).put("selected",view.isSelected()).put("width",view.getWidth()).put("height",view.getHeight());
            if(view instanceof TextView text)node.put("text_sp",text.getTextSize()/text.getResources().getDisplayMetrics().scaledDensity).put("text_color",text.getCurrentTextColor());
            JSONArray icons=new JSONArray();icons(view,icons);
            // A setting title lives inside a text column; include its row's leading glyph.
            if(icons.length()==0&&view instanceof TextView){ViewParent parent=view.getParent();for(int level=0;level<2&&parent instanceof View;level++,parent=parent.getParent())if(parent instanceof android.widget.LinearLayout row&&row.getOrientation()==android.widget.LinearLayout.HORIZONTAL){icons(row,icons);break;}}
            node.put("icons",icons);nodes.put(node);
        }if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)append(group.getChildAt(i),window,nodes);}
    private void icons(View view,JSONArray output)throws JSONException{
        if(view instanceof Ui.Icon icon)output.put(new JSONObject().put("name",icon.name).put("color",icon.color));
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)icons(group.getChildAt(i),output);
    }

}
