package com.kongye.floatball;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.*;

final class V121Checks {
    private static int checks;
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    private static Bitmap draw(Drawable art,int w,int h){Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);art.setBounds(0,0,w,h);art.draw(new Canvas(b));return b;}
    static void run(Activity a){checks=0;try{
        check(Prefs.label("settings").equals("打开空叶"),"short open label");
        Bitmap app=draw(a.getDrawable(R.drawable.ic_launcher),512,512);check(app.getPixel(60,60)==0xFFD5E3B8,"SVG icon pale quadrant");check(app.getPixel(450,70)==0xFF294233,"SVG icon dark quadrant");check(app.getPixel(60,450)==0xFF829F5B,"SVG icon green quadrant");check(app.getPixel(450,450)==0xFF9EC16E,"SVG icon arc");check(Color.alpha(app.getPixel(0,0))==0,"SVG rounded clip");app.recycle();
        for(int id:new int[]{R.drawable.plant_stage_1,R.drawable.plant_stage_2,R.drawable.plant_stage_3,R.drawable.plant_stage_4,R.drawable.plant_stage_5,R.drawable.plant_stage_6}){Bitmap plant=draw(a.getDrawable(id),128,128);int count=0;for(int y=0;y<128;y++)for(int x=0;x<128;x++)if(Color.alpha(plant.getPixel(x,y))>0)count++;check(count>100,"supplied plant SVG renders");plant.recycle();}
        LaunchIntroView intro=new LaunchIntroView(a,()->{});java.lang.reflect.Field frame=LaunchIntroView.class.getDeclaredField("elapsed");frame.setAccessible(true);frame.setFloat(intro,1600);intro.layout(0,0,1080,2400);Bitmap splash=Bitmap.createBitmap(1080,2400,Bitmap.Config.ARGB_8888);intro.draw(new Canvas(splash));check(splash.getPixel(0,0)==0xFFF4F7F0,"SVG splash backdrop");check(splash.getPixel(540,1000)!=0xFFF4F7F0,"SVG splash orb paints");check(splash.getPixel(420,900)!=0xFFF4F7F0,"SVG splash geometry paints");for(int yy=1320;yy<1376;yy+=4)for(int xx=420;xx<660;xx+=4)check(splash.getPixel(xx,yy)==0xFFF4F7F0,"splash status chip removed");splash.recycle();
        Prefs.of(a).edit().putString("qa_v121_result",checks+" checks passed").commit();
    }catch(Throwable e){Prefs.of(a).edit().putString("qa_v121_result",checks+" passed; FAILED "+e).commit();}}
    static void update(Activity a,String phase){a.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
        public void onActivityResumed(Activity page){if(!(page instanceof UpdateActivity))return;a.getApplication().unregisterActivityLifecycleCallbacks(this);try{java.lang.reflect.Method stop=UpdateActivity.class.getDeclaredMethod("stopTask");stop.setAccessible(true);stop.invoke(page);for(String key:new String[]{"phase","message","release"}){java.lang.reflect.Field field=UpdateActivity.class.getDeclaredField(key);field.setAccessible(true);field.set(page,key.equals("phase")?phase:key.equals("message")?(phase.equals("current")?"已是最新版本。":"发现新版本，可直接下载安装。"):new GitHubUpdate.Result("9.9.9",phase.equals("current")?0:1,"Kongye-test.apk","https://github.com/Mayleaves/KongyeFloat/releases/download/v9.9.9/Kongye-test.apk",1000,""));}java.lang.reflect.Method render=UpdateActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(page);}catch(Exception e){Prefs.of(a).edit().putString("qa_v121_update_error",e.toString()).apply();}}
        public void onActivityCreated(Activity x,Bundle b){}public void onActivityStarted(Activity x){}public void onActivityPaused(Activity x){}public void onActivityStopped(Activity x){}public void onActivitySaveInstanceState(Activity x,Bundle b){}public void onActivityDestroyed(Activity x){}
    });a.startActivity(new android.content.Intent(a,UpdateActivity.class));}
}
