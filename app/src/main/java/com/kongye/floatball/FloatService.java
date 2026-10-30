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
    private FloatingBrowser browser;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ValueAnimator snapAnimator;
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
        super.onCreate();instance=this;handler.postDelayed(appPolicyTick,300);handler.postDelayed(ballWatchdog,1500);foreground=AssistService.foregroundPackage;appMode=AppBehavior.mode(this,foreground);QuickActions.migrate(this);ThemePolicy.apply(this);renderedDark=Ui.dark;setTheme(Ui.dark?R.style.AppThemeDark:R.style.AppTheme);windows=OverlayWindows.get(this);displayRotation=windows.getDefaultDisplay().getRotation();quickDock=new QuickDock(this,this::perform);captures=new CaptureController(this,visible->{captureVisible=visible;applyPolicy();});handler.postDelayed(themeTick,60000-System.currentTimeMillis()%60000);
        NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("float","空叶悬浮球运行状态",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent recover=PendingIntent.getService(this,3,new Intent(this,FloatService.class).setAction(RECOVER),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,FloatService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,"float").setSmallIcon(R.drawable.ic_leaf_notification).setContentTitle("空叶在这里").setContentText("轻点小叶展开工具 · 按住后拖动").setContentIntent(open).setOngoing(true).setShowWhen(false).addAction(new Notification.Action.Builder(null,"找回悬浮球",recover).build()).addAction(new Notification.Action.Builder(null,"停止悬浮球",stop).build()).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(1001,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(1001,notification);
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_PRESENT);filter.addAction(Intent.ACTION_SCREEN_ON);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(screenReceiver,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screenReceiver,filter);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
        if(intent!=null&&REFRESH.equals(intent.getAction())){appMode=AppBehavior.mode(this,AssistService.foregroundPackage);ThemePolicy.apply(this);renderedDark=Ui.dark;setTheme(Ui.dark?R.style.AppThemeDark:R.style.AppTheme);closePanel();removeBall();}
        if(ball==null)try{addBall();}catch(RuntimeException e){toast("无法显示悬浮窗，请检查权限");stopSelf();return START_NOT_STICKY;}
        running=true;if(intent!=null&&RECOVER.equals(intent.getAction()))recoverBall();
        if(intent!=null&&intent.hasExtra("action")){String action=intent.getStringExtra("action");handler.postDelayed(()->perform(action),220);}
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent i){return null;}
    private int dp(float value){return Ui.dp(this,value);}
    private int screenWidth(){android.util.DisplayMetrics m=new android.util.DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(m);return m.widthPixels;}
    private int screenHeight(){android.util.DisplayMetrics m=new android.util.DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(m);return m.heightPixels;}
    private float baseAlpha(){return Prefs.of(this).getInt("opacity",90)/100f;}
    private void addBall(){
        int size=dp(GestureRules.clamp(Prefs.of(this).getInt("size",BallArt.DEFAULT_SIZE),36,150));
        ball=new FrameLayout(this);ball.setContentDescription("空叶悬浮球：点击菜单，双击返回，按住后拖动");
        if(Build.VERSION.SDK_INT>=29)ball.setSystemGestureExclusionRects(Collections.singletonList(new Rect(0,0,size,size)));
        View art=BallArt.create(this,size);customBallArt=art!=null;BallArt.styleContainer(ball,art!=null);View leaf=art==null?new Ui.Icon(this,"leaf_outline",BallArt.FOREGROUND):art;int iconSize=art==null?Math.round(size*BallArt.LEAF_SCALE):size;FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(iconSize,iconSize,Gravity.CENTER);ball.addView(leaf,lp);
        ballParams=new WindowManager.LayoutParams(size,size,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        ballParams.setTitle("空叶悬浮球");if(Build.VERSION.SDK_INT>=30)ballParams.setFitInsetsTypes(0);if(Build.VERSION.SDK_INT>=28)ballParams.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;ballParams.gravity=Gravity.TOP|Gravity.LEFT;ballParams.x=GestureRules.clamp(Prefs.of(this).getInt("x",screenWidth()-size-edgeGap()),edgeGap(),screenWidth()-size-edgeGap());ballParams.y=GestureRules.clamp(Prefs.of(this).getInt("y",screenHeight()/3),edgeGap(),screenHeight()-size-edgeGap());
        if(Prefs.of(this).getBoolean("snap",true))ballParams.x=GestureRules.edgeWithInset(ballParams.x,screenWidth(),size,edgeGap());
        touch=new BallTouch();ball.setAlpha(baseAlpha());ball.setOnTouchListener(touch);ball.setOnClickListener(v->perform(Prefs.action(this,"tap")));windows.addView(ball,ballParams);applyPolicy();wakeBall();
        KeyguardManager km=getSystemService(KeyguardManager.class);if(km.isKeyguardLocked())ball.setVisibility(View.GONE);
    }
    private int edgeGap(){return GestureRules.visibleInset(dp(GestureRules.clamp(Prefs.of(this).getInt("edge_gap",0),-100,0)),dp(GestureRules.clamp(Prefs.of(this).getInt("size",BallArt.DEFAULT_SIZE),36,150)),dp(12));}
    void foregroundChanged(String pkg){foreground=pkg==null?"":pkg;String next=AppBehavior.mode(this,foreground);if(!next.equals(appMode)){appMode=next;if(next.equals("disabled")){closePanel();quickDock.close();hideCaptureTarget();if(touch!=null){touch.pressed=false;handler.removeCallbacks(touch.single);handler.removeCallbacks(touch.hold);handler.removeCallbacks(touch.arm);}}applyPolicy();}}
    private void applyPolicy(){if(ball==null)return;String effective=SystemClock.uptimeMillis()<recoveryUntil?"normal":appMode;boolean disabled=effective.equals("disabled"),locked=getSystemService(KeyguardManager.class).isKeyguardLocked();ball.animate().cancel();boolean hidden=effective.equals("hidden");if(hidden){ball.setBackground(null);ball.setElevation(0);ball.setAlpha(1);}else{BallArt.styleContainer(ball,customBallArt);ball.setAlpha(baseAlpha());}for(int i=0;i<ball.getChildCount();i++)ball.getChildAt(i).setAlpha(hidden?0:1);boolean recording=ScreenRecordingService.running;ball.setVisibility(disabled||locked||recording?View.GONE:captureVisible?View.VISIBLE:View.INVISIBLE);if(disabled||recording)ballParams.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;else ballParams.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;updateBall();if((getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0)Prefs.of(this).edit().putString("qa_ball_app_mode",appMode).putString("qa_ball_foreground",foreground).putBoolean("qa_ball_touchable",!disabled&&!recording).putBoolean("qa_ball_visible",ball.getVisibility()==View.VISIBLE).putFloat("qa_ball_alpha",appMode.equals("hidden")?0:ball.getAlpha()).apply();}
    int[] recordingAnchor(){int size=ballParams==null?dp(48):ballParams.width;return new int[]{Math.max(0,Math.min(screenWidth()-dp(58),(ballParams==null?screenWidth()-size:ballParams.x)+size/2-dp(27))),Math.max(dp(30),Math.min(screenHeight()-dp(66),(ballParams==null?screenHeight()/3:ballParams.y)+size/2-dp(27)))};}
    void recordingChanged(){if(ScreenRecordingService.running){closePanel();hideCaptureTarget();if(touch!=null){touch.pressed=false;handler.removeCallbacks(touch.single);handler.removeCallbacks(touch.hold);handler.removeCallbacks(touch.arm);}}applyPolicy();wakeBall();}
    private void recoverBall(){if(ScreenRecordingService.running){startService(new Intent(this,ScreenRecordingService.class).setAction(ScreenRecordingService.SHOW));return;}closePanel();captureVisible=true;recoveryUntil=SystemClock.uptimeMillis()+8000;if(touch!=null){touch.pressed=false;handler.removeCallbacks(touch.single);handler.removeCallbacks(touch.hold);handler.removeCallbacks(touch.arm);}hideCaptureTarget();if(snapAnimator!=null)snapAnimator.cancel();if(ballParams!=null){ballParams.x=screenWidth()-ballParams.width;ballParams.y=screenHeight()/3;updateBall();savePosition();}applyPolicy();wakeBall();handler.postDelayed(this::applyPolicy,8100);}
    private final Runnable ballWatchdog=new Runnable(){public void run(){if(instance!=FloatService.this)return;if(running&&Settings.canDrawOverlays(FloatService.this)){if(ball==null||!ball.isAttachedToWindow()){removeBall();try{addBall();}catch(RuntimeException ignored){}}if(captures!=null&&!captures.isActive())captureVisible=true;boolean locked=getSystemService(KeyguardManager.class).isKeyguardLocked();if(ball!=null&&!locked&&!ScreenRecordingService.running&&captureVisible&&!appMode.equals("disabled")){if(ball.getVisibility()!=View.VISIBLE)applyPolicy();if(touch==null||!touch.pressed){int min=edgeGap(),maxX=screenWidth()-ballParams.width-min,maxY=screenHeight()-ballParams.height-min;int nx=GestureRules.clamp(ballParams.x,min,maxX),ny=GestureRules.clamp(ballParams.y,min,maxY);if(nx!=ballParams.x||ny!=ballParams.y){ballParams.x=nx;ballParams.y=ny;updateBall();savePosition();}}}}handler.postDelayed(this,1500);}};
    private void removeBall(){handler.removeCallbacks(fade);if(touch!=null){handler.removeCallbacks(touch.single);handler.removeCallbacks(touch.hold);handler.removeCallbacks(touch.arm);touch=null;}if(snapAnimator!=null)snapAnimator.cancel();if(ball!=null){ball.animate().cancel();try{windows.removeView(ball);}catch(IllegalArgumentException ignored){}ball=null;}}
    private void wakeBall(){handler.removeCallbacks(fade);if(ball!=null){ball.animate().cancel();ball.setAlpha(appMode.equals("hidden")?1:baseAlpha());handler.postDelayed(fade,3000);}}
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
                case MotionEvent.ACTION_DOWN->{touchReceipt("DOWN",event.getRawX(),event.getRawY());wakeBall();if(snapAnimator!=null)snapAnimator.cancel();if(followReturning){ballParams.x=followHomeX;ballParams.y=followHomeY;followReturning=false;updateBall();}downX=lastMoveX=event.getRawX();downY=lastMoveY=event.getRawY();initialX=ballParams.x;initialY=ballParams.y;downAt=SystemClock.uptimeMillis();moved=dragging=holdTriggered=holdMoved=armed=drawing=false;pressed=true;maxFollow=0;handler.postDelayed(hold,650);return true;}
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
                    }else if(moved&&Math.abs(dy)>Math.abs(dx)*1.2f){int offset=Math.round(GestureRules.followOffset(dy,dp(64)));ballParams.y=GestureRules.clamp(initialY+offset,edgeGap(),screenHeight()-ballParams.height-edgeGap());maxFollow=Math.abs(offset)>Math.abs(maxFollow)?offset:maxFollow;updateBall();ball.setScaleX(.94f);ball.setScaleY(1.06f);}
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
        ballParams.y=GestureRules.clamp(ballParams.y,edgeGap(),screenHeight()-ballParams.height-edgeGap());if(!Prefs.of(this).getBoolean("snap",true)){ballParams.x=GestureRules.clamp(ballParams.x,edgeGap(),screenWidth()-ballParams.width-edgeGap());updateBall();savePosition();wakeBall();return;}
        int target=GestureRules.edgeWithInset(ballParams.x,screenWidth(),ballParams.width,edgeGap());
        snapAnimator=ValueAnimator.ofInt(ballParams.x,target);snapAnimator.setDuration(210);snapAnimator.addUpdateListener(a->{ballParams.x=(int)a.getAnimatedValue();updateBall();});snapAnimator.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){savePosition();wakeBall();}});snapAnimator.start();
    }
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);closePanel();int rotation=windows.getDefaultDisplay().getRotation();boolean rotated=rotation!=displayRotation;displayRotation=rotation;if(rotated&&captures!=null&&captures.isActive()){Prefs.of(this).edit().putString("last_capture_cancel_reason","rotation").apply();captures.cancel();toast("屏幕方向已改变，请重新开始截图");}hideCaptureTarget();if(ball!=null){ballParams.x=GestureRules.edgeWithInset(ballParams.x,screenWidth(),ballParams.width,edgeGap());ballParams.y=GestureRules.clamp(ballParams.y,edgeGap(),screenHeight()-ballParams.height-edgeGap());updateBall();savePosition();}}
    private void toast(String msg){Feedback.show(this,msg);}
    private void launch(Intent i){if(i.getComponent()!=null&&i.getComponent().getClassName().equals(QuickSettingsActivity.class.getName()))i.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);try{startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(RuntimeException e){toast("无法打开，请在主界面重试");}}
    void perform(String action){
        if(action==null||!running||captureInFlight)return;action=Prefs.canonicalAction(action);if("none".equals(action))return;
        if(captures!=null&&captures.isActive()&&!captures.allowsBall()){toast("请先完成或取消当前截图");return;}
        if("quick".equals(action)){if(touch==null||!touch.pressed)return;closePanel();quickDock.show(ballParams.x+ballParams.width/2,ballParams.y+ballParams.height/2);return;}
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
            case "files" -> launch(new Intent(this,FilePickerActivity.class));
            case "settings" -> {if(!MainActivity.hideIfVisible())launch(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
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
        LinearLayout outer=Ui.card(this);outer.setOnClickListener(v->{});Ui.pad(outer,20,20,20,20);
        int width=Math.min(screenWidth()-dp(28),dp(400));FrameLayout.LayoutParams olp=new FrameLayout.LayoutParams(width,-2,Gravity.CENTER);olp.topMargin=dp(30);olp.bottomMargin=dp(30);ScrollView viewport=new ScrollView(this);viewport.setVerticalScrollBarEnabled(false);viewport.addView(outer);panel.addView(viewport,olp);
        LinearLayout head=Ui.row(this);LinearLayout titles=Ui.column(this);titles.addView(Ui.text(this,title,18,Ui.INK,true));if(subtitle!=null){Ui.gap(titles,5);titles.addView(Ui.text(this,subtitle,11,Ui.MUTED,false));}head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));head.addView(Ui.tool(this,"close","关闭窗口",this::closePanel),new LinearLayout.LayoutParams(dp(40),dp(40)));outer.addView(head);Ui.gap(outer,14);
        LinearLayout body=Ui.column(this);outer.addView(body,new LinearLayout.LayoutParams(-1,-2));
        int flags=WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,flags,PixelFormat.TRANSLUCENT);params.setTitle("空叶工具面板");params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;params.gravity=Gravity.TOP|Gravity.LEFT;
        try{windows.addView(panel,params);panel.requestFocus();panel.requestApplyInsets();}catch(RuntimeException e){panel=null;toast("无法打开悬浮窗口");}
        return body;
    }
    private void closePanel(){
        if(quickDock!=null)quickDock.close();
        if(panel!=null){InputMethodManager ime=getSystemService(InputMethodManager.class);ime.hideSoftInputFromWindow(panel.getWindowToken(),0);try{windows.removeView(panel);}catch(IllegalArgumentException ignored){}panel=null;}
        if(browser!=null){FloatingBrowser old=browser;browser=null;old.close();}
        wakeBall();
    }
    private void showMenu(){
        LinearLayout body=showPanel("工具",null,false);
        // Rows: navigation, capture, small tools, apps. Stop replaces record only while a recording is running.
        String[] ids={"back","home","recents","notifications","screenshot","region","longshot",ScreenRecordingService.running?"record_stop":"record","gif_record","notes","browser","files","recent_apps","favorite_apps","apps","settings"};
        for(int r=0;r<ids.length/4;r++){LinearLayout row=Ui.row(this);for(int c=0;c<4;c++){String id=ids[r*4+c];LinearLayout item=Ui.column(this);item.setGravity(Gravity.CENTER);Ui.pad(item,0,9,0,9);item.setBackground(Ui.ripple(this,0,16));FrameLayout box=new FrameLayout(this);box.setBackground(Ui.bg(Ui.PALE,15,this));box.addView(Ui.icon(this,id,Ui.GREEN,22),new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));item.addView(box,new LinearLayout.LayoutParams(dp(46),dp(46)));Ui.gap(item,7);TextView name=Ui.centeredText(this,menuLabel(id),11,Ui.INK,false);name.setSingleLine();item.addView(name);item.setContentDescription(menuLabel(id));item.setOnClickListener(v->perform(id));row.addView(item,new LinearLayout.LayoutParams(0,-2,1));}body.addView(row);}
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
        String title=switch(mode){case "recent"->"最近打开的软件";case "favorites"->"常用软件";case "manage"->"选择常用软件";default->"应用启动";};
        LinearLayout body=showPanel(title,"manage".equals(mode)?"点击星标加入常用":null,true);
        String[] modes={"recent","favorites","all"},labels={"最近","常用","全部"};body.addView(Ui.segmented(this,labels,Arrays.asList(modes).indexOf(mode),index->showApps(modes[index])));Ui.gap(body,10);
        if("recent".equals(mode)&&!AppCatalog.hasUsage(this)){TextView note=Ui.text(this,"需要你在系统中允许使用记录访问权限。只在打开最近软件列表时查询最近 7 天的启动记录，不上传。",13,Ui.MUTED,false);note.setLineSpacing(dp(3),1);Ui.pad(note,4,6,4,0);body.addView(note);Ui.gap(body,14);body.addView(Ui.button(this,"允许查看最近软件",true,()->{closePanel();launch(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,Uri.parse("package:"+getPackageName())));}));return;}
        EditText search=Ui.field(this,"搜索已安装应用");search.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);body.addView(search,new LinearLayout.LayoutParams(-1,dp(42)));Ui.gap(body,8);
        ScrollView scroll=new ScrollView(this);LinearLayout list=Ui.column(this);scroll.addView(list);body.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(330),screenHeight()*42/100)));
        PackageManager pm=getPackageManager();List<AppCatalog.Entry> entries=AppCatalog.all(this);Set<String> favorites=new HashSet<>(Prefs.of(this).getStringSet("favorites",Collections.emptySet()));Map<String,Long> recent="recent".equals(mode)?AppCatalog.recent(this):Collections.emptyMap();
        if("recent".equals(mode))entries.sort((a,b)->Long.compare(recent.getOrDefault(b.pkg,0L),recent.getOrDefault(a.pkg,0L)));
        Runnable populate=()->{list.removeAllViews();String filter=search.getText().toString().toLowerCase(Locale.ROOT);int count=0;
            for(AppCatalog.Entry entry:entries){String pkg=entry.pkg,label=entry.label;if("recent".equals(mode)&&!recent.containsKey(pkg))continue;if("favorites".equals(mode)&&!favorites.contains(pkg))continue;if(!label.toLowerCase(Locale.ROOT).contains(filter)&&!pkg.toLowerCase(Locale.ROOT).contains(filter))continue;if("recent".equals(mode)&&count++>=30)break;
                LinearLayout row=Ui.row(this);Ui.pad(row,6,6,0,6);row.setBackground(Ui.ripple(this,0,14));ImageView icon=new ImageView(this);icon.setImageDrawable(entry.info.loadIcon(pm));row.addView(icon,new LinearLayout.LayoutParams(dp(36),dp(36)));TextView text=Ui.text(this,label,14,Ui.INK,false);text.setSingleLine();text.setEllipsize(android.text.TextUtils.TruncateAt.END);Ui.pad(text,12,0,4,0);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
                boolean starred=favorites.contains(pkg);FrameLayout star=new FrameLayout(this);star.setBackground(Ui.ripple(this,0,22));star.addView(new Ui.Icon(this,starred?"star_on":"favorite_apps",starred?Ui.GREEN:Ui.MUTED),new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));star.setContentDescription((starred?"取消常用：":"设为常用：")+label);row.addView(star,new LinearLayout.LayoutParams(dp(44),dp(44)));
                Runnable toggle=()->{if(!favorites.remove(pkg))favorites.add(pkg);Prefs.of(this).edit().putStringSet("favorites",new HashSet<>(favorites)).apply();showApps(mode);};star.setOnClickListener(v->toggle.run());row.setOnLongClickListener(v->{toggle.run();return true;});row.setOnClickListener(v->{if("manage".equals(mode))toggle.run();else{closePanel();launchApp(pkg);}});list.addView(row);
            }
            if(list.getChildCount()==0){TextView empty=Ui.centeredText(this,"favorites".equals(mode)?"还没有常用软件，点击下方选择。":"没有找到可启动的软件",13,Ui.MUTED,false);Ui.pad(empty,0,28,0,28);list.addView(empty,new LinearLayout.LayoutParams(-1,-2));}
        };
        populate.run();search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){populate.run();}public void afterTextChanged(Editable e){}});
        if(!"manage".equals(mode)){Ui.gap(body,10);body.addView(Ui.button(this,"选择常用软件",false,()->showApps("manage")));}
    }
    private void launchApp(String pkg){Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);if(intent==null)toast("该应用已卸载或无法启动");else launch(intent);}
    @Override public void onDestroy(){if(instance==this)instance=null;running=false;handler.removeCallbacksAndMessages(null);captureInFlight=false;hideCaptureTarget();if(captures!=null)captures.destroy();closePanel();removeBall();try{unregisterReceiver(screenReceiver);}catch(IllegalArgumentException ignored){}stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
