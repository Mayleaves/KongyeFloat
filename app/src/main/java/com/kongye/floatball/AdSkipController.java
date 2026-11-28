package com.kongye.floatball;

import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Rect;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;

final class AdSkipController {
    private final AssistService service;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable scan = this::scan;
    private String foreground = "";
    private long started;
    private boolean pending;
    // Kept in memory for Debug device checks; never persisted or uploaded.
    static int debugClicks;
    static String debugPackage="",debugLabel="",debugCandidate="";
    AdSkipController(AssistService service) { this.service = service; }
    void settingsChanged(){
        stop();if(allowed(foreground)){started=SystemClock.uptimeMillis();pending=true;main.post(scan);}
    }
    void foreground(String pkg) {
        if (pkg == null || pkg.equals(foreground)) return;
        stop(); foreground = pkg;
        if (!allowed(pkg)) return;
        started = SystemClock.uptimeMillis(); pending = true; main.postDelayed(scan, 180);
    }
    private boolean allowed(String pkg) {
        if (!Prefs.of(service).getBoolean("ad_skip_enabled", false)
                || !Prefs.of(service).getBoolean("app_skip_" + pkg, true)
                || pkg.equals(service.getPackageName()) || pkg.equals("com.android.systemui")
                || pkg.equals("com.android.settings") || pkg.equals("com.android.permissioncontroller")) return false;
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        var resolved = service.getPackageManager().resolveActivity(home, 0);
        return resolved == null || !pkg.equals(resolved.activityInfo.packageName);
    }
    private Rect previousBounds;
    private String previousTarget;
    private void scan() {
        if (!pending || !allowed(foreground) || !AdSkipPolicy.within(SystemClock.uptimeMillis(), started)) { stop(); return; }
        KeyguardManager lock=service.getSystemService(KeyguardManager.class);
        if(lock!=null&&lock.isKeyguardLocked()){stop();return;}
        AccessibilityNodeInfo root=applicationRoot(),target=null;
        Rect screen=new Rect();boolean ambiguous=false;
        if(root!=null){
            if(!foreground.contentEquals(root.getPackageName()==null?"":root.getPackageName()))root.recycle();
            else{
                root.getBoundsInScreen(screen);ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();queue.add(root);int visited=0;
                while(!queue.isEmpty()){
                    AccessibilityNodeInfo node=queue.removeFirst();
                    try{
                        if(++visited<=1200&&node.isVisibleToUser()&&node.isEnabled()&&!node.isPassword()&&!node.isEditable()){
                            if(safeTarget(node,screen)){
                                if(target!=null)ambiguous=true;
                                else target=AccessibilityNodeInfo.obtain(node);
                            }
                            for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)queue.add(child);}
                        }
                    }finally{node.recycle();}
                }
            }
        }
        if(target==null||ambiguous){previousBounds=null;previousTarget=null;if(target!=null)target.recycle();}
        else try{
            Rect bounds=new Rect();target.getBoundsInScreen(bounds);
            String identity=AdSkipPolicy.identity(target.getText(),target.getContentDescription(),target.getViewIdResourceName());
            if(bounds.equals(previousBounds)&&identity.equals(previousTarget)){
                // Never click an ancestor or a coordinate; only the independently clickable skip node.
                if(target.refresh()&&safeTarget(target,screen)){
                    Rect current=new Rect();target.getBoundsInScreen(current);
                    if(current.equals(bounds)&&target.performAction(AccessibilityNodeInfo.ACTION_CLICK)){
                        if((service.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0){debugClicks++;debugPackage=foreground;debugLabel=String.valueOf(target.getText());}
                        stop();
                    }
                }
            }else{previousBounds=bounds;previousTarget=identity;}
        }finally{target.recycle();}
        if(pending)main.postDelayed(scan,220);
    }
    private AccessibilityNodeInfo applicationRoot(){
        AccessibilityNodeInfo root=service.getRootInActiveWindow();
        if(root!=null){if(foreground.contentEquals(root.getPackageName()==null?"":root.getPackageName()))return root;root.recycle();}
        // The floating panel can be the active window. Read only the focused application's tree.
        for(var window:service.getWindows()){
            if(window.getType()!=android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION||(!window.isActive()&&!window.isFocused()))continue;
            root=window.getRoot();if(root!=null){if(foreground.contentEquals(root.getPackageName()==null?"":root.getPackageName()))return root;root.recycle();}
        }
        return null;
    }
    private boolean safeTarget(AccessibilityNodeInfo node,Rect screen){
        if((service.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0){
            String text=node.getText()==null?"":node.getText().toString(),description=node.getContentDescription()==null?"":node.getContentDescription().toString();
            if(AdSkipPolicy.label(text)||AdSkipPolicy.label(description)){Rect box=new Rect();node.getBoundsInScreen(box);debugCandidate=foreground+"|"+text+"|"+description+"|"+node.getViewIdResourceName()+"|clickable="+node.isClickable()+"|"+box.toShortString();}
        }
        if(!node.isVisibleToUser()||!node.isEnabled()||!node.isClickable()||node.isPassword()||node.isEditable())return false;
        CharSequence pkg=node.getPackageName();if(pkg==null||!foreground.contentEquals(pkg))return false;
        String id=node.getViewIdResourceName();CharSequence text=node.getText(),description=node.getContentDescription();
        if(!AdSkipPolicy.target(text==null?null:text.toString(),id,true)&&!AdSkipPolicy.target(description==null?null:description.toString(),id,true))return false;
        Rect bounds=new Rect();node.getBoundsInScreen(bounds);
        return AdSkipPolicy.corner(bounds.left-screen.left,bounds.top-screen.top,bounds.right-screen.left,bounds.bottom-screen.top,screen.width(),screen.height());
    }
    void stop(){pending=false;previousBounds=null;previousTarget=null;main.removeCallbacks(scan);}
}
