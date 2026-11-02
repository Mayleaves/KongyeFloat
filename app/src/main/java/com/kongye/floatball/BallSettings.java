package com.kongye.floatball;

import android.content.Context;
import android.content.SharedPreferences;

final class BallSettings {
    private BallSettings(){}
    static int percent(Context c){return Math.max(30,Math.min(500,Prefs.of(c).getInt("size_percent",BallScale.sizePercent(Prefs.of(c).getInt("size",36)))));}
    static float dp(Context c){return BallScale.dp(percent(c));}
    static int pixels(Context c){return Ui.dp(c,dp(c));}
    static int edge(Context c){SharedPreferences p=Prefs.of(c);if(p.contains("edge_percent"))return Math.max(30,Math.min(200,p.getInt("edge_percent",100)));int width=BallArt.dimensions(c,pixels(c))[0];return BallScale.edgePercent(Ui.dp(c,p.getInt("edge_gap",0)),width);}
    static void migrate(Context c){SharedPreferences p=Prefs.of(c);SharedPreferences.Editor e=p.edit();if(!p.contains("size_percent"))e.putInt("size_percent",percent(c));if(!p.contains("edge_percent"))e.putInt("edge_percent",edge(c));e.apply();}
}
