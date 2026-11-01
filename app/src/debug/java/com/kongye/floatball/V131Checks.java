package com.kongye.floatball;
import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;
/** Own-view animation scalars and in-memory renders only. */
final class V131Checks {
 static Object get(Object o,String f)throws Exception{return V126Checks.get(o,f);}
 static void run(Context c,String cmd)throws Exception{
  MainActivity activity=V1231Checks.main();String target=cmd.equals("home")?"首页":"设置";View nav=V124Checks.find(activity.getWindow().getDecorView(),"底部悬浮导航");V124Checks.find(nav,target).performClick();
  LinearLayout page=(LinearLayout)get(activity,"content"),shell=(LinearLayout)get(activity,"shell");View header=shell.getChildAt(0),body=shell.getChildAt(1),plant=(View)get(activity,"perchedPlant"),group=(View)get(activity,"plantEntrance");JSONArray frames=new JSONArray();Handler handler=new Handler(Looper.getMainLooper());
  for(int delay:new int[]{0,90,220,380,900})handler.postDelayed(()->{try{JSONObject f=new JSONObject();f.put("at",delay).put("header_z",header.getZ()).put("body_z",body.getZ()).put("page_alpha",page.getAlpha());
   if(plant!=null){int[] potAt=new int[2],cardAt=new int[2];plant.getLocationOnScreen(potAt);page.getChildAt(0).getLocationOnScreen(cardAt);f.put("pot_bottom_screen",potAt[1]+plant.getHeight()*group.getScaleY()).put("card_top_screen",cardAt[1]);android.graphics.RectF bounds=new android.graphics.RectF(0,0,plant.getWidth(),plant.getHeight());plant.getMatrix().mapRect(bounds);bounds.offset(plant.getLeft(),plant.getTop());f.put("plant_bottom",bounds.bottom).put("group_height",group.getHeight()).put("plant_top",bounds.top).put("plant_alpha",group.getAlpha()).put("plant_x",group.getTranslationX()).put("page_x",page.getTranslationX()).put("group_clips",((ViewGroup)group).getClipChildren()).put("header_clips",((ViewGroup)header).getClipChildren());
    android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(shell.getWidth(),shell.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);shell.draw(new android.graphics.Canvas(bitmap));bitmap.recycle();
   }
   JSONArray modules=new JSONArray();for(int i=0;i<page.getChildCount();i++){View child=page.getChildAt(i);if(child.getClass()==View.class)continue;JSONObject item=new JSONObject().put("alpha",child.getAlpha()).put("y",child.getTranslationY()).put("label",child instanceof TextView?((TextView)child).getText():child.getContentDescription());modules.put(item);}f.put("modules",modules);frames.put(f);if(delay==900)Prefs.of(c).edit().putString("qa_v131_"+cmd,frames.toString()).commit();
  }catch(Exception e){throw new RuntimeException(e);}},delay);
 }
}
