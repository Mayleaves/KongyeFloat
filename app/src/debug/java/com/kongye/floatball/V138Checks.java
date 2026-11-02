package com.kongye.floatball;
import android.content.*;import android.graphics.*;import android.os.*;import java.util.*;import java.util.concurrent.*;import org.json.*;
import com.kongye.paddle.PaddleBridge;

/** Synthetic font benchmark; no user image, no phone screen export. */
final class V138Checks {
 static ExecutorService worker=Executors.newSingleThreadExecutor();
 static RegionResult result;
 static final String[] SAMPLES={"吴玫正在阅读，今天心情很好。","霞鹜文楷：你好，世界！这是测试。","中文标点：逗号，句号。问号？感叹号！","他说：“春风又绿江南岸。”","价格 3.14 元，时间 12:30，编号 A-102。","English: Hello, world! URL example.com"};
 static void run(Context c,String command){
  if(command.equals("result")){
   if(result!=null)result.close();Bitmap image=Bitmap.createBitmap(1200,640,Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);Paint p=new Paint(3);p.setColor(Color.BLACK);p.setTypeface(Typeface.createFromFile(new java.io.File(c.getFilesDir(),"v126-wenkai.ttf")));p.setTextSize(40);Canvas canvas=new Canvas(image);canvas.drawText("原文,不要改!",30,60,p);for(int i=0;i<SAMPLES.length;i++)canvas.drawText(SAMPLES[i],30,145+i*80,p);
   result=new RegionResult(c,image,worker,()->{},on->{},"Region",List.of(new TextRegion("原文,不要改!",28,15,350,68)));return;
  }
  if(command.equals("close")){if(result!=null)result.close();result=null;return;}
  if(command.equals("state")){try{if(result==null){Prefs.of(c).edit().putString("qa_v138_state","{}").apply();return;}JSONObject state=new JSONObject().put("recognizing",V126Checks.get(result,"recognizing")).put("raw",V126Checks.get(result,"rawValue")).put("value",V126Checks.get(result,"value")).put("status",V126Checks.get(result,"statusText"));CaptureWindow window=(CaptureWindow)V126Checks.get(result,"window");state.put("window",System.identityHashCode(window.root()));Prefs.of(c).edit().putString("qa_v138_state",state.toString()).apply();}catch(Exception e){throw new IllegalStateException(e);}return;}
  if(command.equals("punctuation_fixture")){try{V126Checks.set(result,"lastOcr",List.of(new TextRegion("中文标点:逗号,问号?金额 3.14 元",0,100,1000,140)));V126Checks.set(result,"recognizing",false);V126Checks.set(result,"rawValue","中文标点:逗号,问号?金额 3.14 元");V126Checks.call(result,"show");}catch(Exception e){throw new IllegalStateException(e);}return;}
  if(command.equals("toggle_punctuation")){try{CaptureWindow window=(CaptureWindow)V126Checks.get(result,"window");android.view.View field=find(window.root());((android.widget.CheckBox)field).setChecked(!((android.widget.CheckBox)field).isChecked());}catch(Exception e){throw new IllegalStateException(e);}return;}
  if(!command.equals("benchmark"))return;Prefs.of(c).edit().putString("qa_v138_benchmark","running").remove("qa_v138_error").apply();worker.execute(()->{
   JSONArray results=new JSONArray();long started=SystemClock.elapsedRealtime();
   try(PaddleBridge paddle=new PaddleBridge(c)){
    String[] fonts={"sans","wenkai","kaiti"};for(String font:fonts){
     Typeface type=font.equals("sans")?Typeface.create("sans-serif",Typeface.NORMAL):Typeface.createFromFile(new java.io.File(c.getFilesDir(),font.equals("wenkai")?"v126-wenkai.ttf":"v138-kaiti.ttf"));
     for(int size:new int[]{28,40}){Bitmap b=Bitmap.createBitmap(1200,560,Bitmap.Config.ARGB_8888);try{b.eraseColor(Color.WHITE);Canvas canvas=new Canvas(b);Paint paint=new Paint(3);paint.setColor(Color.BLACK);paint.setTypeface(type);paint.setTextSize(size);for(int i=0;i<SAMPLES.length;i++)canvas.drawText(SAMPLES[i],30,65+i*80,paint);
      long at=SystemClock.elapsedRealtime();List<TextRegion> mlRegions=OcrPipeline.recognizeRegions(b,()->false);String ml=RegionTextFusion.text(mlRegions);long mlMs=SystemClock.elapsedRealtime()-at;at=SystemClock.elapsedRealtime();List<PaddleBridge.Line> lines=paddle.recognize(b,()->false);ArrayList<TextRegion> ppRegions=new ArrayList<>();StringBuilder pp=new StringBuilder();JSONArray boxes=new JSONArray();for(PaddleBridge.Line line:lines){if(pp.length()>0)pp.append('\n');pp.append(line.getText());ppRegions.add(new TextRegion(line.getText(),line.getLeft(),line.getTop(),line.getRight(),line.getBottom(),Collections.emptyList(),false,line.getConfidence()));boxes.put(new JSONObject().put("text",line.getText()).put("score",line.getConfidence()).put("l",line.getLeft()).put("t",line.getTop()).put("r",line.getRight()).put("b",line.getBottom()));}ArrayList<TextRegion> combined=new ArrayList<>();for(TextRegion r:OcrEngineFusion.merge(ppRegions,mlRegions))combined.add(OcrPunctuation.normalize(r));results.put(new JSONObject().put("combined",RegionTextFusion.text(combined)).put("font",font).put("size",size).put("expected",String.join("\n",SAMPLES)).put("ml",ml).put("ml_ms",mlMs).put("paddle",pp.toString()).put("paddle_ms",SystemClock.elapsedRealtime()-at).put("lines",boxes));Prefs.of(c).edit().putString("qa_v138_partial",results.toString()).apply();
     }finally{b.recycle();}}
    }
    Prefs.of(c).edit().putString("qa_v138_benchmark",results.toString()).putLong("qa_v138_ms",SystemClock.elapsedRealtime()-started).apply();
   }catch(Exception|LinkageError|OutOfMemoryError e){Prefs.of(c).edit().putString("qa_v138_error",e.toString()).putString("qa_v138_benchmark","failed").apply();}
  });
 }
 static android.view.View find(android.view.View v){if("OCR 中文标点优化".contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++){android.view.View child=find(group.getChildAt(i));if(child!=null)return child;}return null;}
}
