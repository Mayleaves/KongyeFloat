package com.kongye.floatball;

import android.content.Context;
import android.os.*;
import android.view.*;
import java.util.*;

/** Routes only this app's overlay views through the connected accessibility service. */
final class OverlayWindows implements WindowManager {
    private static final Set<OverlayWindows> hosts=Collections.newSetFromMap(new WeakHashMap<>());
    private final Context context;
    private final WindowManager ordinary;
    private final IdentityHashMap<View,Entry> entries=new IdentityHashMap<>();
    private static final class Entry {
        WindowManager owner;final WindowManager.LayoutParams params;final boolean overlay;
        Entry(WindowManager owner,WindowManager.LayoutParams p,boolean overlay){this.owner=owner;params=p;this.overlay=overlay;}
    }
    private record Target(WindowManager manager,int type){}
    private OverlayWindows(Context c){context=c;ordinary=c.getSystemService(WindowManager.class);hosts.add(this);}
    static WindowManager get(Context c){return new OverlayWindows(c);}
    static boolean enabled(Context c){return Prefs.of(c).getBoolean("enhanced_overlay",true);}
    private Target target(){AssistService service=AssistService.instance;if(enabled(context)&&service!=null)return new Target(service.getSystemService(WindowManager.class),LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);return new Target(ordinary,LayoutParams.TYPE_APPLICATION_OVERLAY);}
    private static boolean overlay(int type){return type==LayoutParams.TYPE_APPLICATION_OVERLAY||type==LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;}
    @Override public void addView(View view,ViewGroup.LayoutParams layout){
        if(!(layout instanceof WindowManager.LayoutParams p)){ordinary.addView(view,layout);return;}
        boolean managed=overlay(p.type);Target t=managed?target():new Target(ordinary,p.type);if(managed){p.type=t.type;p.token=null;}
        try{t.manager.addView(view,p);}catch(RuntimeException error){if(!managed||t.manager==ordinary)throw error;p.type=LayoutParams.TYPE_APPLICATION_OVERLAY;p.token=null;ordinary.addView(view,p);t=new Target(ordinary,p.type);}
        entries.put(view,new Entry(t.manager,p,managed));
    }
    @Override public void updateViewLayout(View view,ViewGroup.LayoutParams layout){Entry e=entries.get(view);WindowManager owner=e==null?ordinary:e.owner;if(e!=null&&layout instanceof LayoutParams p&&p!=e.params){p.type=e.params.type;p.token=e.params.token;entries.put(view,new Entry(owner,p,e.overlay));}owner.updateViewLayout(view,layout);}
    @Override public void removeView(View view){Entry e=entries.remove(view);(e==null?ordinary:e.owner).removeView(view);}
    @Override public void removeViewImmediate(View view){Entry e=entries.remove(view);(e==null?ordinary:e.owner).removeViewImmediate(view);}
    @Override public Display getDefaultDisplay(){return ordinary.getDefaultDisplay();}
    static void refreshAll(){if(Looper.myLooper()!=Looper.getMainLooper()){new Handler(Looper.getMainLooper()).post(OverlayWindows::refreshAll);return;}for(OverlayWindows host:new ArrayList<>(hosts))host.refresh();}
    private void refresh(){
        Target next=target();
        for(Map.Entry<View,Entry> item:new ArrayList<>(entries.entrySet())){
            View view=item.getKey();Entry e=item.getValue();if(!e.overlay||(e.owner==next.manager&&e.params.type==next.type))continue;
            WindowManager previous=e.owner;int previousType=e.params.type;View focused=view.findFocus();
            try{previous.removeViewImmediate(view);}catch(IllegalArgumentException ignored){}
            e.params.token=null;e.params.type=next.type;
            try{next.manager.addView(view,e.params);e.owner=next.manager;if(focused!=null)focused.requestFocus();}
            catch(RuntimeException failure){e.params.token=null;e.params.type=LayoutParams.TYPE_APPLICATION_OVERLAY;try{ordinary.addView(view,e.params);e.owner=ordinary;}catch(RuntimeException fallback){e.params.type=previousType;e.owner=previous;}}
        }
    }
}
