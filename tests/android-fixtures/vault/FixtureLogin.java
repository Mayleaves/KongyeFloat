package com.kongye.fixture;
import android.app.Activity;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
public class FixtureLogin extends Activity {
    static FixtureLogin current;
    EditText account,password;
    @Override public void onCreate(Bundle b){super.onCreate(b);current=this;getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);LinearLayout ui=new LinearLayout(this);ui.setOrientation(1);ui.setPadding(36,80,36,36);
        TextView title=new TextView(this);title.setText("空叶密码填充登录测试（模拟页面）");ui.addView(title);
        // Known address-control ID is declared in the test APK; no browser or network required.
        TextView url=new TextView(this);url.setId(getResources().getIdentifier("url_bar","id",getPackageName()));url.setText(getIntent().getStringExtra("url")==null?"https://login.example.com/signin":getIntent().getStringExtra("url"));ui.addView(url);
        account=new EditText(this);account.setId(1001);account.setHint("测试账号");account.setInputType(InputType.TYPE_CLASS_TEXT);ui.addView(account);
        password=new EditText(this);password.setId(1002);password.setHint("测试密码");password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);ui.addView(password);
        if(getIntent().getBooleanExtra("unsupported",false))password.setAccessibilityDelegate(new View.AccessibilityDelegate(){
            public void onInitializeAccessibilityNodeInfo(View v,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(v,info);info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT);}
            public boolean performAccessibilityAction(View v,int a,Bundle data){return a==AccessibilityNodeInfo.ACTION_SET_TEXT?false:super.performAccessibilityAction(v,a,data);}
        });
        Button other=new Button(this);other.setText("切换到另一个测试页面");other.setOnClickListener(v->{account.setId(1003);password.setId(1004);url.setText("https://other.example.net/signin");});ui.addView(other);setContentView(ui);account.requestFocus();getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    @Override public void onDestroy(){if(current==this)current=null;super.onDestroy();}
}
