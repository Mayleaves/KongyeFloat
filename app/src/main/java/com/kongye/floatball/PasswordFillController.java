package com.kongye.floatball;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.*;
import android.text.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Short-lived, explicitly requested fill (需求文档 v2 第 9.3 节). Only masked metadata lives in the chooser.
 * 选择框贴着当前输入框弹出（下面放不下就放上面）：
 * - 登录页：匹配的账号（首字头像 + 打码账号 +「名称 · ********」），最后一行「更多密码」；
 * - 注册 / 改密码页：「生成强密码 · 要求摘要」填进新密码框和确认框；「保存这一页到空叶文档」；
 * - 签名和上次不同的条目：第一次点只提醒，再点一次才填。
 */
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
    private boolean shouldBind,confirmCert,unlockAttempted;
    private Set<String> features=Collections.emptySet();
    private FrameLayout panel;
    private LinearLayout card,rows;
    private TextView status;
    private Runnable searchTask;
    /** 这次打开时生成好的密码（注册 / 改密码页），点「生成强密码」才填 */
    private char[] offer=new char[0];
    private String offerSummary="";
    /** 签名不同的条目：第一次点记下，10 秒内再点才填 */
    private String armed="";
    private long armedUntil;
    /** 刚填进注册页的密码：同一个 App / 网站 10 分钟内「保存这一页」用；只在内存 */
    private static char[] filledPassword=new char[0];
    private static String filledScope="";
    private static long filledAt;
    private final Runnable timeout=()->finish("密码填充已超时，请重新点击");

    PasswordFillController(Context c){context=c;client=new VaultClient(c);windows=OverlayWindows.get(c);}

    void start(){
        cancel();
        AssistService service=AssistService.instance;
        if(service==null){Feedback.show(context,"请先开启空叶无障碍服务");return;}
        PasswordTarget target=PasswordTarget.inspect(service);
        if(target==null){Feedback.show(context,"这里填不了，请打开登录或注册页面；网页请显示地址栏");return;}
        LinkedHashMap<String,String> accounts;
        try{destination=target.snapshot;accounts=target.accountValues();}finally{target.close();}
        active=true;expires=SystemClock.elapsedRealtime()+90000;provider=client.providerPackage();
        main.postDelayed(timeout,90000);
        call("status",null,result->{
            if(!"ok".equals(result.getString("status"))){finish(VaultClient.message(result.getString("status")));return;}
            ArrayList<String> list=result.getStringArrayList("features");
            features=list==null?Collections.emptySet():new HashSet<>(list);
            if(destination.kind()==PageRules.Kind.LOGIN){call("match",matchArgs(),reply->showPanel(reply));return;}
            // 注册 / 改密码：先按页面要求生成好，候选上显示要求摘要
            Bundle gen=new Bundle();
            if(features.contains("generate.rules")){
                if(!destination.ruleText().isEmpty())gen.putString("ruleText",destination.ruleText());
                ArrayList<String> avoid=new ArrayList<>();for(String v:accounts.values())if(v.length()>=3)avoid.add(v);
                if(!avoid.isEmpty())gen.putStringArrayList("avoid",avoid);
            }
            call("generate",gen,g->{
                if("ok".equals(g.getString("status"))){
                    String pw=g.getString("password","");wipeOffer();offer=pw.toCharArray();
                    offerSummary=g.getString("summary","");
                }
                g.remove("password");
                if(destination.kind()==PageRules.Kind.CHANGE&&destination.password()!=null)call("match",matchArgs(),this::showPanel);
                else showPanel(null);
            });
        });
    }
    private Bundle targetArgs(){Bundle args=new Bundle();if(destination.domain().isEmpty())args.putString("package",destination.pkg());else args.putString("domain",destination.domain());return args;}
    private Bundle matchArgs(){Bundle args=targetArgs();if(features.contains("match.cert"))args.putStringArrayList("features",new ArrayList<>(List.of("match.cert")));return args;}
    private String scope(){return destination.pkg()+"|"+destination.domain();}
    private void call(String method,Bundle args,Consumer<Bundle> callback){
        int ticket=generation;
        try{worker.execute(()->{
            Bundle reply=client.call(method,args);
            if(args!=null)args.clear();
            main.post(()->{try{if(valid(ticket))callback.accept(reply);}finally{reply.clear();}});
        });}catch(RejectedExecutionException ignored){finish("密码填充已关闭");}
    }
    private boolean valid(int ticket){return active&&ticket==generation&&SystemClock.elapsedRealtime()<expires&&provider.equals(client.providerPackage());}

    // ---------- 选择框 ----------

    private void showPanel(Bundle matches){
        if(matches!=null&&!"ok".equals(matches.getString("status"))){finish(VaultClient.message(matches.getString("status")));return;}
        createPanel();
        if(panel==null)return;
        boolean register=destination.kind()!=PageRules.Kind.LOGIN&&destination.newPassword()!=null;
        if(register){
            if(offer.length>0)rows.addView(row("+","生成强密码",offerSummary.isEmpty()?"填进新密码框和确认框":offerSummary+" · 填进新密码框和确认框",false,this::fillGenerated));
            boolean ready=filledScope.equals(scope())&&filledPassword.length>0&&SystemClock.elapsedRealtime()-filledAt<600000;
            rows.addView(row("↓","保存这一页到空叶文档",ready?"账号、手机号、邮箱和刚生成的密码":"先生成密码；自己输入的密码请用空叶键盘保存",false,this::savePage));
        }
        if(matches!=null)renderEntries(matches,false);
        rows.addView(row("⋯","更多密码","搜索空叶文档的密码空间",false,this::enterSearch));
        status.setText(register?"生成的密码只填进新密码框和确认框，不会自动提交":"只会填入所选账号，不会自动提交");
        place();
    }

    private void createPanel(){
        ThemePolicy.apply(context);
        panel=new FrameLayout(context){
            @Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getKeyCode()==KeyEvent.KEYCODE_BACK){if(e.getAction()==KeyEvent.ACTION_UP)cancel();return true;}return super.dispatchKeyEvent(e);}
        };
        panel.setBackgroundColor(Ui.dark?0x33000000:0x14000000);panel.setOnClickListener(v->cancel());
        card=Ui.column(context);Ui.pad(card,8,8,8,8);card.setBackground(Ui.bg(Ui.SURFACE,16,context));card.setElevation(dp(10));card.setOnClickListener(v->{});
        LinearLayout head=Ui.row(context);
        String targetLabel=destination.domain().isEmpty()?AppCatalog.label(context,destination.pkg()):destination.domain();
        TextView title=Ui.text(context,"空叶密码空间 · "+targetLabel,12,Ui.MUTED,false);title.setSingleLine();title.setEllipsize(TextUtils.TruncateAt.END);Ui.pad(title,8,0,0,0);
        head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        head.addView(Ui.tool(context,"close","取消密码填充",this::cancel),new LinearLayout.LayoutParams(dp(32),dp(32)));
        card.addView(head);
        rows=Ui.column(context);
        ScrollView scroller=new ScrollView(context);scroller.addView(rows);
        card.addView(scroller,new LinearLayout.LayoutParams(-1,-2));
        status=Ui.text(context,"",11,Ui.MUTED,false);Ui.pad(status,8,4,8,2);card.addView(status);
        panel.addView(card,new FrameLayout.LayoutParams(dp(300),-2));
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_SECURE,PixelFormat.TRANSLUCENT);
        p.setTitle("空叶密码填充");p.gravity=Gravity.TOP|Gravity.LEFT;
        if(Build.VERSION.SDK_INT>=30){p.setFitInsetsTypes(0);p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;}
        try{windows.addView(panel,p);}catch(RuntimeException e){panel=null;finish("无法打开密码选择窗口");}
    }

    /** 贴着输入框：宽度跟输入框（至少 280dp），下面放不下就放到上面 */
    private void place(){
        if(panel==null||card==null)return;
        DisplayMetrics m=context.getResources().getDisplayMetrics();
        int sw=m.widthPixels,sh=m.heightPixels;
        Rect b=destination.anchor()==null?new Rect(dp(8),sh/3,sw-dp(8),sh/3):destination.anchor().bounds();
        int w=Math.min(sw-dp(16),Math.max(dp(280),b.width()));
        // 太多行时限制高度，列表可以滚动
        View scroller=card.getChildAt(1);
        scroller.getLayoutParams().height=rows.getChildCount()>5?dp(320):-2;
        card.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(sh,View.MeasureSpec.AT_MOST));
        int h=card.getMeasuredHeight();
        int left=Math.max(dp(8),Math.min(b.left,sw-w-dp(8)));
        int top=b.bottom+dp(6);
        if(top+h>sh-dp(24))top=Math.max(dp(28),b.top-dp(6)-h);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(w,-2);lp.leftMargin=left;lp.topMargin=top;
        card.setLayoutParams(lp);
    }

    /** 一行：首字头像、两行文字 */
    private LinearLayout row(String avatar,String line1,String line2,boolean warn,Runnable click){
        LinearLayout row=Ui.row(context);Ui.pad(row,10,8,10,8);row.setBackground(Ui.ripple(context,0,12));row.setMinimumHeight(dp(56));
        TextView a=Ui.text(context,avatar,15,Ui.GREEN,true);a.setGravity(Gravity.CENTER);a.setBackground(Ui.bg(Ui.PALE,18,context));
        row.addView(a,new LinearLayout.LayoutParams(dp(36),dp(36)));
        LinearLayout texts=Ui.column(context);Ui.pad(texts,12,0,0,0);
        TextView t1=Ui.text(context,line1,15,Ui.INK,true);t1.setSingleLine();t1.setEllipsize(TextUtils.TruncateAt.END);texts.addView(t1);
        TextView t2=Ui.text(context,line2,12,warn?0xFFD84A3A:Ui.MUTED,false);t2.setSingleLine();t2.setEllipsize(TextUtils.TruncateAt.END);texts.addView(t2);
        row.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        row.setContentDescription(line1+"，"+line2);
        row.setOnClickListener(v->click.run());
        return row;
    }

    private void renderEntries(Bundle reply,boolean searched){
        ArrayList<Bundle> entries=reply.getParcelableArrayList("entries");
        int count=0;
        if(entries!=null)for(Bundle item:entries){
            String id=item.getString("id","");if(id.isEmpty())continue;
            if(++count>50)break;
            String matchedBy=item.getString("matchedBy","");
            String name=item.getString("name","未命名");
            boolean mismatch="cert_mismatch".equals(matchedBy);
            // Only the provider's masked account is used here. No reveal until a row is tapped.
            String account=item.getString("account","");
            String first=name.trim().isEmpty()?"#":name.trim().substring(0,name.trim().offsetByCodePoints(0,1));
            rows.addView(row(first,account.isEmpty()?name:account,mismatch?name+" · ⚠ 签名和上次不同":name+" · ********",mismatch,()->{
                if(mismatch&&!(armed.equals(id)&&SystemClock.elapsedRealtime()<armedUntil)){
                    armed=id;armedUntil=SystemClock.elapsedRealtime()+10000;
                    status.setText("「"+name+"」关联的 App 签名和现在不同，可能是仿冒 App。确认是正版，再点一次填入");
                    return;
                }
                boolean confirm=mismatch||armed.equals(id)&&SystemClock.elapsedRealtime()<armedUntil;armed="";
                select(id,searched||!"package".equals(matchedBy)||!destination.domain().isEmpty(),confirm);
            }));
        }
        if(searched)status.setText(count==0?"没有找到，请换个关键词":"选择后填入当前页面，不会自动提交");
    }

    /** 「更多密码」：卡片移到上方，显示搜索框 */
    private void enterSearch(){
        if(panel==null)return;
        rows.removeAllViews();
        EditText search=new EditText(context);search.setSingleLine();search.setTextSize(14);search.setTextColor(Ui.INK);search.setHintTextColor(Ui.MUTED);search.setHint("搜索名称、账号、网址");search.setContentDescription("搜索密码空间");
        search.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        search.setSaveEnabled(false);search.setBackground(Ui.bg(Ui.PALE,12,context));Ui.pad(search,12,8,12,8);
        card.addView(search,1,new LinearLayout.LayoutParams(-1,dp(42)));
        status.setText("输入关键词搜索");
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                if(searchTask!=null)main.removeCallbacks(searchTask);
                int request=++searchGeneration;String query=s.toString().trim();
                rows.removeAllViews();status.setText(query.isEmpty()?"输入关键词搜索":"搜索中…");
                if(query.isEmpty())return;
                searchTask=()->{Bundle args=new Bundle();args.putString("query",query);
                    call("search",args,reply->{if(panel!=null&&request==searchGeneration){if(!"ok".equals(reply.getString("status"))){finish(VaultClient.message(reply.getString("status")));return;}renderEntries(reply,true);}});};
                main.postDelayed(searchTask,260);
            }
            public void afterTextChanged(Editable e){}
        });
        // 卡片放到屏幕上方，键盘弹出也不挡住
        DisplayMetrics m=context.getResources().getDisplayMetrics();
        int w=Math.min(m.widthPixels-dp(16),dp(400));
        card.getChildAt(2).getLayoutParams().height=dp(260);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(w,-2);lp.leftMargin=(m.widthPixels-w)/2;lp.topMargin=dp(56);card.setLayoutParams(lp);
        WindowManager.LayoutParams p=(WindowManager.LayoutParams)panel.getLayoutParams();
        p.flags&=~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;p.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        windows.updateViewLayout(panel,p);
        search.requestFocus();
    }

    // ---------- 填充 ----------

    private void select(String id,boolean bind,boolean confirm){
        entryId=id;shouldBind=bind;confirmCert=confirm;removePanel();searchGeneration++;
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
        Bundle args=targetArgs();args.putString("id",entryId);if(confirmCert)args.putBoolean("confirmCert",true);
        call("reveal",args,reply->{
            String state=reply.getString("status","");
            if("locked".equals(state)){unlock(reply);return;}
            if("cert_mismatch".equals(state)){
                armed=entryId;armedUntil=SystemClock.elapsedRealtime()+15000;
                finish("这个 App 的签名和上次关联时不同，可能是仿冒 App；确认是正版，请再点悬浮球选同一条");return;
            }
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

    // ---------- 注册 / 改密码 ----------

    private void fillGenerated(){
        if(offer.length==0){finish("暂时无法生成密码");return;}
        char[] pw=offer.clone();String summary=offerSummary;
        removePanel();
        awaitTarget(0,()->{
            AssistService service=AssistService.instance;
            PasswordTarget current=service==null?null:PasswordTarget.inspect(service);
            boolean ok=false;
            try{ok=current!=null&&PasswordTarget.same(destination,current.snapshot)&&current.fillNew(new String(pw));}
            catch(RuntimeException ignored){}finally{if(current!=null)current.close();}
            if(!ok){Arrays.fill(pw,'\0');finish("这里填不了");return;}
            Arrays.fill(filledPassword,'\0');filledPassword=pw;filledScope=scope();filledAt=SystemClock.elapsedRealtime();
            finish("已填入强密码"+(summary.isEmpty()?"":"（"+summary+"）")+"。填好账号等信息后，再点悬浮球「保存这一页」");
        });
    }

    private void savePage(){
        boolean ready=filledScope.equals(scope())&&filledPassword.length>0&&SystemClock.elapsedRealtime()-filledAt<600000;
        if(!ready){finish("悬浮球读不到你自己输入的密码（屏幕上是圆点）。请用空叶键盘输入，它会提示保存；或稍后在空叶文档里补填");return;}
        removePanel();
        awaitTarget(0,()->{
            AssistService service=AssistService.instance;
            PasswordTarget current=service==null?null:PasswordTarget.inspect(service);
            LinkedHashMap<String,String> values;
            try{
                if(current==null||!PasswordTarget.same(destination,current.snapshot)){finish("页面或输入框已改变，请重新点击");return;}
                values=current.accountValues();
            }finally{if(current!=null)current.close();}
            Bundle args=targetArgs();
            args.putString("name",destination.domain().isEmpty()?AppCatalog.label(context,destination.pkg()):destination.domain());
            String account="";
            for(String key:new String[]{"username","phone","email"})if(account.isEmpty()&&values.containsKey(key))account=values.get(key);
            args.putString("account",account);
            args.putString("password",new String(filledPassword));
            args.putString("kind",destination.kind()==PageRules.Kind.CHANGE?"change":"register");
            ArrayList<Bundle> fields=new ArrayList<>();
            for(Map.Entry<String,String> e:values.entrySet()){Bundle f=new Bundle();f.putString("type",e.getKey());f.putString("value",e.getValue());fields.add(f);}
            args.putParcelableArrayList("fields",fields);
            call("save",args,result->{
                boolean ok="ok".equals(result.getString("status"));
                if(ok){Arrays.fill(filledPassword,'\0');filledPassword=new char[0];filledScope="";}
                finish(ok?("updated".equals(result.getString("result"))?"已更新空叶文档里的密码，旧密码保留在条目里":"已存到空叶文档"):VaultClient.message(result.getString("status","")));
            });
        });
    }

    private void wipeOffer(){Arrays.fill(offer,'\0');offer=new char[0];offerSummary="";}
    private void removePanel(){
        if(panel==null){return;}
        context.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(panel.getWindowToken(),0);
        try{windows.removeViewImmediate(panel);}catch(IllegalArgumentException ignored){}
        panel=null;card=null;rows=null;status=null;
        if(searchTask!=null)main.removeCallbacks(searchTask);searchTask=null;
    }
    private int dp(int value){return Ui.dp(context,value);}
    private void finish(String message){cancel();Feedback.show(context,message);}
    void cancel(){active=false;generation++;searchGeneration++;main.removeCallbacksAndMessages(null);removePanel();destination=null;entryId=null;provider=null;unlockAttempted=false;confirmCert=false;wipeOffer();}
    void destroy(){cancel();worker.shutdownNow();Arrays.fill(filledPassword,'\0');filledPassword=new char[0];filledScope="";}
}
