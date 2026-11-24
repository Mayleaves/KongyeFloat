package com.kongye.floatball;

import android.content.Context;
import android.content.SharedPreferences;

final class BallSettings {
    static final String EDGE_REFERENCE_SIZE = "edge_reference_size";
    private BallSettings(){}
    static int percent(Context c){return Math.max(30,Math.min(500,Prefs.of(c).getInt("size_percent",BallScale.sizePercent(Prefs.of(c).getInt("size",36)))));}
    static float dp(Context c){return BallScale.dp(percent(c));}
    static int pixels(Context c){return Ui.dp(c,dp(c));}
    static int edge(Context c){SharedPreferences p=Prefs.of(c);if(p.contains("edge_percent"))return Math.max(30,Math.min(200,p.getInt("edge_percent",100)));int width=BallArt.dimensions(c,pixels(c))[0];return BallScale.edgePercent(Ui.dp(c,p.getInt("edge_gap",0)),width);}
    static void setSize(Context context, int percent) {
        SharedPreferences prefs = Prefs.of(context);
        SharedPreferences.Editor edit = prefs.edit().putInt("size_percent", percent);
        if (!prefs.contains(EDGE_REFERENCE_SIZE)) {
            edit.putInt(EDGE_REFERENCE_SIZE, percent(context));
        }
        edit.apply();
    }
    static int edgeInset(Context context, int extent, boolean vertical, boolean customArt) {
        SharedPreferences prefs = Prefs.of(context);
        int reference = extent;
        if (prefs.contains(EDGE_REFERENCE_SIZE)) {
            int size = Ui.dp(context, BallScale.dp(prefs.getInt(EDGE_REFERENCE_SIZE, percent(context))));
            reference = customArt ? BallArt.dimensions(context, size)[vertical ? 1 : 0] : size;
        }
        int distance = BallScale.inset(edge(context), reference);
        // An inset that hides part of a large ball must still leave a smaller ball reachable.
        return Math.max(distance, BallScale.inset(30, extent));
    }
    static void migrate(Context c){SharedPreferences p=Prefs.of(c);SharedPreferences.Editor e=p.edit();if(!p.contains("size_percent"))e.putInt("size_percent",percent(c));if(!p.contains("edge_percent"))e.putInt("edge_percent",edge(c));e.apply();}
}
