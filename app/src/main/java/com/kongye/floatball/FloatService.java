package com.kongye.floatball;

import android.accessibilityservice.AccessibilityService;
import android.animation.ValueAnimator;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import android.widget.*;
import java.util.*;

public class FloatService extends Service {
    public static boolean running;static FloatService instance;private String foreground="",appMode="normal";private boolean captureVisible=true,customBallArt;
    static final String REFRESH="com.kongye.floatball.REFRESH",STOP="com.kongye.floatball.STOP",RECOVER="com.kongye.floatball.RECOVER";private long recoveryUntil;
    private WindowManager windows;
    private WindowManager.LayoutParams ballParams;
    private FrameLayout ball,panel;
    private boolean menuManaging;private LinearLayout menuGrid,menuManageBar;private FrameLayout menuManageButton;private ArrayList<String> menuOrder,menuSelected;private FloatingBrowser browser;private AppLauncher appLauncher;private TextView panelHeading;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ValueAnimator snapAnimator,recoveryAnimator;private RecoveryPulse recoveryPulse;private boolean stopping;private int stopGeneration;private Runnable stopAfterMotion;
    private BallTouch touch;
    private boolean captureInFlight;
    private CaptureController captures;
    private QuickDock quickDock;private GestureCaptureOverlay captureTarget;private boolean followReturning;private int followHomeX,followHomeY;
    private boolean renderedDark;private int displayRotation;
    private final Runnable themeTick=new Runnable(){public void run(){boolean now=ThemePolicy.isDark(FloatService.this);if(now!=renderedDark){ThemePolicy.apply(FloatService.this);renderedDark=now;closePanel();}handler.postDelayed(this,60000-System.currentTimeMillis()%60000);}};
    private final Runnable appPolicyTick=new Runnable(){public void run(){boolean configured=false;for(String key:Prefs.of(FloatService.this).getAll().keySet())if(key.startsWith("app_mode_")){configured=true;break;}if(configured&&AppCatalog.hasUsage(FloatService.this)){long now=System.currentTimeMillis();android.app.usage.UsageEvents events=getSystemService(android.app.usage.UsageStatsManager.class).queryEvents(now-10000,now);android.app.usage.UsageEvents.Event event=new android.app.usage.UsageEvents.Event();String pkg=null;long last=0;if(events!=null)while(events.hasNextEvent()){events.getNextEvent(event);if(event.getEventType()==android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED&&event.getTimeStamp()>=last&&!"com.android.systemui".equals(event.getPackageName())){last=event.getTimeStamp();pkg=event.getPackageName();}}if(pkg!=null)foregroundChanged(pkg);}handler.postDelayed(this,750);}};
    private final Runnable fade=()->{if(ball!=null&&panel==null&&browser==null&&(quickDock==null||!quickDock.isVisible())&&!appMode.equals("hidden")&&Prefs.of(this).getBoolean("fade",true))ball.animate().alpha(baseAlpha()*.45f).setDuration(300).start();};
    private final BroadcastReceiver screenReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(Intent.ACTION_SCREEN_OFF.equals(i.getAction())){closePanel();if(captures!=null&&captures.isActive()){Prefs.of(c).edit().putString("last_capture_cancel_reason","screen_off").apply();captures.cancel();}hideCaptureTarget();if(ball!=null)ball.setVisibility(View.GONE);}else if(Intent.ACTION_USER_PRESENT.equals(i.getAction())||Intent.ACTION_SCREEN_ON.equals(i.getAction())){handler.postDelayed(()->{applyPolicy();wakeBall();},250);}}};
    @Override public void onCreate(){
        super.onCreate();instance=this;BallSettings.migrate(this);handler.postDelayed(appPolicyTick,300);handler.postDelayed(ballWatchdog,1500);foreground=AssistService.foregroundPackage;appMode=AppBehavior.mode(this,foreground);QuickActions.migrate(this);ThemePolicy.apply(this);renderedDark=Ui.dark;setTheme(Ui.dark?R.style.AppThemeDark:R.style.AppTheme);windows=OverlayWindows.get(this);displayRotation=windows.getDefaultDisplay().getRotation();quickDock=new QuickDock(this,this::perform);captures=new CaptureController(this,visible->{captureVisible=visible;applyPolicy();});handler.postDelayed(themeTick,60000-System.currentTimeMillis()%60000);
        NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("float","空叶悬浮球运行状态",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent recover=PendingIntent.getService(this,3,new Intent(this,FloatService.class).setAction(RECOVER),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,FloatService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,"float").setSmallIcon(R.drawable.ic_leaf_notification).setContentTitle("空叶在这里").setContentText("轻点小叶展开工具 · 按住后拖动").setContentIntent(open).setOngoing(true).setShowWhen(false).addAction(new Notification.Action.Builder(null,"找回悬浮球",recover).build()).addAction(new Notification.Action.Builder(null,"停止悬浮球",stop).build()).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(1001,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(1001,notification);
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_PRESENT);filter.addAction(Intent.ACTION_SCREEN_ON);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(screenReceiver,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screenReceiver,filter);AppCatalog.prefetch(this);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&STOP.equals(intent.getAction())){stopWithMotion();return START_NOT_STICKY;}
        if(stopping){stopGeneration++;handler.removeCallbacks(stopAfterMotion);stopping=false;running=true;if(ball!=null){ballParams.alpha=1;ball.animate().cancel();ball.setScaleX(1);ball.setScaleY(1);applyPolicy();wakeBall();}MainActivity.runningChanged();}
        if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
        if(intent!=null&&REFRESH.equals(intent.getAction())){appMode=AppBehavior.mode(this,AssistService.foregroundPackage);ThemePolicy.apply(this);renderedDark=Ui.dark;setTheme(Ui.dark?R.style.AppThemeDark:R.style.AppTheme);closePanel();removeBall();}
        boolean wasRunning=running;if(ball==null)try{addBall();}catch(RuntimeException e){toast("无法显示悬浮窗，请检查权限");stopSelf();return START_NOT_STICKY;}
        running=true;if(!wasRunning){MainActivity.runningChanged();if(ball!=null&&ball.getVisibility()==View.VISIBLE&&!appMode.equals("hidden")){ball.setAlpha(0);ball.setScaleX(.65f);ball.setScaleY(.65f);ball.animate().alpha(baseAlpha()).scaleX(1).scaleY(1).setDuration(android.animation.ValueAnimator.areAnimatorsEnabled()?750:0).start();}}if(intent!=null&&RECOVER.equals(intent.getAction()))recoverBall();
        if(intent!=null&&intent.hasExtra("action")){String action=intent.getStringExtra("action");handler.postDelayed(()->perform(action),wasRunning&&"apps".equals(action)?0:220);}
        BallArt.prepare(this,()->{if(instance==this&&running)startService(new Intent(this,FloatService.class).setAction(REFRESH));});return START_STICKY;
    }
    @Override public IBinder onBind(Intent i){return null;}
    private int dp(float value){return Ui.dp(this,value);}
    private int screenWidth(){android.util.DisplayMetrics m=new android.util.DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(m);return m.widthPixels;}
    private int screenHeight(){android.util.DisplayMetrics m=new android.util.DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(m);return m.heightPixels;}
    private float baseAlpha(){return Prefs.of(this).getInt("opacity",90)/100f;}
    private void addBall(){
        int size=BallSettings.pixels(this);
        ball=new FrameLayout(this);ball.setContentDescription("空叶悬浮球：点击菜单，双击返回，按住后拖动");
        if(Build.VERSION.SDK_INT>=29)ball.setSystemGestureExclusionRects(Collections.singletonList(new Rect(0,0,size,size)));
        View art=BallArt.create(this,size);customBallArt=art!=null;BallArt.styleContainer(ball,art!=null);View leaf=art==null?new Ui.Icon(this,"ball_leaf",BallArt.FOREGROUND):art;int iconSize=art==null?Math.round(size*BallArt.LEAF_SCALE):size;FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(iconSize,iconSize,Gravity.CENTER);ball.addView(leaf,lp);
        int[] artSize=art!=null?BallArt.dimensions(this,size):new int[]{size,size};if(art!=null)leaf.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));ballParams=new WindowManager.LayoutParams(artSize[0],artSize[1],WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        ballParams.setTitle("空叶悬浮球");if(Build.VERSION.SDK_INT>=30)ballParams.setFitInsetsTypes(0);if(Build.VERSION.SDK_INT>=28)ballParams.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;ballParams.gravity=Gravity.TOP|Gravity.LEFT;ballParams.x=GestureRules.clamp(Prefs.of(this).getInt("x",screenWidth()-ballParams.width-edgeGap()),edgeGap(),screenWidth()-ballParams.width-edgeGap());ballParams.y=GestureRules.clamp(Prefs.of(this).getInt("y",screenHeight()/3),edgeGapY(),screenHeight()-ballParams.height-edgeGapY());
        if(Prefs.of(this).getBoolean("snap",true))ballParams.x=GestureRules.edgeWithInset(ballParams.x,screenWidth(),ballParams.width,edgeGap());
        touch=new BallTouch();ball.setAlpha(baseAlpha());ball.setOnTouchListener(touch);ball.setOnClickListener(v->perform(Prefs.action(this,"tap")));windows.addView(ball,ballParams);applyPolicy();wakeBall();
        KeyguardManager km=getSystemService(KeyguardManager.class);if(km.isKeyguardLocked())ball.setVisibility(View.GONE);
    }
    private int edgeGap(){return BallScale.inset(BallSettings.edge(this),ballParams==null?BallArt.dimensions(this,BallSettings.pixels(this))[0]:ballParams.width);}
    private int edgeGapY(){return BallScale.inset(BallSettings.edge(this),ballParams==null?BallArt.dimensions(this,BallSettings.pixels(this))[1]:ballParams.height);}
    void foregroundChanged(String pkg){foreground=pkg==null?"":pkg;String next=AppBehavior.mode(this,foreground);if(!next.equals(appMode)){appMode=next;if(next.equals("disabled")){closePanel();quickDock.close();hideCaptureTarget();if(touch!=null){touch.pressed=false;handler.removeCallbacks(touch.single);handler.removeCallbacks(touch.hold);handler.removeCallbacks(touch.arm);}}applyPolicy();}}
    private void applyPolicy(){if(ball==null||stopping)return;String effective=SystemClock.uptimeMillis()<recoveryUntil?"normal":appMode;boolean disabled=effective.equals("disabled"),locked=getSystemService(KeyguardManager.class).isKeyguardLocked();ball.animate().cancel();boolean hidden=effective.equals("hidden");if(hidden){ball.setBackground(null);ball.setElevation(0);ball.setAlpha(1);}else{BallArt.styleContainer(ball,customBallArt);ball.setAlpha(baseAlpha());}for(int i=0;i<ball.getChildCount();i++)ball.getChildAt(i).setAlpha(hidden?0:1);boolean recording=ScreenRecordingService.running;ball.setVisibility(disabled||locked||recording?View.GONE:captureVisible?View.VISIBLE:View.INVISIBLE);if(disabled||recording)ballParams.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;else ballParams.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;updateBall();if((getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0)Prefs.of(this).edit().putString("qa_ball_app_mode",appMode).putString("qa_ball_foreground",foreground).putBoolean("qa_ball_touchable",!disabled&&!recording).putBoolean("qa_ball_visible",ball.getVisibility()==View.VISIBLE).putFloat("qa_ball_alpha",appMode.equals("hidden")?0:ball.getAlpha()).apply();}
    int[] recordingAnchor(){int size=ballParams==null?dp(48):ballParams.width;return new int[]{Math.max(0,Math.min(screenWidth()-dp(58),(ballParams==null?screenWidth()-size:ballParams.x)+size/2-dp(27))),Math.max(dp(30),Math.min(screenHeight()-dp(66),(ballParams==null?screenHeight()/3:ballParams.y)+(ballParams==null?size:ballParams.height)/2-dp(27)))};}
    void recordingChanged(){if(ScreenRecordingService.running){closePanel();hideCaptureTarget();if(touch!=null){touch.pressed=false;handler.removeCallbacks(touch.single);handler.removeCallbacks(touch.hold);handler.removeCallbacks(touch.arm);}}applyPolicy();wakeBall();}
    private void stopWithMotion(){
        if(stopping)return;stopping=true;running=false;MainActivity.runningChanged();closePanel();hideCaptureTarget();clearRecovery();handler.removeCallbacks(fade);
        if(captures!=null&&captures.isActive())captures.cancel();
        if(ball!=null){ballParams.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;updateBall();ball.animate().cancel();}
        int token=++stopGeneration;stopAfterMotion=()->{if(!stopping||token!=stopGeneration)return;if(ball!=null){ball.animate().cancel();ball.setAlpha(0);ball.setVisibility(View.INVISIBLE);ballParams.alpha=0;updateBall();if((getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0)Prefs.of(this).edit().putFloat("qa_stop_window_alpha",ballParams.alpha).putFloat("qa_stop_view_alpha",ball.getAlpha()).putInt("qa_stop_visibility",ball.getVisibility()).apply();}handler.postDelayed(()->{if(stopping&&token==stopGeneration)stopSelf();},48);};
        if(ball!=null&&ValueAnimator.areAnimatorsEnabled())ball.animate().alpha(0).scaleX(.65f).scaleY(.65f).setDuration(650).withEndAction(stopAfterMotion).start();else handler.post(stopAfterMotion);
        handler.postDelayed(stopAfterMotion,900);
    }
    private void clearRecovery(){
        if(recoveryAnimator!=null){recoveryAnimator.cancel();recoveryAnimator=null;}
        if(recoveryPulse!=null){try{windows.removeView(recoveryPulse);}catch(IllegalArgumentException ignored){}recoveryPulse=null;}
        if(ball!=null){ball.setScaleX(1);ball.setScaleY(1);}
    }
    private void recoverBall(){
        if(ScreenRecordingService.running){startService(new Intent(this,ScreenRecordingService.class).setAction(ScreenRecordingService.SHOW));return;}
        clearRecovery();closePanel();captureVisible=true;recoveryUntil=SystemClock.uptimeMillis()+8000;
        if(touch!=null){touch.pressed=false;handler.removeCallbacks(touch.single);handler.removeCallbacks(touch.hold);handler.removeCallbacks(touch.arm);}
        hideCaptureTarget();if(snapAnimator!=null)snapAnimator.cancel();
        if(ball==null)return;int fromX=ballParams.x,fromY=ballParams.y;
        boolean left=fromX+ballParams.width/2<screenWidth()/2;
        int toX=left?dp(16):screenWidth()-ballParams.width-dp(16);
        int toY=GestureRules.clamp(fromY,dp(24),screenHeight()-ballParams.height-dp(24));
        applyPolicy();wakeBall();
        recoveryPulse=new RecoveryPulse(this);recoveryPulse.target=ball;WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.LEFT;p.setTitle("空叶找回光圈");if(Build.VERSION.SDK_INT>=30)p.setFitInsetsTypes(0);try{windows.addView(recoveryPulse,p);}catch(RuntimeException e){recoveryPulse=null;}
        final long began=SystemClock.uptimeMillis();recoveryAnimator=ValueAnimator.ofFloat(0,1,0);recoveryAnimator.setDuration(ValueAnimator.areAnimatorsEnabled()?800:1);recoveryAnimator.setRepeatCount(ValueAnimator.areAnimatorsEnabled()?1:0);recoveryAnimator.setInterpolator(new android.view.animation.PathInterpolator(.25f,.1f,.25f,1));
        recoveryAnimator.addUpdateListener(a->{if(ball==null||stopping)return;float inward=Math.min(1,(SystemClock.uptimeMillis()-began)/250f);inward=1-(1-inward)*(1-inward);ballParams.x=fromX+Math.round((toX-fromX)*inward);ballParams.y=fromY+Math.round((toY-fromY)*inward);updateBall();float phase=(float)a.getAnimatedValue();ball.setScaleX(1+.25f*phase);ball.setScaleY(1+.25f*phase);if(recoveryPulse!=null){recoveryPulse.radius=Math.max(ballParams.width,ballParams.height)/2f;recoveryPulse.phase=phase;recoveryPulse.invalidate();}});
        recoveryAnimator.addListener(new android.animation.AnimatorListenerAdapter(){boolean cancelled;@Override public void onAnimationCancel(android.animation.Animator a){cancelled=true;}@Override public void onAnimationEnd(android.animation.Animator a){if(cancelled)return;if(ball!=null){ballParams.x=toX;ballParams.y=toY;updateBall();savePosition();}clearRecovery();}});recoveryAnimator.start();handler.postDelayed(this::applyPolicy,8100);
    }
    private final Runnable ballWatchdog=new Runnable(){public void run(){if(instance!=FloatService.this)return;if(running&&Settings.canDrawOverlays(FloatService.this)){if(ball==null||!ball.isAttachedToWindow()){removeBall();try{addBall();}catch(RuntimeException ignored){}}if(captures!=null&&!captures.isActive())captureVisible=true;boolean locked=getSystemService(KeyguardManager.class).isKeyguardLocked();if(ball!=null&&!locked&&!ScreenRecordingService.running&&captureVisible&&!appMode.equals("disabled")){if(ball.getVisibility()!=View.VISIBLE)applyPolicy();if(SystemClock.uptimeMillis()>=recoveryUntil&&(touch==null||!touch.pressed)){int min=edgeGap(),minY=edgeGapY(),maxX=screenWidth()-ballParams.width-min,maxY=screenHeight()-ballParams.height-minY;int nx=GestureRules.clamp(ballParams.x,min,maxX),ny=GestureRules.clamp(ballParams.y,minY,maxY);if(nx!=ballParams.x||ny!=ballParams.y){ballParams.x=nx;ballParams.y=ny;updateBall();savePosition();}}}}handler.postDelayed(this,1500);}};
    private void removeBall(){clearRecovery();handler.removeCallbacks(fade);if(touch!=null){handler.removeCallbacks(touch.single);handler.removeCallbacks(touch.hold);handler.removeCallbacks(touch.arm);touch=null;}if(snapAnimator!=null)snapAnimator.cancel();if(ball!=null){ball.animate().cancel();try{windows.removeView(ball);}catch(IllegalArgumentException ignored){}ball=null;}}
    private void wakeBall(){if(stopping)return;handler.removeCallbacks(fade);if(ball!=null){ball.animate().cancel();ball.setAlpha(appMode.equals("hidden")?1:baseAlpha());handler.postDelayed(fade,3000);}}
    private void vibrate(){if(Prefs.of(this).getBoolean("haptic",true)){Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator())v.vibrate(VibrationEffect.createOneShot(12,VibrationEffect.DEFAULT_AMPLITUDE));}}
    private void savePosition(){Prefs.of(this).edit().putInt("x",ballParams.x).putInt("y",ballParams.y).apply();}
    private class BallTouch implements View.OnTouchListener {
        float downX,downY,lastMoveX,lastMoveY,anchorX,anchorY,pointerX,pointerY;int initialX,initialY,maxFollow;long downAt;
        boolean moved,dragging,pendingTap,holdTriggered,holdMoved,pressed,armed,drawing;
        final Runnable single=()->{pendingTap=false;if(ball!=null)ball.performClick();};
        final Runnable hold=()->{if(!pressed||moved||dragging)return;handler.removeCallbacks(single);pendingTap=false;holdTriggered=true;holdMoved=false;touchReceipt("HOLD",downX,downY);vibrate();perform(Prefs.action(FloatService.this,"long"));};
        final Runnable arm=()->{if(!pressed||!dragging||armed)return;armed=true;anchorX=captureX();anchorY=captureY();vibrate();showCaptureCross();Prefs.of(FloatService.this).edit().putString("drag_cross_state","yellow").apply();};
        private void reset(){handler.removeCallbacks(hold);handler.removeCallbacks(arm);hideCaptureTarget();if(ball!=null){ball.setScaleX(1);ball.setScaleY(1);}}
        private void restore(){ballParams.x=initialX;ballParams.y=initialY;updateBall();}
        @Override public boolean onTouch(View view,MotionEvent event){
            pointerX=event.getRawX();pointerY=event.getRawY();
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN->{touchReceipt("DOWN",event.getRawX(),event.getRawY());clearRecovery();wakeBall();if(snapAnimator!=null)snapAnimator.cancel();if(followReturning){ballParams.x=followHomeX;ballParams.y=followHomeY;followReturning=false;updateBall();}downX=lastMoveX=event.getRawX();downY=lastMoveY=event.getRawY();initialX=ballParams.x;initialY=ballParams.y;downAt=SystemClock.uptimeMillis();moved=dragging=holdTriggered=holdMoved=armed=drawing=false;pressed=true;maxFollow=0;handler.postDelayed(hold,650);return true;}
                case MotionEvent.ACTION_MOVE->{
                    float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
                    if(holdTriggered){if(Math.hypot(dx,dy)>dp(12))holdMoved=true;if(holdMoved&&quickDock.isVisible())quickDock.hover(event.getRawX(),event.getRawY());return true;}
                    long duration=SystemClock.uptimeMillis()-downAt;if(Math.hypot(dx,dy)>dp(8)){moved=true;handler.removeCallbacks(hold);}
                    if(!dragging&&GestureRules.isDrag(dx,dy,duration,dp(8))){dragging=true;handler.removeCallbacks(single);pendingTap=false;vibrate();Prefs.of(FloatService.this).edit().putString("drag_cross_state","red").apply();handler.postDelayed(arm,450);}
                    if(dragging){
                        ballParams.x=GestureRules.dragPosition(initialX,event.getRawX(),downX);ballParams.y=GestureRules.dragPosition(initialY,event.getRawY(),downY);updateBall();
                        if(armed){drawing|=Math.hypot(captureX()-anchorX,captureY()-anchorY)>dp(12);}
                        else if(Math.hypot(event.getRawX()-lastMoveX,event.getRawY()-lastMoveY)>dp(2)){handler.removeCallbacks(arm);handler.postDelayed(arm,450);lastMoveX=event.getRawX();lastMoveY=event.getRawY();}
                        showCaptureCross();
                    }else if(moved&&Math.abs(dy)>Math.abs(dx)*1.2f){int offset=Math.round(GestureRules.followOffset(dy,dp(64)));ballParams.y=GestureRules.clamp(initialY+offset,edgeGapY(),screenHeight()-ballParams.height-edgeGapY());maxFollow=Math.abs(offset)>Math.abs(maxFollow)?offset:maxFollow;updateBall();ball.setScaleX(.94f);ball.setScaleY(1.06f);}
                    return true;
                }
                case MotionEvent.ACTION_UP->{
                    touchReceipt("UP",event.getRawX(),event.getRawY());pressed=false;reset();float dx=event.getRawX()-downX,dy=event.getRawY()-downY;long duration=SystemClock.uptimeMillis()-downAt;
                    if(holdTriggered){if(quickDock.isVisible()){if(holdMoved)quickDock.hover(event.getRawX(),event.getRawY());quickDock.release();}holdTriggered=false;return true;}
                    moved|=Math.hypot(dx,dy)>dp(8);
                    if(dragging){
                        if(armed)drawing|=Math.hypot(captureX()-anchorX,captureY()-anchorY)>dp(12);
                        if(drawing){Rect area=new Rect(Math.round(Math.min(anchorX,captureX())),Math.round(Math.min(anchorY,captureY())),Math.round(Math.max(anchorX,captureX())),Math.round(Math.max(anchorY,captureY())));restore();if(area.width()>=32&&area.height()>=32){captures.startRegion(area);return true;}}
                        if(armed){restore();perform("region");}else snap();return true;
                    }
                    String swipe=GestureRules.swipe(dx,dy,duration,dp(28));
                    if(swipe!=null){handler.removeCallbacks(single);pendingTap=false;vibrate();Prefs.of(FloatService.this).edit().putInt("last_swipe_follow_offset",maxFollow).apply();animateFollowHome(initialX,initialY,()->perform(Prefs.action(FloatService.this,swipe)));}
                    else if(!moved&&duration>=650){handler.removeCallbacks(single);pendingTap=false;perform(Prefs.action(FloatService.this,"long"));if(quickDock.isVisible())quickDock.close();}
                    else if(!moved){restore();if(pendingTap){handler.removeCallbacks(single);pendingTap=false;vibrate();perform(Prefs.action(FloatService.this,"double"));}else{pendingTap=true;handler.postDelayed(single,ViewConfiguration.getDoubleTapTimeout());}}
                    else animateFollowHome(initialX,initialY,()->{});return true;
                }
                case MotionEvent.ACTION_CANCEL->{touchReceipt("CANCEL",event.getRawX(),event.getRawY());pressed=false;reset();handler.removeCallbacks(single);pendingTap=false;quickDock.close();if(dragging)snap();else restore();holdTriggered=false;return true;}
            }
            return true;
        }
    }
    private void touchReceipt(String event,float x,float y){if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)Prefs.of(this).edit().putString("qa_touch_event",event).putFloat("qa_touch_x",x).putFloat("qa_touch_y",y).putLong("qa_touch_time",SystemClock.uptimeMillis()).apply();}
    private void animateFollowHome(int x,int y,Runnable done){
        if(ball==null)return;int startX=ballParams.x,startY=ballParams.y;followReturning=true;followHomeX=x;followHomeY=y;snapAnimator=ValueAnimator.ofFloat(0,1);snapAnimator.setDuration(160);
        snapAnimator.addUpdateListener(animation->{float t=(float)animation.getAnimatedValue();ballParams.x=startX+Math.round((x-startX)*t);ballParams.y=startY+Math.round((y-startY)*t);updateBall();});
        snapAnimator.addListener(new android.animation.AnimatorListenerAdapter(){boolean cancelled;@Override public void onAnimationCancel(android.animation.Animator animation){cancelled=true;}@Override public void onAnimationEnd(android.animation.Animator animation){if(!cancelled){followReturning=false;if(running)done.run();}}});snapAnimator.start();
    }
    private float captureX(){return GestureRules.capturePointer(touch.pointerX,screenWidth(),dp(56),dp(96),dp(8));}
    private float captureY(){return GestureRules.capturePointer(touch.pointerY,screenHeight(),dp(56),dp(96),dp(8));}
    private void showCaptureCross(){
        if(captureTarget==null){captureTarget=new GestureCaptureOverlay(this);captureTarget.position=()->new PointF(captureX(),captureY());WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.LEFT;p.alpha=1f;p.setTitle("空叶截图十字");if(Build.VERSION.SDK_INT>=30)p.setFitInsetsTypes(0);try{windows.addView(captureTarget,p);}catch(RuntimeException e){captureTarget=null;return;}}
        captureTarget.x=captureX();captureTarget.y=captureY();captureTarget.ready=touch.armed;captureTarget.drawing=touch.drawing;captureTarget.anchorX=touch.anchorX;captureTarget.anchorY=touch.anchorY;captureTarget.invalidate();
    }
    private void hideCaptureTarget(){if(captureTarget!=null){try{windows.removeView(captureTarget);}catch(IllegalArgumentException ignored){}captureTarget=null;}}

    private void updateBall(){if(ball!=null)try{windows.updateViewLayout(ball,ballParams);}catch(IllegalArgumentException ignored){}}
    private void snap(){
        ballParams.y=GestureRules.clamp(ballParams.y,edgeGapY(),screenHeight()-ballParams.height-edgeGapY());if(!Prefs.of(this).getBoolean("snap",true)){ballParams.x=GestureRules.clamp(ballParams.x,edgeGap(),screenWidth()-ballParams.width-edgeGap());updateBall();savePosition();wakeBall();return;}
        int target=GestureRules.edgeWithInset(ballParams.x,screenWidth(),ballParams.width,edgeGap());
        snapAnimator=ValueAnimator.ofInt(ballParams.x,target);snapAnimator.setDuration(210);snapAnimator.addUpdateListener(a->{ballParams.x=(int)a.getAnimatedValue();updateBall();});snapAnimator.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){savePosition();wakeBall();}});snapAnimator.start();
    }
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);closePanel();int rotation=windows.getDefaultDisplay().getRotation();boolean rotated=rotation!=displayRotation;displayRotation=rotation;if(rotated&&captures!=null&&captures.isActive()){Prefs.of(this).edit().putString("last_capture_cancel_reason","rotation").apply();captures.cancel();toast("屏幕方向已改变，请重新开始截图");}hideCaptureTarget();if(ball!=null){ballParams.x=GestureRules.edgeWithInset(ballParams.x,screenWidth(),ballParams.width,edgeGap());ballParams.y=GestureRules.clamp(ballParams.y,edgeGapY(),screenHeight()-ballParams.height-edgeGapY());updateBall();savePosition();}}
    private void toast(String msg){Feedback.show(this,msg);}
    private void launch(Intent i){if(i.getComponent()!=null&&i.getComponent().getClassName().equals(QuickSettingsActivity.class.getName()))i.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);try{startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(RuntimeException e){toast("无法打开，请在主界面重试");}}
    static int[] ballCenter(){FloatService service=instance;if(service==null||service.ball==null||!service.ball.isAttachedToWindow())return null;int[] at=new int[2];service.ball.getLocationOnScreen(at);at[0]+=service.ball.getWidth()/2;at[1]+=service.ball.getHeight()/2;return at;}
    void perform(String action){
        if(action==null||!running||captureInFlight)return;action=Prefs.canonicalAction(action);if("none".equals(action))return;
        if(captures!=null&&captures.isActive()&&!captures.allowsBall()){toast("请先完成或取消当前截图");return;}
        if("quick".equals(action)){if(touch==null||!touch.pressed)return;closePanel();int[] center=ballCenter();if(center!=null)quickDock.show(center[0],center[1]);return;}
        if("menu".equals(action)){if(panel!=null||browser!=null)closePanel();else showMenu();return;}
        closePanel();vibrate();if(action.startsWith("edit_slot:")){try{launch(new Intent(this,QuickSettingsActivity.class).putExtra("edit_slot",Integer.parseInt(action.substring(10))));}catch(NumberFormatException ignored){}return;}if(action.startsWith("app:")){launchApp(action.substring(4));return;}
        switch(action){
            case "none" -> {}
            case "notes" -> showNotes();
            case "browser" -> showBrowser();
            case "apps" -> showApps();
            case "recent_apps" -> showApps("recent");
            case "favorite_apps" -> showApps("favorites");
            case "manage_apps" -> showApps("manage");
            case "manage_quick" -> launch(new Intent(this,QuickSettingsActivity.class));
            case "auto_longshot" -> captures.startAutomatic();
            case "region" -> captures.start(false);
            case "longshot" -> captures.start(true);
            case "record", "gif_record" -> launch(new Intent(this,ScreenRecordingActivity.class).putExtra("gif",action.equals("gif_record")));
            case "record_stop" -> {if(ScreenRecordingService.running)startService(new Intent(this,ScreenRecordingService.class).setAction(ScreenRecordingService.STOP));else toast("当前没有正在进行的录屏");}
            case "files" -> FilePickerActivity.open(this);
            case "settings" -> {if(!MainActivity.hideIfVisible())try{AppMotion.open(this);}catch(RuntimeException e){toast("无法打开，请在主界面重试");}}
            case "text" -> {
                AssistService a=AssistService.instance;
                if(a==null){needAssist();return;}
                handler.postDelayed(()->{if(!running)return;AssistService current=AssistService.instance;if(current==null){needAssist();return;}String text=current.readScreen();showText(text);},160);
            }
            case "screenshot" -> screenshot();
            default -> {
                AssistService a=AssistService.instance;if(a==null){needAssist();return;}
                int global=switch(action){case "back"->AccessibilityService.GLOBAL_ACTION_BACK;case "home"->AccessibilityService.GLOBAL_ACTION_HOME;case "recents"->AccessibilityService.GLOBAL_ACTION_RECENTS;case "notifications"->AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS;case "quick_settings"->AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS;case "lock"->Build.VERSION.SDK_INT>=28?AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN:-1;default->-1;};
                if(global<0||!a.performGlobalAction(global))toast("系统暂不支持此操作");
            }
        }
    }
    private void needAssist(){toast("请先在空叶主界面开启无障碍服务");launch(new Intent(this,MainActivity.class));}
    private void screenshot(){captures.startWhole();}
    private void restoreAfterCapture(){captureInFlight=false;if(ball!=null){KeyguardManager km=getSystemService(KeyguardManager.class);applyPolicy();wakeBall();}}
    private LinearLayout showPanel(String title,String subtitle,boolean keyboard){
        ThemePolicy.apply(this);renderedDark=Ui.dark;setTheme(Ui.dark?R.style.AppThemeDark:R.style.AppTheme);
        closePanel();wakeBall();
        panel=new FrameLayout(this){
            @Override public boolean dispatchKeyEvent(KeyEvent event){
                if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){
                    if(event.getAction()==KeyEvent.ACTION_UP){if(browser==null||!browser.goBack())closePanel();}
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        panel.setBackgroundColor(Ui.dark?0x99000000:0x44304134);panel.setOnClickListener(v->closePanel());panel.setFocusableInTouchMode(true);
        panel.setOnApplyWindowInsetsListener((view,insets)->{
            int keyboardBottom;
            if(Build.VERSION.SDK_INT>=30)keyboardBottom=Math.max(0,insets.getInsets(WindowInsets.Type.ime()).bottom-insets.getInsets(WindowInsets.Type.navigationBars()).bottom);
            else keyboardBottom=insets.getSystemWindowInsetBottom();
            view.setPadding(0,0,0,keyboardBottom);return insets;
        });
        LinearLayout outer=Ui.card(this);Ui.softShadow(outer);outer.setOnClickListener(v->{});Ui.pad(outer,20,20,20,20);
        int width=Math.min(screenWidth()-dp(28),dp(400));FrameLayout.LayoutParams olp=new FrameLayout.LayoutParams(width,-2,Gravity.CENTER);olp.topMargin=dp(30);olp.bottomMargin=dp(30);ScrollView viewport=new ScrollView(this);viewport.setVerticalScrollBarEnabled(false);viewport.addView(outer);panel.addView(viewport,olp);
        LinearLayout head=Ui.row(this);LinearLayout titles=Ui.column(this);panelHeading=Ui.text(this,title,18,Ui.INK,true);titles.addView(panelHeading);if(subtitle!=null){Ui.gap(titles,5);titles.addView(Ui.text(this,subtitle,11,Ui.MUTED,false));}head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));head.addView(Ui.tool(this,"close","关闭窗口",this::closePanel),new LinearLayout.LayoutParams(dp(40),dp(40)));outer.addView(head);Ui.gap(outer,14);
        LinearLayout body=Ui.column(this);outer.addView(body,new LinearLayout.LayoutParams(-1,-2));
        int flags=WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,flags,PixelFormat.TRANSLUCENT);params.setTitle("空叶工具面板");params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;params.gravity=Gravity.TOP|Gravity.LEFT;
        try{windows.addView(panel,params);panel.requestFocus();panel.requestApplyInsets();}catch(RuntimeException e){panel=null;toast("无法打开悬浮窗口");}
        return body;
    }
    private void closePanel(){
        if(appLauncher!=null){AppLauncher old=appLauncher;appLauncher=null;old.close();}panelHeading=null;
        if(quickDock!=null)quickDock.close();
        if(panel!=null){InputMethodManager ime=getSystemService(InputMethodManager.class);ime.hideSoftInputFromWindow(panel.getWindowToken(),0);try{windows.removeView(panel);}catch(IllegalArgumentException ignored){}panel=null;}
        if(browser!=null){FloatingBrowser old=browser;browser=null;old.close();}
        wakeBall();
    }
    private void showMenu(){
        menuManaging=false;menuSelected=MenuTools.load(this);menuOrder=MenuTools.order(this);LinearLayout body=showPanel("快捷菜单",null,false);LinearLayout outer=(LinearLayout)body.getParent();outer.setBackground(Ui.bg(Ui.SURFACE,32,this));Ui.pad(outer,16,18,16,18);LinearLayout head=(LinearLayout)outer.getChildAt(0);head.removeAllViews();TextView title=Ui.text(this,"快捷菜单",19,Ui.INK,true);Ui.pad(title,4,0,0,0);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));menuManageButton=menuHeaderButton("menu_manage","管理快捷菜单工具",()->{menuManaging=!menuManaging;updateMenuManage();renderMenuGrid();});head.addView(menuManageButton,new LinearLayout.LayoutParams(dp(34),dp(34)));LinearLayout.LayoutParams end=new LinearLayout.LayoutParams(dp(34),dp(34));end.leftMargin=dp(6);head.addView(menuHeaderButton("close","关闭窗口",this::closePanel),end);
        menuManageBar=Ui.row(this);menuManageBar.setContentDescription("快捷菜单原位编辑");Ui.pad(menuManageBar,10,8,10,8);menuManageBar.setBackground(Ui.bg(Ui.dark?Ui.PALE:0xFFEEF3EA,14,this));menuManageBar.addView(Ui.text(this,"点按显隐 · 长按拖动调序",11.5f,Ui.MUTED,false),new LinearLayout.LayoutParams(0,-2,1));for(boolean all:new boolean[]{false,true}){TextView pick=Ui.centeredText(this,all?"全选":"全不选",11,Ui.INK,true);pick.setContentDescription(all?"快捷菜单全选":"快捷菜单全不选");pick.setBackground(Ui.ripple(this,Ui.SURFACE,20));pick.setOnClickListener(v->{menuSelected.clear();if(all)menuSelected.addAll(menuOrder);saveMenuSelection();renderMenuGrid();});LinearLayout.LayoutParams at=new LinearLayout.LayoutParams(dp(all?42:52),dp(26));at.leftMargin=dp(6);menuManageBar.addView(pick,at);}body.addView(menuManageBar);menuGrid=Ui.column(this);menuGrid.setClipChildren(false);menuGrid.setClipToPadding(false);menuGrid.setContentDescription("快捷菜单四列网格");Ui.pad(menuGrid,2,2,2,0);body.addView(menuGrid);updateMenuManage();renderMenuGrid();
    }
    private FrameLayout menuHeaderButton(String icon,String label,Runnable run){FrameLayout v=Ui.tool(this,icon,label,run);v.setMinimumWidth(0);v.setMinimumHeight(0);v.setBackground(Ui.ripple(this,Ui.dark?Ui.PALE:0xFFEEF3EA,12));return v;}
    private void updateMenuManage(){menuManageBar.setVisibility(menuManaging?View.VISIBLE:View.GONE);menuManageButton.setSelected(menuManaging);menuManageButton.removeAllViews();menuManageButton.addView(Ui.icon(this,"menu_manage",menuManaging?Color.WHITE:Ui.GREEN,19),new FrameLayout.LayoutParams(dp(19),dp(19),Gravity.CENTER));menuManageButton.setBackground(Ui.ripple(this,menuManaging?0xFF21372A:Ui.dark?Ui.PALE:0xFFEEF3EA,12));}
    private void saveMenuSelection(){ArrayList<String> visible=new ArrayList<>();for(String id:menuOrder)if(menuSelected.contains(id))visible.add(id);MenuTools.save(this,visible);MenuTools.saveOrder(this,menuOrder);}
    private void renderMenuGrid(){
        if(menuGrid==null)return;boolean[] reordered={false};menuGrid.removeAllViews();ArrayList<String> ids=new ArrayList<>();for(String id:menuOrder)if(menuManaging||menuSelected.contains(id))ids.add(id);if(ids.isEmpty()){TextView empty=Ui.centeredText(this,"还没有显示的工具，点击右上角管理添加",13,Ui.MUTED,false);Ui.pad(empty,12,32,12,32);menuGrid.addView(empty);return;}
        for(int r=0;r<(ids.size()+3)/4;r++){LinearLayout row=Ui.row(this);row.setClipChildren(false);row.setClipToPadding(false);for(int c=0;c<4;c++){int index=r*4+c;if(index>=ids.size()){row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));continue;}String id=ids.get(index);boolean on=menuSelected.contains(id);LinearLayout item=Ui.column(this);item.setClipChildren(false);item.setClipToPadding(false);item.setGravity(Gravity.CENTER);Ui.pad(item,2,6,2,6);FrameLayout iconGroup=new FrameLayout(this);iconGroup.setClipChildren(false);iconGroup.setClipToPadding(false);FrameLayout box=new FrameLayout(this);box.setBackground(Ui.ripple(this,Ui.dark?Ui.PALE:0xFFE5EDE0,18));box.addView(Ui.icon(this,id.equals("settings")?"open_leaf":id,Ui.dark?Ui.GREEN:0xFF21372A,24),new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));iconGroup.addView(box,new FrameLayout.LayoutParams(dp(52),dp(52),Gravity.CENTER));if(menuManaging){FrameLayout badge=new FrameLayout(this);badge.setTag("menu_badge:"+id);badge.setBackground(Ui.bg(on?0xFF28664B:0xFF92A396,9,this));badge.addView(Ui.icon(this,"check",Color.WHITE,12),new FrameLayout.LayoutParams(dp(12),dp(12),Gravity.CENTER));FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(18),dp(18),Gravity.TOP|Gravity.RIGHT);bp.topMargin=0;bp.rightMargin=0;iconGroup.addView(badge,bp);}item.addView(iconGroup,new LinearLayout.LayoutParams(dp(menuManaging?60:52),dp(menuManaging?60:52)));Ui.gap(item,menuManaging?2:6);TextView name=Ui.centeredText(this,HomeTools.label(id),11.5f,Ui.INK,true);name.setSingleLine();item.addView(name);item.setAlpha(menuManaging&&!on?.32f:1);item.setSelected(on);item.setContentDescription(HomeTools.label(id));item.setOnClickListener(v->{if(menuManaging){if(menuSelected.contains(id))menuSelected.remove(id);else menuSelected.add(id);saveMenuSelection();renderMenuGrid();}else perform(id);});item.setOnLongClickListener(v->{v.startDragAndDrop(ClipData.newPlainText("空叶快捷菜单排序",id),new View.DragShadowBuilder(v),id,0);v.animate().alpha(.28f).scaleX(.92f).scaleY(.92f).setDuration(150).start();return true;});item.setOnDragListener((v,e)->{if(!(e.getLocalState() instanceof String dragged))return false;if(e.getAction()==DragEvent.ACTION_DROP){int from=menuOrder.indexOf(dragged),to=menuOrder.indexOf(id);if(from>=0&&to>=0){CompositionLayout.move(menuOrder,from,to);saveMenuSelection();reordered[0]=true;}}else if(e.getAction()==DragEvent.ACTION_DRAG_ENDED){v.animate().alpha(menuManaging&&!on?.32f:1).scaleX(1).scaleY(1).setDuration(150).start();if(reordered[0]){reordered[0]=false;menuGrid.post(this::renderMenuGrid);}}return true;});row.addView(item,new LinearLayout.LayoutParams(0,-2,1));if(menuManaging){android.animation.ObjectAnimator jiggle=android.animation.ObjectAnimator.ofFloat(item,"rotation",index%2==0?-1.1f:1.1f,index%2==0?1.1f:-1.1f);jiggle.setDuration(360);jiggle.setRepeatCount(android.animation.ValueAnimator.INFINITE);jiggle.setRepeatMode(android.animation.ValueAnimator.REVERSE);item.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){jiggle.start();}public void onViewDetachedFromWindow(View v){jiggle.cancel();}});if(item.isAttachedToWindow())jiggle.start();}}menuGrid.addView(row);}
    }
    private static String menuLabel(String id){return switch(id){case "recent_apps"->"最近软件";case "browser"->"浏览器";case "settings"->"空叶设置";default->Prefs.label(id);};}
    private EditText editor(String hint,int lines){EditText e=new EditText(this);e.setTextColor(Ui.INK);e.setTextSize(14);e.setHintTextColor(Ui.MUTED);e.setHint(hint);e.setGravity(Gravity.TOP);e.setMinLines(lines);e.setBackground(Ui.bg(Ui.BG,16,this));Ui.pad(e,14,13,14,13);e.setSelectAllOnFocus(false);return e;}
    private void showNotes(){
        LinearLayout body=showPanel("便签","自动保存在本机",true);
        EditText e=editor("记下此刻的想法…",6);e.setText(Prefs.of(this).getString("note",""));e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100000)});
        body.addView(e,new LinearLayout.LayoutParams(-1,Math.min(dp(220),screenHeight()/3)));
        e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){Prefs.of(FloatService.this).edit().putString("note",s.toString()).apply();}public void afterTextChanged(Editable s){}});
        Ui.gap(body,14);body.addView(Ui.button(this,"复制便签",true,()->copy(e.getText().toString())));
    }
    private void copy(String text){if(text.isEmpty()){toast("还没有内容");return;}try{getSystemService(android.content.ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("空叶",text));toast("文字已复制");}catch(RuntimeException error){toast("复制失败，请重试");}}
    private void showText(String text){
        LinearLayout body=showPanel("提取界面文字",text.isEmpty()?"当前界面没有可提取的文字":"读取可访问文字 · 不包含图片中的文字",true);
        EditText e=editor("尝试在含文字的页面重新提取。图片、密码框或受保护内容无法读取。",5);e.setText(text);body.addView(e,new LinearLayout.LayoutParams(-1,Math.min(dp(250),screenHeight()/3)));Ui.gap(body,14);
        body.addView(Ui.button(this,"复制文字",true,()->copy(e.getText().toString())));Ui.gap(body,8);body.addView(Ui.button(this,"追加到便签",false,()->{String value=e.getText().toString();if(value.isBlank()){toast("还没有内容");return;}String old=Prefs.of(this).getString("note","");String combined=old.isEmpty()?value:old+"\n\n"+value;if(combined.length()>100000){toast("便签已达字数上限，请先整理");return;}Prefs.of(this).edit().putString("note",combined).apply();showNotes();}));
    }
    private void showBrowser(){ThemePolicy.apply(this);closePanel();browser=new FloatingBrowser(this,this::closePanel,this::launch);try{browser.show();}catch(RuntimeException e){closePanel();toast("无法打开浏览器");}}
    private void showApps(){showApps("all");}
    private void showApps(String mode){
        long opened=SystemClock.elapsedRealtime();LinearLayout body=showPanel("应用启动",null,true);
        appLauncher=new AppLauncher(this,body,mode,title->{if(panelHeading!=null)panelHeading.setText(title);},pkg->{closePanel();launchApp(pkg);},()->{closePanel();launch(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,Uri.parse("package:"+getPackageName())));},opened);
    }
    private void launchApp(String pkg){Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);if(intent==null)toast("该应用已卸载或无法启动");else launch(intent);}
    @Override public void onDestroy(){stopping=true;if(ball!=null){ball.animate().cancel();ball.setAlpha(0);ball.setVisibility(View.INVISIBLE);ballParams.alpha=0;updateBall();}if(instance==this)instance=null;running=false;MainActivity.runningChanged();handler.removeCallbacksAndMessages(null);captureInFlight=false;hideCaptureTarget();if(captures!=null)captures.destroy();closePanel();removeBall();try{unregisterReceiver(screenReceiver);}catch(IllegalArgumentException ignored){}stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
