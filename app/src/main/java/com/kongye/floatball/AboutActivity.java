package com.kongye.floatball;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.webkit.*;
import android.widget.FrameLayout;
import java.nio.charset.StandardCharsets;

/** The supplied specimen-card HTML, packaged offline with its original vector animations. */
public final class AboutActivity extends Activity {
    private WebView web;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);ThemePolicy.apply(this);setTheme(Ui.dark?R.style.AppThemeDark:R.style.AppTheme);
        getWindow().setStatusBarColor(Ui.BG);getWindow().setNavigationBarColor(Ui.BG);getWindow().getDecorView().setSystemUiVisibility(Ui.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Ui.BG);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
        web=new WebView(this);web.setContentDescription("关于空叶：小叶的一天");web.setBackgroundColor(Ui.BG);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setBlockNetworkLoads(true);settings.setTextZoom(100);
        web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){if(!request.isForMainFrame())return true;android.net.Uri uri=request.getUrl();if("about.kongye.local".equals(uri.getHost())){if("/back".equals(uri.getPath()))finish();}return true;}});
        root.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(root);root.requestApplyInsets();
        try(java.io.InputStream in=getAssets().open("about.html")){
            String version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1)bytes.write(block,0,n);
            String html=new String(bytes.toByteArray(),StandardCharsets.UTF_8).replace("__APP_VERSION__",android.text.TextUtils.htmlEncode(version)).replace("__APP_DARK__",Ui.dark?"night":"");
            web.loadDataWithBaseURL("https://about.kongye.local/",html,"text/html","UTF-8",null);
        }catch(Exception e){Feedback.show(this,"关于页加载失败，请重试");finish();}
    }

    @Override protected void onPause(){if(web!=null){web.evaluateJavascript("document.documentElement.classList.add('suspended');if(typeof audioCtx!=='undefined'&&audioCtx)audioCtx.suspend();if(typeof pressTimer!=='undefined')clearTimeout(pressTimer);",null);web.onPause();}super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null){web.onResume();web.evaluateJavascript("document.documentElement.classList.remove('suspended')",null);}}
    @Override protected void onDestroy(){if(web!=null){((android.view.ViewGroup)web.getParent()).removeView(web);web.destroy();web=null;}super.onDestroy();}
}
