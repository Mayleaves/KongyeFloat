package com.kongye.floatball;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.net.IDN;
import java.util.*;

/** Examined only during a user-triggered fill session; field values are never read. */
final class PasswordTarget implements AutoCloseable {
    record Field(String path,String viewId,Rect bounds){}
    record Snapshot(String pkg,String domain,int window,Field account,Field password){}
    private final AccessibilityNodeInfo root,account,password;
    final Snapshot snapshot;
    private PasswordTarget(AccessibilityNodeInfo root,AccessibilityNodeInfo account,AccessibilityNodeInfo password,Snapshot snapshot){this.root=root;this.account=account;this.password=password;this.snapshot=snapshot;}
    static PasswordTarget inspect(AccessibilityService service){
        KeyguardManager guard=service.getSystemService(KeyguardManager.class);
        if(guard!=null&&guard.isKeyguardLocked())return null;
        AccessibilityNodeInfo root=service.getRootInActiveWindow();
        if(root==null)return null;
        String pkg=root.getPackageName()==null?"":root.getPackageName().toString();
        if(pkg.isEmpty()||pkg.equals(service.getPackageName())||pkg.equals("com.android.systemui")||pkg.startsWith("com.kongye.docs")){root.recycle();return null;}
        ArrayList<Node> fields=new ArrayList<>();
        try{
            if(!walk(root,"",fields,new int[]{0})){root.recycle();release(fields);return null;}
            int selected=-1;
            for(int i=0;i<fields.size();i++)if(fields.get(i).node.isPassword()){
                if(selected<0)selected=i;
                if(fields.get(i).node.isFocused()){selected=i;break;}
                if(i>0&&!fields.get(i-1).node.isPassword()&&fields.get(i-1).node.isFocused()){selected=i;break;}
            }
            if(selected<0){root.recycle();release(fields);return null;}
            Node pass=fields.get(selected),user=null;
            for(int i=selected-1;i>=0;i--)if(!fields.get(i).node.isPassword()){user=fields.get(i);break;}
            String domain="";
            if(isBrowser(service,pkg)){
                domain=browserDomain(root,pkg);
                if(domain.isEmpty()){root.recycle();release(fields);return null;}
            }
            AccessibilityNodeInfo account=user==null?null:AccessibilityNodeInfo.obtain(user.node),password=AccessibilityNodeInfo.obtain(pass.node);
            Snapshot snapshot=new Snapshot(pkg,domain,root.getWindowId(),user==null?null:user.field,pass.field);
            release(fields);
            return new PasswordTarget(root,account,password,snapshot);
        }catch(RuntimeException e){root.recycle();release(fields);return null;}
    }
    private record Node(AccessibilityNodeInfo node,Field field){}
    private static boolean walk(AccessibilityNodeInfo node,String path,ArrayList<Node> fields,int[] count){
        if(++count[0]>2500||path.length()>400)return false;
        if(node.isVisibleToUser()&&node.isEnabled()&&node.isEditable()&&!addressControl(node.getViewIdResourceName())){
            Rect bounds=new Rect();node.getBoundsInScreen(bounds);
            fields.add(new Node(AccessibilityNodeInfo.obtain(node),new Field(path,node.getViewIdResourceName(),bounds)));
        }
        for(int i=0;i<node.getChildCount();i++){
            AccessibilityNodeInfo child=node.getChild(i);
            if(child==null)continue;
            try{if(!walk(child,path+"/"+i,fields,count))return false;}finally{child.recycle();}
        }
        return true;
    }
    private static void release(List<Node> nodes){for(Node node:nodes)node.node.recycle();nodes.clear();}
    private static boolean addressControl(String id){return id!=null&&(id.endsWith(":id/url_bar")||id.endsWith(":id/url")||id.endsWith(":id/location_bar_edit_text")||id.endsWith(":id/mozac_browser_toolbar_url_view")||id.endsWith(":id/mozac_browser_toolbar_edit_url_view"));}
    static boolean same(Snapshot a,Snapshot b){return a!=null&&b!=null&&a.pkg.equals(b.pkg)&&a.domain.equals(b.domain)&&a.window==b.window&&Objects.equals(a.account,b.account)&&a.password.equals(b.password);}
    boolean fill(String user,String secret){
        if(!supports(password)||(account!=null&&!user.isEmpty()&&!supports(account)))return false;
        if(account!=null&&!user.isEmpty()&&!set(account,user))return false;
        return set(password,secret);
    }
    private static boolean supports(AccessibilityNodeInfo node){return node.refresh()&&node.isVisibleToUser()&&node.isEnabled()&&node.isEditable()&&node.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT);}
    private static boolean set(AccessibilityNodeInfo node,String value){Bundle data=new Bundle();data.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);try{return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,data);}finally{data.clear();}}
    private static boolean isBrowser(Context c,String pkg){
        if(Set.of("com.android.chrome","com.chrome.beta","com.chrome.dev","com.chrome.canary","com.microsoft.emmx","com.brave.browser","org.mozilla.firefox","org.mozilla.fenix","com.sec.android.app.sbrowser","com.android.browser","com.heytap.browser","com.oplus.browser").contains(pkg))return true;
        // Query the target explicitly: Android may filter non-default web handlers from a global query.
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(pkg);
        for(var match:c.getPackageManager().queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY))if(match.activityInfo!=null&&pkg.equals(match.activityInfo.packageName))return true;
        return false;
    }
    private static String browserDomain(AccessibilityNodeInfo root,String pkg){
        // Read only known browser address controls, never page or input-field text.
        for(String id:new String[]{"url_bar","url","location_bar_edit_text","mozac_browser_toolbar_url_view","mozac_browser_toolbar_edit_url_view"}){
            List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByViewId(pkg+":id/"+id);
            String host="";
            for(AccessibilityNodeInfo node:nodes){try{if(host.isEmpty()&&node.isVisibleToUser()&&!node.isPassword())host=host(node.getText());}finally{node.recycle();}}
            if(!host.isEmpty())return host;
        }
        return "";
    }
    static String host(CharSequence value){
        if(value==null)return "";
        String text=value.toString().trim();
        if(text.isEmpty()||text.matches(".*\\s.*")||text.contains("@"))return "";
        try{
            Uri uri=Uri.parse(text.contains("://")?text:"https://"+text);
            if(!"https".equalsIgnoreCase(uri.getScheme())&&!"http".equalsIgnoreCase(uri.getScheme()))return "";
            String host=uri.getHost();if(host==null)return "";
            host=IDN.toASCII(host).toLowerCase(Locale.ROOT).replaceFirst("^www\\.","").replaceFirst("\\.$","");
            return host.length()<=253&&host.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")&&host.contains(".")?host:"";
        }catch(RuntimeException e){return "";}
    }
    @Override public void close(){if(account!=null)account.recycle();password.recycle();root.recycle();}
}
