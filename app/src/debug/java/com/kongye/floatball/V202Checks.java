package com.kongye.floatball;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.json.*;

/** Animation interruption and generated crop fixtures. No screen pixels are read or exported. */
final class V202Checks {
    private static CaptureController capture;

    static void run(Context context,String command)throws Exception{
        if(command.equals("interrupt_policy")||command.equals("interrupt_wake")){
            FloatService service=FloatService.instance;
            View ball=(View)V126Checks.get(service,"ball");
            ball.animate().cancel();
            ball.setScaleX(.65f);
            ball.setScaleY(.65f);
            ball.animate().scaleX(1).scaleY(1).setDuration(750).start();
            new Handler(Looper.getMainLooper()).postDelayed(()->{
                try{V126Checks.call(service,command.endsWith("policy")?"applyPolicy":"wakeBall");}
                catch(Exception error){throw new IllegalStateException(error);}
            },100);
            return;
        }
        if(command.equals("fixture")){
            if(capture!=null)capture.destroy();
            capture=new CaptureController(context,on->{});
            Bitmap background=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888);
            background.eraseColor(0xFF194868);
            Paint text=new Paint(3);
            text.setColor(Color.WHITE);
            text.setTextSize(34);
            new Canvas(background).drawText("Generated region 123",80,500,text);
            V126Checks.set(capture,"frozen",background);
            V126Checks.set(capture,"active",true);
            java.lang.reflect.Method show=CaptureController.class.getDeclaredMethod("showRegion",Bitmap.class);
            show.setAccessible(true);
            show.invoke(capture,background);
            return;
        }
        if(command.equals("close")){
            if(capture!=null){capture.destroy();capture=null;}
            return;
        }
        if(command.equals("feedback_short")){Feedback.show(context,"文字已复制");return;}
        if(command.equals("feedback_long")){Feedback.show(context,"保存失败，请检查剩余空间或权限后重试。当前图片会保留，可稍后再次保存。");return;}
        if(command.equals("native")){
            ResizableCropView crop=new ResizableCropView(context,null,false);
            crop.useRegionFrame();
            crop.layout(0,0,1000,1400);
            Bitmap raster=Bitmap.createBitmap(1000,1400,Bitmap.Config.ARGB_8888);
            crop.draw(new Canvas(raster));
            require(Color.alpha(raster.getPixel(20,700))==158,"outside mask");
            require(Color.alpha(raster.getPixel(220,650))==0,"clear selection interior");
            require(Color.alpha(raster.getPixel(Math.round(crop.area.left+crop.area.width()/3),650))>0,"thirds grid");
            require(crop.sourceRect(720,1280).equals(new Rect(72,384,648,896)),"source crop remains rectangular and exact");
            raster.recycle();
            Prefs.of(context).edit().putBoolean("qa_v202_native",true).apply();
            return;
        }
        if(command.equals("state")){
            JSONObject out=new JSONObject();
            if(FloatService.instance!=null){
                View ball=(View)V126Checks.get(FloatService.instance,"ball");
                out.put("scale_x",ball.getScaleX()).put("scale_y",ball.getScaleY()).put("ball_width",ball.getWidth());
            }
            if(capture!=null){
                CaptureWindow window=(CaptureWindow)V126Checks.get(capture,"window");
                View root=window.root();
                out.put("active",capture.isActive()).put("selection",root!=null);
                RegionResult result=(RegionResult)V126Checks.get(capture,"result");
                out.put("result",result!=null);
                if(result!=null)out.put("recognizing",V126Checks.get(result,"recognizing"));
                if(root instanceof ViewGroup group){
                    ResizableCropView crop=(ResizableCropView)group.getChildAt(0);
                    View toolbar=V124Checks.find(root,"区域截图跟随工具栏");
                    out.put("area",new JSONArray(new float[]{crop.area.left,crop.area.top,crop.area.right,crop.area.bottom}));
                    out.put("toolbar",new JSONArray(new float[]{toolbar.getX(),toolbar.getY(),toolbar.getWidth(),toolbar.getHeight()}));
                    out.put("viewport",new JSONArray(new int[]{root.getWidth(),root.getHeight()}));
                }
            }
            java.lang.reflect.Field field=Feedback.class.getDeclaredField("banner");
            field.setAccessible(true);
            if(field.get(null) instanceof ViewGroup banner){
                ViewGroup pill=(ViewGroup)banner.getChildAt(0);
                TextView label=(TextView)pill.getChildAt(1);
                out.put("feedback_width",pill.getWidth()).put("feedback_height",pill.getHeight()).put("feedback_lines",label.getLineCount()).put("feedback_text",label.getText());
                out.put("feedback_flags",((WindowManager.LayoutParams)banner.getLayoutParams()).flags);
            }
            Prefs.of(context).edit().putString("qa_v202_state",out.toString()).apply();
        }
    }
    private static void require(boolean value,String name){if(!value)throw new AssertionError(name);}
}
