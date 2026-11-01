package com.kongye.floatball;
import android.content.*;import android.graphics.*;import android.view.*;import android.webkit.WebView;import android.widget.*;
/** Generated fixtures and own-object identity only; never screen/media export. */
final class V127Checks {
 static Object get(Object o,String f)throws Exception{return V126Checks.get(o,f);}static void call(Object o,String name)throws Exception{V126Checks.call(o,name);}
 static void run(Context c,String cmd)throws Exception{
  if(cmd.equals("restore")){org.json.JSONObject j=new org.json.JSONObject(Prefs.of(c).getString("qa_v127_restore","{}"));if(j.has("ocr_keep_lines")){if(j.isNull("ocr_keep_lines"))Prefs.of(c).edit().remove("ocr_keep_lines").commit();else Prefs.of(c).edit().putBoolean("ocr_keep_lines",Boolean.parseBoolean(j.getString("ocr_keep_lines"))).commit();}return;}
  if(cmd.equals("lines")){V122Fixture.close();Bitmap b=Bitmap.createBitmap(300,240,Bitmap.Config.ARGB_8888);b.eraseColor(0xFFE3EEDC);V122Fixture.result=new RegionResult(c,b,V125Checks.worker==null?java.util.concurrent.Executors.newSingleThreadExecutor():V125Checks.worker,()->{V122Fixture.result=null;},on->{},"Region","Hello little leaf.\nWelcome home.");return;}
  android.content.SharedPreferences.Editor out=Prefs.of(c).edit();RegionResult result=V122Fixture.result;
  if(cmd.equals("snapshot")){if(result!=null){CaptureWindow window=(CaptureWindow)get(result,"window");out.putInt("qa_v127_root",System.identityHashCode(window.root())).putInt("qa_v127_mode",(int)get(result,"mode")).putString("qa_v127_text",(String)get(result,"value")).putString("qa_v127_status",(String)get(result,"statusText")).putBoolean("qa_v127_recognizing",(boolean)get(result,"recognizing"));View card=(View)get(result,"currentCard");out.putInt("qa_v127_card_height",card==null?0:card.getHeight());View text=(View)get(result,"editor");out.putInt("qa_v127_editor",System.identityHashCode(text));}if(FloatService.instance!=null)out.putInt("qa_v127_panel",System.identityHashCode(get(FloatService.instance,"panel")));}
  if(cmd.equals("recognize")){call(result,"recognizeAgain");}
  if(cmd.equals("about")){WebView web=(WebView)get(V1221Checks.about,"web");web.evaluateJavascript("JSON.stringify({count:getComputedStyle(document.getElementById('tap-lv')).opacity,active:document.getAnimations().filter(x=>x.effect&&x.effect.target.classList.contains('ff')).map(x=>({opacity:getComputedStyle(x.effect.target).opacity,duration:x.effect.getTiming().duration}))})",v->Prefs.of(c).edit().putString("qa_v127_about",v).apply());}
  out.commit();
 }
}
