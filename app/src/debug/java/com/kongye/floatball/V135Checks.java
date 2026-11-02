package com.kongye.floatball;
import android.content.*;import android.app.*;import android.graphics.*;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;import java.util.*;import java.util.concurrent.*;
/** Generated text only. Reports own client/model/window metadata; no device images. */
final class V135Checks {
 static TranslationPane pane;static RegionResult result;
 static void run(Context c,String command,Intent intent)throws Exception{
  if(command.equals("show")){
   if(result!=null)result.close();String text=intent.getStringExtra("text");if(!Arrays.asList("sad","Hello, world.","吴玫","こんにちは","안녕하세요").contains(text))throw new IllegalArgumentException("Generated fixture required");
   ExecutorService worker=Executors.newSingleThreadExecutor();Bitmap b=Bitmap.createBitmap(120,120,Bitmap.Config.ARGB_8888);b.eraseColor(Ui.PALE);result=new RegionResult(c,b,worker,()->worker.shutdownNow(),on->{},"Region",text);V126Checks.call(result,"translateText");CaptureWindow w=(CaptureWindow)V126Checks.get(result,"window");pane=(TranslationPane)V1251Checks.findType(w.root(),TranslationPane.class);return;
  }
  if(command.equals("retry")){V126Checks.call(pane,"run");return;}
  if(command.equals("swap")){V126Checks.call(pane,"swap");return;}
  if(command.equals("choose")){V126Checks.set(pane,"source",intent.getStringExtra("source"));V126Checks.set(pane,"target",intent.getStringExtra("target"));V126Checks.set(pane,"initialAuto",false);V126Checks.call(pane,"labels");V126Checks.call(pane,"run");return;}
  if(command.equals("close")){if(result!=null)result.close();result=null;pane=null;return;}
  if(command.equals("state")){JSONObject j=new JSONObject();for(String key:new String[]{"phase","source","target","detected","lastFailure"})j.put(key,V126Checks.get(pane,key));j.put("closed",V126Checks.get(pane,"closed")).put("attached",pane.isAttachedToWindow());for(String key:new String[]{"input","output","status"})j.put(key,((TextView)V126Checks.get(pane,key)).getText());Prefs.of(c).edit().putString("qa_v135_translation",j.toString()).commit();return;}
  if(command.equals("models")){
   com.google.mlkit.common.model.RemoteModelManager manager=com.google.mlkit.common.model.RemoteModelManager.getInstance();for(String code:new String[]{"zh","ja","ko"})manager.isModelDownloaded(new com.google.mlkit.nl.translate.TranslateRemoteModel.Builder(code).build()).addOnSuccessListener(on->Prefs.of(c).edit().putBoolean("qa_v135_model_"+code,on).apply());
   ArrayList<String> statuses=new ArrayList<>();android.content.SharedPreferences ml=c.getSharedPreferences("com.google.mlkit.internal",0);for(String key:ml.getAll().keySet())if(key.startsWith("downloading_model_id_")){long id=ml.getLong(key,-1);try(android.database.Cursor cursor=c.getSystemService(DownloadManager.class).query(new DownloadManager.Query().setFilterById(id))){if(cursor!=null&&cursor.moveToFirst())statuses.add(key.substring(key.lastIndexOf(':')+1)+":status="+cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))+",reason="+cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))+",bytes="+cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)));}}
   Prefs.of(c).edit().putString("qa_v135_downloads",statuses.toString()).commit();return;
  }
  if(command.equals("entry")){
   AppMotion.open(c);Handler h=new Handler(Looper.getMainLooper());JSONArray frames=new JSONArray();for(int delay:new int[]{30,100,200,350,650,850})h.postDelayed(()->{try{Activity a=V133Checks.top();if(a==null)return;View root=a.findViewById(android.R.id.content);JSONObject j=V133Checks.state(a);j.put("at",delay).put("x",root.getTranslationX()).put("sx",root.getScaleX()).put("sy",root.getScaleY()).put("no_animation",(a.getIntent().getFlags()&Intent.FLAG_ACTIVITY_NO_ANIMATION)!=0);frames.put(j);if(delay==850)Prefs.of(c).edit().putString("qa_v135_entry",frames.toString()).commit();}catch(Exception e){Prefs.of(c).edit().putString("qa_v135_error",e.toString()).commit();}},delay);return;
  }
 }
}
