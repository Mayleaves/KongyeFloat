package com.kongye.floatball;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

/** Generated update metadata only; never downloads or installs a fixture APK. */
final class Update342Checks {
    private static int requests;
    private static Activity top;
    private static boolean registered;
    private static java.lang.ref.WeakReference<MainActivity> mainPage=new java.lang.ref.WeakReference<>(null);

    static void run(Context context, String command) throws Exception {
        MainActivity page=V1231Checks.main();
        if(page==null)page=mainPage.get();
        if(page==null)throw new IllegalStateException("Open the main page first");
        mainPage=new java.lang.ref.WeakReference<>(page);
        Prefs.of(context).edit().remove("qa_overlay_error").apply();
        UpdateCheckSession check=(UpdateCheckSession)V126Checks.get(page,"updateCheck");
        if(command.startsWith("source_")) {
            String mode=command.substring(7);
            if(!java.util.List.of("available","current","error","long").contains(mode))throw new IllegalArgumentException(mode);
            if(check.checking)throw new IllegalStateException("Wait for the current check");
            requests=0;top=page;
            if(!registered) {
                registered=true;
                page.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    public void onActivityResumed(Activity activity){top=activity;if(activity instanceof MainActivity main)mainPage=new java.lang.ref.WeakReference<>(main);}
                    public void onActivityDestroyed(Activity activity){if(top==activity)top=null;}
                    public void onActivityCreated(Activity a,Bundle b){}
                    public void onActivityStarted(Activity a){}
                    public void onActivityPaused(Activity a){}
                    public void onActivityStopped(Activity a){}
                    public void onActivitySaveInstanceState(Activity a,Bundle b){}
                });
            }
            check.source=version->{
                synchronized(Update342Checks.class){requests++;}
                Thread.sleep(3000);
                if(mode.equals("error"))throw new java.io.IOException("Generated network failure");
                String latest=mode.equals("current")?version:mode.equals("long")?"99.123.4567":"9.9.9";
                return new GitHubUpdate.Result(latest,ReleaseVersion.compare(latest,version),"fixture.apk",
                    "https://github.com/Mayleaves/KongyeFloat/releases/download/v9.9.9/fixture.apk",4404019,"",
                    "Generated update fixture. This is not a public release.");
            };
            return;
        }
        if(command.equals("manual")){V126Checks.call(page,"checkForUpdates");return;}
        if(command.equals("rotate")){page.recreate();return;}
        JSONObject report=new JSONObject().put("checking",check.checking).put("automatic",check.automatic)
            .put("requests",requests).put("page",top==null?"":top.getClass().getSimpleName())
            .put("latest",Prefs.of(context).getString("update_latest_version",""));
        FrameLayout capsule=(FrameLayout)V126Checks.get(page,"updateCapsule");
        if(capsule!=null&&capsule.getChildAt(0) instanceof TextView label) {
            report.put("badge_color",String.format("#%08X",((GradientDrawable)label.getBackground()).getColor().getDefaultColor()))
                .put("badge_visible",label.getVisibility()==View.VISIBLE)
                .put("badge_description",label.getContentDescription());
        }
        if(top instanceof UpdateActivity) {
            View root=top.findViewById(android.R.id.content);
            report.put("later",V124Checks.find(root,"稍后")!=null).put("download",V124Checks.find(root,"立即下载")!=null);
            View current=V124Checks.find(root,"当前版本"), latest=V124Checks.find(root,"最新版本");
            if(current!=null&&latest!=null) {
                View left=(View)current.getParent(),right=(View)latest.getParent();
                Rect l=new Rect(),r=new Rect();left.getGlobalVisibleRect(l);right.getGlobalVisibleRect(r);
                report.put("equal_columns",Math.abs(left.getWidth()-right.getWidth())<=1&&l.top==r.top&&l.right<r.left)
                    .put("current_width",left.getWidth()).put("latest_width",right.getWidth());
                if(right instanceof LinearLayout column&&column.getChildAt(column.getChildCount()-1) instanceof LinearLayout values&&values.getChildAt(0) instanceof TextView number) {
                    report.put("version_fits",number.getPaint().measureText(number.getText().toString())<=number.getWidth()-number.getCompoundPaddingLeft()-number.getCompoundPaddingRight()+1)
                        .put("version_size",number.getTextSize());
                }
            }
        }
        Prefs.of(context).edit().putString("qa_update342",report.toString()).commit();
    }
}
