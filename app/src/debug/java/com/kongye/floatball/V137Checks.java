package com.kongye.floatball;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import android.widget.*;import java.util.*;import org.json.*;

/** Own generated document only. No screenshot file, screen export or user text in fixtures. */
final class V137Checks {
    static CaptureController control;static LinearLayout document;static MainActivity page;
    static void run(Context c,String command)throws Exception{
        if(command.equals("fixture")||command.equals("image_only")){
            if(control!=null)control.destroy();control=null;page=V1231Checks.main();if(page==null||AssistService.instance==null)throw new IllegalStateException("Own page and accessibility required");
            ThemePolicy.apply(page);FrameLayout root=new FrameLayout(page);root.setBackgroundColor(Color.WHITE);document=Ui.column(page);document.setBackgroundColor(Color.WHITE);Ui.pad(document,12,12,12,12);
            boolean imageOnly=command.equals("image_only");if(!imageOnly)addText("原文标题 2026");addImage("图片独有文字 789",520,92);
            if(!imageOnly){LinearLayout row=Ui.row(page);TextView text=Ui.text(page,"原文 ABC",20,Color.BLACK,false);row.addView(text,new LinearLayout.LayoutParams(Ui.dp(page,130),Ui.dp(page,54)));row.addView(image("Image 246",350,92),new LinearLayout.LayoutParams(Ui.dp(page,160),Ui.dp(page,54)));document.addView(row);addText("金额 ￥1,234.50");addText("重复标签");}
            addImage("重复标签",520,92);if(!imageOnly){addText("重复标签");EditText password=new EditText(page);password.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);password.setText("not-read-fixture");password.setFocusable(false);document.addView(password,new LinearLayout.LayoutParams(-1,Ui.dp(page,44)));}
            FrameLayout.LayoutParams at=new FrameLayout.LayoutParams(-1,-2);at.setMargins(Ui.dp(page,24),Ui.dp(page,70),Ui.dp(page,24),0);root.addView(document,at);TextView outside=Ui.text(page,"选区外不要读取",20,Color.BLACK,false);FrameLayout.LayoutParams bottom=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.LEFT);bottom.bottomMargin=Ui.dp(page,24);root.addView(outside,bottom);page.setContentView(root);Prefs.of(c).edit().remove("qa_v137_state").remove("qa_v137_error").apply();return;
        }
        if(command.equals("capture")){Rect crop=new Rect();document.getGlobalVisibleRect(crop);List<TextRegion> original=AssistService.instance.textRegionsInRegion(crop);Prefs.of(c).edit().putString("qa_v137_direct",RegionTextFusion.text(original)).putInt("qa_v137_direct_count",original.size()).apply();control=new CaptureController(page,on->{});control.startRegion(crop);return;}
        if(command.equals("close")){if(control!=null)control.destroy();control=null;return;}
        RegionResult result=control==null?null:(RegionResult)V126Checks.get(control,"result");if(result==null){Prefs.of(c).edit().putString("qa_v137_state","{}").apply();return;}
        if(command.equals("retry")){V126Checks.call(result,"recognizeAgain");return;}
        if(command.equals("translate")){V126Checks.call(result,"translateText");return;}
        if(command.equals("translation_state")){CaptureWindow window=(CaptureWindow)V126Checks.get(result,"window");TranslationPane pane=(TranslationPane)V1251Checks.findType(window.root(),TranslationPane.class);JSONObject j=new JSONObject();for(String key:new String[]{"input","output","status"})j.put(key,((TextView)V126Checks.get(pane,key)).getText());Prefs.of(c).edit().putString("qa_v137_translation",j.toString()).apply();return;}
        if(command.equals("keep_lines")){CaptureWindow window=(CaptureWindow)V126Checks.get(result,"window");CheckBox keep=(CheckBox)V1251Checks.findType(window.root(),CheckBox.class);keep.setChecked(!keep.isChecked());return;}
        if(command.equals("fallback")){V126Checks.set(result,"recognitionGeneration",(int)V126Checks.get(result,"recognitionGeneration")+1);V126Checks.call(result,"failed");return;}
        if(command.equals("state")){CaptureWindow window=(CaptureWindow)V126Checks.get(result,"window");JSONObject j=new JSONObject().put("recognizing",V126Checks.get(result,"recognizing")).put("raw",V126Checks.get(result,"rawValue")).put("value",V126Checks.get(result,"value")).put("status",V126Checks.get(result,"statusText")).put("original_count",((List<?>)V126Checks.get(result,"originalText")).size()).put("window",System.identityHashCode(window.root())).put("card",System.identityHashCode(V126Checks.get(result,"currentCard")));Prefs.of(c).edit().putString("qa_v137_state",j.toString()).apply();}
    }
    static void addText(String value){TextView text=Ui.text(page,value,20,Color.BLACK,false);document.addView(text,new LinearLayout.LayoutParams(-1,Ui.dp(page,46)));}
    static ImageView image(String text,int width,int height){Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);Paint paint=new Paint(3);paint.setColor(Color.BLACK);paint.setTextSize(40);new Canvas(b).drawText(text,12,60,paint);ImageView image=new ImageView(page);image.setImageBitmap(b);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){image.setImageDrawable(null);b.recycle();}});return image;}
    static void addImage(String text,int width,int height){document.addView(image(text,width,height),new LinearLayout.LayoutParams(Ui.dp(page,260),Ui.dp(page,50)));}
}
