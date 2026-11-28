package com.kongye.floatball;

import android.app.Activity;
import android.graphics.*;
import android.os.Bundle;
import android.widget.*;
import com.google.android.gms.tasks.*;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import org.json.*;

/** Explicit public/synthetic-image diagnostics, packaged only in Debug builds. */
public class ComicCheckActivity extends Activity {
    private TextRecognizer reader;private Translator translator;private Bitmap image;
    private JSONObject report=new JSONObject();private String name;
    @Override public void onCreate(Bundle state){super.onCreate(state);
        name=getIntent().getStringExtra("name");if(name==null||!name.matches("[a-z0-9-]+")){finish();return;}
        String command=getIntent().getStringExtra("command");
        if(command!=null){try{
            if(command.equals("rules"))startActivity(new android.content.Intent(this,AppBehaviorActivity.class));
            else if(command.equals("config"))startActivity(new android.content.Intent(this,ComicSettingsActivity.class));
            else if(command.equals("source-en"))Prefs.of(this).edit().putString("comic_source","en").putString("comic_engine","offline").apply();
            else if(command.equals("source-ja"))Prefs.of(this).edit().putString("comic_source","ja").putString("comic_engine","offline").apply();
            else if(command.equals("comic")){if(FloatService.instance!=null)FloatService.instance.perform("comic");}
            else if(command.equals("stop")){if(FloatService.instance!=null)FloatService.instance.stopComics();}
            else if(command.equals("snapshot")){
                report.put("connected",AssistService.instance!=null);
                if(FloatService.instance!=null){var field=FloatService.class.getDeclaredField("comics");field.setAccessible(true);Object session=field.get(FloatService.instance);if(session!=null)for(String key:new String[]{"active","busy","translating","generation","status","boundPackage"}){var f=ComicSession.class.getDeclaredField(key);f.setAccessible(true);report.put(key,f.get(session));}}
            }
            save("control_complete");
        }catch(Exception error){error(error.getClass().getSimpleName());}finish();return;}
        String language=getIntent().getStringExtra("language");if(language==null)language="en";
        File file=new File(getExternalFilesDir("comic-check"),name+".jpg");image=BitmapFactory.decodeFile(file.getPath());
        if(image==null){error("Missing image");return;}
        FrameLayout root=new FrameLayout(this);ImageView source=new ImageView(this);source.setImageBitmap(image);source.setScaleType(ImageView.ScaleType.FIT_XY);root.addView(source,new FrameLayout.LayoutParams(-1,-1));ComicOverlayView overlay=new ComicOverlayView(this);root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        String lang=language;reader=switch(lang){case "ja"->TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());case "ko"->TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());default->TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);};
        save("ocr");reader.process(InputImage.fromBitmap(image,0)).addOnCompleteListener(task->{
            if(!task.isSuccessful()){error("OCR failed");return;}
            ComicFrame frame=ComicFrame.read(task.getResult(),image,lang);
            try{report.put("ocr",task.getResult().getText());JSONArray boxes=new JSONArray();for(var b:frame.blocks)boxes.put(new JSONObject().put("text",b.text()).put("vertical",b.vertical()).put("bounds",b.bounds().toShortString()));report.put("blocks",boxes).put("count",frame.blocks.size());}catch(JSONException e){throw new RuntimeException(e);}save("model");
            if(frame.blocks.isEmpty()){error("No plain text blocks");return;}
            if(getIntent().getBooleanExtra("layout",false)){
                ArrayList<String> test=new ArrayList<>();for(int i=0;i<frame.blocks.size();i++)test.add("这是第"+(i+1)+"段排版测试译文，检查标点、换列和气泡边界。");
                overlay.post(()->{overlay.show(frame,test,new Rect(0,0,root.getWidth(),root.getHeight()));save("layout_complete");});return;
            }
            translator=Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(lang).setTargetLanguage("zh").build());translator.downloadModelIfNeeded(new DownloadConditions.Builder().build()).addOnCompleteListener(model->{
                if(!model.isSuccessful()){error("Model unavailable");return;}save("translation");
                ArrayList<Task<String>> jobs=new ArrayList<>();for(var b:frame.blocks)jobs.add(translator.translate(b.text()));Tasks.whenAllComplete(jobs).addOnCompleteListener(all->{
                    ArrayList<String> output=new ArrayList<>();for(var job:jobs){if(!job.isSuccessful()){error("Translation failed");return;}output.add(job.getResult());}
                    try{report.put("translations",new JSONArray(output));}catch(JSONException e){throw new RuntimeException(e);}
                    overlay.post(()->{overlay.show(frame,output,new Rect(0,0,root.getWidth(),root.getHeight()));save("complete");});
                });
            });
        });
    }
    private void error(String message){try{report.put("error",message);}catch(JSONException ignored){}save("error");}
    private void save(String phase){try{report.put("phase",phase);Files.writeString(new File(getExternalFilesDir("comic-check"),name+".json").toPath(),report.toString(2));}catch(Exception ignored){}}
    @Override public void onDestroy(){if(reader!=null)reader.close();if(translator!=null)translator.close();super.onDestroy();}
}
