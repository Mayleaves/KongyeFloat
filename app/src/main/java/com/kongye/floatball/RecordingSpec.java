package com.kongye.floatball;
import android.content.*;
import android.graphics.Rect;
/** Serializable capture geometry and output choices; recording always samples the chosen screen rectangle. */
final class RecordingSpec {
    int screenW,screenH,density,shortSide=720;Rect crop;boolean gif,hide;String audio="silent",watermark="";
    int width,height;
    void size(){float scale=Math.min(1f,shortSide/(float)Math.min(crop.width(),crop.height()));scale=Math.min(scale,1920f/Math.max(crop.width(),crop.height()));width=Math.max(16,((int)(crop.width()*scale)/16)*16);height=Math.max(16,((int)(crop.height()*scale)/16)*16);}
    Intent write(Intent i){return i.putExtra("screen_w",screenW).putExtra("screen_h",screenH).putExtra("density",density).putExtra("crop",crop).putExtra("short_side",shortSide).putExtra("gif",gif).putExtra("hide",hide).putExtra("audio",audio).putExtra("watermark",watermark);}
    static RecordingSpec read(Intent i){RecordingSpec s=new RecordingSpec();s.screenW=i.getIntExtra("screen_w",1080);s.screenH=i.getIntExtra("screen_h",1920);s.density=i.getIntExtra("density",320);s.crop=i.getParcelableExtra("crop");if(s.crop==null)s.crop=new Rect(0,0,s.screenW,s.screenH);s.crop.intersect(0,0,s.screenW,s.screenH);if(s.crop.width()<16||s.crop.height()<16)s.crop=new Rect(0,0,s.screenW,s.screenH);s.shortSide=i.getIntExtra("short_side",720);s.gif=i.getBooleanExtra("gif",false);s.hide=i.getBooleanExtra("hide",false);s.audio=s.gif?"silent":i.getStringExtra("audio");if(s.audio==null)s.audio="silent";s.watermark=i.getStringExtra("watermark");if(s.watermark==null)s.watermark="";s.size();return s;}
}
