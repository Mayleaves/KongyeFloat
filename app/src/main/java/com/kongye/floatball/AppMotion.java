package com.kongye.floatball;
import android.app.Activity;import android.content.*;import android.graphics.Color;import android.view.*;import android.view.animation.PathInterpolator;import java.lang.ref.WeakReference;import java.util.*;
/** Main and About belong to one visible app; move both live layers over the real background. */
final class AppMotion {
 private static final Set<Activity> pages=Collections.newSetFromMap(new WeakHashMap<>());private static WeakReference<Activity> visible=new WeakReference<>(null),last=new WeakReference<>(null);private static boolean closing;
 static void resume(Activity page){pages.add(page);visible=new WeakReference<>(page);last=new WeakReference<>(page);prepare(page);}
 static void pause(Activity page){if(visible.get()==page)visible.clear();}
 static void destroy(Activity page){pages.remove(page);if(visible.get()==page)visible.clear();if(last.get()==page)last.clear();}
 private static boolean alive(Activity a){return a!=null&&!a.isFinishing()&&!a.isDestroyed();}
 static Intent openIntent(Context c){Activity page=last.get();Class<?> destination=alive(page)&&page instanceof AboutActivity?AboutActivity.class:MainActivity.class;return new Intent(c,destination).putExtra("gesture_entry",true).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);}
 private static void prepare(Activity page){page.getWindow().setBackgroundDrawableResource(android.R.color.transparent);page.getWindow().getDecorView().setBackgroundColor(Color.TRANSPARENT);page.getWindow().setStatusBarColor(Color.TRANSPARENT);page.getWindow().setNavigationBarColor(Color.TRANSPARENT);page.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);}
 private static View content(Activity page){return page.findViewById(android.R.id.content);}
 private static ArrayList<Activity> group(){ArrayList<Activity> group=new ArrayList<>();for(Activity a:new ArrayList<>(pages))if(alive(a))group.add(a);return group;}
 static boolean hideIfVisible(){Activity top=visible.get();if(!alive(top))return false;if(closing)return true;closing=true;last=new WeakReference<>(top);for(Activity page:group()){prepare(page);View view=content(page);if(view==null)continue;view.animate().cancel();ViewPropertyAnimator motion=view.animate().translationY(-page.getResources().getDisplayMetrics().heightPixels).alpha(1).scaleX(1).scaleY(1).setDuration(650).setInterpolator(new PathInterpolator(.22f,1,.36f,1));if(page==top)motion.withEndAction(()->{if(alive(top)){top.moveTaskToBack(true);top.overridePendingTransition(0,0);}closing=false;});motion.start();}return true;}
 static void enter(Activity top){resume(top);closing=false;for(Activity page:group()){prepare(page);View view=content(page);if(view==null)continue;view.animate().cancel();view.setTranslationY(page.getResources().getDisplayMetrics().heightPixels);view.setAlpha(1);view.setScaleX(1);view.setScaleY(1);view.animate().translationY(0).setDuration(650).setInterpolator(new PathInterpolator(.22f,1,.36f,1)).start();}}
 static void reset(Activity page){View view=content(page);if(view!=null){view.animate().cancel();view.setTranslationY(0);view.setAlpha(1);view.setScaleX(1);view.setScaleY(1);}}
}
