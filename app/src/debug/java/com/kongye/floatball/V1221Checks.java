package com.kongye.floatball;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.lang.reflect.*;

/** Generated geometry, release metadata and animation scalars; no phone media. */
final class V1221Checks {
    static AboutActivity about;static Activity page;
    private static Object field(Object target,String key)throws Exception{Field f=target.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(target);}
    private static void set(Object target,String key,Object value)throws Exception{Field f=target.getClass().getDeclaredField(key);f.setAccessible(true);f.set(target,value);}
    static void snapshot(Context c){try{
        android.content.SharedPreferences.Editor out=Prefs.of(c).edit().putBoolean("qa_motion_allowed",android.animation.ValueAnimator.areAnimatorsEnabled());Field visible=MainActivity.class.getDeclaredField("visible");visible.setAccessible(true);MainActivity page=(MainActivity)((java.lang.ref.WeakReference<?>)visible.get(null)).get();
        if(page!=null){PlantStageView plant=(PlantStageView)field(page,"perchedPlant");if(plant!=null){android.animation.ValueAnimator motion=(android.animation.ValueAnimator)field(plant,"swayAnimator");out.putBoolean("qa_plant_motion",motion!=null&&motion.isRunning()).putFloat("qa_plant_sway",(Float)field(plant,"sway")).putInt("qa_plant_visibility",plant.getVisibility());}}
        if(about!=null&&!about.isDestroyed()){AboutLogoView logo=(AboutLogoView)field(about,"logo");View orb=(View)field(logo,"orb");android.animation.ObjectAnimator a=(android.animation.ObjectAnimator)field(logo,"floating");out.putBoolean("qa_logo_motion",a!=null&&a.isRunning()).putFloat("qa_logo_y",orb.getTranslationY()).putFloat("qa_logo_rotation",orb.getRotation());}if(V1221Checks.page instanceof UpdateActivity){Object emblem=field(V1221Checks.page,"emblem");if(emblem!=null){android.animation.ValueAnimator pulse=(android.animation.ValueAnimator)field(emblem,"pulse");out.putBoolean("qa_update_pulse_running",pulse!=null&&pulse.isRunning()).putFloat("qa_update_pulse_phase",(Float)field(emblem,"phase"));}}out.commit();
    }catch(Exception e){Prefs.of(c).edit().putString("qa_v1221_error",e.toString()).commit();}}
    static void openAbout(Activity a){a.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
        public void onActivityResumed(Activity page){if(page instanceof AboutActivity){about=(AboutActivity)page;V1221Checks.page=page;a.getApplication().unregisterActivityLifecycleCallbacks(this);}}
        public void onActivityCreated(Activity p,Bundle b){}public void onActivityStarted(Activity p){}public void onActivityPaused(Activity p){}public void onActivityStopped(Activity p){}public void onActivitySaveInstanceState(Activity p,Bundle b){}public void onActivityDestroyed(Activity p){}
    });a.startActivity(new Intent(a,AboutActivity.class));}
    static void nativeChecks(Activity a){int count=0;try{
        String json="{\"tag_name\":\"v9.9.9\",\"body\":\"• First real release note\\n• Second note\",\"assets\":[]}";GitHubUpdate.Result r=GitHubUpdate.parse(json,"1.22.0");if(!r.notes.contains("First real release note")||r.comparison<=0)throw new AssertionError("release notes");count++;
        String oversized="x".repeat(15000);if(new GitHubUpdate.Result("9",1,"","",0,"",oversized).notes.length()!=12000)throw new AssertionError("notes bound");count++;
        android.widget.FrameLayout ball=new android.widget.FrameLayout(a);BallArt.styleContainer(ball,false);ball.layout(0,0,144,144);Bitmap b=Bitmap.createBitmap(144,144,Bitmap.Config.ARGB_8888);ball.draw(new Canvas(b));int center=b.getPixel(72,72);if(Color.alpha(center)!=166)throw new AssertionError("HTML ball fill alpha");count++;if(Math.abs(Color.red(center)-Color.red(BallArt.color(a)))>1||Math.abs(Color.green(center)-Color.green(BallArt.color(a)))>1||Math.abs(Color.blue(center)-Color.blue(BallArt.color(a)))>1)throw new AssertionError("HTML ball fill color");count++;if(Color.alpha(b.getPixel(0,0))!=0)throw new AssertionError("round corners");count++;b.recycle();
        android.widget.FrameLayout custom=new android.widget.FrameLayout(a);BallArt.styleContainer(custom,true);if(custom.getBackground()!=null||custom.getElevation()!=0)throw new AssertionError("custom art retained");count++;
        Prefs.of(a).edit().putString("qa_v1221_native",count+" checks passed").commit();
    }catch(Throwable e){Prefs.of(a).edit().putString("qa_v1221_native",count+" passed; FAILED "+e).commit();}}
    static void update(Activity a,String phase){a.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
        public void onActivityResumed(Activity page){if(!(page instanceof UpdateActivity))return;V1221Checks.page=page;a.getApplication().unregisterActivityLifecycleCallbacks(this);try{Method stop=UpdateActivity.class.getDeclaredMethod("stopTask");stop.setAccessible(true);stop.invoke(page);set(page,"phase",phase.equals("available_long")?"available":phase.equals("current_ahead")?"current":phase);set(page,"message",phase.startsWith("current")?"已是最新版本。":phase.equals("error")?"测试网络错误，请重试。":phase.equals("downloading")?"正在下载安装包…":phase.equals("ready")?"下载完成，安装包校验通过。":"发现新版本，可直接下载安装。");String version=a.getPackageManager().getPackageInfo(a.getPackageName(),0).versionName;set(page,"release",phase.equals("error")||phase.equals("checking")?null:new GitHubUpdate.Result(phase.equals("current_ahead")?"1.19.2":phase.equals("current")?version:"9.9.9",phase.equals("current_ahead")?-1:phase.equals("current")?0:1,"Kongye-test.apk","https://github.com/Mayleaves/KongyeFloat/releases/download/v9.9.9/Kongye-test.apk",4404019,"",phase.equals("available_long")?"• Generated long release note\n".repeat(100):"• Generated release note A\n• Generated release note B"));if(phase.equals("downloading"))set(page,"received",2202009L);Method render=UpdateActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(page);}catch(Exception e){Prefs.of(a).edit().putString("qa_v1221_error",e.toString()).apply();}}
        public void onActivityCreated(Activity p,Bundle b){}public void onActivityStarted(Activity p){}public void onActivityPaused(Activity p){}public void onActivityStopped(Activity p){}public void onActivitySaveInstanceState(Activity p,Bundle b){}public void onActivityDestroyed(Activity p){}
    });a.startActivity(new Intent(a,UpdateActivity.class));}
}
