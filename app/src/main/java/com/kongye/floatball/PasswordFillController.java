package com.kongye.floatball;

import android.accessibilityservice.AccessibilityService;
import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Short-lived, explicitly requested fill. Only masked metadata lives in the chooser. */
final class PasswordFillController {
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Kongye-Vault");t.setDaemon(true);return t;});
    private final VaultClient client;
    private final WindowManager windows;
    private int generation,searchGeneration;
    private boolean active;
    private long expires;
    private PasswordTarget.Snapshot destination;
    private String entryId,provider;
    private boolean shouldBind,unlockAttempted;
    private FrameLayout panel;
    private LinearLayout rows;
    private TextView status;
    private Runnable searchTask;
    private final Runnable timeout=()->finish("密码填充已超时，请重新点击");

    PasswordFillController(Context c){context=c;client=new VaultClient(c);windows=OverlayWindows.get(c);}
    void start(){
        cancel();
        AssistService service=AssistService.instance;
        if(service==null){Feedback.show(context,"请先开启空叶无障碍服务");return;}
        PasswordTarget target=PasswordTarget.inspect(service);
        if(target==null){Feedback.show(context,"这里填不了，请打开登录页面；网页请显示地址栏");return;}
        try{destination=target.snapshot;}finally{target.close();}
        active=true;expires=SystemClock.elapsedRealtime()+90000;provider=client.providerPackage();
        main.postDelayed(timeout,90000);
        call("status",null,result->{
            if(!"ok".equals(result.getString("status"))){finish(VaultClient.message(result.getString("status")));return;}
            Bundle args=targetArgs();
            call("match",args,this::showMatches);
        });
    }
    private Bundle targetArgs(){Bundle args=new Bundle();if(destination.domain().isEmpty())args.putString("package",destination.pkg());else args.putString("domain",destination.domain());return args;}
    private void call(String method,Bundle args,Consumer<Bundle> callback){
        int ticket=generation;
        try{worker.execute(()->{
            Bundle reply=client.call(method,args);
            if(args!=null)args.clear();
            main.post(()->{try{if(valid(ticket))callback.accept(reply);}finally{reply.clear();}});
        });}catch(RejectedExecutionException ignored){finish("密码填充已关闭");}
    }
    private boolean valid(int ticket){return active&&ticket==generation&&SystemClock.elapsedRealtime()<expires&&provider.equals(client.providerPackage());}
    private void showMatches(Bundle reply){
        if(!"ok".equals(reply.getString("status"))){finish(VaultClient.message(reply.getString("status")));return;}
        createPanel();if(panel!=null)renderEntries(reply,false);
    }
    private void createPanel(){
        ThemePolicy.apply(context);
        panel=new FrameLayout(context){
            @Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getKeyCode()==KeyEvent.KEYCODE_BACK){if(e.getAction()==KeyEvent.ACTION_UP)cancel();return true;}return super.dispatchKeyEvent(e);}
        };
        panel.setBackgroundColor(Ui.dark?0x77000000:0x44304134);panel.setOnClickListener(v->cancel());
        LinearLayout card=Ui.card(context);Ui.pad(card,20,20,20,20);card.setOnClickListener(v->{});Ui.softShadow(card);
        LinearLayout head=Ui.row(context);
        head.addView(Ui.text(context,"填充密码",19,Ui.INK,true),new LinearLayout.LayoutParams(0,-2,1));
        head.addView(Ui.tool(context,"close","取消密码填充",this::cancel),new LinearLayout.LayoutParams(dp(36),dp(36)));card.addView(head);
        Ui.gap(card,8);
        String targetLabel=destination.domain().isEmpty()?AppCatalog.label(context,destination.pkg()):destination.domain();
        card.addView(Ui.text(context,targetLabel,12,Ui.MUTED,false));
        Ui.gap(card,12);
        EditText search=new EditText(context);search.setSingleLine();search.setTextSize(14);search.setTextColor(Ui.INK);search.setHintTextColor(Ui.MUTED);search.setHint("搜索密码空间");search.setContentDescription("搜索密码空间");
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        search.setSaveEnabled(false);search.setBackground(Ui.bg(Ui.PALE,14,context));Ui.pad(search,12,10,12,10);
        search.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)enableKeyboard();return false;});
        card.addView(search,new LinearLayout.LayoutParams(-1,dp(44)));
        Ui.gap(card,8);status=Ui.text(context,"只会填入所选账号，不会自动提交",12,Ui.MUTED,false);card.addView(status);
        rows=Ui.column(context);ScrollView scroller=new ScrollView(context);scroller.addView(rows);
        int height=context.getResources().getDisplayMetrics().heightPixels;card.addView(scroller,new LinearLayout.LayoutParams(-1,Math.max(dp(100),Math.min(dp(260),height/3))));
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                if(searchTask!=null)main.removeCallbacks(searchTask);
                int request=++searchGeneration;String query=s.toString().trim();
                rows.removeAllViews();status.setText("搜索中…");
                searchTask=()->{Bundle args=query.isEmpty()?targetArgs():new Bundle();if(!query.isEmpty())args.putString("query",query);
                    call(query.isEmpty()?"match":"search",args,reply->{if(panel!=null&&request==searchGeneration)renderEntries(reply,!query.isEmpty());});};
                main.postDelayed(searchTask,260);
            }
            public void afterTextChanged(Editable e){}
        });
        int width=Math.min(context.getResources().getDisplayMetrics().widthPixels-dp(28),dp(400));
        panel.addView(card,new FrameLayout.LayoutParams(width,-2,Gravity.CENTER));
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_SECURE,PixelFormat.TRANSLUCENT);
        p.setTitle("空叶密码填充");p.gravity=Gravity.TOP|Gravity.LEFT;
        if(Build.VERSION.SDK_INT>=30){p.setFitInsetsTypes(0);p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;}
        try{windows.addView(panel,p);}catch(RuntimeException e){finish("无法打开密码选择窗口");}
    }
    private void enableKeyboard(){
        if(panel==null){return;}
        WindowManager.LayoutParams p=(WindowManager.LayoutParams)panel.getLayoutParams();
        p.flags&=~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;p.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        windows.updateViewLayout(panel,p);
    }
    private void renderEntries(Bundle reply,boolean searched){
        rows.removeAllViews();
        if(!"ok".equals(reply.getString("status"))){finish(VaultClient.message(reply.getString("status")));return;}
        ArrayList<Bundle> entries=reply.getParcelableArrayList("entries");
        int count=0;
        if(entries!=null)for(Bundle item:entries){
            String id=item.getString("id","");if(id.isEmpty())continue;
            if(++count>50)break;
            String matchedBy=item.getString("matchedBy","");
            LinearLayout row=Ui.column(context);Ui.pad(row,12,12,12,12);row.setBackground(Ui.ripple(context,Ui.SURFACE,14));
            row.addView(Ui.text(context,item.getString("name","未命名"),15,Ui.INK,true));Ui.gap(row,5);
            // Only the provider's masked account is used here. No reveal until a row is tapped.
            row.addView(Ui.text(context,item.getString("account",""),13,Ui.MUTED,false));
            row.setContentDescription("填充 "+item.getString("name","未命名"));
            row.setOnClickListener(v->select(id,searched||!"package".equals(matchedBy)||!destination.domain().isEmpty()));
            rows.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        status.setText(count==0?(searched?"没有找到，请换个关键词":"没有匹配的条目，可以搜索后选择"):"选择后填入当前登录页面，不会自动提交");
    }
    private void select(String id,boolean bind){
        entryId=id;shouldBind=bind;removePanel();searchGeneration++;
        // Reacquire after the chooser/keyboard has gone; never retain AccessibilityNodeInfo across unlock.
        awaitTarget(0,this::reveal);
    }
    private void awaitTarget(int attempt,Runnable next){
        int ticket=generation;
        main.postDelayed(()->{
            if(!valid(ticket))return;
            AssistService service=AssistService.instance;
            PasswordTarget current=service==null?null:PasswordTarget.inspect(service);
            boolean same=false;try{same=current!=null&&PasswordTarget.same(destination,current.snapshot);}finally{if(current!=null)current.close();}
            if(same)next.run();else if(attempt<8)awaitTarget(attempt+1,next);else finish("页面或输入框已改变，请重新点击填充密码");
        },attempt==0?220:140);
    }
    private void reveal(){
        Bundle args=targetArgs();args.putString("id",entryId);
        call("reveal",args,reply->{
            String state=reply.getString("status","");
            if("locked".equals(state)){unlock(reply);return;}
            if(!"ok".equals(state)){finish(VaultClient.message(state));return;}
            AssistService service=AssistService.instance;
            PasswordTarget current=service==null?null:PasswordTarget.inspect(service);
            boolean filled=false;
            try{
                if(current==null||!PasswordTarget.same(destination,current.snapshot)){finish("页面或输入框已改变，请重新点击填充密码");return;}
                // Transient IPC values: never saved, logged, or sent to the clipboard.
                filled=current.fill(reply.getString("account",""),reply.getString("password",""));
            }catch(RuntimeException ignored){}finally{if(current!=null)current.close();reply.remove("account");reply.remove("password");}
            if(!filled){finish("这里填不了");return;}
            if(shouldBind){Bundle bind=targetArgs();bind.putString("id",entryId);call("bind",bind,result->finish("ok".equals(result.getString("status"))?"账号密码已填入":"账号密码已填入，应用关联未保存"));}
            else finish("账号密码已填入");
        });
    }
    private void unlock(Bundle reply){
        if(unlockAttempted){finish("密码空间未解锁，请重新点击填充密码");return;}unlockAttempted=true;
        PendingIntent unlock=reply.getParcelable("unlock");
        if(unlock==null||!provider.equals(unlock.getCreatorPackage())){finish("密码空间解锁入口无效");return;}
        expires=SystemClock.elapsedRealtime()+30000;main.removeCallbacks(timeout);main.postDelayed(timeout,30000);
        try{
            ActivityOptions options=ActivityOptions.makeBasic();
            if(Build.VERSION.SDK_INT>=34)options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            unlock.send(context,0,null,null,null,null,options.toBundle());
            pollUnlock(SystemClock.elapsedRealtime()+700);
        }catch(PendingIntent.CanceledException|RuntimeException e){finish("无法打开密码空间解锁，请重试");}
    }
    private void pollUnlock(long notBefore){
        int ticket=generation;
        main.postDelayed(()->{
            if(!valid(ticket))return;
            AssistService service=AssistService.instance;
            PasswordTarget current=service==null?null:PasswordTarget.inspect(service);
            boolean same=false;try{same=current!=null&&PasswordTarget.same(destination,current.snapshot);}finally{if(current!=null)current.close();}
            if(!same||SystemClock.elapsedRealtime()<notBefore){pollUnlock(notBefore);return;}
            call("status",null,result->{
                if(!"ok".equals(result.getString("status"))){finish(VaultClient.message(result.getString("status")));return;}
                if(!result.getBoolean("unlocked")){finish("已取消密码填充，密码空间未解锁");return;}
                reveal();
            });
        },250);
    }
    private void removePanel(){
        if(panel==null){return;}
        context.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(panel.getWindowToken(),0);
        try{windows.removeViewImmediate(panel);}catch(IllegalArgumentException ignored){}
        panel=null;rows=null;status=null;
        if(searchTask!=null)main.removeCallbacks(searchTask);searchTask=null;
    }
    private int dp(int value){return Ui.dp(context,value);}
    private void finish(String message){cancel();Feedback.show(context,message);}
    void cancel(){active=false;generation++;searchGeneration++;main.removeCallbacksAndMessages(null);removePanel();destination=null;entryId=null;provider=null;unlockAttempted=false;}
    void destroy(){cancel();worker.shutdownNow();}
}
