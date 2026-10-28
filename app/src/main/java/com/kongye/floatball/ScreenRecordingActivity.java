package com.kongye.floatball;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.projection.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
/** Small transparent permission host; the live selector is drawn using separately touchable overlay windows. */
public class ScreenRecordingActivity extends Activity {
    private RecordingSetup setup;private boolean requested;
    @Override public void onCreate(Bundle saved){super.onCreate(saved);ThemePolicy.apply(this);getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);setContentView(new FrameLayout(this));getWindow().setLayout(1,1);getWindow().setGravity(Gravity.TOP|Gravity.LEFT);if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"请先允许悬浮窗权限",Toast.LENGTH_LONG).show();finish();return;}if(ScreenRecordingService.running){startService(new Intent(this,ScreenRecordingService.class).setAction(ScreenRecordingService.SHOW));finish();return;}setup=new RecordingSetup(this,getIntent().getBooleanExtra("gif",false),this::begin,this::finish);if(getIntent().getBooleanExtra("resume_setup",false))setup.restore(RecordingSpec.read(getIntent()));if(saved!=null){setup.spec.crop=saved.getParcelable("crop");setup.spec.gif=saved.getBoolean("gif");setup.spec.shortSide=saved.getInt("resolution",setup.spec.shortSide);setup.spec.audio=saved.getString("audio",setup.spec.audio);setup.spec.hide=saved.getBoolean("hide",setup.spec.hide);setup.spec.watermark=saved.getString("watermark",setup.spec.watermark);requested=saved.getBoolean("requested");}setup.show();if(requested)setup.visible(false);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(requested)return;if(ScreenRecordingService.running){startService(new Intent(this,ScreenRecordingService.class).setAction(ScreenRecordingService.SHOW));finish();return;}if(setup!=null)setup.remove();setup=new RecordingSetup(this,intent.getBooleanExtra("gif",false),this::begin,this::finish);if(intent.getBooleanExtra("resume_setup",false))setup.restore(RecordingSpec.read(intent));setup.show();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(setup!=null){state.putParcelable("crop",setup.spec.crop);state.putBoolean("gif",setup.spec.gif);state.putInt("resolution",setup.spec.shortSide);state.putString("audio",setup.spec.audio);state.putBoolean("hide",setup.spec.hide);state.putString("watermark",setup.spec.watermark);}state.putBoolean("requested",requested);}
    private void begin(){if(requested||setup==null)return;setup.visible(false);if(!setup.spec.gif&&!"silent".equals(setup.spec.audio)&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},81);return;}requestProjection();}
    private void requestProjection(){try{requested=true;MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);startActivityForResult(Build.VERSION.SDK_INT>=34?manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()):manager.createScreenCaptureIntent(),82);}catch(RuntimeException e){requested=false;setup.visible(true);Toast.makeText(this,"无法请求录制授权",Toast.LENGTH_LONG).show();}}
    @Override public void onRequestPermissionsResult(int request,String[] p,int[] results){super.onRequestPermissionsResult(request,p,results);if(request==81){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)requestProjection();else{setup.visible(true);Toast.makeText(this,"声音权限未允许，可改为无声",Toast.LENGTH_LONG).show();}}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==82){requested=false;if(result==RESULT_OK&&data!=null){try{setup.spec.size();startForegroundService(setup.spec.write(new Intent(this,ScreenRecordingService.class)).putExtra("result_code",result).putExtra("projection",data));finish();}catch(RuntimeException e){setup.visible(true);Toast.makeText(this,"录制未启动，请重试",Toast.LENGTH_LONG).show();}}else setup.visible(true);}}
    @Override public void onDestroy(){if(setup!=null)setup.remove();super.onDestroy();}
}
