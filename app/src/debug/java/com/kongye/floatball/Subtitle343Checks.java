package com.kongye.floatball;

import android.content.Context;
import android.graphics.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.*;
import org.json.*;
import java.io.File;
import java.util.*;
import com.google.android.gms.tasks.*;

/** Explicit local screenshot diagnostics. Debug APK only; never reads other app data. */
final class Subtitle343Checks {
    static void run(Context context,String command) throws Exception {
        if(command.equals("pronouns")){pronouns(context);return;}
        if(!command.equals("i")&&!command.equals("tail"))throw new IllegalArgumentException(command);
        File file=new File(context.getExternalFilesDir("subtitle343"),command+".png");
        Bitmap image=BitmapFactory.decodeFile(file.getPath());
        if(image==null)throw new IllegalStateException("Missing test image");
        Bitmap crop=Bitmap.createBitmap(image,0,180,image.getWidth(),110);image.recycle();
        var reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        JSONObject report=new JSONObject().put("phase","ocr");
        save(context,command,report);
        reader.process(InputImage.fromBitmap(crop,0)).addOnCompleteListener(task->{
            try {
                if(!task.isSuccessful())throw new IllegalStateException("OCR failed",task.getException());
                SubtitleFrame frame=SubtitleFrame.read(task.getResult(),crop);
                JSONArray blocks=new JSONArray();for(var block:frame.blocks)blocks.put(block.text());
                JSONArray lines=new JSONArray();
                for(var block:task.getResult().getTextBlocks())for(var line:block.getLines()) {
                    var r=line.getBoundingBox();if(r==null)continue;
                    TextRegion region=new TextRegion(line.getText(),r.left,r.top,r.right,r.bottom);
                    lines.put(new JSONObject().put("text",line.getText()).put("bounds",r.toShortString())
                        .put("color",String.format("#%08X",SubtitleLayout.foreground(crop::getPixel,crop.getWidth(),crop.getHeight(),region))));
                }
                report.put("source",frame.text).put("blocks",blocks).put("lines",lines).put("phase","model");save(context,command,report);
                var translator=Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage("en").setTargetLanguage("zh").build());
                translator.downloadModelIfNeeded(new DownloadConditions.Builder().build()).addOnCompleteListener(model->{
                    if(!model.isSuccessful()){error(context,command,report,model.getException());translator.close();return;}
                    translator.translate(frame.text.replace('\n',' ')).addOnCompleteListener(result->{
                        try {
                            if(!result.isSuccessful())throw new IllegalStateException("Translation failed",result.getException());
                            report.put("raw_translation",result.getResult()).put("phase","sentences");save(context,command,report);
                            translatePlanned(context,command,report,frame,translator);
                        }catch(Exception e){error(context,command,report,e);translator.close();}
                    });
                });
            }catch(Exception e){error(context,command,report,e);}finally{reader.close();crop.recycle();}
        });
    }
    private static void pronouns(Context context) {
        var sources=List.of("I mean Violet already knows the answer.","I think I should leave before I get into trouble.",
            "I need to get out of here.","Violet and I will wait here.","I remember what I said.","Press I to open the inventory.");
        var translator=Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage("en").setTargetLanguage("zh").build());
        JSONObject report=new JSONObject();try{report.put("phase","model");save(context,"pronouns",report);}catch(JSONException e){throw new RuntimeException(e);}
        translator.downloadModelIfNeeded(new DownloadConditions.Builder().build()).addOnCompleteListener(model->{
            if(!model.isSuccessful()){error(context,"pronouns",report,model.getException());translator.close();return;}
            ArrayList<Task<String>> jobs=new ArrayList<>();for(String source:sources)jobs.add(translator.translate(source));
            Tasks.whenAllComplete(jobs).addOnCompleteListener(done->{
                try {
                    JSONArray cases=new JSONArray();for(int i=0;i<sources.size();i++){
                        var job=jobs.get(i);if(!job.isSuccessful())throw new IllegalStateException("Pronoun fixture failed",job.getException());
                        cases.put(new JSONObject().put("source",sources.get(i)).put("raw",job.getResult())
                            .put("repaired",SubtitleTranslation.repair(sources.get(i),job.getResult(),"en","zh")));
                    }
                    report.put("cases",cases).put("phase","complete");save(context,"pronouns",report);
                }catch(Exception e){error(context,"pronouns",report,e);}finally{translator.close();}
            });
        });
    }
    private static void translatePlanned(Context c,String key,JSONObject report,SubtitleFrame frame,Translator translator) throws JSONException {
        ArrayList<SubtitleTranslation.Plan> plans=new ArrayList<>();ArrayList<Task<String>> jobs=new ArrayList<>();
        JSONArray pieces=new JSONArray();
        for(var block:frame.blocks){var plan=SubtitleTranslation.plan(block.text(),"en");plans.add(plan);for(String piece:plan.pieces()){pieces.put(piece);jobs.add(translator.translate(piece));}}
        report.put("pieces",pieces);
        Tasks.whenAllComplete(jobs).addOnCompleteListener(done->{
            try {
                ArrayList<String> outputs=new ArrayList<>();for(var job:jobs){if(!job.isSuccessful())throw new IllegalStateException("Piece failed",job.getException());outputs.add(job.getResult());}
                JSONArray paragraphs=new JSONArray();int cursor=0;
                for(var plan:plans){int end=cursor+plan.pieces().size();paragraphs.put(plan.join(outputs.subList(cursor,end),"zh"));cursor=end;}
                report.put("piece_translations",new JSONArray(outputs)).put("paragraphs",paragraphs).put("phase","complete");save(c,key,report);
            }catch(Exception e){error(c,key,report,e);}finally{translator.close();}
        });
    }
    private static void save(Context c,String key,JSONObject report){Prefs.of(c).edit().putString("qa_subtitle343_"+key,report.toString()).commit();}
    private static void error(Context c,String key,JSONObject report,Exception e){try{report.put("phase","error").put("error",String.valueOf(e));save(c,key,report);}catch(JSONException ignored){}}
}
