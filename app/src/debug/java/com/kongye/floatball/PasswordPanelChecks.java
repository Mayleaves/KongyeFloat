package com.kongye.floatball;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import java.lang.reflect.*;
import java.util.ArrayList;

/** Generated visual fixtures only; no vault IPC or password reveal. */
final class PasswordPanelChecks {
    static PasswordFillController controller;
    static Object get(String name)throws Exception{Field f=PasswordFillController.class.getDeclaredField(name);f.setAccessible(true);return f.get(controller);}
    static void call(String name,Class<?>[] types,Object...args)throws Exception{Method m=PasswordFillController.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(controller,args);}
    static void show(Activity c,String mode)throws Exception{
        close();controller=new PasswordFillController(c);
        Field field=PasswordFillController.class.getDeclaredField("destination");field.setAccessible(true);
        PasswordTarget.Field input=new PasswordTarget.Field("/1","fixture",new Rect(32,280,c.getResources().getDisplayMetrics().widthPixels-32,400));
        field.set(controller,new PasswordTarget.Snapshot("com.example.fixture","example.com",1,PageRules.Kind.LOGIN,input,input,null,null,input,""));
        ArrayList<Bundle> entries=new ArrayList<>();Bundle entry=new Bundle();entry.putString("id","generated-only");entry.putString("name","示例账号（模拟数据）");entry.putString("account","de****er");entry.putString("matchedBy","package");entries.add(entry);Bundle reply=new Bundle();reply.putString("status","ok");reply.putParcelableArrayList("entries",entries);
        call("showPanel",new Class<?>[]{Bundle.class},reply);
        if(((View)get("status")).getVisibility()!=View.GONE)throw new AssertionError("default footer must be hidden");
        LinearLayout card=(LinearLayout)get("card");if(((LinearLayout)card.getChildAt(0)).getChildCount()!=1)throw new AssertionError("close icon still present");
        if(mode.equals("password_failure")){call("showHelp",new Class<?>[]{PasswordTarget.Problem.class,String.class},PasswordTarget.Problem.NO_FIELDS,null);if(controller==null)throw new AssertionError("controller lost");return;}
        if(!mode.equals("password")){
            call("enterSearch",new Class<?>[0]);
            if(mode.equals("password_empty")){((LinearLayout)get("rows")).removeAllViews();Bundle empty=new Bundle();empty.putParcelableArrayList("entries",new ArrayList<Bundle>());call("renderEntries",new Class<?>[]{Bundle.class,boolean.class},empty,true);}
            if(((View)get("choices")).getLayoutParams().height!=-2)throw new AssertionError("empty search still fixed-height");
        }
        Prefs.of(c).edit().putString("qa_password_panel","passed:"+mode).apply();
        card.postDelayed(()->{if(card.getWidth()==0||card.getHeight()==0)return;android.graphics.Bitmap pixels=android.graphics.Bitmap.createBitmap(card.getWidth(),card.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);card.draw(new android.graphics.Canvas(pixels));try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(c.getCacheDir(),"fixture-"+mode+".png"))){pixels.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}catch(java.io.IOException error){throw new RuntimeException(error);}finally{pixels.recycle();}},400);
    }
    static void close(){if(controller!=null){controller.destroy();controller=null;}}
}
