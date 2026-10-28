package com.kongye.floatball;

import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.Display;
import android.view.accessibility.*;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;

public class AssistService extends AccessibilityService {
    public static AssistService instance;static volatile String foregroundPackage="";
    @Override protected void onServiceConnected(){super.onServiceConnected();instance=this;OverlayWindows.refreshAll();}
    @Override public boolean onUnbind(Intent intent){if(instance==this)instance=null;OverlayWindows.refreshAll();return super.onUnbind(intent);}
    @Override public void onDestroy(){if(instance==this)instance=null;OverlayWindows.refreshAll();foregroundPackage="";if(FloatService.instance!=null)FloatService.instance.foregroundChanged("");super.onDestroy();}
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return;
        CharSequence name=event.getPackageName(),clazz=event.getClassName();String pkg=name==null?null:name.toString();
        if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)Prefs.of(this).edit().putString("qa_a11y_pkg",pkg==null?"":pkg).putInt("qa_a11y_events",Prefs.of(this).getInt("qa_a11y_events",0)+1).apply();
        // Activity metadata arrives before the accessibility root cache on some devices.
        if(pkg!=null&&clazz!=null&&!pkg.equals("com.android.systemui")){try{getPackageManager().getActivityInfo(new ComponentName(pkg,clazz.toString()),0);publishForeground(pkg);return;}catch(android.content.pm.PackageManager.NameNotFoundException ignored){}}
        for(AccessibilityWindowInfo window:getWindows()){if(window.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)continue;if(window.getId()==event.getWindowId()&&pkg!=null){publishForeground(pkg);return;}if(!window.isFocused()&&!window.isActive())continue;AccessibilityNodeInfo root=window.getRoot();if(root!=null){CharSequence packageName=root.getPackageName();root.recycle();if(packageName!=null){publishForeground(packageName.toString());return;}}}
    }
    private void publishForeground(String pkg){if(pkg.equals("com.android.systemui"))return;foregroundPackage=pkg;if(FloatService.instance!=null)FloatService.instance.foregroundChanged(pkg);}
    @Override public void onInterrupt(){}
    static String captureError(int error){if(error==ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT)return "截图太快，请稍后重试";if(Build.VERSION.SDK_INT>=34&&error==ERROR_TAKE_SCREENSHOT_SECURE_WINDOW)return "当前页面禁止截图，增强悬浮层不能解除截图保护";return "当前界面受保护或无法截图";}
    void captureBitmap(java.util.function.BiConsumer<Bitmap,String> callback){
        if(Build.VERSION.SDK_INT<30){callback.accept(null,"区域截图和长截图需要 Android 11 或以上");return;}
        takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
            public void onSuccess(ScreenshotResult result){HardwareBuffer buffer=result.getHardwareBuffer();Bitmap hardware=null;try{hardware=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());Bitmap copy=hardware==null?null:hardware.copy(Bitmap.Config.ARGB_8888,false);callback.accept(copy,copy==null?"无法读取截图":null);}catch(RuntimeException|OutOfMemoryError e){callback.accept(null,"无法读取截图，请关闭其他工具后重试");}finally{if(hardware!=null)hardware.recycle();buffer.close();}}
            public void onFailure(int error){callback.accept(null,captureError(error));}
        });
    }
    android.graphics.Rect scrollableBounds(){
        AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return null;ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();queue.add(root);android.graphics.Rect best=null;int visited=0;
        while(!queue.isEmpty()){AccessibilityNodeInfo node=queue.removeFirst();if(++visited<=4000){if(node.isScrollable()&&node.isVisibleToUser()){android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);if(best==null||(long)bounds.width()*bounds.height()>(long)best.width()*best.height())best=bounds;}for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)queue.add(child);}}node.recycle();}return best;
    }
    void scrollPage(android.graphics.Rect area,Runnable finished){
        android.graphics.Path path=new android.graphics.Path();path.moveTo(area.centerX(),area.top+area.height()*.78f);path.lineTo(area.centerX(),area.top+area.height()*.43f);
        android.accessibilityservice.GestureDescription.StrokeDescription stroke=new android.accessibilityservice.GestureDescription.StrokeDescription(path,0,900,true);
        android.accessibilityservice.GestureDescription gesture=new android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build();
        boolean dispatched=dispatchGesture(gesture,new GestureResultCallback(){public void onCompleted(android.accessibilityservice.GestureDescription g){android.graphics.Path hold=new android.graphics.Path();hold.moveTo(area.centerX(),area.top+area.height()*.43f);android.accessibilityservice.GestureDescription end=new android.accessibilityservice.GestureDescription.Builder().addStroke(stroke.continueStroke(hold,0,450,false)).build();boolean ended=dispatchGesture(end,new GestureResultCallback(){public void onCompleted(android.accessibilityservice.GestureDescription e){new Handler(Looper.getMainLooper()).postDelayed(finished,350);}public void onCancelled(android.accessibilityservice.GestureDescription e){finished.run();}},null);if(!ended)finished.run();}public void onCancelled(android.accessibilityservice.GestureDescription g){finished.run();}},null);if(!dispatched)finished.run();
    }
    String readScreen(){
        AccessibilityNodeInfo root=null;
        for(AccessibilityWindowInfo w:getWindows()) {
            if(w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION && (w.isActive()||w.isFocused())) {
                AccessibilityNodeInfo candidate=w.getRoot();
                if(candidate!=null && !getPackageName().contentEquals(candidate.getPackageName()==null?"":candidate.getPackageName())) {root=candidate;break;}
                if(candidate!=null)candidate.recycle();
            }
        }
        if(root==null)root=getRootInActiveWindow();
        if(root==null)return "";
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();queue.add(root);
        LinkedHashSet<String> lines=new LinkedHashSet<>();int visited=0,total=0;
        while(!queue.isEmpty()) {
            AccessibilityNodeInfo n=queue.removeFirst();
            if(++visited<=5000 && total<50000 && !n.isPassword() && n.isVisibleToUser()) {
                CharSequence text=n.getText();if(text==null||text.toString().isBlank())text=n.getContentDescription();
                if(text!=null){String value=text.toString().trim();if(!value.isEmpty()&&lines.add(value))total+=value.length();}
                for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo child=n.getChild(i);if(child!=null)queue.add(child);}
            }
            n.recycle();
        }
        return String.join("\n",lines);
    }
    void capture(Consumer<String> callback){
        if(Build.VERSION.SDK_INT<30){callback.accept("截图需要 Android 11 及以上版本");return;}
        takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
            @Override public void onSuccess(ScreenshotResult result){
                HardwareBuffer buffer=result.getHardwareBuffer();
                Bitmap hardware=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());
                Bitmap image=hardware==null?null:hardware.copy(Bitmap.Config.ARGB_8888,false);
                if(hardware!=null)hardware.recycle();buffer.close();
                if(image==null){callback.accept("无法读取截图，请重试");return;}
                new Thread(()->{
                    Uri uri=null;String message;
                    try {
                        ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"Kongye_"+new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date())+".png");values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Kongye");values.put(MediaStore.Images.Media.IS_PENDING,1);
                        uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
                        if(uri==null)throw new java.io.IOException("No media destination");
                        try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null||!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new java.io.IOException("Cannot save");}
                        values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(uri,values,null,null);
                        message="截图已保存到相册 Pictures/Kongye";
                    }catch(Exception e){if(uri!=null)getContentResolver().delete(uri,null,null);message="截图保存失败，请检查剩余空间";}
                    finally{image.recycle();}
                    final String response=message;new Handler(Looper.getMainLooper()).post(()->callback.accept(response));
                },"Kongye-Screenshot").start();
            }
            @Override public void onFailure(int error){callback.accept(captureError(error));}
        });
    }
}
