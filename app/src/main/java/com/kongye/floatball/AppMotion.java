package com.kongye.floatball;
import android.app.Activity;import android.content.*;import android.graphics.Color;import android.view.*;import android.view.animation.PathInterpolator;import java.lang.ref.WeakReference;import java.util.*;
/** Main and About belong to one visible app; move both live layers over the real background. */
final class AppMotion {
 static final long SLIDE_MS=280;
 static final PathInterpolator SLIDE_CURVE=new PathInterpolator(.25f,.25f,.60f,1);
 private static final Set<Activity> pages=Collections.newSetFromMap(new WeakHashMap<>());private static WeakReference<Activity> visible=new WeakReference<>(null),last=new WeakReference<>(null);private static boolean closing;private static AppExitLayer exitLayer;
 static void resume(Activity page){pages.add(page);prepare(page);if(!closing){show(page);visible=new WeakReference<>(page);last=new WeakReference<>(page);}}
 static void pause(Activity page){if(visible.get()==page)visible.clear();}
 static void destroy(Activity page){pages.remove(page);if(visible.get()==page)visible.clear();if(last.get()==page)last.clear();}
 private static boolean alive(Activity a){return a!=null&&!a.isFinishing()&&!a.isDestroyed();}
 static boolean isShowing(){return alive(visible.get());}
 static Intent openIntent(Context c){Activity page=last.get();Class<?> destination=alive(page)&&page instanceof AboutActivity?AboutActivity.class:MainActivity.class;return new Intent(c,destination).putExtra("gesture_entry",true).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION);}
 static void open(Context c){c.startActivity(openIntent(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),android.app.ActivityOptions.makeCustomAnimation(c,0,0).toBundle());}
 private static void prepare(Activity page){page.getWindow().setWindowAnimations(0);if(android.os.Build.VERSION.SDK_INT>=34){page.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN,0,0);page.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE,0,0);}page.getWindow().setBackgroundDrawableResource(android.R.color.transparent);page.getWindow().getDecorView().setBackgroundColor(Color.TRANSPARENT);page.getWindow().setStatusBarColor(Color.TRANSPARENT);page.getWindow().setNavigationBarColor(Color.TRANSPARENT);page.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);page.getWindow().setFormat(android.graphics.PixelFormat.TRANSLUCENT);}
 private static void show(Activity page){page.setVisible(true);WindowManager.LayoutParams at=page.getWindow().getAttributes();if(at.alpha!=1){at.alpha=1;page.getWindow().setAttributes(at);}}
 private static void mask(){for(Activity page:group()){WindowManager.LayoutParams at=page.getWindow().getAttributes();at.alpha=0;page.getWindow().setAttributes(at);page.setVisible(false);}}
 private static View content(Activity page){return page.findViewById(android.R.id.content);}
 private static ArrayList<Activity> group(){ArrayList<Activity> group=new ArrayList<>();for(Activity a:new ArrayList<>(pages))if(alive(a))group.add(a);return group;}
 private static boolean toBack(Activity top){if(!alive(top))return false;top.getIntent().addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);top.getWindow().setWindowAnimations(0);boolean moved=top.moveTaskToBack(true);top.overridePendingTransition(0,0);if(android.os.Build.VERSION.SDK_INT>=34)top.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE,0,0);return moved;}
 static boolean hideIfVisible(){Activity top=visible.get();if(!alive(top))return false;if(closing)return true;closing=true;last=new WeakReference<>(top);for(Activity page:group()){prepare(page);View root=content(page);if(root!=null)root.animate().cancel();}
  exitLayer=new AppExitLayer(top,AppMotion::mask,()->toBack(top),()->{exitLayer=null;closing=false;},()->{exitLayer=null;for(Activity page:group())show(page);slideWithoutOverlay(top);});exitLayer.begin();return true;
 }
 private static void slideWithoutOverlay(Activity top){for(Activity page:group()){View view=content(page);if(view==null)continue;view.animate().cancel();ViewPropertyAnimator motion=view.animate().translationY(-page.getResources().getDisplayMetrics().heightPixels).alpha(1).scaleX(1).scaleY(1).setDuration(android.animation.ValueAnimator.areAnimatorsEnabled()?SLIDE_MS:0).setInterpolator(SLIDE_CURVE);if(page==top)motion.withEndAction(()->view.postOnAnimation(()->{toBack(top);closing=false;}));motion.start();}}
 static void enter(Activity top){if(exitLayer!=null){exitLayer.cancel();exitLayer=null;}closing=false;resume(top);for(Activity page:group()){prepare(page);View view=content(page);if(view==null)continue;view.animate().cancel();view.setTranslationY(page.getResources().getDisplayMetrics().heightPixels);view.setAlpha(1);view.setScaleX(1);view.setScaleY(1);show(page);view.animate().translationY(0).setDuration(SLIDE_MS).setInterpolator(SLIDE_CURVE).start();}}
 static void reset(Activity page){View view=content(page);if(view!=null){view.animate().cancel();view.setTranslationY(0);view.setAlpha(1);view.setScaleX(1);view.setScaleY(1);}}
}
