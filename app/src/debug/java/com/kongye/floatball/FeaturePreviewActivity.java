package com.kongye.floatball;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Debug-only navigation to app-owned previews; never opens a user's password vault. */
public final class FeaturePreviewActivity extends Activity {
    private PasswordFillController previewController;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);String page=getIntent().getStringExtra("page");Intent target;
        if("connection_success".equals(page)){
            android.app.Application.ActivityLifecycleCallbacks listener=new android.app.Application.ActivityLifecycleCallbacks(){
                public void onActivityResumed(Activity activity){if(!(activity instanceof PasswordHelpActivity))return;getApplication().unregisterActivityLifecycleCallbacks(this);activity.getWindow().getDecorView().postDelayed(()->{try{java.lang.reflect.Field source=PasswordHelpActivity.class.getDeclaredField("connectionSource");source.setAccessible(true);source.set(activity,(java.util.function.Supplier<Bundle>)()->{Bundle b=VaultClient.result("ok");b.putBoolean("unlocked",true);return b;});java.lang.reflect.Method check=PasswordHelpActivity.class.getDeclaredMethod("checkConnection");check.setAccessible(true);check.invoke(activity);}catch(Exception error){throw new RuntimeException(error);}},600);}
                public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
            };getApplication().registerActivityLifecycleCallbacks(listener);startActivity(PasswordHelpActivity.intent(this,PasswordTarget.Problem.NONE));finish();return;
        }
        if(page!=null&&page.startsWith("password")){android.widget.TextView background=new android.widget.TextView(this);background.setText("密码选择界面测试（模拟数据）");background.setPadding(24,100,24,24);setContentView(background);try{PasswordPanelChecks.show(this,page);previewController=PasswordPanelChecks.controller;}catch(Exception error){throw new RuntimeException(error);}return;}
        if("history".equals(page))target=ReleaseHistoryActivity.intent(this);
        else if("update_live".equals(page))target=new Intent(this,UpdateActivity.class);
        else if("update".equals(page)||"update_available".equals(page)){
            String tag="update".equals(page)?"3.2.0":"3.3.0";
            String notes="# 界面测试示例\n\n此页面用于核对 **Markdown 显示**，不是新版本发布通知。\n\n## 改进\n\n- 标题、列表和**粗体**正常显示\n- `行内代码` 与[项目主页](https://github.com/Mayleaves/KongyeFloat)\n\n> 示例引用内容\n\n```text\n示例代码块\n```";
            target=UpdateActivity.checkedIntent(this,new GitHubUpdate.Result(tag,0,"","",0,"",notes),null);
        }else target=PasswordHelpActivity.intent(this,PasswordTarget.Problem.OWN_APP);
        startActivity(target);finish();
    }
    @Override protected void onDestroy(){if(previewController!=null&&previewController==PasswordPanelChecks.controller)PasswordPanelChecks.close();super.onDestroy();}
}
