package com.kongye.floatball;
import android.content.*;import android.os.*;import android.view.*;import android.widget.*;
import com.google.mlkit.nl.languageid.*;import com.google.mlkit.nl.translate.*;
import java.util.*;

/** Compact local translation. Language controls transcribed from references/v127/2.svg. */
final class TranslationPane extends LinearLayout implements AutoCloseable {
 private final String[] codes=new String[]{"en","zh","ja","ko"};
 private final ArrayList<String> names=new ArrayList<>();private final TextView from,to,status;private final EditText input,output;private final android.os.Handler main=new Handler(Looper.getMainLooper());
 private OverlayPicker picker;private boolean initialAuto=true,targetChosen;private String source="auto",target="zh",detected="";private int generation;private boolean closed;private Translator translator;private LanguageIdentifier identifier;private final java.util.function.Consumer<String> copy;
 TranslationPane(Context c,String text,java.util.function.Consumer<String> copy){super(c);this.copy=copy;setOrientation(VERTICAL);names.addAll(Arrays.asList("英文","中文","日文","韩文"));LinearLayout row=Ui.row(c);row.setGravity(Gravity.CENTER_VERTICAL);from=Ui.centeredText(c,"自动检测语言",14,Ui.INK,true);to=Ui.centeredText(c,"中文",14,Ui.INK,true);row.addView(chip(from,"翻译源语言",()->choose(true)),new LayoutParams(0,Ui.dp(c,64),1));FrameLayout exchange=new FrameLayout(c);exchange.setContentDescription("交换翻译语言");exchange.setBackground(Ui.ripple(c,Ui.dark?Ui.PALE:0xFFE6EFE4,14));exchange.addView(Ui.icon(c,"translation_swap",Ui.GREEN,24),new FrameLayout.LayoutParams(Ui.dp(c,24),Ui.dp(c,24),Gravity.CENTER));exchange.setOnClickListener(v->swap());row.addView(exchange,new LayoutParams(Ui.dp(c,38),Ui.dp(c,28)));row.addView(chip(to,"翻译目标语言",()->choose(false)),new LayoutParams(0,Ui.dp(c,64),1));addView(row);input=multiline(c,"输入或编辑原文","翻译原文");input.setText(text);
 addView(textSection(c,"原文",input,false),new LayoutParams(-1,0,1));Ui.gap(this,10);
 output=multiline(c,"翻译结果将显示在这里","翻译结果");
 addView(textSection(c,"译文",output,true),new LayoutParams(-1,0,1.15f));Ui.gap(this,8);status=Ui.text(c,"正在自动检测语言…",12,Ui.MUTED,false);status.setContentDescription("翻译状态");addView(status);Ui.gap(this,8);LinearLayout actions=Ui.row(c);addAction(actions,"translate","翻译",this::run);addAction(actions,"copy","复制",()->copy.accept(output.getText().toString()));addView(actions);post(this::run);}
 /** These two fields wrap independently; other single-line fields keep their existing behaviour. */
 private EditText multiline(Context c,String hint,String description){
  EditText field=Ui.field(c,hint);field.setContentDescription(description);
  field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
  field.setSingleLine(false);field.setHorizontallyScrolling(false);field.setGravity(Gravity.TOP|Gravity.START);
  field.setMinHeight(0);field.setMinimumHeight(0);field.setMinLines(1);field.setBackground(null);
  Ui.pad(field,0,0,0,0);field.setLineSpacing(Ui.dp(c,3),1);field.setVerticalScrollBarEnabled(true);
  field.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
  field.setOnTouchListener((view,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN)view.getParent().requestDisallowInterceptTouchEvent(true);else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)view.getParent().requestDisallowInterceptTouchEvent(false);return false;});
  return field;
 }
 private LinearLayout textSection(Context c,String title,EditText field,boolean translated){
  LinearLayout card=Ui.column(c);card.setContentDescription(title+"区域");Ui.pad(card,12,10,12,10);
  android.graphics.drawable.GradientDrawable background=Ui.bg(translated?Ui.PALE:Ui.SURFACE,16,c);background.setStroke(Math.max(1,Ui.dp(c,.7f)),Ui.LINE);card.setBackground(background);
  LinearLayout heading=Ui.row(c);if(translated){View accent=new View(c);accent.setBackground(Ui.bg(Ui.GREEN,2,c));LayoutParams at=new LayoutParams(Ui.dp(c,3),Ui.dp(c,12));at.rightMargin=Ui.dp(c,6);heading.addView(accent,at);}
  heading.addView(Ui.text(c,title,12,translated?Ui.GREEN:Ui.MUTED,true));card.addView(heading);Ui.gap(card,6);
  card.addView(field,new LayoutParams(-1,0,1));return card;
 }
 private LinearLayout chip(TextView text,String label,Runnable click){LinearLayout button=Ui.row(getContext());button.setGravity(Gravity.CENTER);button.setContentDescription(label);button.setBackground(Ui.ripple(getContext(),0,14));text.setSingleLine();text.setMaxLines(1);button.addView(text);View arrow=Ui.icon(getContext(),"down_chevron",Ui.MUTED,12);LayoutParams at=new LayoutParams(Ui.dp(getContext(),12),Ui.dp(getContext(),12));at.leftMargin=Ui.dp(getContext(),6);button.addView(arrow,at);button.setOnClickListener(v->click.run());return button;}
 private void addAction(LinearLayout row,String icon,String text,Runnable run){LayoutParams p=new LayoutParams(0,Ui.dp(getContext(),40),1);p.setMargins(Ui.dp(getContext(),3),0,Ui.dp(getContext(),3),0);row.addView(Ui.action(getContext(),icon,text,text,run),p);}
 private String name(String code){int i=Arrays.asList(codes).indexOf(code);return code.equals("auto")?"自动检测语言":i<0?code:names.get(i);}
 private void labels(){from.setText(name(source));from.setTextSize(14);to.setText(name(target));}
 private void choose(boolean first){if(picker!=null)picker.close();ArrayList<String> options=new ArrayList<>(names);if(first)options.add(0,"自动检测语言");picker=new OverlayPicker(getContext(),first?"源语言":"目标语言");int selected=first?(source.equals("auto")?0:Arrays.asList(codes).indexOf(source)+1):Arrays.asList(codes).indexOf(target);picker.choices(options.toArray(new String[0]),selected,i->{if(first)source=i==0?"auto":codes[i-1];else{target=codes[i];targetChosen=true;}initialAuto=false;labels();run();});}
 private void swap(){initialAuto=false;targetChosen=true;String actual=source.equals("auto")?detected:source;if(!Arrays.asList(codes).contains(actual)){status.setText("请先选择源语言");return;}String old=target;target=actual;source=old;if(!output.getText().toString().isBlank()){String before=input.getText().toString();input.setText(output.getText());output.setText(before);}labels();run();}
 private void stopClients(){if(translator!=null){translator.close();translator=null;}if(identifier!=null){identifier.close();identifier=null;}}
 private boolean valid(int token){return !closed&&generation==token;}
 private void run(){if(closed)return;int token=++generation;stopClients();String text=input.getText().toString().trim();if(text.isEmpty()){status.setText("请输入原文");return;}output.setText("");main.removeCallbacksAndMessages(null);main.postDelayed(()->{if(valid(token))status.setText("模型准备较慢，请检查 Wi-Fi；可重新翻译");},60000);if(!source.equals("auto")){translate(source,text,token);return;}status.setText("正在自动检测语言…");identifier=LanguageIdentification.getClient();identifier.identifyLanguage(text).addOnSuccessListener(code->{if(!valid(token))return;String language=TranslateLanguage.fromLanguageTag(code);if(language==null||!Arrays.asList(codes).contains(language)){status.setText("未能可靠识别，请手动选择源语言");main.removeCallbacksAndMessages(null);return;}detected=language;if(initialAuto&&!targetChosen&&language.equals(target)){target=language.equals("zh")?"en":"zh";to.setText(name(target));}initialAuto=false;from.setText("自动检测语言");translate(language,text,token);}).addOnFailureListener(e->{if(valid(token)){main.removeCallbacksAndMessages(null);status.setText("语言检测失败，请选择源语言");}});}
 private void translate(String language,String text,int token){detected=language;if(language.equals(target)){output.setText(text);status.setText("翻译完成");main.removeCallbacksAndMessages(null);return;}
  status.setText("正在翻译…");Translator client=Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(language).setTargetLanguage(target).build());translator=client;client.downloadModelIfNeeded(new com.google.mlkit.common.model.DownloadConditions.Builder().requireWifi().build()).continueWithTask(task->{if(!valid(token))throw new java.util.concurrent.CancellationException();if(!task.isSuccessful())throw task.getException();return client.translate(text);}).addOnSuccessListener(result->{if(valid(token)){output.setText(result);status.setText("翻译完成");}}).addOnFailureListener(error->{if(valid(token))status.setText("翻译失败，请检查 Wi-Fi 或语言模型后重试");}).addOnCompleteListener(task->{client.close();if(translator==client)translator=null;if(valid(token))main.removeCallbacksAndMessages(null);});
 }
 @Override public void close(){closed=true;if(picker!=null)picker.close();generation++;main.removeCallbacksAndMessages(null);stopClients();}
 @Override protected void onDetachedFromWindow(){close();super.onDetachedFromWindow();}
}
