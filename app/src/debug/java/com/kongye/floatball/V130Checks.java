package com.kongye.floatball;
import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;
/** Own-control metadata, generated release fixtures and read-only public version checks. */
final class V130Checks {
 static Object get(Object o,String name)throws Exception{return V126Checks.get(o,name);}
 static void require(boolean ok,String name){if(!ok)throw new AssertionError(name);}
 static void run(Context c,String cmd)throws Exception{
  MainActivity activity=V1231Checks.main();android.content.SharedPreferences.Editor out=Prefs.of(c).edit();
  if(cmd.equals("native")){require(Math.abs(BallScale.dp(30)-10.8f)<.001,"30 percent size");require(BallScale.dp(500)==180,"maximum size");require(BallScale.dp(0)==BallScale.dp(30),"lower size bound");require(BallScale.dp(900)==180,"upper size bound");require(BallScale.inset(150,100)==50,"150 percent inset");require(BallScale.inset(100,100)==0,"existing 100 percent edge unchanged");require(BallScale.inset(30,100)==-70,"existing 30 percent edge unchanged");require(BallScale.edgePercent(50,100)==150,"legacy inset migration");
   String release="{\"tag_name\":\"v1.29.2\"}";require(GitHubUpdate.parse(release,"1.25.1").comparison>0,"older installed release offers update");require(GitHubUpdate.parse(release,"1.29.2").comparison==0,"same installed release is current");require(GitHubUpdate.parse(release,"1.30.0").comparison<0,"test version ahead of public release");out.putInt("qa_v130_native",11);
  }
  if(cmd.equals("network_old")){Prefs.of(c).edit().remove("qa_v130_network").commit();V126Checks.worker.submit(()->{try{GitHubUpdate.Result r=GitHubUpdate.check("1.25.1");Prefs.of(c).edit().putString("qa_v130_network",new JSONObject().put("latest",r.latest).put("comparison",r.comparison).put("downloadable",r.downloadable()).toString()).commit();}catch(Exception e){Prefs.of(c).edit().putString("qa_v130_network","error: "+e.getMessage()).commit();}});return;}
  if(cmd.startsWith("update_")){V126Checks.watch(activity);String mode=cmd.substring(7),installed=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName;String latest=mode.equals("available")?"9.9.9":mode.equals("current")?installed:"1.25.1";GitHubUpdate.Result r=new GitHubUpdate.Result(latest,0,"fixture.apk","https://github.com/Mayleaves/KongyeFloat/releases/download/v9.9.9/fixture.apk",4000000,"","Generated release fixture; no download.");activity.startActivity(UpdateActivity.checkedIntent(activity,r,null));return;}
  if(cmd.equals("ranges")){JSONObject j=new JSONObject();j.put("size",BallSettings.percent(c)).put("edge",BallSettings.edge(c));Object preview=get(activity,"preview");View b=(View)get(preview,"ball");j.put("preview_width",b.getWidth()).put("preview_x",b.getTranslationX());if(FloatService.instance!=null){WindowManager.LayoutParams p=(WindowManager.LayoutParams)get(FloatService.instance,"ballParams");j.put("ball_width",p.width).put("ball_x",p.x);}out.putString("qa_v130_ranges",j.toString());}
  if(cmd.equals("home_motion")){
   View nav=V124Checks.find(activity.getWindow().getDecorView(),"底部悬浮导航");V124Checks.find(nav,"首页").performClick();View page=(View)get(activity,"content"),plant=(View)get(activity,"perchedPlant"),group=(View)get(activity,"plantEntrance");JSONArray frames=new JSONArray();Handler handler=new Handler(Looper.getMainLooper());
   for(int delay:new int[]{0,80,180,320,650})handler.postDelayed(()->{try{JSONObject f=new JSONObject();f.put("at",delay).put("plant_alpha",group.getAlpha()).put("page_alpha",page.getAlpha()).put("plant_x",group.getTranslationX()).put("page_x",page.getTranslationX()).put("plant_y",group.getTranslationY()).put("page_y",page.getTranslationY()).put("plant_scale",group.getScaleX()).put("page_scale",page.getScaleX()).put("plant_attached",plant.isAttachedToWindow()).put("anchor",group.getBottom()+plant.getTranslationY()).put("header_height",((View)group.getParent()).getHeight());frames.put(f);if(delay==650)Prefs.of(c).edit().putString("qa_v130_motion",frames.toString()).commit();}catch(Exception e){throw new RuntimeException(e);}},delay);return;
  }
  out.commit();
 }
}
