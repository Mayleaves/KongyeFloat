package com.kongye.floatball;

import android.app.*;
import android.animation.ValueAnimator;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** In-app release check and download. Only Android's permission/installer UI is external. */
public final class UpdateActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
    private UpdatePulseView emblem;private FrameLayout dialogRoot;private GitHubUpdate.Result release;private UpdateDownload transfer;private Future<?> task;private File apk;private String phase="checking",message="正在检查最新版本…";private int generation;private long received;private boolean permissionPage;private TextView progress;private ProgressBar bar;
    private boolean historyOpen, historyReturning, renderDeferred;
    private UpdateHistoryLayer historyLayer;
    static Intent checkedIntent(Context c,GitHubUpdate.Result r,String error){Intent i=new Intent(c,UpdateActivity.class).putExtra("checked",true);if(r==null)return i.putExtra("check_error",error==null?"检查失败，请稍后重试。":error);return i.putExtra("release",r.latest).putExtra("comparison",r.comparison).putExtra("asset_name",r.name).putExtra("asset_url",r.url).putExtra("asset_size",r.size).putExtra("digest",r.digest).putExtra("notes",r.notes);}
    private boolean acceptChecked(){Intent i=getIntent();if(!i.getBooleanExtra("checked",false))return false;if(!i.hasExtra("release")){phase="error";message=i.getStringExtra("check_error");}else{release=new GitHubUpdate.Result(i.getStringExtra("release"),ReleaseVersion.compare(i.getStringExtra("release"),version()),i.getStringExtra("asset_name"),i.getStringExtra("asset_url"),i.getLongExtra("asset_size",0),i.getStringExtra("digest"),i.getStringExtra("notes"));phase=release.comparison>0?"available":"current";message=release.comparison>0?(release.downloadable()?"发现新版本，可直接下载安装。":"该版本暂未提供 APK，请稍后检查。 "):currentMessage();}render();return true;}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);historyOpen=saved!=null&&saved.getBoolean("history_open",false);if(saved!=null){String name=saved.getString("apk","");if(name.matches("[a-f0-9-]{36}\\.apk")){File file=new File(new File(getCacheDir(),"updates"),name);try{UpdateDownload.verify(this,file);apk=file;phase="ready";message="安装包已下载，点击安装更新继续。";release=new GitHubUpdate.Result(saved.getString("latest",""),1,"","",file.length(),"",saved.getString("notes",""));render();return;}catch(IOException ignored){}}}if(!acceptChecked())check();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean("history_open",historyOpen);if(apk!=null){state.putString("apk",apk.getName());state.putString("latest",release==null?"":release.latest);state.putString("notes",release==null?"":release.notes);}}
    private boolean ahead(){return release!=null&&ReleaseVersion.compare(version(),release.latest)>0;}
    private String currentMessage(){return ahead()?"当前安装版本高于公开正式版本。":"已是最新版本。";}
    private String version(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){return "0";}}
    private int dp(float n){return Ui.dp(this,n);}
    @Override protected void onResume(){
        super.onResume();
        boolean returning=historyOpen;
        historyOpen=false;
        if(returning&&historyLayer!=null){
            historyReturning=true;
            if(!ValueAnimator.areAnimatorsEnabled())completeHistoryReturn();
            return;
        }
        resumeContent();
    }
    private void resumeContent(){
        if(permissionPage){permissionPage=false;message=getPackageManager().canRequestPackageInstalls()?"安装权限已允许，点击安装更新继续。":"安装包已下载，可再次点击安装更新。";render();}
        else if(dialogRoot==null||renderDeferred)render();
        else if(emblem!=null)emblem.active(true);
    }
    private void openHistory(){
        if(historyOpen||historyReturning||isFinishing())return;
        historyLayer=UpdateHistoryLayer.show(this,dialogRoot);
        historyOpen=true;
        try{
            startActivity(ReleaseHistoryActivity.fromUpdate(this),ActivityOptions.makeCustomAnimation(
                    this,R.anim.history_page_enter_right,R.anim.history_page_exit_left).toBundle());
        }catch(ActivityNotFoundException error){
            historyOpen=false;
            clearHistoryLayer();
            Feedback.show(this,"暂时无法打开更新历史");
        }
    }
    @Override public void onEnterAnimationComplete(){
        super.onEnterAnimationComplete();
        if(historyReturning)completeHistoryReturn();
    }
    private void completeHistoryReturn(){
        clearHistoryLayer();
        resumeContent();
    }
    private void clearHistoryLayer(){
        historyReturning=false;
        if(historyLayer!=null){historyLayer.release();historyLayer=null;}
    }
    @Override protected void onStop(){
        if(historyReturning)clearHistoryLayer();
        super.onStop();
    }
    private boolean alive(int token){return token==generation&&!isFinishing()&&!isDestroyed();}
    private void stopTask(){generation++;if(transfer!=null){transfer.cancel();transfer=null;}if(task!=null){task.cancel(true);task=null;}}
    private void check(){stopTask();phase="checking";message="正在检查最新版本…";render();int token=generation;task=worker.submit(()->{GitHubUpdate.Result answer=null;String failure=null;try{answer=GitHubUpdate.check(version());}catch(IOException|RuntimeException e){failure=e.getMessage();}GitHubUpdate.Result result=answer;String error=failure;main.post(()->{if(!alive(token))return;task=null;release=result;if(result==null){phase="error";message=error==null?"检查失败，请稍后重试。":error;}else if(result.comparison<=0){phase="current";message=currentMessage();}else{phase="available";message=result.downloadable()?"发现新版本，可直接下载安装。":"该版本暂未提供 APK，请稍后检查。";}render();});});}
    private void download(){if(release==null||!release.downloadable())return;stopTask();phase="downloading";message="正在下载安装包…";received=0;render();int token=generation;UpdateDownload request=new UpdateDownload();transfer=request;GitHubUpdate.Result selected=release;
        task=worker.submit(()->{File result=null;String failure=null;try{result=request.download(this,selected,bytes->main.post(()->{if(alive(token)){received=bytes;refreshProgress();}}));}catch(IOException|RuntimeException e){failure=e.getMessage();}File complete=result;String error=failure;main.post(()->{if(!alive(token))return;transfer=null;task=null;apk=complete;phase=complete==null?"available":"ready";message=complete==null?"下载失败："+(error==null?"请重试":error):"下载完成，安装包校验通过。";render();});});
    }
    private void cancelDownload(){stopTask();phase="available";message="已取消下载，可重新开始。";render();}
    private void install(){if(apk==null||!apk.isFile()){phase="available";message="安装包已失效，请重新下载。";render();return;}
        try{UpdateDownload.verify(this,apk);}catch(IOException e){phase="available";message=e.getMessage();apk=null;render();return;}
        if(!getPackageManager().canRequestPackageInstalls()){
            new UiDialog(this).setTitle("允许安装更新").setMessage("安装包已下载。请在系统设置中允许空叶安装应用，然后返回这里继续安装。")
                .setPositiveButton("前往设置",(d,w)->{try{permissionPage=true;startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}catch(RuntimeException e){permissionPage=false;Feedback.show(this,"无法打开安装权限设置");}}).setNegativeButton("稍后",null).show();return;
        }
        try{Uri uri=UpdateProvider.uri(apk);Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("空叶更新安装包",uri));startActivity(intent);}catch(RuntimeException e){message="无法打开系统安装器，请稍后重试。";render();}
    }
    private int ink,muted,tint,surface,border,accent,background;
    private TextView copy(String value,float size,int color,boolean bold){return Ui.text(this,value,size,color,bold);}
    private LinearLayout card(){LinearLayout box=Ui.column(this);Ui.pad(box,16,14,16,14);android.graphics.drawable.GradientDrawable frame=Ui.bg(surface,18,this);frame.setStroke(dp(.7f),border);box.setBackground(frame);return box;}
    private TextView action(String label,boolean primary,Runnable run){TextView t=Ui.centeredText(this,label,14,primary?(Ui.dark?0xFF233B2C:android.graphics.Color.WHITE):muted,true);t.setBackground(Ui.ripple(this,primary?(Ui.dark?0xFF85A867:0xFF233B2C):tint,16));Ui.pad(t,20,0,20,0);t.setContentDescription(label);t.setOnClickListener(v->run.run());t.setFocusable(true);return t;}
    private void versionColumn(LinearLayout row,String label,String number,boolean right){LinearLayout col=Ui.column(this);col.setGravity(right?Gravity.RIGHT:Gravity.LEFT);col.addView(copy(label,11,right?accent:muted,right));Ui.gap(col,6);TextView v=copy(number,18,right?ink:muted,true);v.setTypeface(android.graphics.Typeface.create("monospace",android.graphics.Typeface.BOLD));v.setSingleLine();v.setAutoSizeTextTypeUniformWithConfiguration(11,18,1,android.util.TypedValue.COMPLEX_UNIT_SP);col.addView(v,new LinearLayout.LayoutParams(-1,dp(24)));row.addView(col,new LinearLayout.LayoutParams(0,-2,1));}
    private void currentColumn(LinearLayout row,String label,String number,boolean installed,boolean ahead){
        LinearLayout col=Ui.column(this);Ui.pad(col,12,12,12,12);col.setBackground(Ui.bg(Ui.dark?(installed?0xFF25392D:0xFF1B2A21):(installed?0xFFF1F5EB:0xFFF7F9F5),14,this));col.addView(copy(label,11,muted,true));Ui.gap(col,12);LinearLayout values=Ui.row(this);TextView value=copy(number,18,installed?ink:muted,true);value.setTypeface(android.graphics.Typeface.create("monospace",android.graphics.Typeface.BOLD));value.setSingleLine();value.setAutoSizeTextTypeUniformWithConfiguration(11,18,1,android.util.TypedValue.COMPLEX_UNIT_SP);values.addView(value,new LinearLayout.LayoutParams(0,dp(25),1));
        if(ahead){TextView chip=Ui.centeredText(this,"领先",9,ink,true);chip.setBackground(Ui.bg(Ui.dark?tint:0xFFD7E4C8,5,this));Ui.pad(chip,6,2,6,2);LinearLayout.LayoutParams at=new LinearLayout.LayoutParams(-2,-2);at.leftMargin=dp(4);values.addView(chip,at);}else if(!installed){TextView releaseTag=copy("Release",10,muted,false);LinearLayout.LayoutParams at=new LinearLayout.LayoutParams(-2,-2);at.leftMargin=dp(4);values.addView(releaseTag,at);}col.addView(values);row.addView(col,new LinearLayout.LayoutParams(0,-2,1));
    }
    @Override protected void onPause(){if(emblem!=null)emblem.active(false);super.onPause();}
    private void render(){if(isFinishing()||isDestroyed())return;
        if(historyOpen||historyReturning){renderDeferred=true;return;}
        renderDeferred=false;
        ThemePolicy.apply(this);
        ink=Ui.dark?0xFFE6F0E8:0xFF233B2C;muted=Ui.dark?0xFF93AA9B:0xFF5D7464;tint=Ui.dark?0xFF25392D:0xFFE7EFE0;surface=Ui.dark?0xFF25392D:android.graphics.Color.WHITE;border=Ui.dark?0xFF354A3C:0xFFDFEAE0;accent=Ui.dark?0xFFB4D398:0xFF85A867;background=Ui.dark?0xFF1B2A21:0xFFF8FAF5;
        boolean newer=release!=null&&release.comparison>0;LinearLayout content=Ui.column(this);content.setContentDescription("应用内更新弹窗");Ui.pad(content,20,20,20,22);
        LinearLayout header=Ui.row(this);emblem=new UpdatePulseView(this,tint,accent,ink);header.addView(emblem,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView title=copy("检查更新",18,ink,true);Ui.pad(title,4,0,0,0);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));if(release!=null){TextView status=Ui.centeredText(this,newer?"🎉 发现新版本":ahead()?"高于公开版":"✓ 已是最新版本",10,newer?0xFF9D5718:0xFF2D653B,true);status.setBackground(Ui.bg(newer?0xFFFFF1C1:0xFFE2F2D8,18,this));Ui.pad(status,8,6,8,6);header.addView(status,new LinearLayout.LayoutParams(-2,-2));}content.addView(header);Ui.gap(content,12);View line=new View(this);line.setBackgroundColor(border);content.addView(line,new LinearLayout.LayoutParams(-1,dp(.7f)));Ui.gap(content,18);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);LinearLayout body=Ui.column(this);scroll.addView(body);LinearLayout versions=card();versions.setContentDescription("更新版本对比");LinearLayout versionRow=Ui.row(this);
        if(phase.equals("current")){
            Ui.pad(versions,12,12,12,12);currentColumn(versionRow,"当前安装版本",version(),true,release!=null&&release.comparison<0);View gap=new View(this);versionRow.addView(gap,new LinearLayout.LayoutParams(dp(10),1));currentColumn(versionRow,"公开稳定版本",release==null?"—":release.latest,false,false);
        }else{
            versionColumn(versionRow,"当前版本","v"+version(),false);LinearLayout flow=Ui.column(this);flow.setGravity(Gravity.CENTER);TextView badge=Ui.centeredText(this,newer?"NEW":"…",10,Ui.dark?0xFFF5D042:0xFF8C6B00,true);badge.setBackground(Ui.bg(Ui.dark?tint:0xFFFCF2C4,16,this));Ui.pad(badge,8,3,8,3);flow.addView(badge);Ui.gap(flow,4);flow.addView(Ui.icon(this,"update_arrow",accent,18),new LinearLayout.LayoutParams(dp(18),dp(18)));versionRow.addView(flow,new LinearLayout.LayoutParams(dp(44),-2));versionColumn(versionRow,"最新版本",release==null?"—":"v"+release.latest,true);
        }
        versions.addView(versionRow);body.addView(versions);
        if(release!=null){
            Ui.gap(body,16);
            LinearLayout notes=card();
            String size=newer&&release.size>0?" ("+String.format(java.util.Locale.getDefault(),"%.1f MB",release.size/1048576.0)+")":"";
            notes.addView(copy("更新摘要"+size,14,ink,true));
            Ui.gap(notes,8);
            ReleaseHistory.Release entry=new ReleaseHistory.Release(release.latest,release.latest,"",release.notes);
            String excerpt=ReleaseSummary.excerpt(entry, "");
            TextView summary=copy(excerpt.isEmpty()?"此版本未提供更新摘要。":excerpt,13,muted,false);
            summary.setMaxLines(3);
            summary.setEllipsize(TextUtils.TruncateAt.END);
            summary.setLineSpacing(dp(3),1);
            notes.addView(summary,new LinearLayout.LayoutParams(-1,-2));
            body.addView(notes);
        }
        progress=null;bar=null;
        if(!phase.equals("current")&&(!phase.equals("available")||!message.equals("发现新版本，可直接下载安装。"))){
            Ui.gap(body,14);TextView status=copy(message,12,muted,false);status.setContentDescription("更新状态："+message);status.setLineSpacing(dp(3),1);body.addView(status);
        }
        if(phase.equals("checking")){Ui.gap(body,14);ProgressBar busy=new ProgressBar(this);busy.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(accent));body.addView(busy,new LinearLayout.LayoutParams(dp(26),dp(26)));}
        else if(phase.equals("downloading")){Ui.gap(body,14);bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));body.addView(bar,new LinearLayout.LayoutParams(-1,dp(8)));Ui.gap(body,10);progress=copy("",12,muted,false);progress.setContentDescription("更新下载进度");body.addView(progress);refreshProgress();}
        android.util.DisplayMetrics display=getResources().getDisplayMetrics();int width=display.widthPixels-dp(72);body.measure(View.MeasureSpec.makeMeasureSpec(Math.max(dp(220),width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));content.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(body.getMeasuredHeight(),Math.max(dp(180),(int)(display.heightPixels*.84f)-dp(180)))));Ui.gap(content,22);
        LinearLayout actions=Ui.row(this);
        TextView history=action("更新历史  ›",false,this::openHistory);
        history.setBackground(null);
        history.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        history.setSingleLine();
        history.setEllipsize(TextUtils.TruncateAt.END);
        history.setTextSize(13);
        Ui.pad(history,2,0,8,0);
        actions.addView(history,new LinearLayout.LayoutParams(0,dp(42),1));
        if(phase.equals("available")&&newer&&release.downloadable()||phase.equals("ready")||phase.equals("error")||phase.equals("downloading")){
            boolean downloading=phase.equals("downloading");TextView later=action(downloading?"取消下载":"稍后",false,downloading?this::cancelDownload:this::finish);Ui.pad(later,12,0,12,0);actions.addView(later,new LinearLayout.LayoutParams(-2,dp(42)));String label=phase.equals("ready")?"安装更新":phase.equals("error")?"重试":downloading?"下载中":"立即下载";Runnable run=phase.equals("ready")?this::install:phase.equals("error")?this::check:this::download;TextView primary=action(label,true,run);Ui.pad(primary,14,0,14,0);primary.setEnabled(!downloading);LinearLayout.LayoutParams at=new LinearLayout.LayoutParams(-2,dp(42));at.leftMargin=dp(8);actions.addView(primary,at);
        }else {TextView close=action("我知道了",false,this::finish);if(phase.equals("current")){close.setTextColor(ink);close.setBackground(Ui.ripple(this,Ui.dark?tint:0xFFDCE8D2,16));}actions.addView(close,new LinearLayout.LayoutParams(dp(118),dp(42)));}content.addView(actions);
        showModal(content);
    }
    private void showModal(LinearLayout content){
        FrameLayout root=new FrameLayout(this);
        root.setClickable(true);
        root.setClipChildren(false);
        View shade=new View(this);
        shade.setBackgroundColor(0x66000000);
        shade.setOnClickListener(view->finish());
        shade.setContentDescription("检查更新背景遮罩");
        shade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        content.setBackground(Ui.bg(background,32,this));
        content.setClickable(true);
        content.setElevation(dp(12));
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);
        position.setMargins(dp(16),dp(16),dp(16),dp(16));
        root.addView(content,position);
        Window window=getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        int layout=View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        window.getDecorView().setSystemUiVisibility(layout|(Ui.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
        if(Build.VERSION.SDK_INT>=30)window.setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int left,top,right,bottom;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                left=bars.left;top=bars.top;right=bars.right;bottom=bars.bottom;
            }else{
                left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();
                right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();
            }
            position.setMargins(left+dp(16),top+dp(16),right+dp(16),bottom+dp(16));
            content.setLayoutParams(position);
            return insets;
        });
        dialogRoot=root;
        setContentView(root);
        root.requestApplyInsets();
    }
    private void refreshProgress(){if(progress==null||bar==null||release==null)return;int percent=(int)Math.min(100,received*100/Math.max(1,release.size));bar.setProgress(percent);progress.setText(percent+"% · "+String.format(java.util.Locale.getDefault(),"%.1f / %.1f MB",received/1048576.0,release.size/1048576.0));}
    @Override protected void onDestroy(){clearHistoryLayer();stopTask();worker.shutdownNow();super.onDestroy();}
}
