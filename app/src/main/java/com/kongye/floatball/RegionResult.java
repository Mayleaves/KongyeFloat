package com.kongye.floatball;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.util.concurrent.ExecutorService;
import java.util.*;
import java.util.function.Consumer;

/** Transparent outside, editable OCR and image sections, movable pin, selectable text and local editing. */
final class RegionResult {
    private final Context context;private Bitmap image;private final CaptureWindow window;private final Runnable finished;private final ExecutorService worker;private final Consumer<Boolean> pinChanged;
    private final String kind;private ImageEditView editingCanvas;private int brushColor=0xFFF44336,brushSize=4;
    private java.util.List<TextRegion> originalText=Collections.emptyList(),lastOcr=Collections.emptyList();private volatile int recognitionGeneration;private volatile boolean closed;
    private String rawValue="",value="",statusText="识别中…";private boolean updatingText;private View retryButton;private boolean recognizing=true,saving,pinned,allFolded,textFolded,imageFolded,edited;
    private int translationGeneration;private LinearLayout editorTools;private EditText editor;private TextView status;private int x=-1,y=-1,mode;private View currentCard;
    RegionResult(Context c,Bitmap image,ExecutorService worker,Runnable finished,Consumer<Boolean> pinChanged){this(c,image,worker,finished,pinChanged,"Region");}
    RegionResult(Context c,Bitmap image,ExecutorService worker,Runnable finished,Consumer<Boolean> pinChanged,String kind){this(c,image,worker,finished,pinChanged,kind,"");}
    RegionResult(Context c,Bitmap image,ExecutorService worker,Runnable finished,Consumer<Boolean> pinChanged,String kind,String selectedText){this(c,image,worker,finished,pinChanged,kind,selectedText.isBlank()?Collections.emptyList():Collections.singletonList(new TextRegion(selectedText,0,0,image.getWidth(),image.getHeight())));}
    RegionResult(Context c,Bitmap image,ExecutorService worker,Runnable finished,Consumer<Boolean> pinChanged,String kind,java.util.List<TextRegion> selectedText){context=c;this.kind=kind;this.image=image;this.worker=worker;this.finished=finished;this.pinChanged=pinChanged;window=new CaptureWindow(c);Prefs.of(c).edit().putString("document_kind",kind).putInt("document_width",image.getWidth()).putInt("document_height",image.getHeight()).apply();recognizing=kind.equals("Region");if(recognizing){originalText=RegionTextFusion.originals(selectedText);rawValue=RegionTextFusion.text(originalText);value=normalized(rawValue);}show();if(recognizing)recognize();}

    private String title(){return kind.equals("Screen")?"整屏截图":kind.equals("Long")?"长截图":"区域截图";}
    private void back(){if(editingCanvas!=null&&editingCanvas.isTyping()){finishInlineText();return;}if(mode==3)cancelEdit();else if(mode==0)close();else show();}
    boolean isPinned(){return pinned;}
    private int dp(float n){return Ui.dp(context,n);}
    private void toast(String text){if(!closed)Feedback.show(context,text);}
    private DisplayMetrics metrics(){DisplayMetrics m=new DisplayMetrics();context.getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(m);return m;}
    private FrameLayout icon(String name,String description,Runnable action){return Ui.symbol(context,name,description,action);}
    private void addIcon(LinearLayout row,String name,String description,Runnable action){FrameLayout v=Ui.tool(context,name,description,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(40),dp(40));p.leftMargin=dp(2);row.addView(v,p);}
    private void addAction(LinearLayout row,String name,String label,String description,Runnable action){LinearLayout v=Ui.action(context,name,label,description,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),1);p.setMargins(dp(4),0,dp(4),0);row.addView(v,p);}
    private TextView section(String title){TextView t=Ui.text(context,title,13,Ui.MUTED,true);Ui.pad(t,2,0,0,0);return t;}
    /** Round primary button floating over a preview. */
    private FrameLayout fab(String name,String description,Runnable action){FrameLayout v=new FrameLayout(context);v.setContentDescription(description);v.setBackground(Ui.ripple(context,Ui.GREEN,26));v.setElevation(dp(6));v.addView(new Ui.Icon(context,name,Ui.dark?0xFF102E21:Color.WHITE),new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));v.setOnClickListener(w->action.run());return v;}
    private LinearLayout heading(String title,Runnable back){LinearLayout row=Ui.row(context);row.setMinimumHeight(dp(44));if(back!=null){addIcon(row,"back","返回截图结果",back);((LinearLayout.LayoutParams)row.getChildAt(0).getLayoutParams()).leftMargin=-dp(8);}TextView label=Ui.text(context,title,17,Ui.INK,true);Ui.pad(label,back!=null?4:2,0,0,0);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));label.setContentDescription("拖动截图结果窗口");drag(label);return row;}
    private void drag(View title){title.setOnTouchListener(new View.OnTouchListener(){float startX,startY;int oldX,oldY;public boolean onTouch(View view,MotionEvent event){if(!pinned||mode==3)return false;switch(event.getActionMasked()){case MotionEvent.ACTION_DOWN->{int[] p=window.position();oldX=p[0];oldY=p[1];startX=event.getRawX();startY=event.getRawY();return true;}case MotionEvent.ACTION_MOVE->{DisplayMetrics m=metrics();x=oldX+Math.round(event.getRawX()-startX);y=Math.max(dp(28),Math.min(m.heightPixels-dp(64),oldY+Math.round(event.getRawY()-startY)));window.move(x,y);return true;}case MotionEvent.ACTION_UP->{view.performClick();if(x<-(currentCard.getWidth()/2)||x>metrics().widthPixels-currentCard.getWidth()/2){showEdgeTab(x<0);return true;}Prefs.of(context).edit().putInt("result_x",x).putInt("result_y",y).apply();return true;}case MotionEvent.ACTION_CANCEL->{return true;}}return false;}});}
    private void showEdgeTab(boolean left){int height=window.root().getHeight();DockMorph.dock(context,window.manager,(FrameLayout)window.root(),left,kind.equals("Region")?"region":kind.equals("Long")?"longshot":"screenshot","展开置顶截图标签",()->{int[] at=window.position();x=at[0];y=at[1];Prefs.of(context).edit().putInt("result_x",x).putInt("result_y",y).apply();});}
    private void present(LinearLayout card){
        if(closed)return;ThemePolicy.apply(context);DisplayMetrics m=metrics();boolean expanded=mode!=0;boolean compactTranslation=mode==4;int width=expanded?m.widthPixels-dp(16):m.widthPixels-dp(pinned?40:16);int editorHeight=compactTranslation?Math.min(m.heightPixels-dp(160),dp(430)):m.heightPixels-dp(80);currentCard=card;card.setTag(mode==3?"capture_editor":null);card.setOnClickListener(v->{});card.setContentDescription(title()+(pinned?"结果已置顶":"结果未置顶"));
        card.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(expanded?editorHeight:0,expanded?View.MeasureSpec.EXACTLY:View.MeasureSpec.UNSPECIFIED));
        if(x<0)x=(m.widthPixels-width)/2;if(y<0)y=Math.max(dp(40),(m.heightPixels-card.getMeasuredHeight())/2);
        if(pinned&&!expanded){x=Math.max(0,Math.min(m.widthPixels-width-dp(24),x));y=Math.max(dp(28),Math.min(m.heightPixels-dp(64),y));FrameLayout floating=window.frame(this::back);floating.setClipChildren(false);floating.setClipToPadding(false);Ui.pad(floating,12,12,12,12);floating.addView(card,new FrameLayout.LayoutParams(-1,-2));window.showFloating(floating,width+dp(24),m.heightPixels-dp(80),x,y);}
        else{FrameLayout root=window.frame(this::back);root.setClipChildren(false);root.setClipToPadding(false);root.setBackgroundColor(Color.TRANSPARENT);root.setContentDescription("截图结果透明背景，点击关闭");root.setOnClickListener(v->close());FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width,expanded?editorHeight:-2,Gravity.CENTER);p.setMargins(0,dp(40),0,dp(32));root.addView(card,p);window.show(root,true);}
        Prefs.of(context).edit().putBoolean("result_pinned",pinned).putBoolean("result_all_folded",allFolded).putBoolean("result_text_folded",textFolded).putBoolean("result_image_folded",imageFolded).apply();pinChanged.accept(pinned);
    }
    private LinearLayout base(){LinearLayout card=Ui.card(context);Ui.pad(card,16,12,16,14);Ui.softShadow(card);return card;}
    private LinearLayout resultSection(){
        LinearLayout section=Ui.column(context);Ui.pad(section,14,10,14,14);
        android.graphics.drawable.GradientDrawable frame=Ui.bg(Ui.SURFACE,18,context);frame.setStroke(dp(.7f),Ui.LINE);section.setBackground(frame);return section;
    }
    private String normalized(String text){return Prefs.of(context).getBoolean("ocr_keep_lines",false)?text:text.replace("\r","").replace("\n","");}
    private void refreshRecognition(){if(mode!=0)return;if(editor!=null){updatingText=true;editor.setText(value);updatingText=false;editor.setEnabled(!recognizing);editor.setHint(recognizing?"识别中…":"可编辑识别文字");}if(status!=null)status.setText(statusText);if(retryButton!=null)retryButton.setEnabled(!recognizing);}
    private void recognizeAgain(){if(recognizing){toast("正在识别，请稍候");return;}recognizing=true;statusText="识别中…";refreshRecognition();recognize();}
    private void show(){
        if(closed)return;mode=0;editor=null;status=null;retryButton=null;ThemePolicy.apply(context);LinearLayout card=base();card.setBackground(Ui.floatingFrame(context,24));
        LinearLayout head=heading(title(),null);
        View accent=new View(context);accent.setBackground(Ui.bg(Ui.GREEN,3,context));LinearLayout.LayoutParams accentAt=new LinearLayout.LayoutParams(dp(5),dp(20));accentAt.rightMargin=dp(7);head.addView(accent,0,accentAt);
        addIcon(head,allFolded?"expand":"collapse",allFolded?"展开全部":"收起全部",()->{allFolded=!allFolded;show();});
        addIcon(head,pinned?"pinned":"pin",pinned?"取消置顶":"置顶截图结果",()->{pinned=!pinned;show();});addIcon(head,"close","关闭截图结果",this::close);card.addView(head);
        if(!allFolded){
            Ui.gap(card,12);ScrollView viewport=new ScrollView(context);viewport.setVerticalScrollBarEnabled(false);LinearLayout sections=Ui.column(context);viewport.addView(sections);
            if(kind.equals("Region")){
                LinearLayout textSection=resultSection();textSection.setContentDescription("OCR 识别文字卡片");LinearLayout textHead=Ui.row(context);textHead.addView(section("OCR 识别文字"),new LinearLayout.LayoutParams(0,-2,1));
                LinearLayout retry=Ui.action(context,"scan_text","重新识别","重新识别文字",this::recognizeAgain);retry.setBackground(Ui.ripple(context,0,10));retry.setEnabled(!recognizing);retryButton=retry;((TextView)retry.getChildAt(1)).setTextSize(11);retry.getChildAt(0).setLayoutParams(new LinearLayout.LayoutParams(dp(14),dp(14)));textHead.addView(retry,new LinearLayout.LayoutParams(dp(82),dp(36)));
                addIcon(textHead,"zoom","放大选择文字",this::selectText);addIcon(textHead,textFolded?"expand":"collapse",textFolded?"展开文字":"收起文字",()->{textFolded=!textFolded;show();});textSection.addView(textHead);
                if(!textFolded){
                    Ui.gap(textSection,6);editor=new EditText(context);editor.setTextColor(Ui.INK);editor.setHintTextColor(Ui.MUTED);editor.setTextSize(14);editor.setGravity(Gravity.TOP);android.graphics.drawable.GradientDrawable field=Ui.bg(Ui.BG,14,context);field.setStroke(dp(.7f),Ui.LINE);editor.setBackground(field);Ui.pad(editor,12,10,12,10);editor.setLineSpacing(dp(2),1);editor.setText(value);editor.setEnabled(!recognizing);editor.setHint(recognizing?"识别中…":"可编辑识别文字");editor.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100000)});textSection.addView(editor,new LinearLayout.LayoutParams(-1,dp(96)));editor.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){value=s.toString();if(!updatingText)rawValue=value;}public void afterTextChanged(Editable e){}});
                    CheckBox keep=new CheckBox(context);keep.setText("保留换行");keep.setTextSize(12);keep.setTextColor(Ui.MUTED);keep.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));keep.setContentDescription("OCR 保留换行");keep.setChecked(Prefs.of(context).getBoolean("ocr_keep_lines",false));keep.setOnCheckedChangeListener((v,on)->{Prefs.of(context).edit().putBoolean("ocr_keep_lines",on).apply();value=normalized(rawValue);refreshRecognition();});LinearLayout options=Ui.row(context);options.addView(keep,new LinearLayout.LayoutParams(0,dp(34),1));CheckBox punctuation=new CheckBox(context);punctuation.setText("中文标点优化");punctuation.setTextSize(12);punctuation.setTextColor(Ui.MUTED);punctuation.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));punctuation.setContentDescription("OCR 中文标点优化");punctuation.setChecked(Prefs.of(context).getBoolean("ocr_chinese_punctuation",true));punctuation.setOnCheckedChangeListener((v,on)->{Prefs.of(context).edit().putBoolean("ocr_chinese_punctuation",on).apply();if(!recognizing&&!lastOcr.isEmpty()){rawValue=combinedText(originalText,lastOcr);value=normalized(rawValue);refreshRecognition();}});options.addView(punctuation,new LinearLayout.LayoutParams(0,dp(34),1));textSection.addView(options);Ui.gap(textSection,6);LinearLayout actions=Ui.row(context);addAction(actions,"copy","复制","复制文字",()->copy(value));addAction(actions,"share","分享","分享文字",this::shareText);addAction(actions,"translate","翻译","翻译文字",this::translateText);addAction(actions,"notes","便签","追加便签",this::note);textSection.addView(actions);
                    status=Ui.text(context,statusText,10,Ui.MUTED,false);Ui.pad(status,2,0,0,0);Ui.gap(textSection,6);textSection.addView(status);
                }
                sections.addView(textSection);Ui.gap(sections,14);
            }
            LinearLayout imageSection=resultSection();imageSection.setContentDescription("画面预览卡片");LinearLayout imageHead=Ui.row(context);imageHead.addView(section("画面预览"),new LinearLayout.LayoutParams(0,-2,1));imageHead.addView(Ui.text(context,image.getWidth()+" × "+image.getHeight()+" px",11,Ui.MUTED,false));addIcon(imageHead,imageFolded?"expand":"collapse",imageFolded?"展开图片":"收起图片",()->{imageFolded=!imageFolded;show();});imageSection.addView(imageHead);
            if(!imageFolded){
                Ui.gap(imageSection,8);ImageView preview=new ImageView(context);preview.setImageBitmap(image);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);Ui.frame(preview,Ui.BG,14);preview.setContentDescription("截图图片，点击预览");preview.setOnClickListener(v->zoom());imageSection.addView(preview,new LinearLayout.LayoutParams(-1,kind.equals("Region")?Math.min(dp(200),metrics().heightPixels/4):Math.min(dp(230),metrics().heightPixels/3)));Ui.gap(imageSection,12);
                LinearLayout actions=Ui.row(context);addAction(actions,"copy","复制","复制截图",this::copyImage);addAction(actions,"share","分享","分享图片",()->save(true));addAction(actions,"edit","编辑","编辑图片",this::editImage);addAction(actions,"save","保存","保存图片",()->save(false));imageSection.addView(actions);
            }
            sections.addView(imageSection);DisplayMetrics m=metrics();int width=m.widthPixels-dp(pinned?72:48);sections.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));card.addView(viewport,new LinearLayout.LayoutParams(-1,Math.min(sections.getMeasuredHeight(),m.heightPixels-dp(190))));
        }
        present(card);
    }
    private void recognize(){
        final int token=++recognitionGeneration;final java.util.List<TextRegion> exact=new ArrayList<>(originalText);final Bitmap input;try{input=image.copy(Bitmap.Config.ARGB_8888,false);}catch(RuntimeException|OutOfMemoryError error){failed();return;}
        Prefs.of(context).edit().putString("last_ocr_state","recognizing").apply();worker.execute(()->{String recognized=null;java.util.List<TextRegion> recognizedRegions=Collections.emptyList();try{recognizedRegions=EnhancedOcr.recognize(context,input,()->closed||token!=recognitionGeneration);recognized=combinedText(exact,recognizedRegions);}catch(Exception|OutOfMemoryError ignored){}finally{input.recycle();}String output=recognized;java.util.List<TextRegion> completedRegions=recognizedRegions;new Handler(Looper.getMainLooper()).post(()->{if(closed||token!=recognitionGeneration)return;if(output==null){failed();return;}lastOcr=completedRegions;recognizing=false;rawValue=output;value=normalized(rawValue);statusText="识别完成";Prefs.of(context).edit().putString("last_ocr_state","done").apply();if(mode==0)refreshRecognition();});});
    }
    private String combinedText(java.util.List<TextRegion> exact,java.util.List<TextRegion> ocr){if(!Prefs.of(context).getBoolean("ocr_chinese_punctuation",true))return RegionTextFusion.text(RegionTextFusion.merge(exact,ocr));ArrayList<TextRegion> formatted=new ArrayList<>();for(TextRegion line:ocr)formatted.add(OcrPunctuation.normalize(line));return RegionTextFusion.text(RegionTextFusion.merge(exact,formatted));}
    private void failed(){recognizing=false;if(!originalText.isEmpty()){rawValue=RegionTextFusion.text(originalText);value=normalized(rawValue);statusText="已提取原文，图片文字识别未完成，可重试";}else statusText="识别暂不可用，仍可编辑文字和保存图片";Prefs.of(context).edit().putString("last_ocr_state","failed").apply();if(!closed&&mode==0)refreshRecognition();}
    private void copy(String text){
        if(text.isBlank()){toast("没有可复制的文字");return;}
        try{
            context.getSystemService(android.content.ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("空叶识别文字",text));
        }catch(RuntimeException error){toast("复制失败，请重试");return;}
        Prefs.of(context).edit().putInt("last_selection_copy_length",text.length()).apply();
        toast("文字已复制");
        close();
    }
    private void note(){if(value.isBlank()){toast("请先输入或识别文字");return;}String old=Prefs.of(context).getString("note","");String combined=old.isEmpty()?value:old+"\n\n"+value;if(combined.length()>100000){toast("便签已达字数上限");return;}Prefs.of(context).edit().putString("note",combined).apply();toast("已追加到便签");}
    private void launch(Intent intent){try{context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(RuntimeException e){toast("没有可处理此内容的应用");}}
    private void shareText(){if(value.isBlank()){toast("没有可分享的文字");return;}Intent intent=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,value);close();launch(Intent.createChooser(intent,"分享文字"));}
    private void translateText(){if(recognizing){toast("请等待识别完成");return;}if(value.isBlank()){toast("请先输入或识别文字");return;}mode=4;++translationGeneration;LinearLayout card=base();card.addView(heading("翻译",this::show));TranslationPane pane=new TranslationPane(context,value,this::copy);card.addView(pane,new LinearLayout.LayoutParams(-1,0,1));present(card);}
    private void selectText(){
        if(recognizing){toast("请等待识别完成");return;}mode=1;LinearLayout card=base();card.addView(heading("选择文字",this::show));Ui.gap(card,12);card.addView(Ui.text(context,"点击或滑动可跳着选择多段；再次点选可取消，不显示选择句柄。",12,Ui.MUTED,false));Ui.gap(card,12);
        DragSelectTextView selectable=new DragSelectTextView(context,value);selectable.setPadding(dp(8),dp(8),dp(8),dp(8));ScrollView scroll=new ScrollView(context);Ui.frame(scroll,Ui.BG,14);scroll.addView(selectable);card.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));Ui.gap(card,12);
        LinearLayout actions=Ui.row(context);addAction(actions,"copy","复制","复制所选文字",()->{Prefs.of(context).edit().putInt("last_selection_range_count",selectable.rangeCount()).apply();copy(selectable.selectedText());});addAction(actions,"select_all","全选","全选文字",selectable::selectAll);addAction(actions,"close","清空","清空所选文字",selectable::clearSelection);card.addView(actions);present(card);
    }
    private void zoom(){
        mode=2;
        FrameLayout root=window.frame(this::show);
        root.setBackgroundColor(0xED101A16);
        root.setContentDescription("图片预览暗色背景");
        root.setOnClickListener(v->show());
        PanZoomImageView large=new PanZoomImageView(context,image).outside(this::show);
        FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(-1,-1);
        ip.setMargins(dp(8),dp(44),dp(8),dp(44));
        root.addView(large,ip);
        FrameLayout back=fab("back","返回",this::show);
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.TOP|Gravity.LEFT);
        bp.setMargins(dp(16),dp(42),0,0);
        root.addView(back,bp);
        FrameLayout pen=fab("edit","编辑图片",this::editImage);
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(dp(52),dp(52),Gravity.BOTTOM|Gravity.RIGHT);
        pp.setMargins(0,0,dp(22),dp(44));
        root.addView(pen,pp);
        window.show(root,true);
    }
    private void finishInlineText(){if(editingCanvas!=null)editingCanvas.finishText();}
    private void cancelEdit(){if(editingCanvas!=null&&editingCanvas.isTyping()){finishInlineText();return;}if(editingCanvas!=null){editingCanvas.dispose();editingCanvas=null;}editorTools=null;zoom();}
    void openEditor(){editImage();}
    private void editImage(){
        if(saving){toast("图片保存中，请稍候");return;}mode=3;if(editingCanvas==null)editingCanvas=new ImageEditView(context,image);ImageEditView canvas=editingCanvas;canvas.color(brushColor);canvas.width(brushSize);if(canvas.getParent() instanceof ViewGroup parent)parent.removeView(canvas);
        LinearLayout card=base();LinearLayout editHead=heading("编辑图片",this::cancelEdit);FrameLayout fold=Ui.tool(context,"collapse","收起编辑工具",null);fold.setOnClickListener(v->{boolean open=editorTools.getVisibility()!=View.VISIBLE;editorTools.setVisibility(open?View.VISIBLE:View.GONE);fold.removeAllViews();fold.addView(Ui.icon(context,open?"collapse":"expand",Ui.GREEN,22),new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));fold.setContentDescription(open?"收起编辑工具":"展开编辑工具");});editHead.addView(fold,new LinearLayout.LayoutParams(dp(40),dp(40)));card.addView(editHead);Ui.gap(card,6);Ui.frame(canvas,Ui.BG,14);card.addView(canvas,new LinearLayout.LayoutParams(-1,0,1));Ui.gap(card,10);
        editorTools=Ui.column(context);card.addView(editorTools);
        TextView toolLabel=Ui.text(context,"绘制工具 · 可选颜色和粗细",12,Ui.MUTED,false);Ui.pad(toolLabel,4,0,0,0);editorTools.addView(toolLabel);Ui.gap(editorTools,6);
        String[] ids={"pen","highlight","rect","line","arrow","number","text","mosaic","crop","rotate","mirror_h","mirror_v","undo","reset"};String[] names={"画笔","高亮","画框","画线","箭头","序号","添加文字","马赛克","裁剪","旋转90度","水平镜像","垂直镜像","撤销","重置图片编辑"};
        java.util.Map<String,FrameLayout> modes=new java.util.HashMap<>();java.util.List<String> modeIds=java.util.Arrays.asList("pen","highlight","rect","line","arrow","number","text","mosaic","crop");
        for(int r=0;r<2;r++){LinearLayout tools=Ui.row(context);for(int col=0;col<7;col++){int at=r*7+col;if(at>=ids.length){tools.addView(new View(context),new LinearLayout.LayoutParams(0,dp(40),1));continue;}String id=ids[at],name=names[at];FrameLayout button=icon(id,name,()->{try{switch(id){case "rotate"->canvas.rotate();case "mirror_h"->canvas.mirror(false);case "mirror_v"->canvas.mirror(true);case "undo"->canvas.undo();case "reset"->canvas.reset();default->canvas.tool(id);}}catch(RuntimeException|OutOfMemoryError error){toast("内存不足，请应用当前编辑后重试");}toolLabel.setText(name);markTools(modes,canvas.tool());});if(modeIds.contains(id))modes.put(id,button);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(40),1);bp.setMargins(dp(2),dp(2),dp(2),dp(2));tools.addView(button,bp);}editorTools.addView(tools);}
        markTools(modes,canvas.tool());Ui.gap(editorTools,4);
        LinearLayout colors=Ui.row(context);editorTools.addView(colors);paintSwatches(colors,canvas,toolLabel);
        LinearLayout widths=Ui.row(context);TextView widthLabel=Ui.text(context,"粗细 "+brushSize,12,Ui.MUTED,false);Ui.pad(widthLabel,4,0,0,0);widths.addView(widthLabel,new LinearLayout.LayoutParams(dp(62),-2));SeekBar width=new SeekBar(context);width.setMax(23);width.setProgress(brushSize-1);width.setContentDescription("画笔粗细");width.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));width.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));width.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int value,boolean user){brushSize=value+1;canvas.width(brushSize);widthLabel.setText("粗细 "+brushSize);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});widths.addView(width,new LinearLayout.LayoutParams(0,dp(36),1));editorTools.addView(widths);Ui.gap(editorTools,6);
        editorTools.addView(Ui.button(context,"应用编辑",true,()->applyImage(canvas)));present(card);
    }
    /** Drawing modes stay filled while active; one-shot actions (rotate, undo…) never stay highlighted. */
    private void markTools(java.util.Map<String,FrameLayout> modes,String active){for(java.util.Map.Entry<String,FrameLayout> e:modes.entrySet()){boolean on=e.getKey().equals(active);FrameLayout b=e.getValue();b.setSelected(on);b.setBackground(Ui.ripple(context,on?Ui.GREEN:Ui.PALE,12));b.removeAllViews();b.addView(new Ui.Icon(context,e.getKey(),on?(Ui.dark?0xFF102E21:Color.WHITE):Ui.GREEN),new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));}}
    private void paintSwatches(LinearLayout colors,ImageEditView canvas,TextView toolLabel){
        colors.removeAllViews();int[] palette={0xFFF44336,0xFFFFC928,0xFF24A86B,0xFF2196F3,0xFF9C53C8,Color.BLACK,Color.WHITE};String[] labels={"红色","黄色","绿色","蓝色","紫色","黑色","白色"};
        for(int i=0;i<palette.length;i++){int color=palette[i],at=i;boolean on=color==brushColor;FrameLayout cell=new FrameLayout(context);cell.setContentDescription("画笔颜色"+labels[i]+(on?"，已选择":""));cell.setOnClickListener(v->{brushColor=color;canvas.color(color);toolLabel.setText("已选择"+labels[at]);paintSwatches(colors,canvas,toolLabel);});
            if(on){android.graphics.drawable.GradientDrawable ring=Ui.bg(Color.TRANSPARENT,17,context);ring.setStroke(dp(2),color==Color.WHITE?Ui.MUTED:color);View r=new View(context);r.setBackground(ring);cell.addView(r,new FrameLayout.LayoutParams(dp(34),dp(34),Gravity.CENTER));}
            android.graphics.drawable.GradientDrawable dot=Ui.bg(color,13,context);if(on)dot.setStroke(dp(2),Ui.SURFACE);else if(color==Color.WHITE||color==Color.BLACK&&Ui.dark)dot.setStroke(1,Ui.MUTED);View d=new View(context);d.setBackground(dot);cell.addView(d,new FrameLayout.LayoutParams(dp(26),dp(26),Gravity.CENTER));colors.addView(cell,new LinearLayout.LayoutParams(0,dp(40),1));}
    }
    private void applyImage(ImageEditView canvas){finishInlineText();Bitmap next;try{next=canvas.render();}catch(RuntimeException|OutOfMemoryError e){toast("编辑内存不足，请重试");return;}Bitmap old=image;image=next;originalText=Collections.emptyList();lastOcr=Collections.emptyList();++recognitionGeneration;if(recognizing){recognizing=false;statusText="图片已编辑，可重新识别文字";}edited=true;Prefs.of(context).edit().putInt("last_edit_width",image.getWidth()).putInt("last_edit_height",image.getHeight()).putString("last_edit_marks",canvas.marks()).apply();canvas.dispose();editingCanvas=null;show();old.recycle();}
    private void copyImage(){
        if(saving)return;
        saving=true;
        Bitmap target=image;
        worker.execute(()->{
            android.net.Uri uri=null;
            try{uri=ShareImageProvider.prepare(context,target);}catch(Exception ignored){}
            android.net.Uri copied=uri;
            new Handler(Looper.getMainLooper()).post(()->{
                saving=false;
                if(closed){target.recycle();return;}
                if(copied==null){toast("复制截图失败，请检查剩余空间");return;}
                try{
                    ClipData clip=ClipData.newUri(context.getContentResolver(),"空叶截图",copied);
                    if(Build.VERSION.SDK_INT>=33){PersistableBundle extra=new PersistableBundle();extra.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE,true);clip.getDescription().setExtras(extra);}
                    context.getSystemService(android.content.ClipboardManager.class).setPrimaryClip(clip);
                    toast("截图已复制");close();
                }catch(RuntimeException error){toast("复制截图失败，请重试");}
            });
        });
    }
    private void save(boolean share){
        if(saving)return;
        saving=true;
        Bitmap target=image;
        worker.execute(()->{
            String message;
            android.net.Uri uri=null;
            try{
                uri=share?ShareImageProvider.prepare(context,target):MediaFiles.saveBitmap(context,target,kind+(edited?"Edited":""));
                message=share?null:"图片已保存到相册";
            }catch(Exception e){message="保存失败，请检查剩余空间";}
            String response=message;
            android.net.Uri saved=uri;
            new Handler(Looper.getMainLooper()).post(()->{
                saving=false;
                if(closed){target.recycle();return;}
                if(response!=null)toast(response);
                if(saved==null)return;
                if(share){
                    Intent intent=new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,saved).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.setClipData(ClipData.newRawUri("空叶截图",saved));
                    close();
                    launch(Intent.createChooser(intent,"分享图片").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                }else close();
            });
        });
    }
    void close(){if(closed)return;closed=true;++recognitionGeneration;window.remove();if(editingCanvas!=null){editingCanvas.dispose();editingCanvas=null;}if(!saving)image.recycle();pinChanged.accept(false);finished.run();}
}
