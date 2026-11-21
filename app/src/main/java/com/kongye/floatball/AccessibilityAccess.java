package com.kongye.floatball;

import android.app.Activity;
import android.app.Dialog;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;

/** Distinguishes system authorization from the asynchronous service connection. */
final class AccessibilityAccess {
    enum State { OFF, WAITING, CONNECTED }
    private static final Handler main=new Handler(Looper.getMainLooper());
    private record ReturnTarget(int taskId,Intent intent,long started){}
    private static ReturnTarget pendingReturn;

    static void launchSettings(Activity activity,Intent intent){
        boolean awaiting=state(activity)!=State.CONNECTED;
        if(awaiting)pendingReturn=new ReturnTarget(activity.getTaskId(),new Intent(activity.getIntent()).setComponent(activity.getComponentName()),SystemClock.elapsedRealtime());
        try{activity.startActivity(intent);}
        catch(ActivityNotFoundException|SecurityException unavailable){if(awaiting)pendingReturn=null;Feedback.show(activity,"请在系统设置中检查空叶悬浮球的无障碍权限");}
    }

    static void returnedTo(Activity activity){if(pendingReturn!=null&&pendingReturn.taskId==activity.getTaskId())pendingReturn=null;}

    static void connected(AssistService service){
        ReturnTarget target=pendingReturn;
        if(target==null)return;
        main.postDelayed(()->returnToOrigin(service,target,0),300);
    }

    private static void returnToOrigin(AssistService service,ReturnTarget target,int attempt){
        if(pendingReturn!=target||AssistService.instance!=service)return;
        if(SystemClock.elapsedRealtime()-target.started>300000){pendingReturn=null;return;}
        android.view.accessibility.AccessibilityNodeInfo root=service.getRootInActiveWindow();
        String pkg="";
        if(root!=null){try{CharSequence name=root.getPackageName();pkg=name==null?"":name.toString();}finally{root.recycle();}}
        if(pkg.equals("android")){
            for(ActivityManager.AppTask task:service.getSystemService(ActivityManager.class).getAppTasks()){
                ActivityManager.RecentTaskInfo info=task.getTaskInfo();
                if((android.os.Build.VERSION.SDK_INT>=29?info.taskId:info.id)==target.taskId&&info.topActivity!=null){pkg=info.topActivity.getPackageName();break;}
            }
            if(!settingsPackage(pkg)&&settingsPackage(AssistService.foregroundPackage))pkg=AssistService.foregroundPackage;
        }
        if(pkg.isEmpty()||pkg.equals("android")||pkg.equals("com.android.systemui")){
            if(attempt<20)main.postDelayed(()->returnToOrigin(service,target,attempt+1),250);
            else pendingReturn=null;
            return;
        }
        pendingReturn=null;
        // A new service connection outside the requested settings flow must not interrupt another app.
        if(!settingsPackage(pkg))return;
        Intent destination=new Intent(target.intent).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try{
            for(ActivityManager.AppTask task:service.getSystemService(ActivityManager.class).getAppTasks()){
                ActivityManager.RecentTaskInfo info=task.getTaskInfo();
                if((android.os.Build.VERSION.SDK_INT>=29?info.taskId:info.id)!=target.taskId)continue;
                task.startActivity(service,destination,null);task.moveToFront();return;
            }
        }catch(IllegalArgumentException|IllegalStateException|SecurityException unavailable){android.util.Log.w("KongyeAccess","Original settings task could not be resumed",unavailable);}
        try{service.startActivity(destination.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(ActivityNotFoundException|SecurityException unavailable){Feedback.success(service,"无障碍已连接，请返回原页面");}
    }

    private static boolean settingsPackage(String pkg){return pkg!=null&&(pkg.contains("settings")||pkg.contains("safecenter")||pkg.contains("securitycenter"));}

    static State state(Context context) {
        if (AssistService.instance != null) return State.CONNECTED;
        String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return contains(enabled, new ComponentName(context, AssistService.class)) ? State.WAITING : State.OFF;
    }

    static boolean contains(String enabled, ComponentName ownService) {
        if (enabled == null || enabled.isEmpty()) return false;
        for (String entry : enabled.split(":")) {
            if (ownService.equals(ComponentName.unflattenFromString(entry))) return true;
        }
        return false;
    }

    static String guidance(State state) {
        return state == State.WAITING
                ? "无障碍已开启，正在等待系统连接。长时间未恢复时，请在系统设置中关闭后重新开启。"
                : "请在系统无障碍设置中开启空叶悬浮球。\n\n如果开关是灰色，或提示“受限制的设置”：先打开应用信息，点击右上角菜单中的“允许受限制的设置”，再返回开启无障碍。";
    }

    static void openSettings(Activity activity) {
        State state = state(activity);
        boolean connected = state == State.CONNECTED;
        ThemePolicy.apply(activity);
        Dialog dialog = new Dialog(activity); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(activity); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = Ui.column(activity); Ui.pad(body, 22, 22, 22, 18); scroll.addView(body);
        LinearLayout header = Ui.row(activity);
        FrameLayout badge = new FrameLayout(activity); badge.setBackground(Ui.bg(Ui.PALE, 14, activity));
        badge.addView(Ui.icon(activity, "assist", Ui.GREEN, 23), new FrameLayout.LayoutParams(Ui.dp(activity,23), Ui.dp(activity,23), Gravity.CENTER));
        header.addView(badge, new LinearLayout.LayoutParams(Ui.dp(activity,44), Ui.dp(activity,44)));
        LinearLayout titles = Ui.column(activity); Ui.pad(titles, 12, 0, 8, 0);
        String title = switch (state) {
            case OFF -> "开启无障碍";
            case WAITING -> "等待无障碍连接";
            case CONNECTED -> "无障碍已连接";
        };
        titles.addView(Ui.text(activity, title, 18, Ui.INK, true));
        Ui.gap(titles, 5); titles.addView(Ui.text(activity, "用于返回、截图与字幕翻译", 11, Ui.MUTED, false));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(Ui.tool(activity, "close", "关闭授权说明", dialog::dismiss), new LinearLayout.LayoutParams(Ui.dp(activity,36), Ui.dp(activity,36)));
        body.addView(header); Ui.gap(body, 20);
        if (!connected) {
            TextView intro = Ui.text(activity, state == State.WAITING
                    ? "服务已开启，正在等待系统连接。若长时间未恢复，可关闭后重新开启。"
                    : "在系统辅助功能中找到「#空叶悬浮球」，开启后会自动返回这里。", 13, Ui.MUTED, false);
            intro.setLineSpacing(Ui.dp(activity, 4), 1);
            body.addView(intro);
            Ui.gap(body, 16);
        }

        LinearLayout help = Ui.column(activity);
        Ui.pad(help, 14, 13, 14, 13);
        help.setBackground(Ui.bg(Ui.BG, 16, activity));
        if (state == State.OFF) {
            addHelpText(help, "开关灰色或提示受限制？",
                    "应用信息 → 右上角菜单 → 允许受限制的设置，再返回开启无障碍。");
            Ui.gap(help, 14);
        }
        boolean onePlus = "OnePlus".equalsIgnoreCase(Build.BRAND) || "OnePlus".equalsIgnoreCase(Build.MANUFACTURER);
        String backgroundPath = onePlus
                ? "应用信息 → 耗电管理 → 完全允许后台行为。"
                : "打开应用信息，在电池设置中允许空叶悬浮球在后台运行。";
        addHelpText(help, "无障碍在后台经常断开？", backgroundPath + "\n允许后台运行可减少因系统省电而断开。");
        body.addView(help);
        Ui.gap(body, 20);
        body.addView(Ui.button(activity, "前往无障碍设置", true, () -> {
            dialog.dismiss();
            launchSettings(activity, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }), new LinearLayout.LayoutParams(-1, Ui.dp(activity, 46)));
        Ui.gap(body,6);
        TextView info=Ui.centeredText(activity,"打开应用信息  ›",13,Ui.GREEN,true);
        info.setBackground(Ui.ripple(activity,0,14)); info.setFocusable(true); info.setOnClickListener(view->{
            dialog.dismiss();
            launchSettings(activity,new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName())));
        }); body.addView(info,new LinearLayout.LayoutParams(-1,Ui.dp(activity,42)));
        dialog.setContentView(scroll); dialog.setCanceledOnTouchOutside(true);
        Window window=dialog.getWindow(); if(window!=null){window.setBackgroundDrawable(Ui.bg(Ui.SURFACE,26,activity));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.28f);}
        dialog.show(); if(window!=null){android.util.DisplayMetrics metrics=activity.getResources().getDisplayMetrics();window.setLayout(Math.min(Ui.dp(activity,420),metrics.widthPixels-Ui.dp(activity,40)),-2);}
    }

    private static void addHelpText(LinearLayout card, String title, String message) {
        Context context = card.getContext();
        card.addView(Ui.text(context, title, 12, Ui.INK, true));
        Ui.gap(card, 7);
        TextView detail = Ui.text(context, message, 12, Ui.MUTED, false);
        detail.setLineSpacing(Ui.dp(context, 3), 1);
        card.addView(detail);
    }
}
