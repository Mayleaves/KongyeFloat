package com.kongye.floatball;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Bundle;

/** Local, signature-protected v1 calls only. Never logs or persists responses. */
final class VaultClient {
    static final String PERMISSION="com.kongye.permission.VAULT";
    static final String[] PACKAGES={"com.kongye.docs","com.kongye.docs.audit","com.kongye.docs.docktest","com.kongye.docs.upgradefixture"};
    private final Context context;
    VaultClient(Context c){context=c.getApplicationContext();}
    static boolean debug(Context c){return (c.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0;}
    String providerPackage(){
        String value=debug(context)?Prefs.of(context).getString("vault_package",PACKAGES[0]):PACKAGES[0];
        for(String pkg:PACKAGES)if(pkg.equals(value))return pkg;
        return PACKAGES[0];
    }
    Bundle call(String method,Bundle extras){
        String pkg=providerPackage();
        try{
            PackageManager pm=context.getPackageManager();
            ProviderInfo info=pm.resolveContentProvider(pkg+".vault",0);
            if(info==null)return result("unavailable");
            if(!pkg.equals(info.packageName)||!info.exported||!PERMISSION.equals(info.readPermission)||!PERMISSION.equals(info.writePermission)
                    ||pm.checkSignatures(context.getPackageName(),pkg)!=PackageManager.SIGNATURE_MATCH)return result("denied");
            PermissionInfo permission=pm.getPermissionInfo(PERMISSION,0);
            if((permission.protectionLevel&PermissionInfo.PROTECTION_MASK_BASE)!=PermissionInfo.PROTECTION_SIGNATURE)return result("denied");
            if(context.checkSelfPermission(PERMISSION)!=PackageManager.PERMISSION_GRANTED)return result("denied");
            {
                Bundle reply=context.getContentResolver().call(Uri.parse("content://"+pkg+".vault"),method,null,extras);
                if(reply==null||!reply.containsKey("status"))return result("error");
                // This app implements v1. Do not guess a future secret-returning protocol.
                if("status".equals(method)&&"ok".equals(reply.getString("status"))&&reply.getInt("version",-1)!=1){reply.clear();return result("version");}
                return reply;
            }
        }catch(SecurityException e){return result("denied");}
        catch(Exception e){return result("error");}
    }
    static Bundle result(String status){Bundle b=new Bundle();b.putString("status",status);return b;}
    static String message(String status){return switch(status){
        case "unavailable"->"请先安装支持密码填充的空叶文档";
        case "disabled"->"请在空叶文档的密码空间中开启键盘 / 悬浮球读取";
        case "denied"->"密码空间拒绝访问，请确认三个空叶应用使用相同签名";
        case "not_found"->"这个密码条目已不存在，请重新选择";
        case "version"->"密码空间接口版本不兼容，请更新空叶应用";
        case "locked"->"密码空间未解锁";
        default->"密码空间暂时无法连接，请稍后重试";
    };}
}
