package com.kongye.floatball;

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
    private static final String HIDE_FROM_RECENTS = "hide_from_recents";
    private static boolean createdUi;
    private boolean gestureEntrance,introTouchActive;
    private static java.lang.ref.WeakReference<MainActivity> visible=new java.lang.ref.WeakReference<>(null);
    private View motionContent(){return findViewById(android.R.id.content);}
    static boolean hideIfVisible(){return AppMotion.hideIfVisible();}
    static boolean openFilePicker(Intent intent){MainActivity page=visible.get();if(page==null||page.isFinishing()||page.isDestroyed())return false;page.skipFileResume=true;page.startActivity(intent);page.overridePendingTransition(0,0);return true;}
    private static final String[] TABS={"首页","悬浮球","设置"},TAB_ICONS={"nav_home","leaf","nav_settings"};
    private static final String[] GESTURES={"tap","double","long","up","down"},GESTURE_NAMES={"单击","双击","长按","上划","下划"};
    private LinearLayout shell, content;
    private ScrollView scroll;
    private UpdateCheckSession updateCheck;
    private FrameLayout updateCapsule,powerControl;private LinearLayout updateRow;
    private TextView readyText,restText;private View stateDot,playGlyph,pauseGlyph;private BotanicalPanel botanical;
    private android.animation.ValueAnimator powerTint,navMotion,tabMotion;private boolean animateTab;private int tabDirection=1;private float navPosition;
    private Preview preview;private LaunchIntroView intro;private PlantStageView perchedPlant;private View plantEntrance;
    private int tab=0, renderedTab=-1;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean renderedDark,renderedRunning,skipFileResume;
    private final Runnable themeTicker=new Runnable(){public void run(){if(ThemePolicy.isDark(MainActivity.this)!=renderedDark)render();handler.postDelayed(this,60000-System.currentTimeMillis()%60000);}};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);UpdateCacheCleanup.schedule(this);updateCheck=getLastNonConfigurationInstance() instanceof UpdateCheckSession retained?retained:new UpdateCheckSession();QuickActions.migrate(this);BallSettings.migrate(this);AppCatalog.prefetch(this);
        updateRecentsVisibility(Prefs.of(this).getBoolean(HIDE_FROM_RECENTS, true));
        if(state!=null)tab=state.getInt("tab",0);
        boolean launcher=(state==null||!createdUi)&&Intent.ACTION_MAIN.equals(getIntent().getAction())&&getIntent().hasCategory(Intent.CATEGORY_LAUNCHER);createdUi=true;
        if(Build.VERSION.SDK_INT>=31)getSplashScreen().setOnExitAnimationListener(view->view.remove());
        gestureEntrance=getIntent().getBooleanExtra("gesture_entry",false);render();if(launcher){FrameLayout layer=new FrameLayout(this);((ViewGroup)shell.getParent()).removeView(shell);layer.addView(shell,new FrameLayout.LayoutParams(-1,-1));intro=new LaunchIntroView(this,()->dismissIntro(true));layer.addView(intro,new FrameLayout.LayoutParams(-1,-1));setContentView(layer);}BallArt.prepare(this,()->{if(!isDestroyed()){refreshBall();if(intro==null)render();}});
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(intro!=null||introTouchActive){
            int action=event.getActionMasked();
            introTouchActive=action!=MotionEvent.ACTION_UP&&action!=MotionEvent.ACTION_CANCEL;
            if(action==MotionEvent.ACTION_UP&&intro!=null)dismissIntro(false);
            return true;
        }
        return super.dispatchTouchEvent(event);
    }
    private void dismissIntro(boolean animate){
        LaunchIntroView old=intro;
        if(old==null)return;
        old.animate().cancel();
        Runnable remove=()->{
            if(old.getParent() instanceof ViewGroup parent)parent.removeView(old);
            if(intro==old)intro=null;
        };
        if(animate)old.animate().alpha(0).setDuration(420).withEndAction(remove).start();
        else remove.run();
    }
    private void animateGestureEntrance(){if(!gestureEntrance)return;gestureEntrance=false;AppMotion.enter(this);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);gestureEntrance=intent.getBooleanExtra("gesture_entry",false);}
    @Override public Object onRetainNonConfigurationInstance(){return updateCheck;}
    static void runningChanged(){MainActivity page=visible.get();if(page!=null)page.handler.post(page::animateRunningState);}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("tab",tab);}
    @Override protected void onResume(){super.onResume();AppMotion.resume(this);if(!gestureEntrance){motionContent().setTranslationY(0);motionContent().setAlpha(1);motionContent().setScaleX(1);motionContent().setScaleY(1);}visible=new java.lang.ref.WeakReference<>(this);boolean keep=(intro!=null||gestureEntrance)||(skipFileResume&&renderedDark==ThemePolicy.isDark(this)&&renderedRunning==FloatService.running);skipFileResume=false;if(!keep)render();animateGestureEntrance();updateCheck.attach((result,error)->{if(isFinishing()||isDestroyed())return;updateCheckingVisual();startActivity(UpdateActivity.checkedIntent(this,result,error));});handler.removeCallbacks(themeTicker);handler.postDelayed(themeTicker,60000-System.currentTimeMillis()%60000);}
    @Override protected void onPause(){AppMotion.pause(this);updateCheck.detach();if(visible.get()==this)visible.clear();handler.removeCallbacks(themeTicker);super.onPause();}
    @Override protected void onDestroy(){AppMotion.destroy(this);if(!isChangingConfigurations())updateCheck.close();handler.removeCallbacksAndMessages(null);if(powerTint!=null)powerTint.cancel();if(navMotion!=null)navMotion.cancel();if(tabMotion!=null)tabMotion.cancel();super.onDestroy();}
    private int dp(float v){return Ui.dp(this,v);}
    private void render() {
        if(intro!=null)return;
        boolean enter=animateTab;int direction=tabDirection;animateTab=false;if(navMotion!=null)navMotion.cancel();if(tabMotion!=null)tabMotion.cancel();if(!enter)navPosition=tab;
        ThemePolicy.apply(this);renderedDark=Ui.dark;renderedRunning=FloatService.running;setTheme(Ui.dark?R.style.AppMotionThemeDark:R.style.AppMotionTheme);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);getWindow().getDecorView().setBackgroundColor(Color.TRANSPARENT);getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);getWindow().getDecorView().setSystemUiVisibility(Ui.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        int keepScroll=scroll!=null&&renderedTab==tab?scroll.getScrollY():0;renderedTab=tab;
        shell=Ui.column(this);shell.setBackgroundColor(Ui.BG);
        shell.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        setContentView(shell);
        LinearLayout header=Ui.row(this);Ui.pad(header,24,8,20,8);header.setMinimumHeight(dp(64));header.setClipToPadding(false);header.setClipChildren(false);
        TextView headerTitle=Ui.text(this,TABS[tab],16,Ui.INK,true);header.addView(headerTitle,new LinearLayout.LayoutParams(0,-2,1));perchedPlant=null;plantEntrance=null;if(tab==0){perchedPlant=new PlantStageView(this,true);perchedPlant.setTranslationY(dp(8));LinearLayout.LayoutParams at=new LinearLayout.LayoutParams(dp(58),dp(58));at.rightMargin=dp(8);View plantSpace=new View(this);header.addView(plantSpace,at);}FrameLayout headerLayer=new FrameLayout(this);headerLayer.setClipChildren(false);headerLayer.setClipToPadding(false);headerLayer.setElevation(dp(1));headerLayer.addView(header,new FrameLayout.LayoutParams(-1,-2));if(perchedPlant!=null){
            // Keep the whole plant inside its animated layer; the header draws above the moving card.
            FrameLayout plantGroup=new FrameLayout(this);plantGroup.setClipChildren(false);plantGroup.setClipToPadding(false);
            FrameLayout.LayoutParams plantAt=new FrameLayout.LayoutParams(dp(58),dp(58),Gravity.BOTTOM|Gravity.RIGHT);plantAt.rightMargin=dp(28);perchedPlant.setTranslationY(0);plantGroup.addView(perchedPlant,plantAt);plantEntrance=plantGroup;headerLayer.addView(plantGroup,new FrameLayout.LayoutParams(-1,-1));
            PlantGreeting greeting=new PlantGreeting(this,perchedPlant);FrameLayout.LayoutParams where=new FrameLayout.LayoutParams(-2,-2,Gravity.RIGHT|Gravity.TOP);where.setMargins(dp(70),dp(10),dp(86),0);headerLayer.addView(greeting,where);greeting.maybeGreet();}shell.addView(headerLayer);
        FrameLayout body=new FrameLayout(this);body.setClipChildren(false);shell.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        content=Ui.column(this);Ui.pad(content,16,tab==0?0:6,16,100);scroll.addView(content);body.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        if(perchedPlant!=null){PlantStageView plant=perchedPlant;scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->plant.setVisibility(y==0?View.VISIBLE:View.INVISIBLE));}
        preview=null;botanical=null;powerControl=null;updateCapsule=null;updateRow=null;if(tab==0)home();else if(tab==1)gestures();else appearance();
        if(keepScroll>0){ScrollView sc=scroll;sc.post(()->sc.scrollTo(0,keepScroll));}
        createNavigation(body,enter);shell.requestApplyInsets();if(enter)animateTabContent(headerTitle,direction);
    }
    private void createNavigation(FrameLayout body,boolean animate){
        FrameLayout nav=new FrameLayout(this);Ui.pad(nav,6,5,6,5);nav.setBackground(Ui.bg(Ui.dark?0xFF304B3D:0xFF2C473E,34,this));nav.setElevation(dp(5));nav.setContentDescription("底部悬浮导航");
        View glider=new View(this);glider.setContentDescription("导航滑动胶囊");glider.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);glider.setBackground(Ui.bg(Ui.PALE,28,this));FrameLayout.LayoutParams glideAt=new FrameLayout.LayoutParams(1,dp(52),Gravity.LEFT|Gravity.CENTER_VERTICAL);nav.addView(glider,glideAt);
        LinearLayout row=Ui.row(this);row.setGravity(Gravity.CENTER);nav.addView(row,new FrameLayout.LayoutParams(-1,-1));
        for(int i=0;i<TABS.length;i++){final int index=i;boolean on=i==tab;int tint=on?Ui.GREEN:0xFFB3BEB0;LinearLayout item=Ui.column(this);item.setGravity(Gravity.CENTER);Ui.pad(item,0,5,0,5);item.setContentDescription(TABS[i]);item.setSelected(on);item.setFocusable(true);item.setBackground(Ui.ripple(this,0,28));View icon=Ui.icon(this,TAB_ICONS[i],tint,24);item.addView(icon,new LinearLayout.LayoutParams(dp(24),dp(24)));Ui.gap(item,3);item.addView(Ui.centeredText(this,TABS[i],11,tint,on));
            if(on){icon.setTranslationY(animate?0:-dp(1.5f));icon.setScaleX(animate?1:1.06f);icon.setScaleY(animate?1:1.06f);if(animate)icon.animate().translationY(-dp(1.5f)).scaleX(1.06f).scaleY(1.06f).setDuration(400).start();}
            item.setOnClickListener(v->{if(tab!=index){tabDirection=index>tab?1:-1;animateTab=true;tab=index;render();}});row.addView(item,new LinearLayout.LayoutParams(0,dp(52),1));
        }
        boolean[] laid={false};nav.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(laid[0]||r<=l)return;laid[0]=true;int step=(nav.getWidth()-nav.getPaddingLeft()-nav.getPaddingRight())/3;glideAt.width=step;glider.setLayoutParams(glideAt);glider.setTranslationX(navPosition*step);int target=tab;
            if(animate&&android.animation.ValueAnimator.areAnimatorsEnabled()){glider.setScaleX(1.16f);glider.setScaleY(.92f);glider.animate().scaleX(1).scaleY(1).setDuration(350).start();navMotion=android.animation.ValueAnimator.ofFloat(navPosition,target);navMotion.setDuration(450);navMotion.setInterpolator(new android.view.animation.PathInterpolator(.22f,1,.36f,1));navMotion.addUpdateListener(a->{navPosition=(float)a.getAnimatedValue();glider.setTranslationX(navPosition*step);});navMotion.start();}else{navPosition=target;glider.setTranslationX(target*step);}
        });FrameLayout.LayoutParams at=new FrameLayout.LayoutParams(-1,dp(64),Gravity.BOTTOM);at.setMargins(dp(16),0,dp(16),dp(12));body.addView(nav,at);
    }
    private void animateTabContent(View title,int direction){
        if(!android.animation.ValueAnimator.areAnimatorsEnabled())return;android.view.animation.PathInterpolator pageEase=new android.view.animation.PathInterpolator(.22f,1,.36f,1),itemEase=new android.view.animation.PathInterpolator(.2f,.9f,.25f,1);
        LinearLayout page=content;View plant=plantEntrance;java.util.ArrayList<View> modules=new java.util.ArrayList<>();
        for(int i=0;i<page.getChildCount();i++){View child=page.getChildAt(i);if(child.getClass()==View.class)continue;child.setAlpha(0);child.setTranslationY(dp(14));modules.add(child);}
        page.setAlpha(0);page.setTranslationX(dp(18)*direction);page.setTranslationY(dp(4));page.setScaleX(.985f);page.setScaleY(.985f);
        if(plant!=null){plant.setAlpha(0);plant.setTranslationX(0);plant.setTranslationY(dp(24));}
        page.post(()->{if(page!=content)return;page.setPivotX(page.getWidth()/2f);
            if(plant!=null){plant.setPivotY(plant.getHeight());plant.setPivotX(perchedPlant.getLeft()+perchedPlant.getWidth()/2f);}
            long duration=Math.max(440,490+35L*Math.max(0,modules.size()-1));tabMotion=android.animation.ValueAnimator.ofFloat(0,duration);tabMotion.setDuration(duration);tabMotion.setInterpolator(new android.view.animation.LinearInterpolator());
            tabMotion.addUpdateListener(a->{float elapsed=(float)a.getAnimatedValue(),progress=pageEase.getInterpolation(Math.min(1,elapsed/440f)),scale=.985f+.015f*progress;
                page.setAlpha(progress);page.setTranslationX(dp(18)*direction*(1-progress));page.setTranslationY(dp(4)*(1-progress));page.setScaleX(scale);page.setScaleY(scale);
                for(int i=0;i<modules.size();i++){View child=modules.get(i);float entrance=itemEase.getInterpolation(Math.max(0,Math.min(1,(elapsed-10-35*i)/480f)));child.setAlpha(entrance);child.setTranslationY(dp(14)*(1-entrance));}
                if(plant!=null&&!modules.isEmpty()){
                    View card=modules.get(0);float lift=page.getTranslationY()+page.getPivotY()*(1-scale)+card.getTop()*(scale-1)+card.getTranslationY()*scale;
                    // Use the page transform at the plant anchor, including the offset caused by scaling.
                    float drift=page.getTranslationX()+(plant.getPivotX()-page.getPivotX())*(scale-1);
                    plant.setAlpha(progress*card.getAlpha());plant.setTranslationX(drift);plant.setTranslationY(lift);plant.setScaleX(scale);plant.setScaleY(scale);
                }
            });tabMotion.start();
        });
        title.setAlpha(0);title.setTranslationX(dp(10)*direction);title.setTranslationY(dp(4));title.animate().alpha(1).translationX(0).translationY(0).setDuration(350).setInterpolator(pageEase).start();
    }
    private void home() {
        boolean on=FloatService.running;
        LinearLayout status=group();status.setContentDescription("悬浮球运行状态卡片");
        botanical=new BotanicalPanel(this);status.addView(botanical,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout state=Ui.row(this);Ui.pad(state,18,16,18,18);state.setBackgroundColor(Ui.dark?0xFF28372B:0xFFEEF5EC);
        LinearLayout texts=Ui.column(this);TextView hello=Ui.text(this,"HELLO, LITTLE LEAF.",10,Ui.dark?0xFFAFBE91:0xFF6E8774,false);hello.setLetterSpacing(.1f);texts.addView(hello);Ui.gap(texts,6);
        LinearLayout stateText=Ui.row(this);stateDot=new View(this);stateDot.setBackground(Ui.bg(on?Ui.GREEN:Ui.MUTED,4,this));LinearLayout.LayoutParams dotAt=new LinearLayout.LayoutParams(dp(8),dp(8));dotAt.rightMargin=dp(8);stateText.addView(stateDot,dotAt);
        FrameLayout labels=new FrameLayout(this);readyText=Ui.text(this,"随时待命。",23,Ui.INK,true);restText=Ui.text(this,"稍作休息。",23,Ui.INK,true);labels.addView(readyText);labels.addView(restText);stateText.addView(labels);texts.addView(stateText);state.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        FrameLayout power=new FrameLayout(this);powerControl=power;power.setFocusable(true);power.setBackground(Ui.bg(on?Ui.GREEN:0xFFA8CC7C,26,this));
        playGlyph=Ui.icon(this,"status_play",0xFF1E3327,23);pauseGlyph=Ui.icon(this,"status_pause",Color.WHITE,23);power.addView(playGlyph,new FrameLayout.LayoutParams(dp(23),dp(23),Gravity.CENTER));power.addView(pauseGlyph,new FrameLayout.LayoutParams(dp(23),dp(23),Gravity.CENTER));
        showRunningState(on,false);
        power.setOnClickListener(v->{if(FloatService.running)startService(new Intent(this,FloatService.class).setAction(FloatService.STOP));else startBall(null);});
        state.addView(power,new LinearLayout.LayoutParams(dp(48),dp(48)));status.addView(state);content.addView(status);
        boolean overlay=Settings.canDrawOverlays(this),assist=AssistService.instance!=null;
        label("权限");content.addView(new PermissionStrip(this,new boolean[]{overlay,assist},new Runnable[]{this::overlayPermission,this::accessibilityPermission}));
        Ui.gap(content,18);LinearLayout toolHead=Ui.row(this);TextView title=Ui.text(this,"工具",13,Ui.MUTED,true);Ui.pad(title,6,0,0,0);toolHead.addView(title,new LinearLayout.LayoutParams(0,-2,1));toolHead.addView(iconButton("tool_sliders","管理首页工具",this::manageHomeTools),new LinearLayout.LayoutParams(dp(44),dp(44)));content.addView(toolHead);
        java.util.ArrayList<String> ids=HomeTools.load(this);LinearLayout tools=group();tools.setContentDescription("首页工具列表");Ui.pad(tools,6,8,6,8);
        tools.addView(new HomeToolGrid(this,ids,id->tool(id,HomeTools.label(id)),order->HomeTools.save(this,order)),new LinearLayout.LayoutParams(-1,-2));
        if(ids.isEmpty())tools.addView(textButton("添加工具",this::manageHomeTools));content.addView(tools);
    }
    private void manageHomeTools(){
        java.util.ArrayList<String> current=HomeTools.load(this);String[] ids=HomeTools.AVAILABLE,names=new String[ids.length];boolean[] checked=new boolean[ids.length];for(int i=0;i<ids.length;i++){names[i]=HomeTools.label(ids[i]);checked[i]=current.contains(ids[i]);}
        new UiDialog(this).setTitle("首页工具").setMultiChoiceItems(names,checked,(dialog,index,on)->checked[index]=on).setPositiveButton("确定",(dialog,which)->{java.util.ArrayList<String> result=new java.util.ArrayList<>();for(String id:current)for(int i=0;i<ids.length;i++)if(ids[i].equals(id)&&checked[i])result.add(id);for(int i=0;i<ids.length;i++)if(checked[i]&&!result.contains(ids[i]))result.add(ids[i]);HomeTools.save(this,result);render();}).setNegativeButton("取消",null).setNeutralButton("恢复默认",(dialog,which)->{HomeTools.save(this,java.util.Arrays.asList(HomeTools.DEFAULT));render();}).show();
    }
    private View tool(String id,String name){
        LinearLayout tile=Ui.column(this);tile.setGravity(Gravity.CENTER_HORIZONTAL);Ui.pad(tile,0,10,0,10);tile.setContentDescription(name);tile.setFocusable(true);tile.setOnClickListener(v->startBall(id));
        FrameLayout box=new FrameLayout(this);box.setBackground(Ui.bg(Ui.PALE,16,this));box.addView(Ui.icon(this,id,Ui.GREEN,24),new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));tile.addView(box,new LinearLayout.LayoutParams(dp(48),dp(48)));
        Ui.gap(tile,8);TextView t=Ui.centeredText(this,name,12,Ui.INK,false);t.setMaxLines(2);t.setMinLines(2);t.setEllipsize(TextUtils.TruncateAt.END);tile.addView(t);return tile;
    }
    private void permission(LinearLayout group,String title,String desc,boolean granted,Runnable action){
        LinearLayout cell=Ui.column(this);cell.setGravity(Gravity.CENTER);Ui.pad(cell,0,4,0,4);cell.setContentDescription(title+(granted?"，已开启":"，未开启")+"。"+desc);cell.setFocusable(true);cell.setBackground(Ui.ripple(this,0,14));cell.setOnClickListener(v->action.run());
        cell.addView(Ui.centeredText(this,title,12,Ui.INK,false));Ui.gap(cell,9);
        TextView chip=Ui.centeredText(this,granted?"✓ 已开启":"去开启",10,granted?Ui.GREEN:Ui.dark?0xFF102E21:Color.WHITE,false);
        chip.setBackground(Ui.bg(granted?Ui.PALE:Ui.GREEN,14,this));Ui.pad(chip,9,5,9,5);cell.addView(chip,new LinearLayout.LayoutParams(-2,-2));group.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
    }
    private void gestures() {
        label("外观与位置");ballAppearance();
        label("手势操作");
        LinearLayout card=group();card.setContentDescription("手势操作设置组");
        String[] icons={"gesture_tap","gesture_double","gesture_hold","up","down"};
        for(int i=0;i<GESTURES.length;i++){
            String key=GESTURES[i],name=GESTURE_NAMES[i];
            LinearLayout row=item(card,name,null,()->{String[] actions=Prefs.gestureActions();new UiDialog(this).setTitle(name).setItems(gestureChoices(),(d,which)->{if(actions[which].equals("pick_app")){pickGestureApp(key);return;}Prefs.of(this).edit().putString("gesture_"+key,actions[which]).apply();render();}).show();});
            gestureBadge(row,icons[i]);
            value(row,Prefs.label(this,Prefs.action(this,key)),Ui.MUTED);chevron(row);
        }
        LinearLayout haptic=item(card,"触感反馈",null,null);addSwitch(haptic,"haptic",true);
        divider(card);TextView reset=textButton("恢复默认手势",()->{SharedPreferences.Editor e=Prefs.of(this).edit();for(String k:GESTURES)e.remove("gesture_"+k);e.apply();render();});reset.setTextColor(Ui.dark?Ui.GREEN:0xFF2D815B);reset.setGravity(Gravity.CENTER);reset.setPadding(0,0,0,0);card.addView(reset,new LinearLayout.LayoutParams(-1,dp(56)));content.addView(card);

    }
    private void ballAppearance() {
        SharedPreferences prefs=Prefs.of(this);boolean custom=prefs.getBoolean("custom_art",false)&&BallArt.file(this).isFile();
        LinearLayout look=group();look.setContentDescription("外观与位置设置组");
        Ui.pad(look,18,18,18,10);
        preview=new Preview(this,0);android.graphics.drawable.GradientDrawable stage=Ui.bg(Ui.dark?0xFF152019:0xFFF4F6F0,18,this);stage.setStroke(dp(.7f),Ui.LINE);preview.setBackground(stage);preview.setClipToOutline(true);look.addView(preview,new LinearLayout.LayoutParams(-1,dp(124)));
        LinearLayout style=Ui.row(this);Ui.pad(style,0,15,0,15);
        if(!custom){LinearLayout colors=Ui.row(this);int[] palette={0xFF28664B,0xFF68A67D,0xFF567AAB,0xFF8D70A6,0xFFD19251};String[] names={"深叶绿","浅叶绿","雾蓝","藤紫","秋杏"};int selected=BallArt.color(this);
            for(int i=0;i<palette.length;i++){final int color=palette[i];FrameLayout cell=new FrameLayout(this);cell.setContentDescription(names[i]);cell.setOnClickListener(v->{Prefs.of(this).edit().putInt("color",color).apply();refreshBall();render();});cell.setBackground(Ui.ripple(this,0,16));
                if(color==selected){View ring=new View(this);android.graphics.drawable.GradientDrawable outline=Ui.bg(Color.TRANSPARENT,16,this);outline.setStroke(dp(2),color);ring.setBackground(outline);cell.addView(ring,new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.CENTER));}
                View dot=new View(this);dot.setBackground(Ui.bg(color,11,this));cell.addView(dot,new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));colors.addView(cell,new LinearLayout.LayoutParams(0,dp(34),1));}
            style.addView(colors,new LinearLayout.LayoutParams(0,-2,1));
        }else{TextView restore=Ui.text(this,"恢复叶片",12,Ui.GREEN,true);Ui.pad(restore,0,8,0,8);restore.setOnClickListener(v->{Prefs.of(this).edit().putBoolean("custom_art",false).apply();refreshBall();render();});style.addView(restore,new LinearLayout.LayoutParams(0,-2,1));}
        TextView image=Ui.centeredText(this,custom?"更换图片 / GIF":"自定义图片 / GIF",11,Ui.GREEN,true);image.setContentDescription("自定义图片 / GIF");image.setBackground(Ui.ripple(this,Ui.PALE,11));Ui.pad(image,11,7,11,7);image.setOnClickListener(v->chooseArt());LinearLayout.LayoutParams imageAt=new LinearLayout.LayoutParams(-2,-2);imageAt.leftMargin=dp(10);style.addView(image,imageAt);look.addView(style);
        LinearLayout controls=Ui.column(this);View separator=new View(this);separator.setBackgroundColor(Ui.LINE);controls.addView(separator,new LinearLayout.LayoutParams(-1,1));slider(controls,"大小","size_percent",100,30,500,"%");
        LinearLayout opacity=slider(controls,"不透明度","opacity",90,30,100,"%");relatedToggle(opacity,"空闲时淡化",null,"fade",true);
        LinearLayout position=slider(controls,"边框距离","edge_percent",100,30,200,"%");position.setPadding(position.getPaddingLeft(),position.getPaddingTop(),position.getPaddingRight(),0);relatedToggle(position,"自动吸边",null,"snap",true);
        look.addView(controls);content.addView(look);
    }
    private void appearance() {
        SharedPreferences prefs=Prefs.of(this);
        label("悬浮球设置");LinearLayout display=group();
        LinearLayout enhanced=settingItem(display,"layers","增强悬浮层",null,null);addSwitch(enhanced,"enhanced_overlay",true);
        settingLink(display,"app_ball","应用内悬浮球",null,null,()->startActivity(new Intent(this,AppBehaviorActivity.class)));
        settingLink(display,"recover","找回悬浮球",null,null,()->startService(new Intent(this,FloatService.class).setAction(FloatService.RECOVER)));
        settingLink(display,"position","重置位置",null,null,this::resetPosition);content.addView(display);
        label("通用与显示");LinearLayout theme=group();
        settingLink(theme,ThemePolicy.icon(this),"深色模式",null,ThemePolicy.label(this),()->new UiDialog(this).setTitle("深色模式").setItems(ThemePolicy.LABELS,(d,which)->{Prefs.of(this).edit().putString("theme",ThemePolicy.MODES[which]).apply();refreshBall();render();}).show());
        boolean schedule="schedule".equals(prefs.getString("theme","system"));int start=prefs.getInt("dark_start",1320),end=prefs.getInt("dark_end",420);
        if(schedule){link(theme,"开始时间",ThemePolicy.time(start),()->pickThemeTime("dark_start",1320));link(theme,"结束时间",ThemePolicy.time(end),()->pickThemeTime("dark_end",420));}
        LinearLayout hide=settingItem(theme,"eye_closed","后台隐藏",null,null);
        addSwitch(hide,HIDE_FROM_RECENTS,true);
        if(VaultClient.debug(this))settingLink(theme,"password_fill","密码空间接口",null,new VaultClient(this).providerPackage(),()->new UiDialog(this).setTitle("密码空间接口（调试）").setItems(VaultClient.PACKAGES,(d,which)->{Prefs.of(this).edit().putString("vault_package",VaultClient.PACKAGES[which]).apply();render();}).setNegativeButton("取消",null).show());
        content.addView(theme);
        if(schedule){Ui.gap(content,10);hint(start==end?"起止时间相同：全天深色。":"按手机本地时间切换，可跨午夜。");}
        label("关于与更新");LinearLayout other=group();
        LinearLayout update=settingItem(other,"update","检查更新",null,this::checkForUpdates);updateRow=update;TextView version=Ui.centeredText(this,"v"+appVersion(),12,Ui.GREEN,true);version.setContentDescription("当前版本 v"+appVersion());version.setBackground(Ui.bg(Ui.dark?Ui.PALE:0xFFE4EEE7,14,this));Ui.pad(version,9,4,9,4);LinearLayout.LayoutParams versionAt=new LinearLayout.LayoutParams(-2,-2);versionAt.leftMargin=dp(12);versionAt.rightMargin=dp(4);updateCapsule=new FrameLayout(this);updateCapsule.setMinimumWidth(dp(64));updateCapsule.addView(version,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));update.addView(updateCapsule,versionAt);chevron(update);updateCheckingVisual();
        settingLink(other,"github","GitHub",null,null,()->openWeb(GitHubUpdate.PROJECT));
        settingLink(other,"info","关于空叶",null,null,this::about);content.addView(other);

    }
    private void resetPosition(){
        Prefs.of(this).edit().remove("x").remove("y").apply();
        refreshBall();
        Feedback.show(this,"位置已重置");
    }

    private LinearLayout settingItem(LinearLayout group,String icon,String title,String subtitle,Runnable action){
        if(group.getChildCount()>0)divider(group);LinearLayout row=Ui.row(this);Ui.pad(row,16,12,16,12);row.setMinimumHeight(dp(64));
        FrameLayout badge=new FrameLayout(this);badge.setBackground(Ui.bg(Ui.dark?Ui.PALE:0xFFEEF3E8,10,this));badge.addView(Ui.icon(this,icon,Ui.GREEN,17),new FrameLayout.LayoutParams(dp(17),dp(17),Gravity.CENTER));row.addView(badge,new LinearLayout.LayoutParams(dp(30),dp(30)));
        LinearLayout texts=Ui.column(this);Ui.pad(texts,10,0,0,0);texts.addView(Ui.text(this,title,14,Ui.INK,false));if(subtitle!=null){Ui.gap(texts,4);TextView sub=Ui.text(this,subtitle,11,Ui.MUTED,false);sub.setMaxLines(2);texts.addView(sub);}row.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        if(action!=null){touchable(row);row.setOnClickListener(v->action.run());}group.addView(row);return row;
    }
    private void settingLink(LinearLayout group,String icon,String title,String subtitle,String value,Runnable action){LinearLayout row=settingItem(group,icon,title,subtitle,action);if(value!=null)value(row,value,Ui.MUTED);chevron(row);}
    private void openWeb(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));}catch(ActivityNotFoundException|SecurityException e){Feedback.show(this,"没有可用的浏览器，请先安装或启用浏览器");}}
    private void updateCheckingVisual(){
        if(updateCapsule==null)return;boolean busy=updateCheck.checking;updateRow.setEnabled(!busy);updateRow.setContentDescription(busy?"检查更新，正在检查":"检查更新");
        View version=updateCapsule.getChildAt(0);version.setVisibility(busy?View.INVISIBLE:View.VISIBLE);
        if(updateCapsule.getChildCount()>1)updateCapsule.removeViewAt(1);
        if(busy){ProgressBar spinner=new ProgressBar(this);spinner.setContentDescription("正在检查更新");spinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.GREEN));updateCapsule.addView(spinner,new FrameLayout.LayoutParams(dp(18),dp(18),Gravity.CENTER));}
    }
    private void checkForUpdates(){if(updateCheck.checking)return;updateCheck.start(appVersion());updateCheckingVisual();}
    private void animateRunningState(){boolean on=FloatService.running;if(on==renderedRunning)return;renderedRunning=on;if(powerControl!=null)showRunningState(on,true);}
    private void showRunningState(boolean on,boolean animate){
        if(powerControl==null)return;long duration=animate&&android.animation.ValueAnimator.areAnimatorsEnabled()?750:0;
        powerControl.setContentDescription(on?"关闭悬浮球":"启动悬浮球");
        View[] views={readyText,restText,pauseGlyph,playGlyph};
        for(int i=0;i<views.length;i++){View v=views[i];boolean active=(i%2==0)==on;v.animate().cancel();v.setImportantForAccessibility(active?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            if(duration==0){v.setAlpha(active?1:0);v.setTranslationY(0);v.setScaleX(1);v.setScaleY(1);}else if(i<2){if(active&&v.getAlpha()==0)v.setTranslationY(dp(9));v.animate().alpha(active?1:0).translationY(active?0:-dp(9)).setDuration(duration).start();}else{v.animate().alpha(active?1:0).scaleX(active?1:.65f).scaleY(active?1:.65f).rotation(active?0:35).setDuration(duration).start();}}
        if(powerTint!=null)powerTint.cancel();int from=on?0xFFA8CC7C:Ui.GREEN,to=on?Ui.GREEN:0xFFA8CC7C;
        if(duration>0){powerTint=android.animation.ValueAnimator.ofArgb(from,to);powerTint.setDuration(duration);powerTint.addUpdateListener(a->{if(powerControl!=null)powerControl.setBackground(Ui.bg((int)a.getAnimatedValue(),26,this));});powerTint.start();}else powerControl.setBackground(Ui.bg(to,26,this));
        stateDot.setBackground(Ui.bg(on?Ui.GREEN:Ui.MUTED,4,this));if(botanical!=null)botanical.running(on,animate);
    }
    private LinearLayout group(){LinearLayout g=Ui.column(this);g.setBackground(Ui.bg(Ui.SURFACE,22,this));g.setClipToOutline(true);return g;}
    private void label(String title){if(content.getChildCount()>0)Ui.gap(content,22);TextView t=Ui.text(this,title,13,Ui.dark?Ui.MUTED:0xFF71806E,true);Ui.pad(t,6,0,6,8);content.addView(t);}
    private void hint(String value){TextView t=Ui.text(this,value,12,Ui.MUTED,false);t.setLineSpacing(dp(3),1);Ui.pad(t,6,0,6,0);content.addView(t);}
    private TextView textButton(String title,Runnable action){TextView t=Ui.centeredText(this,title,14,Ui.GREEN,true);Ui.pad(t,16,12,16,12);touchable(t);t.setOnClickListener(v->action.run());return t;}
    private FrameLayout iconButton(String icon,String description,Runnable action){FrameLayout v=new FrameLayout(this);v.setContentDescription(description);v.addView(Ui.icon(this,icon,Ui.GREEN,22),new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));TypedValue tv=new TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,tv,true);v.setBackgroundResource(tv.resourceId);v.setOnClickListener(w->action.run());return v;}
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
    private void chevron(LinearLayout row){View c=Ui.icon(this,"forward",Ui.GREEN,16);((LinearLayout.LayoutParams)c.getLayoutParams()).leftMargin=dp(4);row.addView(c);}
    private void link(LinearLayout group,String title,String value,Runnable action){LinearLayout row=item(group,title,null,action);if(value!=null)value(row,value,Ui.MUTED);chevron(row);}
    private LinearLayout slider(LinearLayout group,String title,String key,int def,int min,int max,String unit){
        if(group.getChildCount()>1){View line=new View(this);line.setBackgroundColor(Ui.LINE);group.addView(line,new LinearLayout.LayoutParams(-1,1));}
        int current=Math.max(min,Math.min(max,Prefs.of(this).getInt(key,def)));LinearLayout box=Ui.column(this);Ui.pad(box,0,14,0,4);LinearLayout head=Ui.row(this);head.addView(Ui.text(this,title,13.5f,Ui.INK,true),new LinearLayout.LayoutParams(0,-2,1));TextView value=Ui.text(this,current+unit,12,Ui.GREEN,true);head.addView(value);box.addView(head);Ui.gap(box,6);
        SeekBar bar=new LeafSeekBar(this);bar.setContentDescription(title);bar.setMax(max-min);bar.setProgress(current-min);box.addView(bar,new LinearLayout.LayoutParams(-1,dp(32)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int progress,boolean user){value.setText((progress+min)+unit);if(preview==null)return;if(key.equals("size_percent"))preview.size=progress+min;else if(key.equals("opacity"))preview.opacity=progress+min;else if(key.equals("edge_percent"))preview.gap=progress+min;preview.update();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){Prefs.of(MainActivity.this).edit().putInt(key,s.getProgress()+min).apply();refreshBall();}});group.addView(box);return box;
    }
    private void relatedToggle(LinearLayout box,String title,String subtitle,String key,boolean def){LinearLayout row=Ui.row(this);Ui.pad(row,0,0,0,2);row.addView(Ui.text(this,title,11.5f,Ui.MUTED,false),new LinearLayout.LayoutParams(0,-2,1));addSwitch(row,key,def);View toggle=row.getChildAt(row.getChildCount()-1);toggle.getLayoutParams().height=dp(26);box.addView(row);}
    private void addSwitch(LinearLayout row,String key,boolean def){
        LeafSwitch sw=new LeafSwitch(this);sw.setContentDescription(switch(key){case "snap"->"自动吸边";case "fade"->"空闲时淡化";case "haptic"->"触感反馈";case HIDE_FROM_RECENTS->"后台隐藏";default->"增强悬浮层";});sw.setChecked(Prefs.of(this).getBoolean(key,def));sw.setOnCheckedChangeListener((v,on)->{
            Prefs.of(this).edit().putBoolean(key,on).apply();
            switch(key){
                case "enhanced_overlay" -> OverlayWindows.refreshAll();
                case HIDE_FROM_RECENTS -> updateRecentsVisibility(on);
                default -> refreshBall();
            }
        });LinearLayout.LayoutParams at=new LinearLayout.LayoutParams(dp(38),dp(32));at.leftMargin=dp(8);row.addView(sw,at);touchable(row);row.setOnClickListener(v->sw.toggle());
    }
    private void updateRecentsVisibility(boolean hidden) {
        ComponentName main = new ComponentName(this, MainActivity.class);
        for (ActivityManager.AppTask task : getSystemService(ActivityManager.class).getAppTasks()) {
            try {
                Intent root = task.getTaskInfo().baseIntent;
                if (root != null && main.equals(root.getComponent())) task.setExcludeFromRecents(hidden);
            } catch (IllegalArgumentException ignored) {
                // A task may have been removed while the system was returning the list.
            }
        }
    }
    private void gestureBadge(LinearLayout row,String icon){FrameLayout badge=new FrameLayout(this);badge.setBackground(Ui.bg(Ui.PALE,11,this));badge.addView(Ui.icon(this,icon,Ui.GREEN,18),new FrameLayout.LayoutParams(dp(18),dp(18),Gravity.CENTER));LinearLayout.LayoutParams at=new LinearLayout.LayoutParams(dp(32),dp(32));at.rightMargin=dp(12);row.addView(badge,0,at);}
    private String[] gestureChoices(){String[] ids=Prefs.gestureActions(),choices=new String[ids.length];for(int i=0;i<ids.length;i++)choices[i]=Prefs.label(ids[i]);return choices;}
    private void pickGestureApp(String key){java.util.List<AppCatalog.Entry> entries=AppCatalog.all(this);String[] names=entries.stream().map(entry->entry.label).toArray(String[]::new);new UiDialog(this).setTitle("选择要打开的软件").setItems(names,(dialog,index)->{Prefs.of(this).edit().putString("gesture_"+key,"app:"+entries.get(index).pkg).apply();render();}).setNegativeButton("取消",null).show();}
    private void pickThemeTime(String key,int fallback){int current=Prefs.of(this).getInt(key,fallback);new TimePickerDialog(this,(view,hour,minute)->{Prefs.of(this).edit().putInt(key,hour*60+minute).apply();refreshBall();render();},current/60,current%60,true).show();}
    private void chooseArt(){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),71);}catch(ActivityNotFoundException e){Feedback.show(this,"没有可用的系统图片选择器");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==71&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();new Thread(()->{String error=null;try{BallArt.importFile(this,uri);}catch(java.io.IOException e){error=e.getMessage();}final String message=error;runOnUiThread(()->{if(isDestroyed())return;if(message!=null)Feedback.show(this,message);else{refreshBall();render();Feedback.show(this,"自定义悬浮球已更新");}});},"Kongye-Art-Import").start();}}
    private void startBall(String action){
        if(!Settings.canDrawOverlays(this)){new UiDialog(this).setTitle("让空叶出现在屏幕上").setMessage("先允许「显示在其他应用上层」，空叶就能陪你在应用之间自由切换。授权后请返回这里，再次点击开启。").setPositiveButton("去授权",(d,w)->overlayPermission()).setNegativeButton("稍后",null).show();return;}
        boolean changed=!FloatService.running;Intent intent=new Intent(this,FloatService.class);if(action!=null)intent.putExtra("action",action);startForegroundService(intent);if(changed)handler.postDelayed(this::animateRunningState,100);
    }
    private void refreshBall(){if(FloatService.running)startService(new Intent(this,FloatService.class).setAction(FloatService.REFRESH));}
    private void overlayPermission(){safeSettings(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
    private void accessibilityPermission(){safeSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
    private void safeSettings(Intent i){try{startActivity(i);}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_SETTINGS));}}
    private String appVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(android.content.pm.PackageManager.NameNotFoundException e){return "1.0";}}
    private void about(){startActivity(new Intent(this,AboutActivity.class));}
    /** Live appearance and edge-position preview for the unified settings card. */
    private final class Preview extends FrameLayout {
        private final FrameLayout ball;private final View art,leaf;private final int fixed;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);int size,opacity,gap;
        Preview(Context c,int fixedDp){
            super(c);fixed=fixedDp;size=BallSettings.percent(c);gap=BallSettings.edge(c);opacity=fixedDp>0?100:Prefs.of(c).getInt("opacity",90);setContentDescription("悬浮球外观与位置实时预览");setWillNotDraw(fixedDp>0);
            ball=new FrameLayout(c);art=BallArt.create(c,dp(fixedDp>0?fixedDp:180));BallArt.styleContainer(ball,art!=null);if(art==null)ball.setElevation(dp(3));
            leaf=art!=null?art:new Ui.Icon(c,"ball_leaf",BallArt.FOREGROUND);ball.addView(leaf);addView(ball);update();
        }
        void update(){
            int px=dp(fixed>0?fixed:BallScale.dp(size));int[] dims=art!=null?BallArt.dimensions(getContext(),px):new int[]{px,px};ball.setLayoutParams(new FrameLayout.LayoutParams(dims[0],dims[1],fixed>0?Gravity.CENTER:Gravity.RIGHT|Gravity.CENTER_VERTICAL));ball.setTranslationX(fixed>0?0:-BallScale.inset(gap,dims[0]));
            int inner=art!=null?px:Math.round(px*BallArt.LEAF_SCALE);leaf.setLayoutParams(new FrameLayout.LayoutParams(art!=null?-1:inner,art!=null?-1:inner,Gravity.CENTER));ball.setAlpha(opacity/100f);invalidate();
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);if(fixed>0)return;paint.setStrokeWidth(dp(.5f));paint.setColor(Ui.LINE);for(int x=dp(20);x<getWidth();x+=dp(20))canvas.drawLine(x,0,x,getHeight(),paint);for(int y=dp(20);y<getHeight();y+=dp(20))canvas.drawLine(0,y,getWidth(),y,paint);
            paint.setColor(Ui.MUTED);paint.setTextSize(dp(10));canvas.drawText("位置预览",dp(12),dp(21),paint);paint.setTextSize(dp(9));String edge="屏幕边缘";canvas.drawText(edge,getWidth()-dp(10)-paint.measureText(edge),getHeight()-dp(13),paint);
        }
    }
}
