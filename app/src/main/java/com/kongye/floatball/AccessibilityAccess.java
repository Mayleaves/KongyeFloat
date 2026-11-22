package com.kongye.floatball;

import android.app.Activity;
import android.app.AlertDialog;
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
    enum State { OFF, SYSTEM_OFF, WAITING, CONNECTED }
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
        if (!contains(enabled, new ComponentName(context, AssistService.class))) return State.OFF;
        return Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 0
                ? State.SYSTEM_OFF : State.WAITING;
    }

    static boolean contains(String enabled, ComponentName ownService) {
        if (enabled == null || enabled.isEmpty()) return false;
        for (String entry : enabled.split(":")) {
            if (ownService.equals(ComponentName.unflattenFromString(entry))) return true;
        }
        return false;
    }

    static void openSettings(Activity activity) {
        State state = state(activity);
        boolean xiaomi = isBrand("Xiaomi") || isBrand("Redmi") || isBrand("POCO");
        ThemePolicy.apply(activity);
        Dialog dialog = new Dialog(activity); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(activity); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = Ui.column(activity); Ui.pad(body, 22, 22, 22, 18); scroll.addView(body);
        LinearLayout header = Ui.row(activity);
        FrameLayout badge = new FrameLayout(activity); badge.setBackground(Ui.bg(Ui.PALE, 14, activity));
        badge.addView(Ui.icon(activity, "assist", Ui.GREEN, 23), new FrameLayout.LayoutParams(Ui.dp(activity,23), Ui.dp(activity,23), Gravity.CENTER));
        header.addView(badge, new LinearLayout.LayoutParams(Ui.dp(activity,44), Ui.dp(activity,44)));
        String title = switch (state) {
            case OFF -> "开启无障碍";
            case SYSTEM_OFF -> "系统无障碍已关闭";
            case WAITING -> "无障碍尚未连接";
            case CONNECTED -> "无障碍已连接";
        };
        TextView heading = Ui.text(activity, title, 18, Ui.INK, true);
        Ui.pad(heading, 12, 0, 8, 0);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(Ui.tool(activity, "close", "关闭授权说明", dialog::dismiss), new LinearLayout.LayoutParams(Ui.dp(activity,36), Ui.dp(activity,36)));
        body.addView(header);
        Ui.gap(body, 12);
        String message = switch (state) {
            case OFF -> "请开启「#空叶悬浮球」。";
            case SYSTEM_OFF -> "请在系统设置中重新开启「#空叶悬浮球」。";
            case WAITING -> "若持续未连接，请关闭后重新开启。";
            case CONNECTED -> xiaomi ? "后台容易断开时，可将省电策略设为「无限制」。"
                    : "后台容易断开时，可在应用信息中调整电池设置。";
        };
        body.addView(Ui.text(activity, message, 13, Ui.MUTED, false));
        Ui.gap(body, 20);
        body.addView(Ui.button(activity, "前往无障碍设置", true, () -> {
            dialog.dismiss();
            launchSettings(activity, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }), new LinearLayout.LayoutParams(-1, Ui.dp(activity, 46)));
        Ui.gap(body,6);
        addLink(body, xiaomi ? "省电设置  ›" : "后台运行设置  ›", () -> {
            if (openBatterySettings(activity, xiaomi)) dialog.dismiss();
        });
        if (state == State.OFF || state == State.SYSTEM_OFF) {
            addLink(body, "开关置灰？", () -> new AlertDialog.Builder(activity)
                    .setTitle("开关置灰？")
                    .setMessage("在应用信息右上角菜单中，选择「允许受限制的设置」。")
                    .setNegativeButton("返回", null)
                    .setPositiveButton("应用信息", (prompt, button) -> {
                        dialog.dismiss();
                        launchSettings(activity, appInfoIntent(activity));
                    }).show());
        }
        dialog.setContentView(scroll); dialog.setCanceledOnTouchOutside(true);
        Window window=dialog.getWindow(); if(window!=null){window.setBackgroundDrawable(Ui.bg(Ui.SURFACE,26,activity));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.28f);}
        dialog.show(); if(window!=null){android.util.DisplayMetrics metrics=activity.getResources().getDisplayMetrics();window.setLayout(Math.min(Ui.dp(activity,420),metrics.widthPixels-Ui.dp(activity,40)),-2);}
    }

    private static void addLink(LinearLayout body, String title, Runnable action) {
        Context context = body.getContext();
        TextView link = Ui.centeredText(context, title, 13, Ui.GREEN, true);
        link.setBackground(Ui.ripple(context, 0, 14));
        link.setFocusable(true);
        link.setOnClickListener(view -> action.run());
        body.addView(link, new LinearLayout.LayoutParams(-1, Ui.dp(context, 44)));
    }

    private static boolean isBrand(String brand) {
        return brand.equalsIgnoreCase(Build.BRAND) || brand.equalsIgnoreCase(Build.MANUFACTURER);
    }

    private static Intent appInfoIntent(Activity activity) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.getPackageName(), null));
    }

    private static boolean openBatterySettings(Activity activity, boolean xiaomi) {
        if (xiaomi) {
            Intent battery = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, appInfoIntent(activity).getData())
                    .setPackage("com.miui.securitycenter");
            try {
                activity.startActivity(battery);
                return true;
            } catch (ActivityNotFoundException | SecurityException unavailable) {
                // On versions without the direct entry, App info still offers Battery settings.
            }
        }
        try {
            activity.startActivity(appInfoIntent(activity));
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            Feedback.show(activity, "请在系统设置中打开空叶悬浮球的应用信息");
            return false;
        }
        Feedback.show(activity, isBrand("OnePlus") ? "耗电管理 → 完全允许后台行为"
                : xiaomi ? "省电策略 → 无限制" : "在电池设置中允许后台运行");
        return true;
    }
}
