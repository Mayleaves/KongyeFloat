package com.kongye.floatball;

import android.app.Activity;
import android.graphics.*;

/** Pixel assertions for the phone/scissors glyph and the supplied pot baseline. */
final class V1211Checks {
    private static int count;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);count++;}
    static void run(Activity a){count=0;try{
        Ui.Icon icon=new Ui.Icon(a,"screenshot",Ui.GREEN);icon.layout(0,0,100,100);Bitmap b=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);icon.draw(new Canvas(b));check(Color.alpha(b.getPixel(64,85))>0,"SVG phone bottom bezel");check(Color.alpha(b.getPixel(84,50))>0,"SVG phone right edge");check(Color.alpha(b.getPixel(64,45))==0,"SVG phone display cutout");check(Color.alpha(b.getPixel(24,36))==0&&Color.alpha(b.getPixel(24,64))==0,"SVG scissors hollow handles");int thickness=0;for(int x=70;x<100;x++)if(Color.alpha(b.getPixel(x,50))>127)thickness++;check(thickness>=6&&thickness<=8,"phone stroke matches shared 1.7/24 weight");b.recycle();
        for(int resource:new int[]{R.drawable.plant_stage_1,R.drawable.plant_stage_2,R.drawable.plant_stage_3,R.drawable.plant_stage_4,R.drawable.plant_stage_5,R.drawable.plant_stage_6}){PlantStageView plant=new PlantStageView(a,true);java.lang.reflect.Field art=PlantStageView.class.getDeclaredField("drawable");art.setAccessible(true);art.set(plant,a.getDrawable(resource));plant.layout(0,0,192,192);Bitmap image=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888);plant.draw(new Canvas(image));int bottom=-1;for(int y=0;y<192;y++)for(int x=0;x<192;x++)if(Color.alpha(image.getPixel(x,y))>8)bottom=Math.max(bottom,y);check(bottom>=190,"pot baseline aligns to bottom for each stage");image.recycle();}
        Prefs.of(a).edit().putString("qa_v1211_result",count+" checks passed").commit();
    }catch(Throwable e){Prefs.of(a).edit().putString("qa_v1211_result",count+" passed; FAILED "+e).commit();}}
}
