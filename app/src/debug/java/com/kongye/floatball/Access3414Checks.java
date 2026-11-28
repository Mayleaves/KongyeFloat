package com.kongye.floatball;

import android.app.Activity;
import android.content.*;
import android.provider.Settings;
import org.json.JSONObject;

/** Authorization routing checks are used only on the dedicated emulator. */
final class Access3414Checks {
    private static Activity origin;
    static void run(Context context,String command)throws Exception{
        Prefs.of(context).edit().remove("qa_overlay_error").apply();
        if(command.equals("main"))origin=V1231Checks.main();
        if(command.equals("help")){
            Activity main=V1231Checks.main();
            if(main==null)throw new IllegalStateException("Open main first");
            main.getApplication().registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks(){
                public void onActivityResumed(Activity a){if(a instanceof PasswordHelpActivity)origin=a;}
                public void onActivityCreated(Activity a,android.os.Bundle b){} public void onActivityStarted(Activity a){}
                public void onActivityPaused(Activity a){} public void onActivityStopped(Activity a){}
                public void onActivitySaveInstanceState(Activity a,android.os.Bundle b){} public void onActivityDestroyed(Activity a){}
            });
            main.startActivity(PasswordHelpActivity.intent(main,PasswordTarget.Problem.NONE));return;
        }
        if(command.equals("launch")){
            if(origin==null)throw new IllegalStateException("Select origin first");
            AccessibilityAccess.launchSettings(origin,new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
        android.content.pm.ServiceInfo service=context.getPackageManager().getServiceInfo(new ComponentName(context,AssistService.class),0);
        if(!"#空叶悬浮球".contentEquals(service.loadLabel(context.getPackageManager())))throw new AssertionError("Service label");
        if(!"空叶悬浮球".contentEquals(context.getApplicationInfo().loadLabel(context.getPackageManager())))throw new AssertionError("App label changed");
        java.lang.reflect.Field pending=AccessibilityAccess.class.getDeclaredField("pendingReturn");pending.setAccessible(true);
        JSONObject state=new JSONObject().put("pending",pending.get(null)!=null)
                .put("connected",AssistService.instance!=null).put("origin",origin==null?"":origin.getClass().getSimpleName());
        Prefs.of(context).edit().putString("qa_access3414",state.toString()).commit();
    }
}
