package com.kongye.floatball;
import android.content.*;import android.os.Bundle;import android.net.Uri;import org.json.*;import java.util.*;
/** Only explicit assertions and generated fixture data, never real vault values. */
final class V3Checks {
    static String original;static boolean saved,present;
    static RegionResult result;
    static java.util.concurrent.ExecutorService images=java.util.concurrent.Executors.newSingleThreadExecutor();
    static void run(Context c,String command)throws Exception{
        if(command.startsWith("image_")&&!command.equals("image_clip")){
            if(result!=null)result.close();
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(40,30,android.graphics.Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xFF317A52);
            result=new RegionResult(c,bitmap,images,()->{result=null;Prefs.of(c).edit().putBoolean("qa_v3_image_closed",true).apply();},pin->{},command.substring(6));
            Prefs.of(c).edit().putBoolean("qa_v3_image_closed",false).apply();return;
        }
        if(command.equals("image_clip")){
            android.content.ClipData clip=c.getSystemService(android.content.ClipboardManager.class).getPrimaryClip();
            boolean good=false;
            if(clip!=null&&clip.getDescription().hasMimeType("image/png")&&clip.getItemCount()==1){
                android.net.Uri uri=clip.getItemAt(0).getUri();
                if(uri!=null&&"com.kongye.floatball.share".equals(uri.getAuthority()))try(java.io.InputStream in=c.getContentResolver().openInputStream(uri)){
                    android.graphics.Bitmap b=android.graphics.BitmapFactory.decodeStream(in);good=b!=null&&b.getWidth()==40&&b.getHeight()==30&&b.getPixel(20,15)==0xFF317A52;if(b!=null)b.recycle();
                }
            }
            Prefs.of(c).edit().putBoolean("qa_v3_image_clip_ok",good).apply();return;
        }
        if(command.equals("contract")){
            if(!PasswordTarget.host("https://www.Example.com/path?q=one").equals("example.com")||!PasswordTarget.host("example.com:443/a").equals("example.com")||!PasswordTarget.host("https://user@example.com").isEmpty()||!PasswordTarget.host("search term").isEmpty()||!PasswordTarget.host("javascript:alert(1)").isEmpty())throw new IllegalStateException("URL safety");
            android.graphics.Rect bounds=new android.graphics.Rect(1,2,3,4);PasswordTarget.Field f=new PasswordTarget.Field("/1","id",bounds);
            PasswordTarget.Snapshot a=new PasswordTarget.Snapshot("pkg","example.com",3,PageRules.Kind.LOGIN,null,f,null,null,f,"");
            if(!PasswordTarget.same(a,a)||PasswordTarget.same(a,new PasswordTarget.Snapshot("pkg","other.com",3,PageRules.Kind.LOGIN,null,f,null,null,f,""))||PasswordTarget.same(a,new PasswordTarget.Snapshot("other","example.com",3,PageRules.Kind.LOGIN,null,f,null,null,f,"")))throw new IllegalStateException("Destination guard");
            Prefs.of(c).edit().putBoolean("qa_v3_contract",true).apply();return;
        }
        if(command.equals("diagnostic")){
            JSONObject state=new JSONObject();android.content.pm.PackageManager pm=c.getPackageManager();
            state.put("selected",new VaultClient(c).providerPackage());
            state.put("debug",VaultClient.debug(c));
            android.content.pm.ProviderInfo info=pm.resolveContentProvider("com.kongye.docs.docktest.vault",0);
            state.put("resolved",info!=null);if(info!=null)state.put("owner",info.packageName);
            try{var pkg=pm.getPackageInfo("com.kongye.docs.docktest",android.content.pm.PackageManager.GET_PROVIDERS);state.put("installed",true);state.put("providers",pkg.providers==null?0:pkg.providers.length);}catch(Exception e){state.put("installed",false);}
            state.put("permission",c.checkSelfPermission(VaultClient.PERMISSION));
            Bundle reply=new VaultClient(c).call("status",null);state.put("status",reply.getString("status"));reply.clear();
            Prefs.of(c).edit().putString("qa_v3_diagnostic",state.toString()).apply();return;
        }
        if(command.equals("setup")){
            if(!saved){present=Prefs.of(c).contains("vault_package");original=Prefs.of(c).getString("vault_package","");saved=true;}
            Prefs.of(c).edit().putString("vault_package","com.kongye.docs.docktest").apply();return;
        }
        if(command.equals("restore")){if(saved){var e=Prefs.of(c).edit();if(present)e.putString("vault_package",original);else e.remove("vault_package");e.apply();saved=false;original=null;}if(FloatService.instance!=null){Object controller=V126Checks.get(FloatService.instance,"passwordFill");if(controller instanceof PasswordFillController fill)fill.cancel();V126Checks.call(FloatService.instance,"closePanel");}return;}
        if(command.startsWith("config_")){if(!"com.kongye.docs.docktest".equals(new VaultClient(c).providerPackage()))throw new IllegalStateException("Fixture only");Bundle args=new Bundle();args.putString("mode",command.substring(7));new VaultClient(c).call("fixture_config",args).clear();Prefs.of(c).edit().remove("qa_feedback_message").apply();return;}
        if(command.equals("fill")){FloatService.instance.perform("password_fill");return;}
        if(command.equals("change_site")){c.getContentResolver().call(Uri.parse("content://com.kongye.floatball.fillfixture.report"),"change",null,null);return;}
        if(command.equals("state")){
            JSONObject state=new JSONObject();
            state.put("defaults",new JSONArray(Arrays.asList(HomeTools.DEFAULT))).put("home",new JSONArray(HomeTools.load(c)));
            state.put("menu_available",MenuTools.order(c).contains("password_fill")).put("gesture_available",Arrays.asList(Prefs.gestureActions()).contains("password_fill"));
            Bundle report=c.getContentResolver().call(Uri.parse("content://com.kongye.floatball.fillfixture.report"),"state",null,null);
            state.put("account",report!=null&&report.getBoolean("account")).put("password",report!=null&&report.getBoolean("password"));if(report!=null)report.clear();
            Bundle counts=new VaultClient(c).call("fixture_state",null);state.put("reveals",counts.getInt("reveals")).put("binds",counts.getInt("binds"));counts.clear();
            Prefs.of(c).edit().putString("qa_v3_state",state.toString()).apply();
        }
    }
}
