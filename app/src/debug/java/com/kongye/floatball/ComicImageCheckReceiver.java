package com.kongye.floatball;

import android.content.*;
import android.graphics.*;
import android.view.View;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.json.*;

/** Headless diagnostics on explicitly supplied local samples. Never opens another application. */
public class ComicImageCheckReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        String control=intent.getStringExtra("control");if(control!=null){control(c,intent,control);return;}
        String name=intent.getStringExtra("name"),language=intent.getStringExtra("language");
        if(name==null||!name.matches("[a-z0-9-]+"))return;
        File folder=c.getExternalFilesDir("comic-check");Bitmap image=BitmapFactory.decodeFile(new File(folder,name+".jpg").getPath());if(image==null)return;
        String lang="ja".equals(language)?"ja":"en";
        TextRecognizer reader=lang.equals("ja")?TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build()):TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        PendingResult pending=goAsync();reader.process(InputImage.fromBitmap(image,0)).addOnCompleteListener(task->{
            JSONObject report=new JSONObject();try{
                if(!task.isSuccessful())throw new IOException("OCR failed");ComicFrame frame=ComicFrame.read(task.getResult(),image,lang);
                JSONArray blocks=new JSONArray();ArrayList<String> layoutText=new ArrayList<>();
                for(var block:frame.blocks){blocks.put(new JSONObject().put("text",block.text()).put("vertical",block.vertical()).put("bounds",block.bounds().toShortString()));layoutText.add("这是排版测试译文，用于检查标点、换行及边界。");}
                report.put("phase","layout_complete").put("ocr",task.getResult().getText()).put("blocks",blocks).put("count",frame.blocks.size());
                ComicOverlayView view=new ComicOverlayView(c);view.measure(View.MeasureSpec.makeMeasureSpec(image.getWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(image.getHeight(),View.MeasureSpec.EXACTLY));view.layout(0,0,image.getWidth(),image.getHeight());view.show(frame,layoutText,new Rect(0,0,image.getWidth(),image.getHeight()));
                Bitmap rendered=image.copy(Bitmap.Config.ARGB_8888,true);view.draw(new Canvas(rendered));try(FileOutputStream out=new FileOutputStream(new File(folder,name+"-layout.png"))){rendered.compress(Bitmap.CompressFormat.PNG,100,out);}rendered.recycle();
            }catch(Exception error){try{report.put("error",error.getClass().getSimpleName()).put("phase","failed");}catch(JSONException ignored){}}
            finally{try{Files.writeString(new File(folder,name+"-layout.json").toPath(),report.toString(2));}catch(Exception ignored){}image.recycle();reader.close();pending.finish();}
        });
    }
    private static void control(Context c,Intent intent,String command){
        JSONObject report=new JSONObject();try{
            if(command.equals("ad-reset-check")){AdSkipController.debugClicks=0;AdSkipController.debugPackage="";AdSkipController.debugLabel="";AdSkipController.debugCandidate="";}
            if(command.equals("layout-checks")){ComicLayoutChecks.render(c);return;}
            if(command.equals("api-checks")){report=ComicApiChecks.run();Files.writeString(new File(c.getExternalFilesDir("comic-check"),"api-checks.json").toPath(),report.toString(2));return;}
            if(command.equals("interaction-checks")){report=ComicInteractionChecks.run(c);Files.writeString(new File(c.getExternalFilesDir("comic-check"),"interaction-checks.json").toPath(),report.toString(2));return;}
            var prefs=Prefs.of(c);var saved=c.getSharedPreferences("comic_qa_saved",Context.MODE_PRIVATE);
            String[] keys={"ad_skip_enabled","app_skip_com.kongye.comicad.fixture","comic_source","comic_engine"};
            if(command.equals("save")){JSONObject before=new JSONObject();for(String key:keys)before.put(key,prefs.contains(key)?prefs.getAll().get(key):JSONObject.NULL);saved.edit().putString("before",before.toString()).commit();}
            if(command.equals("restore")){JSONObject before=new JSONObject(saved.getString("before","{}"));var edit=prefs.edit();for(String key:keys)if(before.has(key)){Object value=before.get(key);if(value==JSONObject.NULL)edit.remove(key);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else edit.putString(key,value.toString());}edit.commit();saved.edit().clear().apply();if(FloatService.instance!=null)FloatService.instance.stopComics();}
            if(command.equals("ad-on")||command.equals("ad-off"))prefs.edit().putBoolean("ad_skip_enabled",command.equals("ad-on")).commit();
            if(command.equals("app-on")||command.equals("app-off"))prefs.edit().putBoolean("app_skip_com.kongye.comicad.fixture",command.equals("app-on")).commit();
            if((command.startsWith("ad-")||command.startsWith("app-"))&&AssistService.instance!=null)AssistService.instance.adSkipSettingsChanged();
            if(command.equals("source-en")||command.equals("source-ja"))prefs.edit().putString("comic_source",command.endsWith("ja")?"ja":"en").putString("comic_engine","offline").commit();
            if(command.equals("stop")&&FloatService.instance!=null)FloatService.instance.stopComics();
            if(command.equals("start")&&FloatService.instance!=null)FloatService.instance.perform("comic");
            report.put("connected",AssistService.instance!=null).put("running",FloatService.running).put("foreground",AssistService.foregroundPackage);
            report.put("adClicks",AdSkipController.debugClicks).put("adPackage",AdSkipController.debugPackage).put("adLabel",AdSkipController.debugLabel).put("adCandidate",AdSkipController.debugCandidate);
            if(FloatService.instance!=null){var field=FloatService.class.getDeclaredField("comics");field.setAccessible(true);Object session=field.get(FloatService.instance);
                if(session!=null){for(String key:new String[]{"active","busy","translating","generation","status","boundPackage"}){var f=ComicSession.class.getDeclaredField(key);f.setAccessible(true);report.put(key,f.get(session));}
                    for(String key:new String[]{"area","panel"}){var f=ComicSession.class.getDeclaredField(key);f.setAccessible(true);Object value=f.get(session);report.put(key,key.equals("panel")?value!=null:String.valueOf(value));}
                    var blocked=ComicSession.class.getDeclaredField("blocked");blocked.setAccessible(true);report.put("blocked",((java.util.function.BooleanSupplier)blocked.get(session)).getAsBoolean());
                    var f=ComicSession.class.getDeclaredField("overlay");f.setAccessible(true);Object view=f.get(session);if(view!=null){var drawings=ComicOverlayView.class.getDeclaredField("drawings");drawings.setAccessible(true);report.put("drawings",((java.util.List<?>)drawings.get(view)).size());}
                }
            }
        }catch(Exception error){try{report.put("error",error.getClass().getSimpleName());}catch(JSONException ignored){}}
        try{Files.writeString(new File(c.getExternalFilesDir("comic-check"),"control.json").toPath(),report.toString(2));}catch(Exception ignored){}
    }
}
