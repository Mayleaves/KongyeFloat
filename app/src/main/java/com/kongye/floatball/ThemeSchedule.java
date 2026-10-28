package com.kongye.floatball;

/** Local-phone-clock schedule, including ranges that cross midnight. */
public final class ThemeSchedule {
    private ThemeSchedule() {}
    public static boolean isDark(int minute,int start,int end) {
        minute=Math.floorMod(minute,1440);start=Math.floorMod(start,1440);end=Math.floorMod(end,1440);
        if(start==end)return true;
        return start<end ? minute>=start&&minute<end : minute>=start||minute<end;
    }
}
