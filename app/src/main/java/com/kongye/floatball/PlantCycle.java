package com.kongye.floatball;

/** A daily plant cycle using the phone's local clock; no alarms or background service. */
public final class PlantCycle {
    private PlantCycle(){}
    public static int stage(int hour){int h=Math.floorMod(hour,24);if(h<6||h>=23)return 5;if(h>=19)return 4;if(h>=16)return 3;if(h>=12)return 2;return (h-6)/3;}
}
