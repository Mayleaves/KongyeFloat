package com.kongye.floatball;
import android.app.Activity;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;
/** Status-only setup. Connecting the vault never implies a login field was found. */
public final class PasswordHelpActivity extends Activity {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 private TextView connection,check;private LinearLayout connectionBadge;
 private boolean checking;private int connectionState=-1,generation;private Future<?> request;
 private String connectionText="检查是否已允许读取密码空间";
 private java.util.function.Supplier<Bundle> connectionSource=()->new VaultClient(this).call("status",null);
 static Intent intent(Context c,PasswordTarget.Problem reason){return new Intent(c,PasswordHelpActivity.class).putExtra("reason",reason.name());}
 private PasswordTarget.Problem reason(){try{return PasswordTarget.Problem.valueOf(getIntent().getStringExtra("reason"));}catch(Exception ignored){return PasswordTarget.Problem.NONE;}}
 private int dp(float n){return Ui.dp(this,n);}
 @Override public void onCreate(Bundle state){super.onCreate(state);if(state!=null){connectionState=state.getInt("connectionState",-1);connectionText=state.getString("connection",connectionText);}}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);render();}
 @Override protected void onResume(){super.onResume();AccessibilityAccess.returnedTo(this);render();}
 @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("connectionState",connectionState);out.putString("connection",connectionText);}
    static String explanation(PasswordTarget.Problem reason){return switch(reason){
        case ACCESSIBILITY->"空叶无障碍服务尚未连接。请先开启服务，再返回登录页面使用悬浮球。";
        case LOCKED->"手机已锁定。解锁后打开登录页面，再点击悬浮球中的“填充密码”。";
        case OWN_APP->"你现在位于空叶悬浮球界面。请先打开需要登录的软件或网站，再通过悬浮球点击“填充密码”。";
        case VAULT_APP->"空叶文档用于保存密码。请先切换到需要登录的软件或网站，再通过悬浮球填充。";
        case SYSTEM_PAGE->"当前是系统界面。关闭通知栏或系统弹窗，回到登录页面再试。";
        case NO_FIELDS->"当前页面没有找到可填写的输入框。请先打开账号密码登录页面，并点一下账号框或密码框；扫码、验证码或一键登录页面可能没有密码框。";
        case NO_LOGIN_FIELDS->"找到了输入框，但没有认出账号或密码框。请切换到“账号密码登录”，点一下输入框后再试。";
        case ADDRESS,BROWSER_AUTOFILL->"当前无法确认网页地址，请使用空叶文档的系统自动填充。点一下页面输入框，再选择密码建议填充账号密码。\n\n如未出现密码建议，请在系统设置中将自动填充服务设为“空叶文档”。";
        case UNSUPPORTED->"这个页面没有提供可直接填写的输入框。自绘或安全输入框可能不支持悬浮球填充，可尝试空叶键盘或软件提供的其他登录方式。";
        case COMPLEX->"当前页面内容较多，暂时无法安全识别。请关闭多余弹窗，回到简单的登录页面再试。";
        case WINDOW,CHANGED->"页面还没准备好，或输入框已经改变。请等登录页面加载完成，点一下账号或密码框，再重新打开悬浮球填充。";
        default->"先打开需要登录的软件或网站，再通过悬浮球选择账号。密码空间连接与当前页面能否填入是两项不同的检查。";
    };}

 private LinearLayout card(LinearLayout body,String title){LinearLayout box=Ui.card(this);Ui.pad(box,18,16,18,16);box.addView(Ui.text(this,title,15,Ui.INK,true));Ui.gap(box,12);body.addView(box,new LinearLayout.LayoutParams(-1,-2));Ui.gap(body,12);return box;}
 private void paragraph(LinearLayout box,String text){TextView t=Ui.text(this,text,13,Ui.MUTED,false);t.setLineSpacing(dp(3),1);box.addView(t);}
 private void readyRow(LinearLayout box,String title,boolean on,Runnable action){LinearLayout row=Ui.row(this);row.setMinimumHeight(dp(46));row.addView(Ui.icon(this,on?"check":"forward",on?Ui.GREEN:Ui.MUTED,18));TextView name=Ui.text(this,title,14,Ui.INK,false);Ui.pad(name,10,0,0,0);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));TextView value=Ui.text(this,on?("无障碍服务".equals(title)?"已连接":"已开启"):"无障碍服务".equals(title)&&AccessibilityAccess.state(this)==AccessibilityAccess.State.WAITING?"待连接":"去开启",12,on?Ui.MUTED:Ui.GREEN,true);row.addView(value);row.setBackground(Ui.ripple(this,0,10));row.setOnClickListener(v->action.run());box.addView(row);}
 private void render(){
  ThemePolicy.apply(this);setTheme(Ui.dark?R.style.AppThemeDark:R.style.AppTheme);getWindow().setStatusBarColor(Ui.BG);getWindow().setNavigationBarColor(Ui.BG);getWindow().getDecorView().setSystemUiVisibility(Ui.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  LinearLayout root=Ui.column(this);FrameLayout page=Ui.page(this,root);setContentView(page);page.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
  LinearLayout head=Ui.row(this);Ui.pad(head,12,12,20,8);head.addView(Ui.tool(this,"back","返回原页面",this::finish),new LinearLayout.LayoutParams(dp(44),dp(44)));TextView title=Ui.text(this,"密码填充",22,Ui.INK,true);Ui.pad(title,8,0,0,0);head.addView(title);root.addView(head);
  ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);LinearLayout body=Ui.column(this);Ui.pad(body,16,12,16,24);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  if(getIntent().hasExtra("connection_hint")||reason()!=PasswordTarget.Problem.NONE&&reason()!=PasswordTarget.Problem.OWN_APP){LinearLayout why=card(body,"当前页面");paragraph(why,getIntent().hasExtra("connection_hint")?getIntent().getStringExtra("connection_hint"):explanation(reason()));}
  LinearLayout ready=card(body,"准备情况");readyRow(ready,"悬浮窗",Settings.canDrawOverlays(this),()->settings(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+getPackageName()))));readyRow(ready,"无障碍服务",AssistService.instance!=null,()->AccessibilityAccess.openSettings(this));
  Ui.gap(ready,10);connectionBadge=Ui.column(this);Ui.pad(connectionBadge,14,12,14,12);ready.addView(connectionBadge);connection=Ui.text(this,connectionText,13,Ui.INK,false);connection.setContentDescription("密码空间连接状态");connection.setLineSpacing(dp(3),1);connection.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);connectionBadge.addView(connection);Ui.gap(ready,12);check=Ui.button(this,"检查密码空间连接",true,this::checkConnection);ready.addView(check,new LinearLayout.LayoutParams(-1,-2));refreshConnection();Ui.gap(ready,12);paragraph(ready,"连接成功仅表示可以访问密码库；填写时还需在目标软件的登录页找到输入框。");
  LinearLayout how=card(body,"如何填入");step(how,"1","打开登录页","切换到需要登录的软件或网站，选择账号密码登录并点一下输入框。");step(how,"2","通过悬浮球选择账号","点击小叶 → 填充密码 → 选择账号；没有匹配时点更多密码搜索。");step(how,"3","验证并确认登录","需要时解锁密码空间，填入后由你确认登录。");
  TextView setup=Ui.button(this,"密码从哪里来？",false,()->{new UiDialog(this).setTitle("准备账号密码").setMessage("在空叶文档的密码空间保存账号密码，并在密码填充设置里允许空叶键盘 / 悬浮球读取。网页请保持浏览器地址栏可见；扫码、验证码及部分安全输入框不支持直接填入。").setPositiveButton("打开空叶文档",(d,w)->openDocs()).setNegativeButton("关闭",null).show();});body.addView(setup,new LinearLayout.LayoutParams(-1,-2));Ui.gap(body,10);body.addView(Ui.button(this,"返回登录页",true,this::finish),new LinearLayout.LayoutParams(-1,-2));page.requestApplyInsets();
 }
 private void step(LinearLayout box,String number,String title,String detail){LinearLayout row=Ui.row(this);row.setGravity(Gravity.TOP);TextView index=Ui.centeredText(this,number,12,Ui.GREEN,true);index.setBackground(Ui.bg(Ui.PALE,12,this));row.addView(index,new LinearLayout.LayoutParams(dp(24),dp(24)));LinearLayout words=Ui.column(this);Ui.pad(words,10,1,0,12);words.addView(Ui.text(this,title,14,Ui.INK,true));Ui.gap(words,5);paragraph(words,detail);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));box.addView(row);}
 private void settings(Intent i){try{startActivity(i);}catch(ActivityNotFoundException error){Feedback.show(this,"请在系统设置中开启对应权限");}}
 private void openDocs(){Intent i=getPackageManager().getLaunchIntentForPackage("com.kongye.docs");if(i==null)Feedback.show(this,"请先安装支持密码空间的空叶文档");else startActivity(i);}
 private void refreshConnection(){if(connection==null)return;connection.setText(checking?"正在连接密码空间…":connectionText);connectionBadge.setBackground(Ui.bg(connectionState==1?Ui.PALE:Ui.BG,14,this));connection.setTextColor(connectionState==1?Ui.GREEN:Ui.INK);check.setText(checking?"正在检查…":connectionState==1?"✓ 已连接 · 重新检查":connectionState==0?"未连接 · 重试":"检查密码空间连接");check.setEnabled(!checking);}
 private void checkConnection(){
  if(checking)return;checking=true;refreshConnection();int token=++generation;
  main.postDelayed(()->{if(token!=generation||!checking||isFinishing()||isDestroyed())return;generation++;if(request!=null)request.cancel(true);checking=false;connectionState=0;connectionText="连接超时，请重试";refreshConnection();Feedback.show(this,"密码空间连接超时");},8000);
  request=worker.submit(()->{Bundle reply;try{reply=connectionSource.get();}catch(RuntimeException error){reply=VaultClient.result("error");}boolean ok="ok".equals(reply.getString("status"));String text=ok?"✓ 密码空间已连接"+(reply.getBoolean("unlocked")?" · 已解锁":" · 选择账号后按需解锁"):"未连接\n"+VaultClient.message(reply.getString("status",""));reply.clear();main.post(()->{if(token!=generation||isFinishing()||isDestroyed())return;checking=false;connectionState=ok?1:0;connectionText=text;refreshConnection();if(ok)Feedback.success(this,"密码空间已连接");else Feedback.show(this,"未连接，请查看准备情况中的原因");});});
 }
 @Override protected void onDestroy(){generation++;if(request!=null)request.cancel(true);worker.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
