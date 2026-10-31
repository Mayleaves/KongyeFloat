package com.kongye.floatball;
import android.content.*;import android.graphics.*;import android.view.*;import android.widget.*;
final class V1251Checks {
 static View findType(View view,Class<?> type){if(type.isInstance(view))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View child=findType(group.getChildAt(i),type);if(child!=null)return child;}return null;}
 static Bitmap icon(Context c,String name){Ui.Icon icon=new Ui.Icon(c,name,0xFF23533D);icon.layout(0,0,96,96);Bitmap b=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);icon.draw(new Canvas(b));return b;}
 static void run(Context c,String command)throws Exception{
  android.content.SharedPreferences.Editor out=Prefs.of(c).edit();
  if(command.equals("native")){
   Bitmap moon=icon(c,"theme"),quick=icon(c,"quick_settings"),menu=icon(c,"menu");
   if(Color.alpha(moon.getPixel(24,56))!=0||Color.alpha(moon.getPixel(8,48))<240)throw new AssertionError("moon must be outlined, with empty body");
   if(Color.alpha(quick.getPixel(68,28))<240||Color.alpha(menu.getPixel(68,28))!=0||Color.alpha(menu.getPixel(24,28))<240||Color.alpha(quick.getPixel(24,28))!=0)throw new AssertionError("top knob positions");
   if(Color.alpha(quick.getPixel(28,56))<240||Color.alpha(menu.getPixel(28,56))!=0||quick.sameAs(menu))throw new AssertionError("three knobs vs two");
   moon.recycle();quick.recycle();menu.recycle();out.putString("qa_v1251_native","3 checks passed").commit();return;
  }
  MainActivity page=V1231Checks.main();View root=page.getWindow().getDecorView();
  if(command.equals("snapshot")){
   PlantGreeting bubble=(PlantGreeting)findType(root,PlantGreeting.class);if(bubble!=null)out.putFloat("qa_v1251_bubble_alpha",bubble.getAlpha()).putLong("qa_v1251_bubble_due",(Long)V1231Checks.get(bubble,"nextGreetingAt")-android.os.SystemClock.uptimeMillis()).putBoolean("qa_v1251_bubble_scheduled",(Boolean)V1231Checks.get(bubble,"scheduled"));
   View snap=V124Checks.find(root,"自动吸边");if(snap!=null){View row=(View)snap.getParent(),box=(View)row.getParent(),card=(View)box.getParent().getParent();out.putInt("qa_v1251_snap_top",row.getPaddingTop()).putInt("qa_v1251_snap_bottom",row.getPaddingBottom()).putInt("qa_v1251_snap_box_bottom",box.getPaddingBottom()).putInt("qa_v1251_card_bottom",card.getPaddingBottom());}
  }
  out.commit();
 }
}
