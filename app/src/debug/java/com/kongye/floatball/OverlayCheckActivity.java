package com.kongye.floatball;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

/** Synthetic protected page. Never opens a payment app or exports captured pixels. */
public class OverlayCheckActivity extends Activity {
    private EditText input;private final Handler main=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle state){super.onCreate(state);LinearLayout root=Ui.column(this);Ui.pad(root,24,40,24,24);root.setBackgroundColor(0xFFF4F8F0);root.addView(Ui.text(this,"空叶悬浮层本地验收",22,Color.BLACK,true));root.addView(Ui.text(this,"仅为本应用生成的保护测试页",14,Color.BLACK,false));input=new EditText(this);input.setHint("合成密码输入框");input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);root.addView(input,new LinearLayout.LayoutParams(-1,Ui.dp(this,60)));root.addView(new View(this){protected void onDraw(Canvas c){c.drawColor(Color.RED);}},new LinearLayout.LayoutParams(-1,Ui.dp(this,220)));setContentView(root);handle(getIntent());}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handle(intent);}
    private View ball()throws Exception{java.lang.reflect.Field f=FloatService.class.getDeclaredField("ball");f.setAccessible(true);return (View)f.get(FloatService.instance);}
    private void handle(Intent intent){Prefs.of(this).edit().remove("qa_overlay_error").apply();String action=intent.getStringExtra("check");if(action==null)action="page";try{
        switch(action){
            case "page"->{if(Build.VERSION.SDK_INT>=31)getWindow().setHideOverlayWindows(intent.getBooleanExtra("hide",true));if(intent.getBooleanExtra("secure",false))getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);}
            case "enhanced","ordinary"->{Prefs.of(this).edit().putBoolean("enhanced_overlay",action.equals("enhanced")).apply();OverlayWindows.refreshAll();}
            case "capture"->{Prefs.of(this).edit().putBoolean("qa_overlay_capture_done",false).apply();View ball=ball();int visibility=ball.getVisibility();ball.setVisibility(View.INVISIBLE);main.postDelayed(()->{AssistService service=AssistService.instance;if(service==null){Prefs.of(this).edit().putString("qa_overlay_error","service disconnected").putBoolean("qa_overlay_capture_done",true).apply();ball.setVisibility(visibility);return;}service.captureBitmap((image,error)->{int red=0;if(image!=null){for(int y=0;y<image.getHeight();y+=4)for(int x=0;x<image.getWidth();x+=4){int p=image.getPixel(x,y);if(Color.red(p)>220&&Color.green(p)<40&&Color.blue(p)<40)red++;}image.recycle();}Prefs.of(this).edit().putInt("qa_overlay_capture_red",red).putString("qa_overlay_capture_error",error==null?"":error).putBoolean("qa_overlay_capture_done",true).apply();ball.setVisibility(visibility);});},350);}
            case "keyboard"->{input.requestFocus();input.getText().clear();input.append("1234");input.postDelayed(()->getSystemService(InputMethodManager.class).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT),200);}
            case "low","restore"->{View b=ball();WindowManager.LayoutParams p=(WindowManager.LayoutParams)b.getLayoutParams();p.y=action.equals("low")?2100:Prefs.of(this).getInt("y",1087);java.lang.reflect.Field f=FloatService.class.getDeclaredField("windows");f.setAccessible(true);((WindowManager)f.get(FloatService.instance)).updateViewLayout(b,p);}
            case "cycle"->cycle(20);
            case "region","longshot","browser","record","gif_record","menu"->FloatService.instance.perform(action);
            case "close_tools"->{java.lang.reflect.Field field=FloatService.class.getDeclaredField("captures");field.setAccessible(true);((CaptureController)field.get(FloatService.instance)).cancel();java.lang.reflect.Method close=FloatService.class.getDeclaredMethod("closePanel");close.setAccessible(true);close.invoke(FloatService.instance);}
            case "fallback"->{AssistService service=AssistService.instance;AssistService.instance=null;OverlayWindows.refreshAll();main.postDelayed(()->{try{Prefs.of(this).edit().putInt("qa_overlay_fallback_type",((WindowManager.LayoutParams)ball().getLayoutParams()).type).apply();}catch(Exception e){error(e);}AssistService.instance=service;OverlayWindows.refreshAll();},300);}
        }
        main.postDelayed(this::receipt,500);
    }catch(Exception e){error(e);}}
    private void cycle(int remaining){if(remaining==0){Prefs.of(this).edit().putBoolean("enhanced_overlay",true).putBoolean("qa_overlay_cycles_done",true).apply();OverlayWindows.refreshAll();receipt();return;}Prefs.of(this).edit().putBoolean("qa_overlay_cycles_done",false).putBoolean("enhanced_overlay",remaining%2==1).apply();OverlayWindows.refreshAll();main.postDelayed(()->cycle(remaining-1),100);}
    private void receipt(){try{View b=ball();Prefs.of(this).edit().putInt("qa_overlay_type",((WindowManager.LayoutParams)b.getLayoutParams()).type).putBoolean("qa_overlay_attached",b.isAttachedToWindow()).putBoolean("qa_overlay_connected",AssistService.instance!=null).apply();}catch(Exception e){error(e);}}
    private void error(Exception e){Prefs.of(this).edit().putString("qa_overlay_error",e.toString()).apply();}
}
