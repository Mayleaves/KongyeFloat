package com.kongye.floatball;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static SharedPreferences of(Context c) { return c.getSharedPreferences("kongye", Context.MODE_PRIVATE); }
    static java.util.ArrayList<String> addSubtitleTool(Context context, String key, java.util.ArrayList<String> tools) {
        SharedPreferences preferences = of(context);
        String migrated = key + "_subtitle_added";
        if (!preferences.getBoolean(migrated, false)) {
            if (!tools.contains("subtitle")) tools.add("subtitle");
            preferences.edit().putString(key, new org.json.JSONArray(tools).toString())
                    .putBoolean(migrated, true).apply();
        }
        return tools;
    }
    static String action(Context c, String gesture) {
        String fallback = switch (gesture) {
            case "double" -> "back";
            case "long" -> "quick";
            case "up" -> "settings";
            case "down" -> "notifications";
            default -> "menu";
        };
        String value=canonicalAction(of(c).getString("gesture_" + gesture, fallback));return value.equals("manage_quick")||value.equals("record_stop")?"none":value;
    }
    static final String[] ACTIONS = {"quick", "manage_quick", "auto_longshot", "menu", "settings", "back", "home", "recents", "recent_apps", "favorite_apps", "notifications", "quick_settings", "screenshot", "region", "longshot", "record", "gif_record", "subtitle", "record_stop", "notes", "browser", "apps", "files", "lock", "none"};
    static final String[] LABELS = {"边缘快捷栏", "管理边缘快捷", "滚动截图", "快捷菜单", "打开空叶", "返回", "主页", "最近任务", "最近软件", "常用软件", "通知栏", "快捷设置", "整屏截图", "区域截图", "长截图", "录屏", "录制 GIF", "字幕翻译", "停止录屏", "便签", "悬浮浏览器", "应用启动", "打开文件", "锁屏", "不执行"};
    static String[] quickFunctions(){return new String[]{"manage_quick","menu","settings","back","home","recents","notifications","quick_settings","lock","screenshot","region","longshot","auto_longshot","record","gif_record","subtitle","recent_apps","favorite_apps","apps","browser","notes","files"};}
    static String[] gestureActions(){return new String[]{"none","quick","menu","settings","back","home","recents","notifications","quick_settings","lock","screenshot","region","longshot","auto_longshot","record","gif_record","subtitle","recent_apps","favorite_apps","apps","pick_app","browser","notes","files"};}
    static String canonicalAction(String id){if("text".equals(id))return "region";return "record_silent".equals(id)||"record_sound".equals(id)?"record":id;}
    static String label(String id) {
        id=canonicalAction(id);if(id.equals("pick_app"))return "指定一个软件...";
        for (int i = 0; i < ACTIONS.length; i++) if (ACTIONS[i].equals(id)) return LABELS[i];
        return "快捷菜单";
    }
    static String label(Context context,String id){return id.startsWith("app:")?"打开 "+AppCatalog.label(context,id.substring(4)):label(id);}
}
