package com.kongye.fixture;
import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
/** A labelled simulation, never used by the real vault or production APK. */
public class FixtureUnlock extends Activity {
    @Override public void onCreate(Bundle b){super.onCreate(b);LinearLayout ui=new LinearLayout(this);ui.setOrientation(1);ui.setPadding(36,80,36,36);
        TextView title=new TextView(this);title.setText("密码填充解锁模拟（仅测试数据）");ui.addView(title);
        Button unlock=new Button(this);unlock.setText("测试解锁");unlock.setOnClickListener(v->{FixtureVault.unlocked=true;finish();});ui.addView(unlock);
        Button cancel=new Button(this);cancel.setText("取消测试解锁");cancel.setOnClickListener(v->finish());ui.addView(cancel);setContentView(ui);}
}
