package com.kongye.floatball;

import android.app.AppOpsManager;
import android.app.usage.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import java.util.*;

final class AppCatalog {
    static class Entry {
        final String pkg,label;final ResolveInfo info;
        Entry(PackageManager pm,ResolveInfo r){pkg=r.activityInfo.packageName;label=r.loadLabel(pm).toString();info=r;}
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
