package com.kongye.floatball;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** In-app release check and download. Only Android's permission/installer UI is external. */
public final class UpdateActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
    private GitHubUpdate.Result release;private UpdateDownload transfer;private Future<?> task;private File apk;private String phase="checking",message="正在检查最新版本…";private int generation;private long received;private boolean permissionPage;private TextView progress;private ProgressBar bar;
    @Override public void onCreate(Bundle saved){super.onCreate(saved);if(saved!=null){String name=saved.getString("apk","");if(name.matches("[a-f0-9-]{36}\\.apk")){File file=new File(new File(getCacheDir(),"updates"),name);try{UpdateDownload.verify(this,file);apk=file;phase="ready";message="安装包已下载，点击安装更新继续。";release=new GitHubUpdate.Result(saved.getString("latest",""),1,"","",file.length(),"");render();return;}catch(IOException ignored){}}}check();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(apk!=null){state.putString("apk",apk.getName());state.putString("latest",release==null?"":release.latest);}}
    private String version(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){return "0";}}
    private int dp(float n){return Ui.dp(this,n);}
    @Override protected void onResume(){super.onResume();if(permissionPage){permissionPage=false;message=getPackageManager().canRequestPackageInstalls()?"安装权限已允许，点击安装更新继续。":"安装包已下载，可再次点击安装更新。";}render();}
    private boolean alive(int token){return token==generation&&!isFinishing()&&!isDestroyed();}
    private void stopTask(){generation++;if(transfer!=null){transfer.cancel();transfer=null;}if(task!=null){task.cancel(true);task=null;}}
    private void check(){stopTask();phase="checking";message="正在检查最新版本…";render();int token=generation;task=worker.submit(()->{GitHubUpdate.Result answer=null;String failure=null;try{answer=GitHubUpdate.check(version());}catch(IOException|RuntimeException e){failure=e.getMessage();}GitHubUpdate.Result result=answer;String error=failure;main.post(()->{if(!alive(token))return;task=null;release=result;if(result==null){phase="error";message=error==null?"检查失败，请稍后重试。":error;}else if(result.comparison<=0){phase="current";message=result.comparison==0?"已是最新版本。":"当前版本较发布版本更新。";}else{phase="available";message=result.downloadable()?"发现新版本，可直接下载安装。":"该版本暂未提供 APK，请稍后检查。";}render();});});}
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
    private void render(){if(isFinishing()||isDestroyed())return;ThemePolicy.apply(this);setTheme(Ui.dark?R.style.AppThemeDark:R.style.AppTheme);getWindow().setStatusBarColor(Ui.BG);getWindow().setNavigationBarColor(Ui.BG);getWindow().getDecorView().setSystemUiVisibility(Ui.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        LinearLayout heading=Ui.row(this);Ui.pad(heading,12,14,24,12);heading.addView(Ui.tool(this,"back","返回设置",this::finish),new LinearLayout.LayoutParams(dp(40),dp(40)));heading.addView(Ui.text(this,"检查更新",16,Ui.INK,true));root.addView(heading);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout content=Ui.column(this);Ui.pad(content,16,6,16,24);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout card=Ui.card(this);card.setContentDescription("应用内更新卡片");
        LinearLayout branding=Ui.row(this);FrameLayout badge=Ui.symbol(this,"update","空叶更新",()->{});branding.addView(badge,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView name=Ui.text(this,"空叶悬浮球",18,Ui.INK,true);Ui.pad(name,12,0,0,0);branding.addView(name);card.addView(branding);Ui.gap(card,18);card.addView(Ui.text(this,"当前版本  "+version(),13,Ui.MUTED,false));if(release!=null){Ui.gap(card,10);card.addView(Ui.text(this,"发布版本  "+release.latest,13,Ui.INK,false));if(release.downloadable()&&release.comparison>0){Ui.gap(card,6);card.addView(Ui.text(this,release.name+" · "+String.format(java.util.Locale.getDefault(),"%.1f MB",release.size/1048576.0),11,Ui.MUTED,false));}}
        Ui.gap(card,20);TextView status=Ui.text(this,message,14,Ui.INK,false);status.setContentDescription("更新状态："+message);status.setLineSpacing(dp(3),1);card.addView(status);progress=null;bar=null;
        if(phase.equals("checking")){Ui.gap(card,18);ProgressBar busy=new ProgressBar(this);busy.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));card.addView(busy,new LinearLayout.LayoutParams(dp(30),dp(30)));}
        else if(phase.equals("downloading")){Ui.gap(card,18);bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));card.addView(bar,new LinearLayout.LayoutParams(-1,dp(8)));Ui.gap(card,12);progress=Ui.text(this,"",13,Ui.MUTED,false);progress.setContentDescription("更新下载进度");card.addView(progress);refreshProgress();Ui.gap(card,20);card.addView(Ui.button(this,"取消下载",false,this::cancelDownload));}
        else{Ui.gap(card,20);if(phase.equals("ready"))card.addView(Ui.button(this,"安装更新",true,this::install));else if(phase.equals("available")&&release!=null&&release.downloadable())card.addView(Ui.button(this,"下载安装包",true,this::download));else card.addView(Ui.button(this,"重新检查",false,this::check));}
        content.addView(card);Ui.gap(content,16);TextView tip=Ui.text(this,"安装包从空叶的 GitHub 正式发布下载，在应用内完成；安装前由系统再次确认。",12,Ui.MUTED,false);tip.setLineSpacing(dp(3),1);Ui.pad(tip,6,0,6,0);content.addView(tip);setContentView(root);root.requestApplyInsets();
    }
    private void refreshProgress(){if(progress==null||bar==null||release==null)return;int percent=(int)Math.min(100,received*100/Math.max(1,release.size));bar.setProgress(percent);progress.setText(percent+"% · "+String.format(java.util.Locale.getDefault(),"%.1f / %.1f MB",received/1048576.0,release.size/1048576.0));}
    @Override protected void onDestroy(){stopTask();worker.shutdownNow();super.onDestroy();}
}
