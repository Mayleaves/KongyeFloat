package com.kongye.floatball;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.*;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Immediate shell, background catalog, recycled rows and bounded in-memory icon cache. */
final class AppLauncher {
    private static final LruCache<String,Bitmap> ICONS=new LruCache<>(64);
    private final Context context;private final LinearLayout body;private final Runnable permission;private final Consumer<String> title,launch;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);r.run();},"Kongye-App-Rows");t.setDaemon(true);return t;});
    private final Set<String> favorites;private final ArrayList<AppCatalog.Entry> visible=new ArrayList<>();private List<AppCatalog.Entry> entries=Collections.emptyList();private String mode;private boolean alive=true,loaded,reported;private int generation;private final long opened;
    private ListView list;private EditText search;private TextView empty;private BaseAdapter adapter;
    AppLauncher(Context c,LinearLayout body,String mode,Consumer<String> title,Consumer<String> launch,Runnable permission,long opened){context=c;this.body=body;this.mode=mode;this.title=title;this.launch=launch;this.permission=permission;this.opened=opened;favorites=new HashSet<>(Prefs.of(c).getStringSet("favorites",Collections.emptySet()));build();}
    private int dp(float n){return Ui.dp(context,n);}
    private void metric(String key,long value){if((context.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)Prefs.of(context).edit().putLong(key,value).apply();}
    private void build(){
        int token=++generation;loaded=false;reported=false;entries=Collections.emptyList();visible.clear();body.removeAllViews();title.accept(mode.equals("all")?"应用启动":mode.equals("recent")?"最近打开的软件":mode.equals("manage")?"管理常用软件":"常用软件");
        String[] modes={"recent","favorites","all"};body.addView(Ui.segmented(context,new String[]{"最近","常用","全部"},mode.equals("manage")?2:Arrays.asList(modes).indexOf(mode),index->{mode=modes[index];build();}));Ui.gap(body,10);
        if(mode.equals("recent")&&!AppCatalog.hasUsage(context)){TextView text=Ui.text(context,"允许使用记录访问权限后，可查看最近打开的软件。",13,Ui.MUTED,false);body.addView(text);Ui.gap(body,14);body.addView(Ui.button(context,"允许查看最近软件",true,permission));return;}
        search=Ui.field(context,"搜索已安装应用");search.setContentDescription("搜索已安装应用");search.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);body.addView(search,new LinearLayout.LayoutParams(-1,dp(42)));Ui.gap(body,8);
        FrameLayout viewport=new FrameLayout(context);list=new ListView(context);list.setContentDescription("应用启动列表");list.setDivider(new android.graphics.drawable.ColorDrawable(Ui.LINE));list.setDividerHeight(1);list.setCacheColorHint(Color.TRANSPARENT);viewport.addView(list,new FrameLayout.LayoutParams(-1,-1));empty=Ui.centeredText(context,"正在加载应用…",13,Ui.MUTED,false);viewport.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);body.addView(viewport,new LinearLayout.LayoutParams(-1,dp(330)));
        adapter=new Rows();list.setAdapter(adapter);list.setOnItemClickListener((parent,view,index,id)->{if(index>=visible.size())return;String pkg=visible.get(index).pkg;if(mode.equals("manage"))toggle(pkg);else launch.accept(pkg);});list.setOnItemLongClickListener((parent,view,index,id)->{if(index<visible.size())toggle(visible.get(index).pkg);return true;});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){filter();}public void afterTextChanged(Editable e){}});
        if(!mode.equals("manage")){Ui.gap(body,10);body.addView(Ui.button(context,"选择常用软件",false,()->{mode="manage";build();}));}
        body.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){boolean shell;public boolean onPreDraw(){if(!alive||generation!=token){body.getViewTreeObserver().removeOnPreDrawListener(this);return true;}if(!shell){shell=true;metric("qa_apps_shell_ms",SystemClock.elapsedRealtime()-opened);}if(loaded&&list.getChildCount()>0){if(!reported){reported=true;metric("qa_apps_rows_ms",SystemClock.elapsedRealtime()-opened);metric("qa_apps_visible_rows",list.getChildCount());metric("qa_apps_total",entries.size());}body.getViewTreeObserver().removeOnPreDrawListener(this);}return true;}});
        metric("qa_apps_cached",AppCatalog.cached(context)!=null?1:0);metric("qa_apps_shell_ms",-1);metric("qa_apps_rows_ms",-1);
        AppCatalog.load(context,result->{if(!alive||generation!=token)return;if(result==null){empty.setText("暂时无法读取应用，请关闭后重试");return;}if(mode.equals("recent")){worker.execute(()->{Map<String,Long> recent;try{recent=AppCatalog.recent(context);}catch(RuntimeException e){recent=Collections.emptyMap();}List<AppCatalog.Entry> sorted=new ArrayList<>();for(AppCatalog.Entry entry:result)if(recent.containsKey(entry.pkg))sorted.add(entry);Map<String,Long> times=recent;sorted.sort((a,b)->Long.compare(times.get(b.pkg),times.get(a.pkg)));main.post(()->accept(sorted,token));});}else accept(result,token);});
    }
    private void accept(List<AppCatalog.Entry> result,int token){if(!alive||token!=generation)return;entries=result;loaded=true;filter();}
    private void filter(){if(adapter==null)return;String query=search.getText().toString().toLowerCase(Locale.ROOT).trim();visible.clear();for(AppCatalog.Entry e:entries){if(mode.equals("favorites")&&!favorites.contains(e.pkg))continue;if(!e.search.contains(query))continue;visible.add(e);if(mode.equals("recent")&&visible.size()==30)break;}empty.setText(!loaded?"正在加载应用…":mode.equals("favorites")&&query.isEmpty()?"还没有常用软件，点击下方选择。":"没有找到可启动的软件");adapter.notifyDataSetChanged();}
    private void toggle(String pkg){if(!favorites.remove(pkg))favorites.add(pkg);Prefs.of(context).edit().putStringSet("favorites",new HashSet<>(favorites)).apply();filter();}
    private final class Rows extends BaseAdapter {
        public int getCount(){return visible.size();}public Object getItem(int at){return visible.get(at);}public long getItemId(int at){return visible.get(at).pkg.hashCode();}
        public View getView(int position,View recycled,android.view.ViewGroup parent){Holder h;if(recycled==null){LinearLayout row=Ui.row(context);Ui.pad(row,6,6,0,6);row.setBackground(Ui.ripple(context,0,14));h=new Holder();h.icon=new ImageView(context);h.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);row.addView(h.icon,new LinearLayout.LayoutParams(dp(36),dp(36)));h.label=Ui.text(context,"",14,Ui.INK,false);h.label.setSingleLine();h.label.setEllipsize(android.text.TextUtils.TruncateAt.END);Ui.pad(h.label,12,0,4,0);row.addView(h.label,new LinearLayout.LayoutParams(0,-2,1));h.star=new FrameLayout(context);h.star.setBackground(Ui.ripple(context,0,22));row.addView(h.star,new LinearLayout.LayoutParams(dp(44),dp(44)));row.setTag(h);recycled=row;}else h=(Holder)recycled.getTag();AppCatalog.Entry entry=visible.get(position);h.label.setText(entry.label);boolean starred=favorites.contains(entry.pkg);h.star.removeAllViews();h.star.addView(Ui.icon(context,starred?"star_on":"favorite_apps",starred?Ui.GREEN:Ui.MUTED,22),new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));h.star.setContentDescription((starred?"取消常用：":"设为常用：")+entry.label);h.star.setOnClickListener(v->toggle(entry.pkg));icon(entry,h.icon);return recycled;}
    }
    private static final class Holder {ImageView icon;TextView label;FrameLayout star;}
    private void icon(AppCatalog.Entry entry,ImageView image){if(!alive)return;String key=AppCatalog.revision()+":"+context.getResources().getDisplayMetrics().densityDpi+":"+entry.pkg;image.setTag(key);Bitmap cached=ICONS.get(key);if(cached!=null){image.setImageBitmap(cached);return;}image.setImageResource(android.R.drawable.sym_def_app_icon);WeakReference<ImageView> target=new WeakReference<>(image);worker.execute(()->{ImageView pending=target.get();if(!alive||pending==null||!key.equals(pending.getTag()))return;Bitmap bitmap=ICONS.get(key);if(bitmap==null)try{android.graphics.drawable.Drawable art=entry.info.loadIcon(context.getPackageManager());int size=Math.min(192,dp(36));bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);art.setBounds(0,0,size,size);art.draw(new Canvas(bitmap));ICONS.put(key,bitmap);}catch(RuntimeException|OutOfMemoryError ignored){bitmap=null;}Bitmap answer=bitmap;main.post(()->{ImageView view=target.get();if(alive&&view!=null&&key.equals(view.getTag())&&answer!=null)view.setImageBitmap(answer);});});}
    void close(){alive=false;++generation;worker.shutdownNow();main.removeCallbacksAndMessages(null);}
}
