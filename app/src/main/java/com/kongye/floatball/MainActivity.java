package com.kongye.floatball;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private static java.lang.ref.WeakReference<MainActivity> visible=new java.lang.ref.WeakReference<>(null);
    static boolean hideIfVisible(){MainActivity page=visible.get();if(page==null||page.isFinishing()||page.isDestroyed())return false;page.moveTaskToBack(true);return true;}
    private static final String[] TABS={"首页","手势","外观"},TAB_ICONS={"leaf","up","appearance"};
    private static final String[] GESTURES={"tap","double","long","up","down"},GESTURE_NAMES={"单击","双击","长按","上划","下划"};
    private LinearLayout shell, content;
    private ScrollView scroll;
    private Preview preview;
    private int tab=0, renderedTab=-1;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService updateWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private AlertDialog updateDialog;private java.util.concurrent.Future<?> updateTask;private int updateGeneration;
    private boolean renderedDark;
    private final Runnable themeTicker=new Runnable(){public void run(){if(ThemePolicy.isDark(MainActivity.this)!=renderedDark)render();handler.postDelayed(this,60000-System.currentTimeMillis()%60000);}};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);QuickActions.migrate(this);
        if(state!=null)tab=state.getInt("tab",0);
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("tab",tab);}
    @Override protected void onResume(){super.onResume();visible=new java.lang.ref.WeakReference<>(this);render();handler.removeCallbacks(themeTicker);handler.postDelayed(themeTicker,60000-System.currentTimeMillis()%60000);}
    @Override protected void onPause(){if(visible.get()==this)visible.clear();handler.removeCallbacks(themeTicker);cancelUpdate();super.onPause();}
    @Override protected void onDestroy(){cancelUpdate();updateWorker.shutdownNow();super.onDestroy();}
    private int dp(float v){return Ui.dp(this,v);}
    private void render() {
        ThemePolicy.apply(this);renderedDark=Ui.dark;setTheme(Ui.dark?R.style.AppThemeDark:R.style.AppTheme);
        getWindow().setStatusBarColor(Ui.BG);getWindow().setNavigationBarColor(Ui.BG);getWindow().getDecorView().setSystemUiVisibility(Ui.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        int keepScroll=scroll!=null&&renderedTab==tab?scroll.getScrollY():0;renderedTab=tab;
        shell=Ui.column(this);shell.setBackgroundColor(Ui.BG);
        shell.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        setContentView(shell);
        LinearLayout header=Ui.row(this);Ui.pad(header,24,20,12,8);header.setMinimumHeight(dp(76));
        header.addView(Ui.text(this,tab==0?"空叶悬浮球":TABS[tab],26,Ui.INK,true),new LinearLayout.LayoutParams(0,-2,1));
        if(tab==0)header.addView(iconButton("help","关于与使用帮助",this::about),new LinearLayout.LayoutParams(dp(44),dp(44)));
        shell.addView(header);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        content=Ui.column(this);Ui.pad(content,16,6,16,28);scroll.addView(content);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        preview=null;if(tab==0)home();else if(tab==1)gestures();else appearance();
        if(keepScroll>0){ScrollView sc=scroll;sc.post(()->sc.scrollTo(0,keepScroll));}
        View line=new View(this);line.setBackgroundColor(Ui.LINE);shell.addView(line,new LinearLayout.LayoutParams(-1,1));
        LinearLayout nav=Ui.row(this);Ui.pad(nav,12,8,12,8);
        for(int i=0;i<TABS.length;i++){
            final int index=i;boolean on=i==tab;LinearLayout item=Ui.column(this);item.setGravity(Gravity.CENTER);Ui.pad(item,0,2,0,2);item.setContentDescription(TABS[i]);
            FrameLayout pill=new FrameLayout(this);if(on)pill.setBackground(Ui.bg(Ui.PALE,16,this));pill.addView(Ui.icon(this,TAB_ICONS[i],on?Ui.GREEN:Ui.MUTED,22),new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));item.addView(pill,new LinearLayout.LayoutParams(dp(56),dp(32)));
            Ui.gap(item,4);item.addView(Ui.centeredText(this,TABS[i],12,on?Ui.GREEN:Ui.MUTED,on));item.setOnClickListener(v->{if(tab!=index){tab=index;render();}});nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        shell.addView(nav);shell.requestApplyInsets();
    }
    private void home() {
        boolean on=FloatService.running;
        LinearLayout status=group();Ui.pad(status,18,18,18,18);
        LinearLayout top=Ui.row(this);preview=new Preview(this,44);top.addView(preview,new LinearLayout.LayoutParams(dp(56),dp(56)));
        LinearLayout texts=Ui.column(this);Ui.pad(texts,14,0,0,0);
        LinearLayout state=Ui.row(this);View dot=new View(this);dot.setBackground(Ui.bg(on?Ui.GREEN:Ui.MUTED,4,this));state.addView(dot,new LinearLayout.LayoutParams(dp(8),dp(8)));TextView label=Ui.text(this,on?"运行中":"未开启",17,Ui.INK,true);Ui.pad(label,8,0,0,0);state.addView(label);texts.addView(state);
        Ui.gap(texts,6);texts.addView(Ui.text(this,on?"轻点小叶，随时打开工具":"开启后可在任意界面使用",12,Ui.MUTED,false));
        top.addView(texts,new LinearLayout.LayoutParams(0,-2,1));status.addView(top);Ui.gap(status,18);
        status.addView(Ui.button(this,on?"暂停悬浮球":"开启悬浮球",!on,()->{
            if(FloatService.running){stopService(new Intent(this,FloatService.class));handler.postDelayed(this::render,150);}else startBall(null);
        }),new LinearLayout.LayoutParams(-1,-2));content.addView(status);
        boolean overlay=Settings.canDrawOverlays(this),assist=AssistService.instance!=null,notifications=notificationsAllowed();
        label("权限");LinearLayout permissions=group();
        permission(permissions,"悬浮窗","让小叶显示在其他应用上方",overlay,this::overlayPermission);
        permission(permissions,"无障碍服务","导航、截图与长截图需要",assist,this::accessibilityPermission);
        permission(permissions,"通知","显示运行状态与停止入口",notifications,()->{if(notifications||Build.VERSION.SDK_INT<33)notificationSettings();else requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},12);});
        content.addView(permissions);
        Ui.gap(content,18);LinearLayout toolHead=Ui.row(this);TextView title=Ui.text(this,"工具",13,Ui.MUTED,true);Ui.pad(title,6,0,0,0);toolHead.addView(title,new LinearLayout.LayoutParams(0,-2,1));toolHead.addView(iconButton("quick_manage","管理首页工具",this::manageHomeTools),new LinearLayout.LayoutParams(dp(44),dp(44)));content.addView(toolHead);
        java.util.ArrayList<String> ids=HomeTools.load(this);LinearLayout tools=group();tools.setContentDescription("首页工具列表");Ui.pad(tools,6,8,6,8);
        tools.addView(new HomeToolGrid(this,ids,id->tool(id,HomeTools.label(id)),order->{HomeTools.save(this,order);render();}),new LinearLayout.LayoutParams(-1,-2));
        if(ids.isEmpty())tools.addView(textButton("添加工具",this::manageHomeTools));content.addView(tools);
    }
    private void manageHomeTools(){
        java.util.ArrayList<String> current=HomeTools.load(this);String[] ids=HomeTools.AVAILABLE,names=new String[ids.length];boolean[] checked=new boolean[ids.length];for(int i=0;i<ids.length;i++){names[i]=HomeTools.label(ids[i]);checked[i]=current.contains(ids[i]);}
        new AlertDialog.Builder(this).setTitle("首页工具").setMultiChoiceItems(names,checked,(dialog,index,on)->checked[index]=on).setPositiveButton("确定",(dialog,which)->{java.util.ArrayList<String> result=new java.util.ArrayList<>();for(String id:current)for(int i=0;i<ids.length;i++)if(ids[i].equals(id)&&checked[i])result.add(id);for(int i=0;i<ids.length;i++)if(checked[i]&&!result.contains(ids[i]))result.add(ids[i]);HomeTools.save(this,result);render();}).setNegativeButton("取消",null).setNeutralButton("恢复默认",(dialog,which)->{HomeTools.save(this,java.util.Arrays.asList(HomeTools.DEFAULT));render();}).show();
    }
    private View tool(String id,String name){
        LinearLayout tile=Ui.column(this);tile.setGravity(Gravity.CENTER_HORIZONTAL);Ui.pad(tile,0,10,0,10);tile.setContentDescription(name);touchable(tile);tile.setOnClickListener(v->startBall(id));
        FrameLayout box=new FrameLayout(this);box.setBackground(Ui.bg(Ui.PALE,16,this));box.addView(Ui.icon(this,id,Ui.GREEN,24),new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));tile.addView(box,new LinearLayout.LayoutParams(dp(48),dp(48)));
        Ui.gap(tile,8);TextView t=Ui.centeredText(this,name,12,Ui.INK,false);t.setMaxLines(2);t.setMinLines(2);t.setEllipsize(TextUtils.TruncateAt.END);tile.addView(t);return tile;
    }
    private void permission(LinearLayout group,String title,String desc,boolean granted,Runnable action){
        LinearLayout row=item(group,title,desc,action);
        if(granted){View check=Ui.icon(this,"check",Ui.GREEN,16);((LinearLayout.LayoutParams)check.getLayoutParams()).leftMargin=dp(12);row.addView(check);TextView t=Ui.text(this,"已开启",13,Ui.MUTED,false);Ui.pad(t,4,0,4,0);row.addView(t);}
        else{TextView chip=Ui.text(this,"去开启",13,Ui.dark?0xFF102E21:Color.WHITE,true);chip.setBackground(Ui.bg(Ui.GREEN,14,this));Ui.pad(chip,12,6,12,6);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.leftMargin=dp(12);row.addView(chip,lp);}
        row.setContentDescription(title+(granted?"，已开启":"，未开启"));
    }
    private boolean notificationsAllowed(){return Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED;}
    private void notificationSettings(){safeSettings(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));}
    // A permanently denied request returns at once without a dialog; send the user to the system page instead.
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==12&&!notificationsAllowed()&&Build.VERSION.SDK_INT>=33&&!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))notificationSettings();render();}
    private void gestures() {
        LinearLayout card=group();
        for(int i=0;i<GESTURES.length;i++){String key=GESTURES[i],name=GESTURE_NAMES[i];link(card,name,Prefs.label(this,Prefs.action(this,key)),()->{String[] actions=Prefs.gestureActions();new AlertDialog.Builder(this).setTitle(name).setItems(gestureChoices(),(d,which)->{if(which==actions.length){pickGestureApp(key);return;}Prefs.of(this).edit().putString("gesture_"+key,actions[which]).apply();render();}).show();});}
        content.addView(card);Ui.gap(content,12);hint("按住小叶约 0.3 秒即可拖动；拖动中停住，十字变黄后拖出矩形，松手即截图。");
        Ui.gap(content,20);content.addView(textButton("恢复默认手势",()->{SharedPreferences.Editor e=Prefs.of(this).edit();for(String k:GESTURES)e.remove("gesture_"+k);e.apply();render();}));
    }
    private void appearance() {
        SharedPreferences prefs=Prefs.of(this);boolean custom=prefs.getBoolean("custom_art",false)&&BallArt.file(this).isFile();
        LinearLayout look=group();Ui.pad(look,18,18,18,18);
        FrameLayout stage=new FrameLayout(this);stage.setBackground(Ui.bg(Ui.BG,16,this));preview=new Preview(this,0);stage.addView(preview,new FrameLayout.LayoutParams(-1,-1));look.addView(stage,new LinearLayout.LayoutParams(-1,dp(132)));Ui.gap(look,16);
        if(!custom){
            LinearLayout colors=Ui.row(this);int[] palette={Ui.BRAND,0xFF567AAB,0xFF8D70A6,0xFFD19251,0xFF414E48};String[] names={"苔绿","雾蓝","藤紫","秋杏","墨黑"};int selected=prefs.getInt("color",Ui.BRAND);
            for(int i=0;i<palette.length;i++){final int color=palette[i];FrameLayout cell=new FrameLayout(this);cell.setContentDescription(names[i]);cell.setOnClickListener(v->{Prefs.of(this).edit().putInt("color",color).apply();refreshBall();render();});
                FrameLayout swatch=new FrameLayout(this);android.graphics.drawable.GradientDrawable fill=Ui.bg(color,20,this);if(color==selected)fill.setStroke(dp(3),Ui.SURFACE);swatch.setBackground(fill);if(color==selected){FrameLayout ring=new FrameLayout(this);android.graphics.drawable.GradientDrawable outline=Ui.bg(Color.TRANSPARENT,22,this);outline.setStroke(dp(2),color);ring.setBackground(outline);cell.addView(ring,new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.CENTER));}
                cell.addView(swatch,new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.CENTER));colors.addView(cell,new LinearLayout.LayoutParams(0,dp(48),1));}
            look.addView(colors);Ui.gap(look,14);
        }
        LinearLayout buttons=Ui.row(this);buttons.addView(Ui.button(this,custom?"更换图片":"自定义图片或 GIF",false,this::chooseArt),new LinearLayout.LayoutParams(0,-2,1));
        if(custom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(10);buttons.addView(Ui.button(this,"恢复叶片",false,()->{Prefs.of(this).edit().putBoolean("custom_art",false).apply();refreshBall();render();}),lp);}
        look.addView(buttons);content.addView(look);
        label("尺寸");LinearLayout size=group();slider(size,"大小","size",48,36,150," dp");slider(size,"不透明度","opacity",90,30,100,"%");slider(size,"边框距离","edge_gap",0,-100,0," dp");content.addView(size);
        label("行为");LinearLayout behavior=group();
        toggle(behavior,"自动吸边",null,"snap",true);toggle(behavior,"空闲时淡化","3 秒未操作时降低透明度","fade",true);toggle(behavior,"触感反馈",null,"haptic",true);toggle(behavior,"增强悬浮层","无障碍开启时，显示在更多界面上方","enhanced_overlay",true);content.addView(behavior);
        label("主题");LinearLayout theme=group();
        link(theme,"深色模式",ThemePolicy.label(this),()->new AlertDialog.Builder(this).setTitle("深色模式").setItems(ThemePolicy.LABELS,(d,which)->{Prefs.of(this).edit().putString("theme",ThemePolicy.MODES[which]).apply();refreshBall();render();}).show());
        boolean schedule="schedule".equals(prefs.getString("theme","system"));int start=prefs.getInt("dark_start",1320),end=prefs.getInt("dark_end",420);
        if(schedule){link(theme,"开始时间",ThemePolicy.time(start),()->pickThemeTime("dark_start",1320));link(theme,"结束时间",ThemePolicy.time(end),()->pickThemeTime("dark_end",420));}
        content.addView(theme);
        if(schedule){Ui.gap(content,10);hint(start==end?"起止时间相同：全天深色。":"按手机本地时间切换，可跨午夜。");}
        label("其他");LinearLayout other=group();
        link(other,"特定应用中的悬浮球",null,()->startActivity(new Intent(this,AppBehaviorActivity.class)));
        link(other,"找回悬浮球",null,()->startService(new Intent(this,FloatService.class).setAction(FloatService.RECOVER)));
        link(other,"重置悬浮球位置",null,()->{Prefs.of(this).edit().remove("x").remove("y").apply();refreshBall();Toast.makeText(this,"位置已重置",Toast.LENGTH_SHORT).show();});
        link(other,"检查更新",null,this::checkForUpdates);
        link(other,"GitHub",null,()->openWeb(GitHubUpdate.PROJECT));
        content.addView(other);
    }
    private void openWeb(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));}catch(ActivityNotFoundException|SecurityException e){Feedback.show(this,"没有可用的浏览器，请先安装或启用浏览器");}}
    private void cancelUpdate(){updateGeneration++;if(updateTask!=null){updateTask.cancel(true);updateTask=null;}if(updateDialog!=null){updateDialog.dismiss();updateDialog=null;}}
    private void checkForUpdates(){cancelUpdate();int generation=updateGeneration;String current=appVersion();updateDialog=new AlertDialog.Builder(this).setTitle("检查更新").setMessage("正在检查 GitHub 最新版本…").setNegativeButton("取消",(d,w)->cancelUpdate()).setOnCancelListener(d->cancelUpdate()).show();updateTask=updateWorker.submit(()->{GitHubUpdate.Result result=null;String error=null;try{result=GitHubUpdate.check(current);}catch(java.io.IOException|RuntimeException e){error=e.getMessage();}GitHubUpdate.Result answer=result;String failure=error;handler.post(()->{if(generation!=updateGeneration||isFinishing()||isDestroyed())return;updateTask=null;if(updateDialog!=null){updateDialog.dismiss();updateDialog=null;}if(answer==null){new AlertDialog.Builder(this).setTitle("检查更新失败").setMessage((failure==null?"无法连接 GitHub，请检查网络后重试":failure)+"。\n也可以直接打开发布页查看。").setPositiveButton("重试",(d,w)->checkForUpdates()).setNeutralButton("发布页",(d,w)->openWeb(GitHubUpdate.RELEASES)).setNegativeButton("关闭",null).show();return;}String title=answer.comparison>0?"发现新版本":answer.comparison<0?"当前版本较新":"已是最新版本";String detail="当前版本："+current+"\nGitHub 最新版本："+answer.latest+(answer.comparison>0?"\n\n可前往发布页下载 APK，安装时由系统校验签名。":"");new AlertDialog.Builder(this).setTitle(title).setMessage(detail).setPositiveButton(answer.comparison>0?"前往下载":"知道了",(d,w)->{if(answer.comparison>0)openWeb(GitHubUpdate.RELEASES);}).setNeutralButton(answer.comparison>0?null:"发布页",(d,w)->openWeb(GitHubUpdate.RELEASES)).show();});});}
    private LinearLayout group(){LinearLayout g=Ui.column(this);g.setBackground(Ui.bg(Ui.SURFACE,20,this));g.setClipToOutline(true);return g;}
    private void label(String title){Ui.gap(content,22);TextView t=Ui.text(this,title,13,Ui.MUTED,true);Ui.pad(t,6,0,6,8);content.addView(t);}
    private void hint(String value){TextView t=Ui.text(this,value,12,Ui.MUTED,false);t.setLineSpacing(dp(3),1);Ui.pad(t,6,0,6,0);content.addView(t);}
    private TextView textButton(String title,Runnable action){TextView t=Ui.centeredText(this,title,14,Ui.GREEN,true);Ui.pad(t,16,12,16,12);touchable(t);t.setOnClickListener(v->action.run());return t;}
    private FrameLayout iconButton(String icon,String description,Runnable action){FrameLayout v=new FrameLayout(this);v.setContentDescription(description);v.addView(Ui.icon(this,icon,Ui.MUTED,22),new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));TypedValue tv=new TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,tv,true);v.setBackgroundResource(tv.resourceId);v.setOnClickListener(w->action.run());return v;}
    private void touchable(View v){TypedValue tv=new TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackground,tv,true);v.setForeground(getDrawable(tv.resourceId));}
    private void divider(LinearLayout group){View line=new View(this);line.setBackgroundColor(Ui.LINE);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,1);lp.leftMargin=dp(18);group.addView(line,lp);}
    private LinearLayout item(LinearLayout group,String title,String subtitle,Runnable action){
        if(group.getChildCount()>0)divider(group);
        LinearLayout row=Ui.row(this);Ui.pad(row,18,12,14,12);row.setMinimumHeight(dp(56));
        LinearLayout texts=Ui.column(this);texts.addView(Ui.text(this,title,15,Ui.INK,false));if(subtitle!=null){Ui.gap(texts,4);texts.addView(Ui.text(this,subtitle,12,Ui.MUTED,false));}
        row.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        if(action!=null){touchable(row);row.setOnClickListener(v->action.run());}
        group.addView(row);return row;
    }
    private void value(LinearLayout row,String value,int color){TextView t=Ui.text(this,value,13,color,false);t.setSingleLine();t.setEllipsize(TextUtils.TruncateAt.END);t.setMaxWidth(dp(180));Ui.pad(t,12,0,0,0);row.addView(t);}
    private void chevron(LinearLayout row){View c=Ui.icon(this,"forward",Ui.MUTED,16);((LinearLayout.LayoutParams)c.getLayoutParams()).leftMargin=dp(4);row.addView(c);}
    private void link(LinearLayout group,String title,String value,Runnable action){LinearLayout row=item(group,title,null,action);if(value!=null)value(row,value,Ui.MUTED);chevron(row);}
    private void slider(LinearLayout group,String title,String key,int def,int min,int max,String unit){
        if(group.getChildCount()>0)divider(group);
        int current=Math.max(min,Math.min(max,Prefs.of(this).getInt(key,def)));
        LinearLayout box=Ui.column(this);Ui.pad(box,0,16,0,6);
        LinearLayout head=Ui.row(this);Ui.pad(head,18,0,18,0);head.addView(Ui.text(this,title,15,Ui.INK,false),new LinearLayout.LayoutParams(0,-2,1));TextView value=Ui.text(this,current+unit,13,Ui.MUTED,false);head.addView(value);box.addView(head);
        SeekBar bar=new SeekBar(this);bar.setContentDescription(title);bar.setPadding(dp(18),0,dp(18),0);bar.setMax(max-min);bar.setProgress(current-min);
        bar.setProgressTintList(ColorStateList.valueOf(Ui.GREEN));bar.setThumbTintList(ColorStateList.valueOf(Ui.GREEN));bar.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.MUTED));
        box.addView(bar,new LinearLayout.LayoutParams(-1,dp(36)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int progress,boolean user){value.setText((progress+min)+unit);if(preview!=null&&key.equals("size"))preview.size=progress+min;else if(preview!=null&&key.equals("opacity"))preview.opacity=progress+min;else return;preview.update();}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){Prefs.of(MainActivity.this).edit().putInt(key,s.getProgress()+min).apply();refreshBall();}
        });
        group.addView(box);
    }
    private void toggle(LinearLayout group,String title,String subtitle,String key,boolean def){
        LinearLayout row=item(group,title,subtitle,null);Switch sw=new Switch(this);sw.setContentDescription(title);sw.setChecked(Prefs.of(this).getBoolean(key,def));
        int[][] states={{android.R.attr.state_checked},{}};sw.setThumbTintList(new ColorStateList(states,new int[]{Ui.GREEN,Ui.dark?0xFFB9C4BD:0xFFFAFAFA}));sw.setTrackTintList(new ColorStateList(states,new int[]{Ui.GREEN,Ui.MUTED}));
        sw.setOnCheckedChangeListener((v,on)->{Prefs.of(this).edit().putBoolean(key,on).apply();if(key.equals("enhanced_overlay")){OverlayWindows.refreshAll();}else refreshBall();});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.leftMargin=dp(12);row.addView(sw,lp);touchable(row);row.setOnClickListener(v->sw.toggle());
    }
    private String[] gestureChoices(){String[] ids=Prefs.gestureActions(),choices=new String[ids.length+1];for(int i=0;i<ids.length;i++)choices[i]=Prefs.label(ids[i]);choices[choices.length-1]="指定一个软件…";return choices;}
    private void pickGestureApp(String key){java.util.List<AppCatalog.Entry> entries=AppCatalog.all(this);String[] names=entries.stream().map(entry->entry.label).toArray(String[]::new);new AlertDialog.Builder(this).setTitle("选择要打开的软件").setItems(names,(dialog,index)->{Prefs.of(this).edit().putString("gesture_"+key,"app:"+entries.get(index).pkg).apply();render();}).setNegativeButton("取消",null).show();}
    private void pickThemeTime(String key,int fallback){int current=Prefs.of(this).getInt(key,fallback);new TimePickerDialog(this,(view,hour,minute)->{Prefs.of(this).edit().putInt(key,hour*60+minute).apply();refreshBall();render();},current/60,current%60,true).show();}
    private void chooseArt(){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),71);}catch(ActivityNotFoundException e){Toast.makeText(this,"没有可用的系统图片选择器",Toast.LENGTH_SHORT).show();}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==71&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();new Thread(()->{String error=null;try{BallArt.importFile(this,uri);}catch(java.io.IOException e){error=e.getMessage();}final String message=error;runOnUiThread(()->{if(isDestroyed())return;if(message!=null)Toast.makeText(this,message,Toast.LENGTH_LONG).show();else{refreshBall();render();Toast.makeText(this,"自定义悬浮球已更新",Toast.LENGTH_SHORT).show();}});},"Kongye-Art-Import").start();}}
    private void startBall(String action){
        if(!Settings.canDrawOverlays(this)){new AlertDialog.Builder(this).setTitle("让空叶出现在屏幕上").setMessage("先允许「显示在其他应用上层」，空叶就能陪你在应用之间自由切换。授权后请返回这里，再次点击开启。").setPositiveButton("去授权",(d,w)->overlayPermission()).setNegativeButton("稍后",null).show();return;}
        Intent intent=new Intent(this,FloatService.class);if(action!=null)intent.putExtra("action",action);startForegroundService(intent);handler.postDelayed(this::render,250);
    }
    private void refreshBall(){if(FloatService.running)startService(new Intent(this,FloatService.class).setAction(FloatService.REFRESH));}
    private void overlayPermission(){safeSettings(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
    private void accessibilityPermission(){new AlertDialog.Builder(this).setTitle("开启空叶无障碍服务").setMessage("用于增强悬浮层、导航、截图及仅由你触发的长截图滚动。\n\n仅在点击「提取文字」时读取当前界面的可访问文字，跳过密码框。截图和文字保存在本机，不上传。\n\n在系统设置中找到「空叶悬浮球」并手动开启，随时可关闭。侧载安装若遇「受限设置」，可在系统应用信息的右上角菜单中允许受限设置。").setPositiveButton("前往设置",(d,w)->safeSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).setNegativeButton("稍后",null).show();}
    private void safeSettings(Intent i){try{startActivity(i);}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_SETTINGS));}}
    private String appVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(android.content.pm.PackageManager.NameNotFoundException e){return "1.0";}}
    private void about(){new AlertDialog.Builder(this).setTitle("空叶悬浮球  "+appVersion()).setMessage("单击：工具菜单\n双击：返回\n长按：环形快捷，滑到图标松手执行\n上划 / 下划：最近任务 / 通知栏\n按住后拖动：移动；拖动中停住可框选截图\n\n手势可在「手势」页改绑。区域截图和长截图需要 Android 11+ 与无障碍服务。\n\n截图、文字和录屏均在本机处理，不上传。图片 OCR 使用打包的 ML Kit 模型，SDK 可能发送性能和使用统计。浏览器访问你打开的网站。部分系统会限制后台运行，可在电池设置中允许。\n\n本项目为独立开发，交互参考 FV 悬浮球，不隶属于 fooView。").setPositiveButton("知道了",null).show();}
    /** Renders the ball exactly like the overlay; the appearance preview follows the size/opacity sliders live. */
    private final class Preview extends FrameLayout {
        private final FrameLayout ball;private final View art,leaf;private final int fixed;int size,opacity;
        Preview(Context c,int fixedDp){
            super(c);fixed=fixedDp;size=Prefs.of(c).getInt("size",48);opacity=fixedDp>0?100:Prefs.of(c).getInt("opacity",90);setContentDescription("悬浮球外观预览");
            ball=new FrameLayout(c);art=BallArt.create(c,dp(fixedDp>0?fixedDp:116));BallArt.styleContainer(ball,art!=null);if(art==null)ball.setElevation(dp(3));
            leaf=art!=null?art:new Ui.Icon(c,"leaf",Color.WHITE);ball.addView(leaf);addView(ball);update();
        }
        void update(){
            int px=dp(fixed>0?fixed:Math.min(116,Math.max(36,size)));ball.setLayoutParams(new FrameLayout.LayoutParams(px,px,Gravity.CENTER));
            int inner=art!=null?px:Math.round(px*.57f);leaf.setLayoutParams(new FrameLayout.LayoutParams(inner,inner,Gravity.CENTER));ball.setAlpha(opacity/100f);
        }
    }
}
