package com.kongye.floatball;
import android.content.Context;
import android.media.*;
final class RecordingTimelineChecks {
    static void run(Context c){new Thread(()->{MediaExtractor x=new MediaExtractor();String error="";long first=-1,last=-1,duration=-1;int count=0;try{x.setDataSource(Prefs.of(c).getString("last_record_uri",""));for(int track=0;track<x.getTrackCount();track++)if(x.getTrackFormat(track).getString(MediaFormat.KEY_MIME).startsWith("video/")){MediaFormat f=x.getTrackFormat(track);duration=f.getLong(MediaFormat.KEY_DURATION);x.selectTrack(track);break;}while(x.getSampleTime()>=0){long time=x.getSampleTime();if(first<0)first=time;last=time;count++;if(!x.advance())break;}}catch(Exception e){error=e.toString();}finally{x.release();Prefs.of(c).edit().putInt("qa_timeline_count",count).putLong("qa_timeline_first",first).putLong("qa_timeline_last",last).putLong("qa_timeline_duration",duration).putString("qa_timeline_error",error).apply();}},"Kongye-TimelineCheck").start();}
}
