package com.kongye.floatball;
import android.content.*;import android.graphics.*;import android.view.*;import android.widget.*;import org.json.*;
final class V124Checks {
 static View find(View root,String label){if(label.equals(String.valueOf(root.getContentDescription()))||root instanceof TextView t&&t.getText().toString().equals(label))return root;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),label);if(found!=null)return found;}return null;}
 static void run(Context c,String cmd)throws Exception{
  android.content.SharedPreferences.Editor out=Prefs.of(c).edit();MainActivity page=V1231Checks.main();View root=page==null?null:page.getWindow().getDecorView();
  if(cmd.equals("native")){int count=0;LeafSwitch sw=new LeafSwitch(page==null?c:page);int w=Ui.dp(c,38),h=Ui.dp(c,32);sw.layout(0,0,w,h);Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);for(boolean on:new boolean[]{false,true}){sw.setChecked(on);b.eraseColor(0);sw.draw(new Canvas(b));int thumb=b.getPixel(Ui.dp(c,on?27:11),Ui.dp(c,16));if(thumb!=Color.WHITE)throw new AssertionError("white thumb");int rail=b.getPixel(Ui.dp(c,on?8:32),Ui.dp(c,16));if(rail!=(on?0xFF28664B:0xFFD7E0D5))throw new AssertionError("track "+Integer.toHexString(rail));count+=2;}b.recycle();
   Ui.Icon moon=new Ui.Icon(c,"theme",Ui.GREEN);moon.layout(0,0,96,96);b=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);moon.draw(new Canvas(b));if(Color.alpha(b.getPixel(24,56))!=0||Color.alpha(b.getPixel(8,48))<240)throw new AssertionError("outlined crescent");b.recycle();count++;
   String home="返回|主页|最近任务|通知栏|快捷设置|锁屏|整屏截图|区域截图|长截图|滚动截图|录屏|录制 GIF|最近软件|常用软件|应用启动|浏览器|便签|打开文件|打开空叶";String actual=String.join("|",java.util.Arrays.stream(HomeTools.AVAILABLE).map(HomeTools::label).toArray(String[]::new));if(!home.equals(actual))throw new AssertionError("home order");count++;
   String choices="不执行|边缘快捷栏|快捷菜单|打开空叶|返回|主页|最近任务|通知栏|快捷设置|锁屏|整屏截图|区域截图|长截图|滚动截图|录屏|录制 GIF|最近软件|常用软件|应用启动|指定一个软件...|悬浮浏览器|便签|打开文件";java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("gestureChoices");m.setAccessible(true);if(!choices.equals(String.join("|",(String[])m.invoke(page==null?new MainActivity():page))))throw new AssertionError("gesture order");count++;if(!Prefs.gestureActions()[19].equals("pick_app"))throw new AssertionError("picker sentinel");count++;
   out.putString("qa_v124_native",count+" checks passed");
  }
  if(cmd.equals("snapshot")){
   LinearLayout content=(LinearLayout)V1231Checks.get(page,"content");out.putFloat("qa_v124_page_alpha",content.getAlpha()).putFloat("qa_v124_page_x",content.getTranslationX()).putFloat("qa_v124_nav",(float)V1231Checks.get(page,"navPosition"));
   if(find(root,"外观与位置设置组")!=null){View tap=find(root,"单击"),reset=find(root,"恢复默认手势"),haptic=find(root,"触感反馈");out.putInt("qa_v124_tap_height",((View)tap.getParent().getParent()).getHeight()).putInt("qa_v124_reset_height",reset.getHeight()).putInt("qa_v124_reset_color",((TextView)reset).getCurrentTextColor()).putString("qa_v124_switch_class",haptic.getClass().getSimpleName());View bar=find(root,"大小");int seek=0;for(int i=0;i<content.getChildCount();i++)seek+=countSeek(content.getChildAt(i));out.putInt("qa_v124_seek_count",seek);}
  }
  out.commit();
 }
 static int countSeek(View v){int count=v instanceof LeafSeekBar?1:0;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)count+=countSeek(g.getChildAt(i));return count;}
}
