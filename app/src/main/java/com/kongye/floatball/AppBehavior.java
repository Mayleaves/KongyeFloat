package com.kongye.floatball;
import android.content.Context;
final class AppBehavior {
    static String mode(Context c,String pkg){return pkg==null?"normal":Prefs.of(c).getString("app_mode_"+pkg,"normal");}
    static String label(String mode){return mode.equals("disabled")?"禁用":mode.equals("hidden")?"隐身，可操作":"正常显示，可操作";}
}
