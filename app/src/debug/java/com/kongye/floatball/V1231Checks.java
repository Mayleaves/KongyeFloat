package com.kongye.floatball;
import android.content.*;import android.graphics.*;import android.view.*;import android.widget.*;import java.lang.reflect.*;
final class V1231Checks {
 static Object get(Object o,String key)throws Exception{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
 static void set(Object o,String key,Object value)throws Exception{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);f.set(o,value);}
 static MainActivity main()throws Exception{Field f=MainActivity.class.getDeclaredField("visible");f.setAccessible(true);return (MainActivity)((java.lang.ref.WeakReference<?>)f.get(null)).get();}
 static boolean text(View v,String s){if(v instanceof TextView t&&t.getText().toString().contains(s))return true;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)if(text(g.getChildAt(i),s))return true;return false;}
 static void checkTint(View view,int[] count){if(view instanceof Ui.Icon i&&!i.name.startsWith("nav_")&&!i.name.equals("leaf")&&!i.name.equals("ball_leaf")&&!i.name.equals("status_play")&&!i.name.equals("status_pause")){if(i.color!=Ui.GREEN)throw new AssertionError("Icon tint: "+i.name);count[0]++;}if(view instanceof ViewGroup group)for(int j=0;j<group.getChildCount();j++)checkTint(group.getChildAt(j),count);}
 static void run(Context c,String cmd)throws Exception{
  if(cmd.equals("icons")){
   MainActivity page=main();android.view.View decor=page.getWindow().getDecorView();int[] count={0};checkTint(decor,count);
   Bitmap flower=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888),gear=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);Ui.Icon f=new Ui.Icon(c,"nav_settings",Ui.GREEN),g=new Ui.Icon(c,"settings",Ui.GREEN);f.layout(0,0,96,96);g.layout(0,0,96,96);f.draw(new Canvas(flower));g.draw(new Canvas(gear));if(Color.alpha(flower.getPixel(48,48))!=0||Color.alpha(flower.getPixel(48,18))<240||flower.sameAs(gear))throw new AssertionError("flower/gear distinction");flower.recycle();gear.recycle();Prefs.of(c).edit().putInt("qa_v1232_tints",count[0]).putBoolean("qa_v1232_flower",true).commit();return;
  }
  if(cmd.equals("native")){int count=0;
   for(int w:new int[]{333,362,399,400,401,417,1081})for(boolean active:new boolean[]{true,false}){int h=Math.round(w*.4375f);BotanicalPanel panel=new BotanicalPanel(c);panel.layout(0,0,w,h);panel.running(active,false);Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);panel.draw(new Canvas(b));int x=Math.round(w*2.5f/4),y=Math.round(h/2f);for(int yy=y-2;yy<=y+2;yy++){int pixel=b.getPixel(x,yy);if(pixel!=0xFFB5CF83&&pixel!=0xFFB2CF78)throw new AssertionError("seam "+w+" "+Integer.toHexString(pixel));count++;}for(int xx=w/2-2;xx<=w/2+2;xx++)if(Color.alpha(b.getPixel(xx,h/4))!=255)throw new AssertionError("vertical alpha");b.recycle();count++;}
   for(int stage=0;stage<6;stage++){PlantScene scene=new PlantScene(c,stage);Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);for(int time:new int[]{0,500,1500,3000,5900}){b.eraseColor(0);scene.draw(canvas,time);if(Color.alpha(b.getPixel(32,52))==0)throw new AssertionError("pot missing "+stage);count++;}b.recycle();}
   for(boolean[] status:new boolean[][]{{true,true,true},{true,false,false},{false,false,false}}){int[] calls={0};PermissionStrip strip=new PermissionStrip(c,status,new Runnable[]{()->calls[0]++,()->calls[0]++,()->calls[0]++});boolean missing=!status[0]||!status[1]||!status[2];if(text(strip,"一键开启")!=missing)throw new AssertionError("permission hint");LinearLayout row=(LinearLayout)strip.getChildAt(0);if(row.getChildCount()!=3)throw new AssertionError("three chips");row.getChildAt(1).performClick();if(calls[0]!=1)throw new AssertionError("permission routing");count+=3;}
   Prefs.of(c).edit().putString("qa_v1231_native",count+" checks passed").commit();return;
  }
  android.content.SharedPreferences.Editor out=Prefs.of(c).edit();
  if(cmd.equals("jump")){PlantStageView plant=(PlantStageView)get(main(),"perchedPlant");plant.performClick();return;}
  if(cmd.equals("stages")){PlantStageView plant=(PlantStageView)get(main(),"perchedPlant");int count=0;for(int i=0;i<6;i++){set(plant,"scene",new PlantScene(c,i));set(plant,"shown",i);plant.jump();count++;}plant.invalidate();out.putInt("qa_v1231_stages",count);}
  if(cmd.equals("snapshot")){
   PlantStageView plant=(PlantStageView)get(main(),"perchedPlant");if(plant!=null){out.putBoolean("qa_v1231_scene",get(plant,"scene")!=null).putFloat("qa_v1231_jump",(float)get(plant,"jumpProgress")).putFloat("qa_v1231_clock",(float)get(plant,"sway")).putInt("qa_v1231_stage",(int)get(plant,"shown"));android.animation.ValueAnimator a=(android.animation.ValueAnimator)get(plant,"swayAnimator");out.putBoolean("qa_v1231_motion",a!=null&&a.isRunning());}
   if(FloatService.instance!=null){RecoveryPulse pulse=(RecoveryPulse)get(FloatService.instance,"recoveryPulse");View ball=(View)get(FloatService.instance,"ball");out.putBoolean("qa_v1231_pulse",pulse!=null);if(pulse!=null){int[] p=new int[2],b=new int[2];pulse.getLocationOnScreen(p);ball.getLocationOnScreen(b);out.putFloat("qa_v1231_dx",pulse.x+p[0]-(b[0]+ball.getWidth()*ball.getScaleX()/2f)).putFloat("qa_v1231_dy",pulse.y+p[1]-(b[1]+ball.getHeight()*ball.getScaleY()/2f));WindowManager.LayoutParams wp=(WindowManager.LayoutParams)pulse.getLayoutParams();out.putBoolean("qa_v1231_passthrough",(wp.flags&WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)!=0);}}
  }out.commit();
 }
}
