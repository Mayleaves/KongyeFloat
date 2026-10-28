package com.kongye.floatball;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

final class V24Checks {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    static void run(Activity c){count=0;String error="";try{for(int n:new int[]{1,2,3,4,5,7,8,9,18}){HomeToolGrid grid=new HomeToolGrid(c,Arrays.asList(HomeTools.AVAILABLE).subList(0,n),id->{LinearLayout tile=Ui.column(c);tile.addView(new View(c),new LinearLayout.LayoutParams(80,80));return tile;},order->{});grid.measure(View.MeasureSpec.makeMeasureSpec(1120,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.AT_MOST));grid.layout(0,0,grid.getMeasuredWidth(),grid.getMeasuredHeight());int rows=(n+3)/4;check(grid.getMeasuredHeight()==rows*Ui.dp(c,110),"partial rows keep bounded height "+n);for(int r=0;r<rows;r++){LinearLayout row=(LinearLayout)grid.getChildAt(r);check(row.getChildAt(3).getRight()-row.getChildAt(0).getLeft()==1120,"four equal slots span row "+n);check(Math.abs(row.getChildAt(0).getWidth()-row.getChildAt(3).getWidth())<=1,"column widths equal "+n);}}
        Bitmap image=Bitmap.createBitmap(600,600,Bitmap.Config.ARGB_8888);GestureCaptureOverlay cross=new GestureCaptureOverlay(c);cross.x=300;cross.y=350;cross.drawAt(new Canvas(image),37,95);check(Color.red(image.getPixel(263,255))>200,"screen cross subtracts overlay origin");check(Color.alpha(image.getPixel(300,350))==0,"cross is not shifted by inset twice");image.recycle();
    }catch(Throwable t){error=t.toString();}Prefs.of(c).edit().putInt("qa_v24_checks",count).putBoolean("qa_v24_pass",error.isEmpty()).putString("qa_v24_error",error).apply();}
    static void fixture(Activity c)throws Exception{File file=RecordingFiles.create(c,"gif");Bitmap frame=Bitmap.createBitmap(160,100,Bitmap.Config.ARGB_8888);try(GifWriter writer=new GifWriter(new BufferedOutputStream(new FileOutputStream(file)),160,100)){for(int color:new int[]{Color.RED,Color.BLUE,Color.GREEN}){frame.eraseColor(color);writer.frame(frame,200);}}finally{frame.recycle();}Prefs.of(c).edit().putString("qa_v24_fixture",file.getPath()).remove("last_record_saved_uri").apply();c.startActivity(new Intent(c,RecordingResultActivity.class).putExtra("path",file.getPath()));}
}
