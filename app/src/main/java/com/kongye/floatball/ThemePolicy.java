package com.kongye.floatball;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Calendar;
import java.util.Locale;

final class ThemePolicy {
    static final String[] MODES={"system","dark","light","schedule"};
    static final String[] LABELS={"跟随系统","深色模式","浅色模式","定时开启"};
    static boolean isDark(Context c){
        return switch(Prefs.of(c).getString("theme","system")){
            case "dark"->true;
            case "light"->false;
            case "schedule"->{Calendar time=Calendar.getInstance();yield ThemeSchedule.isDark(time.get(Calendar.HOUR_OF_DAY)*60+time.get(Calendar.MINUTE),Prefs.of(c).getInt("dark_start",1320),Prefs.of(c).getInt("dark_end",420));}
            default->(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        };
    }
    static boolean apply(Context c){
        boolean dark=isDark(c),changed=Ui.dark!=dark;Ui.dark=dark;
        Ui.BG=dark?0xFF101613:0xFFF3F5F2;Ui.SURFACE=dark?0xFF1B231F:0xFFFFFFFF;
        Ui.INK=dark?0xFFE6EEE9:0xFF18261F;Ui.MUTED=dark?0xFF97A59D:0xFF6B7871;
        Ui.GREEN=dark?0xFF83CCA8:Ui.BRAND;Ui.PALE=dark?0xFF23372C:0xFFE5F0E8;
        Ui.LINE=dark?0xFF2A3530:0xFFE9EDE8;
        return changed;
    }
    static String label(Context c){String mode=Prefs.of(c).getString("theme","system");for(int i=0;i<MODES.length;i++)if(MODES[i].equals(mode))return LABELS[i];return LABELS[0];}
    static String time(int minute){return String.format(Locale.getDefault(),"%02d:%02d",minute/60,minute%60);}
}
