package com.kongye.floatball;

import android.app.AppOpsManager;
import android.app.usage.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import java.util.*;

final class AppCatalog {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final java.util.concurrent.ExecutorService CATALOG=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);r.run();},"Kongye-App-Catalog");t.setDaemon(true);return t;});
    private static List<Entry> cache;private static String language="";private static long cachedAt;private static boolean loading,registered;private static int revision;
    private static final List<java.util.function.Consumer<List<Entry>>> waiting=new ArrayList<>();
    static synchronized List<Entry> cached(Context c){return cache!=null&&Locale.getDefault().toLanguageTag().equals(language)&&SystemClock.elapsedRealtime()-cachedAt<180000?cache:null;}
    static synchronized int revision(){return revision;}
    static synchronized void invalidate(){cache=null;cachedAt=0;revision++;}
    static void prefetch(Context c){load(c,ignored->{});}
    static void load(Context c,java.util.function.Consumer<List<Entry>> ready){
        Context app=c.getApplicationContext();synchronized(AppCatalog.class){
            if(!registered){registered=true;BroadcastReceiver change=new BroadcastReceiver(){public void onReceive(Context ignored,Intent i){invalidate();prefetch(app);}};IntentFilter packages=new IntentFilter();for(String action:new String[]{Intent.ACTION_PACKAGE_ADDED,Intent.ACTION_PACKAGE_REMOVED,Intent.ACTION_PACKAGE_CHANGED})packages.addAction(action);packages.addDataScheme("package");IntentFilter locale=new IntentFilter(Intent.ACTION_LOCALE_CHANGED);if(Build.VERSION.SDK_INT>=33){app.registerReceiver(change,packages,Context.RECEIVER_NOT_EXPORTED);app.registerReceiver(change,locale,Context.RECEIVER_NOT_EXPORTED);}else{app.registerReceiver(change,packages);app.registerReceiver(change,locale);}}
            List<Entry> snapshot=cached(c);if(snapshot!=null){MAIN.post(()->ready.accept(snapshot));return;}waiting.add(ready);if(loading)return;loading=true;
        }
        CATALOG.execute(()->{List<Entry> result=null;List<java.util.function.Consumer<List<Entry>>> callbacks=Collections.emptyList();
            // Package/locale broadcasts can arrive while a catalog query is running.
            // Re-query before fulfilling waiters so that the first completed load is reusable.
            for(int attempt=0;attempt<3;attempt++){int started;synchronized(AppCatalog.class){started=revision;}String locale=Locale.getDefault().toLanguageTag();try{result=Collections.unmodifiableList(all(app));}catch(RuntimeException|OutOfMemoryError ignored){result=null;}
                synchronized(AppCatalog.class){if(started!=revision&&attempt<2)continue;if(result!=null&&started==revision){cache=result;language=locale;cachedAt=SystemClock.elapsedRealtime();}loading=false;callbacks=new ArrayList<>(waiting);waiting.clear();}break;
            }
            List<Entry> answer=result;List<java.util.function.Consumer<List<Entry>>> listeners=callbacks;MAIN.post(()->{for(java.util.function.Consumer<List<Entry>> callback:listeners)callback.accept(answer);});
        });
    }

    static class Entry {
        final String pkg,label,search;final ResolveInfo info;
        Entry(PackageManager pm,ResolveInfo r){pkg=r.activityInfo.packageName;label=r.loadLabel(pm).toString();search=(label+"\n"+pkg).toLowerCase(Locale.ROOT);info=r;}
    }
    static List<Entry> all(Context c){
        PackageManager pm=c.getPackageManager();List<Entry> list=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0))if(seen.add(r.activityInfo.packageName))list.add(new Entry(pm,r));
        java.text.Collator collator=java.text.Collator.getInstance(Locale.getDefault());list.sort((a,b)->collator.compare(a.label,b.label));return list;
    }
    static boolean hasUsage(Context c){AppOpsManager a=c.getSystemService(AppOpsManager.class);int mode=Build.VERSION.SDK_INT>=29?a.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),c.getPackageName()):a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),c.getPackageName());return mode==AppOpsManager.MODE_ALLOWED;}
    static Map<String,Long> recent(Context c){
        Map<String,Long> result=new HashMap<>();if(!hasUsage(c))return result;
        long now=System.currentTimeMillis();UsageEvents events=c.getSystemService(UsageStatsManager.class).queryEvents(now-7L*24*60*60*1000,now);
        if(events==null)return result;UsageEvents.Event event=new UsageEvents.Event();
        while(events.hasNextEvent()){events.getNextEvent(event);if(event.getEventType()==UsageEvents.Event.MOVE_TO_FOREGROUND&&!c.getPackageName().equals(event.getPackageName()))result.put(event.getPackageName(),event.getTimeStamp());}
        return result;
    }
    static String label(Context c,String pkg){try{return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(PackageManager.NameNotFoundException e){return "应用已卸载";}}
}
