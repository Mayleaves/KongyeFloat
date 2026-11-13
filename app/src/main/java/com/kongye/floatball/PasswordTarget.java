package com.kongye.floatball;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.accessibility.AccessibilityNodeInfo;
import java.net.IDN;
import java.util.*;

/**
 * Examined only during a user-triggered fill session (需求文档 v2 第 9.3 节).
 * 页面识别规则与空叶文档一致（第 5 节）：字段分类、登录 / 注册 / 改密码打分、新密码框附近的要求文字。
 * 密码框的内容从不读取；账号、手机号、邮箱框的文字只在用户点「保存这一页」时读一次。
 */
final class PasswordTarget implements AutoCloseable {
    record Field(String path,String viewId,Rect bounds){}
    /** 页面身份：会话中途页面或输入框变了就停下 */
    record Snapshot(String pkg,String domain,int window,PageRules.Kind kind,Field account,Field password,Field newPassword,Field confirm,Field anchor,String ruleText){}

    private final AccessibilityNodeInfo root,account,password,newPassword,confirm;
    /** 账号类字段（保存这一页时读取文字） */
    private final List<Map.Entry<PageRules.Type,AccessibilityNodeInfo>> accounts;
    final Snapshot snapshot;
    private PasswordTarget(AccessibilityNodeInfo root,AccessibilityNodeInfo account,AccessibilityNodeInfo password,AccessibilityNodeInfo newPassword,AccessibilityNodeInfo confirm,List<Map.Entry<PageRules.Type,AccessibilityNodeInfo>> accounts,Snapshot snapshot){
        this.root=root;this.account=account;this.password=password;this.newPassword=newPassword;this.confirm=confirm;this.accounts=accounts;this.snapshot=snapshot;
    }

    // ---------- 读取当前窗口 ----------
    private record Node(AccessibilityNodeInfo node,Field field,String label,int order){}
    private record Item(Node field,PageRules.Text text){}

    static PasswordTarget inspect(AccessibilityService service){
        KeyguardManager guard=service.getSystemService(KeyguardManager.class);
        if(guard!=null&&guard.isKeyguardLocked())return null;
        AccessibilityNodeInfo root=service.getRootInActiveWindow();
        if(root==null)return null;
        String pkg=root.getPackageName()==null?"":root.getPackageName().toString();
        if(pkg.isEmpty()||pkg.equals(service.getPackageName())||pkg.equals("com.android.systemui")||pkg.startsWith("com.kongye.docs")){root.recycle();return null;}
        ArrayList<Item> items=new ArrayList<>();
        try{
            if(!walk(root,"",items,new int[]{0})){root.recycle();release(items);return null;}
            ArrayList<Node> fields=new ArrayList<>();ArrayList<PageRules.Text> texts=new ArrayList<>();
            for(Item it:items){if(it.field()!=null)fields.add(it.field());else texts.add(it.text());}
            // 标签：输入框自己的提示 / id / 描述，加上紧挨在它前面的一段短文字
            ArrayList<PageRules.Type> raw=new ArrayList<>();
            for(int i=0;i<items.size();i++){
                Item it=items.get(i);if(it.field()==null)continue;
                String before=i>0&&items.get(i-1).text()!=null&&!items.get(i-1).text().button()&&items.get(i-1).text().text().length()<=40?items.get(i-1).text().text():"";
                raw.add(PageRules.classify(it.field().label()+" "+before,it.field().node().isPassword(),it.field().node().getInputType()));
            }
            List<PageRules.Type> types=PageRules.classifyAll(raw);
            PageRules.Kind kind=PageRules.kindOf(types,texts);
            int acc=-1,login=-1,neu=-1,conf=-1,focused=-1;
            for(int i=0;i<fields.size();i++){
                PageRules.Type t=types.get(i);
                if(acc<0&&t.account())acc=i;
                if(kind==PageRules.Kind.CHANGE?t==PageRules.Type.CURRENT&&login<0:(t==PageRules.Type.PASSWORD||t==PageRules.Type.CURRENT)&&login<0)login=i;
                if(neu<0&&t==PageRules.Type.NEW)neu=i;
                if(conf<0&&t==PageRules.Type.CONFIRM)conf=i;
                if(fields.get(i).node().isFocused())focused=i;
            }
            if(kind==PageRules.Kind.REGISTER&&neu<0&&login>=0){neu=login;login=-1;}
            if(kind==PageRules.Kind.LOGIN){neu=-1;}
            if(acc<0&&login<0&&neu<0){root.recycle();release(items);return null;}
            String domain="";
            if(isBrowser(service,pkg)){
                domain=browserDomain(root,pkg);
                if(domain.isEmpty()){root.recycle();release(items);return null;}
            }
            // 新密码框的说明和它后面到下一个框之前的文字（最多 3 段），交给空叶文档解析要求
            StringBuilder rules=new StringBuilder();
            if(neu>=0){
                rules.append(fields.get(neu).label());
                int at=fields.get(neu).order(),n=0;
                for(int i=at+1;i<items.size()&&n<3;i++){Item it=items.get(i);if(it.field()!=null)break;if(!it.text().button()){rules.append('\n').append(it.text().text());n++;}}
                if(at>0&&items.get(at-1).text()!=null)rules.append('\n').append(items.get(at-1).text().text());
            }
            int anchor=focused>=0&&(types.get(focused).account()||types.get(focused).secret())?focused:neu>=0?neu:login>=0?login:acc;
            ArrayList<Map.Entry<PageRules.Type,AccessibilityNodeInfo>> accounts=new ArrayList<>();
            for(int i=0;i<fields.size();i++)if(types.get(i).account())accounts.add(new AbstractMap.SimpleEntry<>(types.get(i),AccessibilityNodeInfo.obtain(fields.get(i).node())));
            Snapshot snapshot=new Snapshot(pkg,domain,root.getWindowId(),kind,
                    acc<0?null:fields.get(acc).field(),login<0?null:fields.get(login).field(),neu<0?null:fields.get(neu).field(),conf<0?null:fields.get(conf).field(),
                    fields.get(anchor).field(),rules.length()>1200?rules.substring(0,1200):rules.toString());
            PasswordTarget target=new PasswordTarget(root,
                    acc<0?null:AccessibilityNodeInfo.obtain(fields.get(acc).node()),login<0?null:AccessibilityNodeInfo.obtain(fields.get(login).node()),
                    neu<0?null:AccessibilityNodeInfo.obtain(fields.get(neu).node()),conf<0?null:AccessibilityNodeInfo.obtain(fields.get(conf).node()),accounts,snapshot);
            release(items);
            return target;
        }catch(RuntimeException e){root.recycle();release(items);return null;}
    }

    private static boolean walk(AccessibilityNodeInfo node,String path,ArrayList<Item> items,int[] count){
        if(++count[0]>2500||path.length()>400)return false;
        if(!node.isVisibleToUser())return true;
        if(node.isEnabled()&&node.isEditable()&&!addressControl(node.getViewIdResourceName())){
            Rect bounds=new Rect();node.getBoundsInScreen(bounds);
            String id=node.getViewIdResourceName();
            StringBuilder label=new StringBuilder();
            if(Build.VERSION.SDK_INT>=26&&node.getHintText()!=null)label.append(node.getHintText()).append(' ');
            if(id!=null)label.append(id.substring(id.indexOf('/')+1)).append(' ');
            if(node.getContentDescription()!=null)label.append(node.getContentDescription()).append(' ');
            // 输入框没内容时，有的 App 把提示放在文字里
            if(!node.isPassword()&&Build.VERSION.SDK_INT>=26&&node.isShowingHintText()&&node.getText()!=null)label.append(node.getText());
            items.add(new Item(new Node(AccessibilityNodeInfo.obtain(node),new Field(path,id,bounds),label.toString().trim(),items.size()),null));
        }else if(items.size()<400){
            CharSequence text=node.getText();
            String cls=node.getClassName()==null?"":node.getClassName().toString();
            if(text!=null&&text.toString().trim().length()>0&&!node.isPassword()){
                String t=text.toString().trim();
                boolean button=cls.contains("Button")||node.isClickable()&&t.length()<=12;
                items.add(new Item(null,new PageRules.Text(t.length()>200?t.substring(0,200):t,button)));
            }
        }
        for(int i=0;i<node.getChildCount();i++){
            AccessibilityNodeInfo child=node.getChild(i);
            if(child==null)continue;
            try{if(!walk(child,path+"/"+i,items,count))return false;}finally{child.recycle();}
        }
        return true;
    }
    private static void release(List<Item> items){for(Item it:items)if(it.field()!=null)it.field().node().recycle();items.clear();}
    private static boolean addressControl(String id){return id!=null&&(id.endsWith(":id/url_bar")||id.endsWith(":id/url")||id.endsWith(":id/location_bar_edit_text")||id.endsWith(":id/mozac_browser_toolbar_url_view")||id.endsWith(":id/mozac_browser_toolbar_edit_url_view"));}
    static boolean same(Snapshot a,Snapshot b){return a!=null&&b!=null&&a.pkg().equals(b.pkg())&&a.domain().equals(b.domain())&&a.window()==b.window()&&a.kind()==b.kind()
            &&Objects.equals(a.account(),b.account())&&Objects.equals(a.password(),b.password())&&Objects.equals(a.newPassword(),b.newPassword())&&Objects.equals(a.confirm(),b.confirm());}

    /** 填账号和密码（登录 / 改密码页的当前密码）；只有账号框的页面只填账号 */
    boolean fill(String user,String secret){
        boolean wantUser=account!=null&&!user.isEmpty(),wantSecret=password!=null&&!secret.isEmpty();
        if(!wantUser&&!wantSecret)return false;
        if(wantUser&&!supports(account)||wantSecret&&!supports(password))return false;
        if(wantUser&&!set(account,user))return false;
        return !wantSecret||set(password,secret);
    }
    /** 生成的密码同时填进新密码框和确认框 */
    boolean fillNew(String secret){
        if(newPassword==null||secret.isEmpty()||!supports(newPassword)||confirm!=null&&!supports(confirm))return false;
        if(!set(newPassword,secret))return false;
        return confirm==null||set(confirm,secret);
    }
    /** 保存这一页：账号、手机号、邮箱框里的文字（不读密码框） */
    LinkedHashMap<String,String> accountValues(){
        LinkedHashMap<String,String> out=new LinkedHashMap<>();
        for(Map.Entry<PageRules.Type,AccessibilityNodeInfo> e:accounts){
            AccessibilityNodeInfo node=e.getValue();
            if(!node.refresh()||node.isPassword())continue;
            if(Build.VERSION.SDK_INT>=26&&node.isShowingHintText())continue;
            CharSequence text=node.getText();
            if(text!=null&&text.toString().trim().length()>0&&text.length()<=200)out.put(e.getKey().key(),text.toString().trim());
        }
        return out;
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
    @Override public void close(){
        if(account!=null)account.recycle();if(password!=null)password.recycle();if(newPassword!=null)newPassword.recycle();if(confirm!=null)confirm.recycle();
        for(Map.Entry<PageRules.Type,AccessibilityNodeInfo> e:accounts)e.getValue().recycle();
        root.recycle();
    }
}
