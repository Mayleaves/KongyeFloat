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
    private static final String[] TABS={"首页","悬浮球","设置"},TAB_ICONS={"nav_home","leaf","nav_settings"};
    private static final String[] GESTURES={"tap","double","long","up","down"},GESTURE_NAMES={"单击","双击","长按","上划","下划"};
    private LinearLayout shell, content;
    private ScrollView scroll;
    private Preview preview;
    private int tab=0, renderedTab=-1;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean renderedDark;
    private final Runnable themeTicker=new Runnable(){public void run(){if(ThemePolicy.isDark(MainActivity.this)!=renderedDark)render();handler.postDelayed(this,60000-System.currentTimeMillis()%60000);}};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);QuickActions.migrate(this);
        if(state!=null)tab=state.getInt("tab",0);
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("tab",tab);}
    @Override protected void onResume(){super.onResume();visible=new java.lang.ref.WeakReference<>(this);render();handler.removeCallbacks(themeTicker);handler.postDelayed(themeTicker,60000-System.currentTimeMillis()%60000);}
    @Override protected void onPause(){if(visible.get()==this)visible.clear();handler.removeCallbacks(themeTicker);super.onPause();}
    @Override protected void onDestroy(){super.onDestroy();}
    private int dp(float v){return Ui.dp(this,v);}
    private void render() {
        ThemePolicy.apply(this);renderedDark=Ui.dark;setTheme(Ui.dark?R.style.AppThemeDark:R.style.AppTheme);
        getWindow().setStatusBarColor(Ui.BG);getWindow().setNavigationBarColor(Ui.BG);getWindow().getDecorView().setSystemUiVisibility(Ui.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        int keepScroll=scroll!=null&&renderedTab==tab?scroll.getScrollY():0;renderedTab=tab;
        shell=Ui.column(this);shell.setBackgroundColor(Ui.BG);
        shell.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        setContentView(shell);
        LinearLayout header=Ui.row(this);Ui.pad(header,24,18,24,16);
        header.addView(Ui.text(this,TABS[tab],16,Ui.INK,true));shell.addView(header);
        FrameLayout body=new FrameLayout(this);body.setClipChildren(false);shell.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        content=Ui.column(this);Ui.pad(content,16,6,16,100);scroll.addView(content);body.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        preview=null;if(tab==0)home();else if(tab==1)gestures();else appearance();
        if(keepScroll>0){ScrollView sc=scroll;sc.post(()->sc.scrollTo(0,keepScroll));}
        LinearLayout nav=Ui.row(this);Ui.pad(nav,6,5,6,5);nav.setGravity(Gravity.CENTER);
        nav.setBackground(Ui.bg(Ui.dark?0xFF304B3D:0xFF2C473E,34,this));nav.setElevation(dp(5));nav.setContentDescription("底部悬浮导航");
        for(int i=0;i<TABS.length;i++){
            final int index=i;boolean on=i==tab;int tint=on?Ui.GREEN:0xFFB3BEB0;
            LinearLayout item=Ui.column(this);item.setGravity(Gravity.CENTER);Ui.pad(item,0,5,0,5);item.setContentDescription(TABS[i]);item.setSelected(on);item.setFocusable(true);
            item.setBackground(Ui.ripple(this,on?Ui.PALE:0,28));
            item.addView(Ui.icon(this,TAB_ICONS[i],tint,24),new LinearLayout.LayoutParams(dp(24),dp(24)));
            Ui.gap(item,3);item.addView(Ui.centeredText(this,TABS[i],11,tint,on));
            item.setOnClickListener(v->{if(tab!=index){tab=index;render();}});nav.addView(item,new LinearLayout.LayoutParams(0,dp(52),1));
        }
        FrameLayout.LayoutParams navParams=new FrameLayout.LayoutParams(-1,dp(64),Gravity.BOTTOM);navParams.setMargins(dp(16),0,dp(16),dp(12));body.addView(nav,navParams);shell.requestApplyInsets();
    }
    private void home() {
        boolean on=FloatService.running;
        LinearLayout status=group();status.setContentDescription("悬浮球运行状态卡片");
        status.addView(new BotanicalPanel(this),new LinearLayout.LayoutParams(-1,-2));
        LinearLayout state=Ui.row(this);Ui.pad(state,18,16,18,18);state.setBackgroundColor(Ui.dark?0xFF28372B:0xFFF0F4E4);
        LinearLayout texts=Ui.column(this);TextView hello=Ui.text(this,"HELLO, LITTLE LEAF.",8,Ui.dark?0xFFAFBE91:0xFF7F9853,false);hello.setLetterSpacing(.18f);texts.addView(hello);Ui.gap(texts,6);
        texts.addView(Ui.text(this,on?"随时待命。":"稍作休息。",26,Ui.dark?Ui.INK:0xFF30462E,true));state.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        FrameLayout power=new FrameLayout(this);power.setContentDescription(on?"关闭悬浮球":"启动悬浮球");power.setFocusable(true);power.setBackground(Ui.ripple(this,Ui.dark?0xFF89B876:0xFFB0D182,26));
        power.addView(Ui.icon(this,on?"pause":"play",0xFF2C473E,23),new FrameLayout.LayoutParams(dp(23),dp(23),Gravity.CENTER));
        power.setOnClickListener(v->{if(FloatService.running){stopService(new Intent(this,FloatService.class));handler.postDelayed(this::render,150);}else startBall(null);});
        state.addView(power,new LinearLayout.LayoutParams(dp(48),dp(48)));status.addView(state);content.addView(status);
        boolean overlay=Settings.canDrawOverlays(this),assist=AssistService.instance!=null,notifications=notificationsAllowed();
        label("权限");LinearLayout permissions=Ui.row(this);permissions.setBackground(Ui.bg(Ui.SURFACE,24,this));Ui.pad(permissions,6,12,6,12);
        permission(permissions,"悬浮窗","让小叶显示在其他应用上方",overlay,this::overlayPermission);
        permission(permissions,"无障碍","导航、截图与长截图需要",assist,this::accessibilityPermission);
        permission(permissions,"通知","显示运行状态与停止入口",notifications,()->{if(notifications||Build.VERSION.SDK_INT<33)notificationSettings();else requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},12);});
        content.addView(permissions);
        Ui.gap(content,18);LinearLayout toolHead=Ui.row(this);TextView title=Ui.text(this,"工具",13,Ui.MUTED,true);Ui.pad(title,6,0,0,0);toolHead.addView(title,new LinearLayout.LayoutParams(0,-2,1));toolHead.addView(iconButton("quick_manage","管理首页工具",this::manageHomeTools),new LinearLayout.LayoutParams(dp(44),dp(44)));content.addView(toolHead);
        java.util.ArrayList<String> ids=HomeTools.load(this);LinearLayout tools=group();tools.setContentDescription("首页工具列表");Ui.pad(tools,6,8,6,8);
        tools.addView(new HomeToolGrid(this,ids,id->tool(id,HomeTools.label(id)),order->{HomeTools.save(this,order);render();}),new LinearLayout.LayoutParams(-1,-2));
        if(ids.isEmpty())tools.addView(textButton("添加工具",this::manageHomeTools));content.addView(tools);
    }
    private void manageHomeTools(){
        java.util.ArrayList<String> current=HomeTools.load(this);String[] ids=HomeTools.AVAILABLE,names=new String[ids.length];boolean[] checked=new boolean[ids.length];for(int i=0;i<ids.length;i++){names[i]=HomeTools.label(ids[i]);checked[i]=current.contains(ids[i]);}
        new UiDialog(this).setTitle("首页工具").setMultiChoiceItems(names,checked,(dialog,index,on)->checked[index]=on).setPositiveButton("确定",(dialog,which)->{java.util.ArrayList<String> result=new java.util.ArrayList<>();for(String id:current)for(int i=0;i<ids.length;i++)if(ids[i].equals(id)&&checked[i])result.add(id);for(int i=0;i<ids.length;i++)if(checked[i]&&!result.contains(ids[i]))result.add(ids[i]);HomeTools.save(this,result);render();}).setNegativeButton("取消",null).setNeutralButton("恢复默认",(dialog,which)->{HomeTools.save(this,java.util.Arrays.asList(HomeTools.DEFAULT));render();}).show();
    }
    private View tool(String id,String name){
        LinearLayout tile=Ui.column(this);tile.setGravity(Gravity.CENTER_HORIZONTAL);Ui.pad(tile,0,10,0,10);tile.setContentDescription(name);touchable(tile);tile.setOnClickListener(v->startBall(id));
        FrameLayout box=new FrameLayout(this);box.setBackground(Ui.bg(Ui.PALE,16,this));box.addView(Ui.icon(this,id,Ui.dark?Ui.GREEN:0xFF4D6C46,24),new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));tile.addView(box,new LinearLayout.LayoutParams(dp(48),dp(48)));
        Ui.gap(tile,8);TextView t=Ui.centeredText(this,name,12,Ui.INK,false);t.setMaxLines(2);t.setMinLines(2);t.setEllipsize(TextUtils.TruncateAt.END);tile.addView(t);return tile;
    }
    private void permission(LinearLayout group,String title,String desc,boolean granted,Runnable action){
        LinearLayout cell=Ui.column(this);cell.setGravity(Gravity.CENTER);Ui.pad(cell,0,4,0,4);cell.setContentDescription(title+(granted?"，已开启":"，未开启")+"。"+desc);cell.setFocusable(true);cell.setBackground(Ui.ripple(this,0,14));cell.setOnClickListener(v->action.run());
        cell.addView(Ui.centeredText(this,title,12,Ui.INK,false));Ui.gap(cell,9);
        TextView chip=Ui.centeredText(this,granted?"✓ 已开启":"去开启",10,granted?Ui.GREEN:Ui.dark?0xFF102E21:Color.WHITE,false);
        chip.setBackground(Ui.bg(granted?Ui.PALE:Ui.GREEN,14,this));Ui.pad(chip,9,5,9,5);cell.addView(chip,new LinearLayout.LayoutParams(-2,-2));group.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
    }
    private boolean notificationsAllowed(){return Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED;}
    private void notificationSettings(){safeSettings(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));}
    // A permanently denied request returns at once without a dialog; send the user to the system page instead.
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==12&&!notificationsAllowed()&&Build.VERSION.SDK_INT>=33&&!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))notificationSettings();render();}
    private void gestures() {
        ballAppearance();
        label("手势操作");
        LinearLayout card=group();card.setContentDescription("手势操作设置组");
        String[] icons={"gesture_tap","gesture_double","gesture_hold","up","down"};
        for(int i=0;i<GESTURES.length;i++){
            String key=GESTURES[i],name=GESTURE_NAMES[i];
            LinearLayout row=item(card,name,null,()->{String[] actions=Prefs.gestureActions();new UiDialog(this).setTitle(name).setItems(gestureChoices(),(d,which)->{if(which==actions.length){pickGestureApp(key);return;}Prefs.of(this).edit().putString("gesture_"+key,actions[which]).apply();render();}).show();});
            LinearLayout.LayoutParams leading=new LinearLayout.LayoutParams(dp(20),dp(20));leading.rightMargin=dp(12);row.addView(Ui.icon(this,icons[i],Ui.dark?Ui.GREEN:0xFF759365,20),0,leading);
            value(row,Prefs.label(this,Prefs.action(this,key)),Ui.MUTED);chevron(row);
        }
        toggle(card,"触感反馈","操作时提供轻微振动","haptic",true);
        divider(card);card.addView(textButton("恢复默认手势",()->{SharedPreferences.Editor e=Prefs.of(this).edit();for(String k:GESTURES)e.remove("gesture_"+k);e.apply();render();}));content.addView(card);
        Ui.gap(content,12);hint("按住小叶约 0.3 秒即可拖动；拖动中停住，十字变黄后拖出矩形，松手即截图。");
    }
    private void ballAppearance() {
        SharedPreferences prefs=Prefs.of(this);boolean custom=prefs.getBoolean("custom_art",false)&&BallArt.file(this).isFile();
        LinearLayout look=group();look.setContentDescription("外观与位置设置组");
        LinearLayout title=Ui.row(this);Ui.pad(title,16,16,16,12);title.addView(Ui.text(this,"外观与位置",15,Ui.INK,true),new LinearLayout.LayoutParams(0,-2,1));look.addView(title);
        preview=new Preview(this,0);android.graphics.drawable.GradientDrawable stage=Ui.bg(Ui.BG,14,this);if(!Ui.dark){stage.setColors(new int[]{0xFFF3F6EE,0xFFEDF3E8});stage.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT);}preview.setBackground(stage);preview.setClipToOutline(true);LinearLayout.LayoutParams stageParams=new LinearLayout.LayoutParams(-1,dp(128));stageParams.setMargins(dp(12),0,dp(12),0);look.addView(preview,stageParams);
        LinearLayout style=Ui.row(this);Ui.pad(style,12,12,12,12);
        if(!custom){
            LinearLayout colors=Ui.row(this);int[] palette={BallArt.DEFAULT_COLOR,0xFF567AAB,0xFF8D70A6,0xFFD19251,0xFF414E48};String[] names={"叶绿","雾蓝","藤紫","秋杏","墨黑"};int selected=BallArt.color(this);
            for(int i=0;i<palette.length;i++){final int color=palette[i];FrameLayout cell=new FrameLayout(this);cell.setContentDescription(names[i]);cell.setOnClickListener(v->{Prefs.of(this).edit().putInt("color",color).apply();refreshBall();render();});cell.setBackground(Ui.ripple(this,0,16));
                if(color==selected){View ring=new View(this);android.graphics.drawable.GradientDrawable outline=Ui.bg(Color.TRANSPARENT,15,this);outline.setStroke(dp(1.5f),color);ring.setBackground(outline);cell.addView(ring,new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.CENTER));}
                View swatch=new View(this);swatch.setBackground(Ui.bg(color,12,this));cell.addView(swatch,new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));colors.addView(cell,new LinearLayout.LayoutParams(0,dp(40),1));}
            style.addView(colors,new LinearLayout.LayoutParams(0,-2,1));
        }else{TextView restore=Ui.text(this,"恢复叶片",12,Ui.GREEN,true);restore.setBackground(Ui.ripple(this,0,12));Ui.pad(restore,8,10,8,10);restore.setOnClickListener(v->{Prefs.of(this).edit().putBoolean("custom_art",false).apply();refreshBall();render();});style.addView(restore,new LinearLayout.LayoutParams(0,-2,1));}
        LinearLayout image=Ui.action(this,"image",custom?"更换图片 / GIF":"自定义图片 / GIF","自定义图片 / GIF",this::chooseArt);((TextView)image.getChildAt(1)).setTextSize(10);Ui.pad(image,9,8,9,8);
        LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(-2,-2);imageParams.leftMargin=dp(8);style.addView(image,imageParams);look.addView(style);
        View line=new View(this);line.setBackgroundColor(Ui.LINE);look.addView(line,new LinearLayout.LayoutParams(-1,1));
        LinearLayout controls=Ui.column(this);slider(controls,"大小","size",BallArt.DEFAULT_SIZE,36,150," dp");
        LinearLayout opacity=slider(controls,"不透明度","opacity",90,30,100,"%");relatedToggle(opacity,"空闲时淡化","3 秒未操作时降低透明度","fade",true);
        LinearLayout position=slider(controls,"边框距离","edge_gap",0,-100,0," dp");relatedToggle(position,"自动吸边","松手后贴近屏幕边缘","snap",true);
        look.addView(controls);content.addView(look);
    }
    private void appearance() {
        SharedPreferences prefs=Prefs.of(this);
        LinearLayout display=group();
        LinearLayout enhanced=settingItem(display,"layers","增强悬浮层","无障碍开启时，在更多界面显示",null);addSwitch(enhanced,"enhanced_overlay",true);
        settingLink(display,"apps_grid","特定应用中的悬浮球","为不同应用设置显示方式",null,()->startActivity(new Intent(this,AppBehaviorActivity.class)));content.addView(display);
        label("界面主题");LinearLayout theme=group();
        settingLink(theme,ThemePolicy.icon(this),"深色模式","调整空叶软件界面的配色",ThemePolicy.label(this),()->new UiDialog(this).setTitle("深色模式").setItems(ThemePolicy.LABELS,(d,which)->{Prefs.of(this).edit().putString("theme",ThemePolicy.MODES[which]).apply();refreshBall();render();}).show());
        boolean schedule="schedule".equals(prefs.getString("theme","system"));int start=prefs.getInt("dark_start",1320),end=prefs.getInt("dark_end",420);
        if(schedule){link(theme,"开始时间",ThemePolicy.time(start),()->pickThemeTime("dark_start",1320));link(theme,"结束时间",ThemePolicy.time(end),()->pickThemeTime("dark_end",420));}content.addView(theme);
        if(schedule){Ui.gap(content,10);hint(start==end?"起止时间相同：全天深色。":"按手机本地时间切换，可跨午夜。");}
        label("悬浮球维护");LinearLayout maintenance=group();
        settingLink(maintenance,"recover","找回悬浮球","暂时显示小叶并移回屏幕内",null,()->startService(new Intent(this,FloatService.class).setAction(FloatService.RECOVER)));
        settingLink(maintenance,"position","重置位置","恢复悬浮球的默认停靠位置",null,()->new UiDialog(this).setTitle("重置悬浮球位置").setMessage("将悬浮球恢复到默认停靠位置。").setPositiveButton("重置位置",(d,w)->{Prefs.of(this).edit().remove("x").remove("y").apply();refreshBall();Feedback.show(this,"位置已重置");}).setNegativeButton("取消",null).show());content.addView(maintenance);
        label("关于与更新");LinearLayout other=group();
        settingLink(other,"update","检查更新",null,"v"+appVersion(),this::checkForUpdates);
        settingLink(other,"github","GitHub",null,null,()->openWeb(GitHubUpdate.PROJECT));
        settingLink(other,"info","关于空叶",null,null,this::about);content.addView(other);

    }
    private LinearLayout settingItem(LinearLayout group,String icon,String title,String subtitle,Runnable action){
        if(group.getChildCount()>0)divider(group);LinearLayout row=Ui.row(this);Ui.pad(row,16,12,16,12);row.setMinimumHeight(dp(64));
        FrameLayout badge=new FrameLayout(this);badge.setBackground(Ui.bg(Ui.dark?Ui.PALE:0xFFEEF3E8,10,this));badge.addView(Ui.icon(this,icon,Ui.dark?Ui.GREEN:0xFF759365,17),new FrameLayout.LayoutParams(dp(17),dp(17),Gravity.CENTER));row.addView(badge,new LinearLayout.LayoutParams(dp(30),dp(30)));
        LinearLayout texts=Ui.column(this);Ui.pad(texts,10,0,0,0);texts.addView(Ui.text(this,title,14,Ui.INK,false));if(subtitle!=null){Ui.gap(texts,4);TextView sub=Ui.text(this,subtitle,11,Ui.MUTED,false);sub.setMaxLines(2);texts.addView(sub);}row.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        if(action!=null){touchable(row);row.setOnClickListener(v->action.run());}group.addView(row);return row;
    }
    private void settingLink(LinearLayout group,String icon,String title,String subtitle,String value,Runnable action){LinearLayout row=settingItem(group,icon,title,subtitle,action);if(value!=null)value(row,value,Ui.MUTED);chevron(row);}
    private void openWeb(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));}catch(ActivityNotFoundException|SecurityException e){Feedback.show(this,"没有可用的浏览器，请先安装或启用浏览器");}}
    private void checkForUpdates(){startActivity(new Intent(this,UpdateActivity.class));}
    private LinearLayout group(){LinearLayout g=Ui.column(this);g.setBackground(Ui.bg(Ui.SURFACE,22,this));g.setClipToOutline(true);return g;}
    private void label(String title){if(content.getChildCount()>0)Ui.gap(content,22);TextView t=Ui.text(this,title,13,Ui.dark?Ui.MUTED:0xFF71806E,true);Ui.pad(t,6,0,6,8);content.addView(t);}
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
    private LinearLayout slider(LinearLayout group,String title,String key,int def,int min,int max,String unit){
        if(group.getChildCount()>0)divider(group);
        int current=Math.max(min,Math.min(max,Prefs.of(this).getInt(key,def)));
        LinearLayout box=Ui.column(this);Ui.pad(box,0,13,0,8);
        LinearLayout head=Ui.row(this);Ui.pad(head,18,0,18,0);head.addView(Ui.text(this,title,15,Ui.INK,true),new LinearLayout.LayoutParams(0,-2,1));TextView value=Ui.text(this,current+unit,13,Ui.MUTED,false);head.addView(value);box.addView(head);
        SeekBar bar=new SeekBar(this);bar.setContentDescription(title);bar.setPadding(dp(18),0,dp(18),0);bar.setMax(max-min);bar.setProgress(current-min);
        bar.setProgressTintList(ColorStateList.valueOf(Ui.GREEN));bar.setThumbTintList(ColorStateList.valueOf(Ui.GREEN));bar.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.MUTED));
        box.addView(bar,new LinearLayout.LayoutParams(-1,dp(32)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int progress,boolean user){value.setText((progress+min)+unit);if(preview!=null&&key.equals("size"))preview.size=progress+min;else if(preview!=null&&key.equals("opacity"))preview.opacity=progress+min;else if(preview!=null&&key.equals("edge_gap"))preview.gap=progress+min;else return;preview.update();}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){Prefs.of(MainActivity.this).edit().putInt(key,s.getProgress()+min).apply();refreshBall();}
        });
        group.addView(box);return box;
    }
    private void toggle(LinearLayout group,String title,String subtitle,String key,boolean def){addSwitch(item(group,title,subtitle,null),key,def);}
    private void relatedToggle(LinearLayout box,String title,String subtitle,String key,boolean def){
        LinearLayout row=Ui.row(this);Ui.pad(row,18,1,18,4);
        LinearLayout copy=Ui.row(this);copy.setGravity(Gravity.CENTER_VERTICAL);copy.addView(Ui.text(this,title,11,Ui.dark?Ui.MUTED:0xFF77846F,false));
        TextView description=Ui.text(this,subtitle,8,Ui.MUTED,false);Ui.pad(description,6,0,0,0);description.setSingleLine();description.setEllipsize(TextUtils.TruncateAt.END);copy.addView(description,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));addSwitch(row,key,def);box.addView(row);
    }
    private void addSwitch(LinearLayout row,String key,boolean def){
        Switch sw=new Switch(this);sw.setContentDescription(switch(key){case "snap"->"自动吸边";case "fade"->"空闲时淡化";case "haptic"->"触感反馈";default->"增强悬浮层";});sw.setChecked(Prefs.of(this).getBoolean(key,def));
        int[][] states={{android.R.attr.state_checked},{}};sw.setThumbTintList(new ColorStateList(states,new int[]{Ui.GREEN,Ui.dark?0xFFB9C4BD:0xFFFAFAFA}));sw.setTrackTintList(new ColorStateList(states,new int[]{Ui.dark?0xFF53775A:0xFFB4D1BB,Ui.dark?0xFF4D594B:0xFFE0E5DB}));
        sw.setOnCheckedChangeListener((v,on)->{Prefs.of(this).edit().putBoolean(key,on).apply();if(key.equals("enhanced_overlay"))OverlayWindows.refreshAll();else refreshBall();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.leftMargin=dp(8);row.addView(sw,lp);touchable(row);row.setOnClickListener(v->sw.toggle());
    }
    private String[] gestureChoices(){String[] ids=Prefs.gestureActions(),choices=new String[ids.length+1];for(int i=0;i<ids.length;i++)choices[i]=Prefs.label(ids[i]);choices[choices.length-1]="指定一个软件…";return choices;}
    private void pickGestureApp(String key){java.util.List<AppCatalog.Entry> entries=AppCatalog.all(this);String[] names=entries.stream().map(entry->entry.label).toArray(String[]::new);new UiDialog(this).setTitle("选择要打开的软件").setItems(names,(dialog,index)->{Prefs.of(this).edit().putString("gesture_"+key,"app:"+entries.get(index).pkg).apply();render();}).setNegativeButton("取消",null).show();}
    private void pickThemeTime(String key,int fallback){int current=Prefs.of(this).getInt(key,fallback);new TimePickerDialog(this,(view,hour,minute)->{Prefs.of(this).edit().putInt(key,hour*60+minute).apply();refreshBall();render();},current/60,current%60,true).show();}
    private void chooseArt(){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),71);}catch(ActivityNotFoundException e){Feedback.show(this,"没有可用的系统图片选择器");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==71&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();new Thread(()->{String error=null;try{BallArt.importFile(this,uri);}catch(java.io.IOException e){error=e.getMessage();}final String message=error;runOnUiThread(()->{if(isDestroyed())return;if(message!=null)Feedback.show(this,message);else{refreshBall();render();Feedback.show(this,"自定义悬浮球已更新");}});},"Kongye-Art-Import").start();}}
    private void startBall(String action){
        if(!Settings.canDrawOverlays(this)){new UiDialog(this).setTitle("让空叶出现在屏幕上").setMessage("先允许「显示在其他应用上层」，空叶就能陪你在应用之间自由切换。授权后请返回这里，再次点击开启。").setPositiveButton("去授权",(d,w)->overlayPermission()).setNegativeButton("稍后",null).show();return;}
        Intent intent=new Intent(this,FloatService.class);if(action!=null)intent.putExtra("action",action);startForegroundService(intent);handler.postDelayed(this::render,250);
    }
    private void refreshBall(){if(FloatService.running)startService(new Intent(this,FloatService.class).setAction(FloatService.REFRESH));}
    private void overlayPermission(){safeSettings(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
    private void accessibilityPermission(){new UiDialog(this).setTitle("开启空叶无障碍服务").setMessage("用于增强悬浮层、导航、截图及仅由你触发的长截图滚动。\n\n仅在点击「提取文字」时读取当前界面的可访问文字，跳过密码框。截图和文字保存在本机，不上传。\n\n在系统设置中找到「空叶悬浮球」并手动开启，随时可关闭。侧载安装若遇「受限设置」，可在系统应用信息的右上角菜单中允许受限设置。").setPositiveButton("前往设置",(d,w)->safeSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).setNegativeButton("稍后",null).show();}
    private void safeSettings(Intent i){try{startActivity(i);}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_SETTINGS));}}
    private String appVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(android.content.pm.PackageManager.NameNotFoundException e){return "1.0";}}
    private void about(){new UiDialog(this).setTitle("空叶悬浮球  "+appVersion()).setMessage("单击：工具菜单\n双击：返回\n长按：环形快捷，滑到图标松手执行\n上划 / 下划：打开空叶软件页面 / 通知栏\n按住后拖动：移动；拖动中停住可框选截图\n\n手势可在「悬浮球」页改绑。区域截图和长截图需要 Android 11+ 与无障碍服务。\n\n截图、文字和录屏均在本机处理，不上传。图片 OCR 使用打包的 ML Kit 模型，SDK 可能发送性能和使用统计。浏览器访问你打开的网站。部分系统会限制后台运行，可在电池设置中允许。\n\n本项目为独立开发，交互参考 FV 悬浮球，不隶属于 fooView。").setPositiveButton("知道了",null).show();}
    /** Live appearance and edge-position preview for the unified settings card. */
    private final class Preview extends FrameLayout {
        private final FrameLayout ball;private final View art,leaf;private final int fixed;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);int size,opacity,gap;
        Preview(Context c,int fixedDp){
            super(c);fixed=fixedDp;size=Prefs.of(c).getInt("size",BallArt.DEFAULT_SIZE);gap=Prefs.of(c).getInt("edge_gap",0);opacity=fixedDp>0?100:Prefs.of(c).getInt("opacity",90);setContentDescription("悬浮球外观与位置实时预览");setWillNotDraw(fixedDp>0);
            ball=new FrameLayout(c);art=BallArt.create(c,dp(fixedDp>0?fixedDp:150));BallArt.styleContainer(ball,art!=null);if(art==null)ball.setElevation(dp(3));
            leaf=art!=null?art:new Ui.Icon(c,"leaf_outline",BallArt.FOREGROUND);ball.addView(leaf);addView(ball);update();
        }
        void update(){
            int px=dp(fixed>0?fixed:Math.max(36,Math.min(150,size)));ball.setLayoutParams(new FrameLayout.LayoutParams(px,px,fixed>0?Gravity.CENTER:Gravity.RIGHT|Gravity.CENTER_VERTICAL));ball.setTranslationX(fixed>0?0:-dp(Math.max(gap,12-size)));
            int inner=art!=null?px:Math.round(px*BallArt.LEAF_SCALE);leaf.setLayoutParams(new FrameLayout.LayoutParams(inner,inner,Gravity.CENTER));ball.setAlpha(opacity/100f);invalidate();
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);if(fixed>0)return;paint.setStrokeWidth(dp(.5f));paint.setColor(Ui.LINE);for(int x=dp(20);x<getWidth();x+=dp(20))canvas.drawLine(x,0,x,getHeight(),paint);for(int y=dp(20);y<getHeight();y+=dp(20))canvas.drawLine(0,y,getWidth(),y,paint);
            paint.setColor(Ui.MUTED);paint.setTextSize(dp(10));canvas.drawText("位置预览",dp(12),dp(21),paint);paint.setTextSize(dp(9));String edge="屏幕边缘";canvas.drawText(edge,getWidth()-dp(10)-paint.measureText(edge),getHeight()-dp(13),paint);
        }
    }
}
