package com.kongye.floatball;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.*;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.File;

/** Texture playback keeps its controls in sync, and supports zoom without a separate activity. */
final class RecordingPlayer extends LinearLayout implements TextureView.SurfaceTextureListener {
    private final File file;private final TextureView texture;private final FrameLayout picture,play;private final SeekBar seek;private final TextView time;
    private final Handler handler=new Handler(Looper.getMainLooper());private final Runnable enlarge;private final boolean large;
    private MediaPlayer player;private Surface surface;private boolean ready,released,desiredPlaying,seeking,completed;private int position,duration,videoW=1,videoH=1,frames;
    private float zoom=1,panX,panY,downX,downY,lastX,lastY,lastSpan,lastFocusX,lastFocusY;private boolean moving,multi;private long lastTap;
    RecordingPlayer(Context context,File file,boolean large,int initialPosition,boolean playing,Runnable enlarge){
        super(context);this.file=file;this.large=large;this.position=initialPosition;desiredPlaying=playing;this.enlarge=enlarge;setOrientation(VERTICAL);
        picture=new FrameLayout(context);Ui.frame(picture,0xFF101412,14);texture=new TextureView(context);texture.setContentDescription(large?"放大录屏画面，可双指缩放移动":"录屏视频预览，点击放大");picture.addView(texture,new FrameLayout.LayoutParams(-1,-1));addView(picture,new LinearLayout.LayoutParams(-1,0,1));texture.setSurfaceTextureListener(this);texture.setOnTouchListener((v,e)->touch(e));
        LinearLayout controls=Ui.row(context);Ui.pad(controls,0,6,0,0);play=Ui.symbol(context,"play","播放或暂停录屏预览",this::toggle);play.setBackground(Ui.ripple(context,Ui.PALE,20));controls.addView(play,new LinearLayout.LayoutParams(dp(40),dp(40)));
        seek=new SeekBar(context);seek.setContentDescription("录屏预览进度");seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));controls.addView(seek,new LinearLayout.LayoutParams(0,dp(44),1));time=Ui.text(context,"00:00 / 00:00",11,Ui.MUTED,false);controls.addView(time);addView(controls,new LinearLayout.LayoutParams(-1,dp(50)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int value,boolean user){if(user){completed=false;position=value;if(ready)player.seekTo(value,MediaPlayer.SEEK_CLOSEST);updateLabel();}}public void onStartTrackingTouch(SeekBar s){seeking=true;}public void onStopTrackingTouch(SeekBar s){seeking=false;}});
        handler.post(tick);
    }
    private int dp(float value){return Ui.dp(getContext(),value);}
    private final Runnable tick=new Runnable(){public void run(){if(released)return;if(ready){try{position=completed?duration:player.getCurrentPosition();if(!seeking)seek.setProgress(position);updateLabel();receipt();}catch(IllegalStateException ignored){}}handler.postDelayed(this,200);}};
    private void updateLabel(){String label=clock(position)+" / "+clock(duration);if(!label.contentEquals(time.getText()))time.setText(label);}
    private String clock(int value){int seconds=value/1000;return String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);}
    private void updatePlay(){play.removeAllViews();play.addView(Ui.icon(getContext(),desiredPlaying?"pause":"play",Ui.GREEN,24),new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));play.setContentDescription(desiredPlaying?"暂停录屏预览":"播放录屏预览");}
    private void toggle(){desiredPlaying=!desiredPlaying;if(ready){if(desiredPlaying){if(completed||position>=duration-200){completed=false;position=0;seek.setProgress(0);player.seekTo(0,MediaPlayer.SEEK_CLOSEST);}player.start();}else player.pause();}updatePlay();receipt();}
    int position(){return completed?duration:ready?player.getCurrentPosition():position;}
    boolean playing(){return desiredPlaying;}
    void pause(){desiredPlaying=false;if(ready)player.pause();updatePlay();}
    void release(){released=true;handler.removeCallbacksAndMessages(null);ready=false;if(player!=null){player.release();player=null;}if(surface!=null){surface.release();surface=null;}}
    public void onSurfaceTextureAvailable(SurfaceTexture st,int width,int height){if(released)return;try{surface=new Surface(st);player=new MediaPlayer();player.setDataSource(file.getPath());player.setSurface(surface);player.setOnVideoSizeChangedListener((p,w,h)->{videoW=w;videoH=h;matrix();});player.setOnPreparedListener(p->{if(released)return;ready=true;duration=p.getDuration();seek.setMax(duration);p.seekTo(Math.min(position,Math.max(0,duration-1)),MediaPlayer.SEEK_CLOSEST);if(desiredPlaying)p.start();updatePlay();updateLabel();receipt();});player.setLooping(false);player.setOnCompletionListener(p->{completed=true;desiredPlaying=false;position=duration;seek.setProgress(duration);updatePlay();});player.setOnErrorListener((p,what,extra)->{ready=false;desiredPlaying=false;updatePlay();Toast.makeText(getContext(),"视频暂时无法播放",Toast.LENGTH_SHORT).show();return true;});player.prepareAsync();}catch(Exception e){Toast.makeText(getContext(),"无法打开录屏视频",Toast.LENGTH_SHORT).show();}}
    public void onSurfaceTextureSizeChanged(SurfaceTexture st,int w,int h){matrix();}
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st){if(player!=null){try{position=player.getCurrentPosition();}catch(IllegalStateException ignored){}player.release();player=null;}ready=false;if(surface!=null){surface.release();surface=null;}return true;}
    public void onSurfaceTextureUpdated(SurfaceTexture st){frames++;}
    private void matrix(){float w=texture.getWidth(),h=texture.getHeight();if(w<=0||h<=0)return;float fit=Math.min(w/videoW,h/videoH),drawW=videoW*fit*zoom,drawH=videoH*fit*zoom;panX=Math.max(-Math.max(0,(drawW-w)/2),Math.min(Math.max(0,(drawW-w)/2),panX));panY=Math.max(-Math.max(0,(drawH-h)/2),Math.min(Math.max(0,(drawH-h)/2),panY));Matrix m=new Matrix();m.setScale(drawW/w,drawH/h,w/2,h/2);m.postTranslate(panX,panY);texture.setTransform(m);receipt();}
    private boolean touch(MotionEvent e){switch(e.getActionMasked()){
        case MotionEvent.ACTION_DOWN->{downX=lastX=e.getX();downY=lastY=e.getY();moving=multi=false;return true;}
        case MotionEvent.ACTION_POINTER_DOWN->{multi=true;lastSpan=(float)Math.hypot(e.getX(1)-e.getX(0),e.getY(1)-e.getY(0));lastFocusX=(e.getX(0)+e.getX(1))/2;lastFocusY=(e.getY(0)+e.getY(1))/2;return true;}
        case MotionEvent.ACTION_MOVE->{if(e.getPointerCount()>1&&large){float x=(e.getX(0)+e.getX(1))/2,y=(e.getY(0)+e.getY(1))/2,span=(float)Math.hypot(e.getX(1)-e.getX(0),e.getY(1)-e.getY(0)),old=zoom;if(lastSpan>dp(8))zoom=Math.max(1,Math.min(8,zoom*span/lastSpan));float factor=zoom/old;panX=x-texture.getWidth()/2f-(lastFocusX-texture.getWidth()/2f-panX)*factor;panY=y-texture.getHeight()/2f-(lastFocusY-texture.getHeight()/2f-panY)*factor;lastSpan=span;lastFocusX=x;lastFocusY=y;multi=true;matrix();}else if(!multi){moving|=Math.hypot(e.getX()-downX,e.getY()-downY)>dp(6);if(large&&moving){panX+=e.getX()-lastX;panY+=e.getY()-lastY;matrix();}lastX=e.getX();lastY=e.getY();}return true;}
        case MotionEvent.ACTION_UP->{if(!multi&&!moving){if(!large)enlarge.run();else{long now=SystemClock.uptimeMillis();if(now-lastTap<300){zoom=zoom>1?1:2;panX=panY=0;matrix();lastTap=0;}else{toggle();lastTap=now;}}texture.performClick();}return true;}
        default->{return true;}
    }}
    private void receipt(){if((getContext().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0)Prefs.of(getContext()).edit().putInt("qa_play_frames",frames).putInt("qa_play_position",position).putInt("qa_play_duration",duration).putBoolean("qa_play_ready",ready).putBoolean("qa_play_playing",desiredPlaying).putBoolean("qa_play_large",large).putFloat("qa_play_zoom",zoom).apply();}
}
