package com.kongye.floatball;

import android.app.Activity;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import java.util.*;

/** A user-initiated handoff of one selected subtitle, with optional processed-text return. */
public final class SubtitleExternalActivity extends Activity {
    private static final int PROCESS = 31, SHARED = 32;
    private String requestId;
    private SubtitleSession.ExternalRequest request;
    private EditText translated;
    private TextView status;
    private boolean submitted, externalOpen;
    private final ArrayList<Intent> handlers = new ArrayList<>();
    private final ArrayList<String> handlerLabels = new ArrayList<>();

    private SubtitleSession session() {
        FloatService service=FloatService.instance;
        return service==null?null:service.subtitleSession();
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);ThemePolicy.apply(this);setTheme(Ui.dark?R.style.AppMotionThemeDark:R.style.AppMotionTheme);
        requestId=getIntent().getStringExtra("request_id");
        SubtitleSession session=session();
        request=session==null?null:session.externalRequest(requestId);
        if(request==null){Feedback.show(this,"这次识别已结束，请返回游戏重新识别");finish();return;}
        externalOpen=saved!=null&&saved.getBoolean("external_open",false);
        render();
        if(saved!=null)translated.setText(saved.getString("translation",""));
    }

    private int dp(float n){return Ui.dp(this,n);}
    private void render() {
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0x66000000);
        LinearLayout card=Ui.column(this);Ui.pad(card,18,16,18,16);card.setBackground(Ui.bg(Ui.SURFACE,28,this));card.setElevation(dp(12));
        card.setClickable(true);root.setOnClickListener(v->finish());
        FrameLayout.LayoutParams at=new FrameLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels-dp(24),dp(420)),dp(640),Gravity.CENTER);
        root.addView(card,at);
        LinearLayout header=Ui.row(this);header.addView(Ui.icon(this,"translate",Ui.GREEN,24));
        TextView title=Ui.text(this,"翻译 App",18,Ui.INK,true);Ui.pad(title,10,0,0,0);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(Ui.tool(this,"close","返回游戏",this::finish),new LinearLayout.LayoutParams(dp(36),dp(36)));card.addView(header);Ui.gap(card,10);
        View headingGap=card.getChildAt(card.getChildCount()-1);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.setFillViewport(false);
        LinearLayout body=Ui.column(this);scroll.addView(body);card.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView explanation=Ui.text(this,"已暂停连续识别。将本次选区原文交给你选择的翻译 App，联网与语言设置由对方应用决定。",12,Ui.MUTED,false);
        explanation.setLineSpacing(dp(3),1);body.addView(explanation);Ui.gap(body,14);
        LinearLayout originalCard=section(body);
        LinearLayout sourceHeading=Ui.row(this);sourceHeading.addView(Ui.text(this,"本次识别原文",12,Ui.MUTED,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView copy=Ui.text(this,"复制",12,Ui.GREEN,true);Ui.pad(copy,10,6,0,6);copy.setContentDescription("复制本次原文");copy.setOnClickListener(v->copyOriginal());sourceHeading.addView(copy);originalCard.addView(sourceHeading);
        TextView original=Ui.text(this,request.frame().text,14,Ui.INK,false);original.setTextIsSelectable(true);original.setContentDescription("本次选区原文");original.setLineSpacing(dp(3),1);originalCard.addView(original);
        Ui.gap(body,12);body.addView(Ui.button(this,"选择翻译 App",true,this::chooseApp),new LinearLayout.LayoutParams(-1,dp(44)));
        Ui.gap(body,12);status=Ui.text(this,"对方若未返回译文，可在翻译 App 中复制，再回到这里粘贴。",12,Ui.MUTED,false);status.setLineSpacing(dp(3),1);status.setContentDescription("外部翻译状态");body.addView(status);Ui.gap(body,12);
        LinearLayout translationCard=section(body);
        LinearLayout targetHeading=Ui.row(this);targetHeading.addView(Ui.text(this,"译文",12,Ui.MUTED,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView paste=Ui.text(this,"粘贴译文",12,Ui.GREEN,true);Ui.pad(paste,8,6,0,6);paste.setContentDescription("粘贴译文");paste.setOnClickListener(v->pasteTranslation());targetHeading.addView(paste);translationCard.addView(targetHeading);
        translated=new EditText(this);translated.setTextSize(14);translated.setTextColor(Ui.INK);translated.setHintTextColor(Ui.MUTED);translated.setBackground(null);translated.setGravity(Gravity.TOP);Ui.pad(translated,0,5,0,5);
        translated.setHint("粘贴或编辑这段对白的译文");translated.setMinLines(3);translated.setFilters(new InputFilter[]{new InputFilter.LengthFilter(SubtitleSession.EXTERNAL_TEXT_LIMIT)});
        translated.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        translated.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        translated.setContentDescription("外部译文输入框");translationCard.addView(translated,new LinearLayout.LayoutParams(-1,-2));Ui.gap(body,8);
        body.addView(Ui.text(this,"确认仍是同一段对白后显示；对白变化时会继续自动翻译。",11,Ui.MUTED,false));
        Ui.gap(card,12);LinearLayout footer=Ui.row(this);
        footer.addView(Ui.button(this,"返回游戏",false,this::finish),new LinearLayout.LayoutParams(0,dp(44),1));
        LinearLayout.LayoutParams applyAt=new LinearLayout.LayoutParams(0,dp(44),1);applyAt.leftMargin=dp(8);
        footer.addView(Ui.button(this,"显示译文",true,this::apply),applyAt);card.addView(footer);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int top,bottom;
            if(android.os.Build.VERSION.SDK_INT>=30){var bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());top=bars.top;bottom=Math.max(bars.bottom,insets.getInsets(WindowInsets.Type.ime()).bottom);}
            else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}
            int height=root.getHeight()>0?root.getHeight():getResources().getDisplayMetrics().heightPixels;
            int windowHeight=android.os.Build.VERSION.SDK_INT>=30?getWindowManager().getCurrentWindowMetrics().getBounds().height():getResources().getDisplayMetrics().heightPixels;
            // A resized window already excludes part or all of the keyboard.
            bottom=Math.max(0,bottom-Math.max(0,windowHeight-height));
            int available=Math.max(dp(120),height-top-bottom-dp(24));
            boolean compact=available<dp(260);
            header.setVisibility(compact?View.GONE:View.VISIBLE);headingGap.setVisibility(compact?View.GONE:View.VISIBLE);
            Ui.pad(card,18,compact?8:16,18,compact?8:16);
            body.measure(View.MeasureSpec.makeMeasureSpec(Math.max(dp(80),at.width-dp(36)),View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            int desired=body.getMeasuredHeight()+dp(compact?72:134);
            at.height=Math.min(desired,Math.min(dp(640),available));at.topMargin=0;at.bottomMargin=0;
            root.setPadding(0,top,0,bottom);
            card.setLayoutParams(at);
            if(translated.hasFocus())translated.post(()->translated.requestRectangleOnScreen(new android.graphics.Rect(0,0,translated.getWidth(),translated.getHeight()),true));
            return insets;
        });
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if(android.os.Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        root.setFocusableInTouchMode(true);setContentView(root);root.requestFocus();root.requestApplyInsets();
    }

    private LinearLayout section(LinearLayout parent) {
        LinearLayout card=Ui.column(this);Ui.pad(card,12,8,12,10);card.setBackground(Ui.bg(Ui.BG,16,this));parent.addView(card,new LinearLayout.LayoutParams(-1,-2));return card;
    }

    private boolean current() {
        SubtitleSession s=session();
        if(s!=null&&s.externalRequest(requestId)!=null)return true;
        Feedback.show(this,"这次识别已结束，请返回游戏重新识别");finish();return false;
    }

    private void copyOriginal() {
        if(!current())return;
        try{getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("字幕原文",request.frame().text));status.setText("原文已复制，可以粘贴到翻译 App。");}
        catch(RuntimeException unavailable){status.setText("复制失败，请长按原文复制。");}
    }

    private void pasteTranslation() {
        if(!current())return;
        try {
            ClipData clip=getSystemService(ClipboardManager.class).getPrimaryClip();
            CharSequence value=clip==null||clip.getItemCount()==0?null:clip.getItemAt(0).getText();
            if(value==null||value.toString().isBlank()){status.setText("剪贴板里没有文字，请先在翻译 App 复制译文。");return;}
            if(value.length()>SubtitleSession.EXTERNAL_TEXT_LIMIT){status.setText("译文过长，请只复制当前这段对白。");return;}
            translated.setText(value.toString());status.setText("译文已粘贴，确认后点击显示译文。");
        }catch(RuntimeException unavailable){status.setText("无法读取剪贴板，请长按译文输入框粘贴。");}
    }

    private void chooseApp() {
        if(!current())return;
        handlers.clear();handlerLabels.clear();Set<String> seen=new HashSet<>();
        for(String action:new String[]{Intent.ACTION_TRANSLATE,Intent.ACTION_PROCESS_TEXT}) {
            Intent base=new Intent(action).setType("text/plain").putExtra(Intent.EXTRA_TEXT,request.frame().text)
                .putExtra(Intent.EXTRA_PROCESS_TEXT,request.frame().text).putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY,false);
            for(ResolveInfo info:getPackageManager().queryIntentActivities(base,0)) {
                var activity=info.activityInfo;
                if(activity==null||!activity.exported||activity.packageName.equals(getPackageName())||!activity.enabled)continue;
                if(activity.permission!=null&&checkSelfPermission(activity.permission)!=android.content.pm.PackageManager.PERMISSION_GRANTED)continue;
                String label=info.loadLabel(getPackageManager()).toString();
                String name=label.toLowerCase(Locale.ROOT);
                if(action.equals(Intent.ACTION_PROCESS_TEXT)&&!name.contains("translat")&&!name.contains("deepl")&&!name.contains("翻译")&&!name.contains("翻譯"))continue;
                if(!seen.add(activity.packageName))continue;
                handlers.add(new Intent(base).setComponent(new ComponentName(activity.packageName,activity.name)));
                handlerLabels.add(label);
            }
        }
        if(handlers.isEmpty()){share();return;}
        ArrayList<String> labels=new ArrayList<>(handlerLabels);labels.add("通过分享选择其他 App");
        new UiDialog(this).setTitle("选择翻译 App").setItems(labels.toArray(new String[0]),(dialog,which)->{
            if(which==handlers.size())share();else launch(handlers.get(which),PROCESS);
        }).setNegativeButton("取消",null).show();
    }

    private void share() {
        status.setText("在分享面板中选择翻译 App；若未安装，可先复制原文。译文可能需要复制后带回。");
        Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,request.frame().text);
        launch(Intent.createChooser(share,"用翻译 App 重译"),SHARED);
    }

    private void launch(Intent intent,int code) {
        if(!current())return;
        try{externalOpen=true;startActivityForResult(intent,code);}
        catch(RuntimeException unavailable){externalOpen=false;status.setText("这个应用暂时无法打开，可复制原文或选择其他翻译 App。");}
    }

    @Override protected void onActivityResult(int code,int result,Intent data) {
        super.onActivityResult(code,result,data);externalOpen=false;
        if(!current())return;
        CharSequence value=null;
        if(code==PROCESS&&result==RESULT_OK&&data!=null)try {
            value=data.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            if(value==null)value=data.getCharSequenceExtra(Intent.EXTRA_TEXT);
        }catch(RuntimeException invalidResult){value=null;}
        if(value!=null&&!value.toString().isBlank()&&value.length()<=SubtitleSession.EXTERNAL_TEXT_LIMIT&&!value.toString().equals(request.frame().text)) {
            translated.setText(value.toString());status.setText("已收到译文，确认后点击显示译文。");
        } else status.setText("对方未返回译文。在翻译 App 中复制译文后，可回到这里粘贴。");
    }

    private void apply() {
        if(!current())return;
        String text=translated.getText().toString().strip();
        if(text.isEmpty()){translated.setError("请先填入译文");return;}
        if(text.equals(request.frame().text)){translated.setError("这里还是原文，请粘贴翻译后的文字");return;}
        if(!session().submitExternal(requestId,text)){status.setText("这次识别已结束，请返回游戏重新识别。");return;}
        submitted=true;finish();
    }

    @Override protected void onResume(){super.onResume();if(request!=null&&!externalOpen)current();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean("external_open",externalOpen);if(translated!=null)state.putString("translation",translated.getText().toString());}
    @Override public void finish(){if(!submitted){SubtitleSession s=session();if(s!=null)s.cancelExternal(requestId);}super.finish();}
}
