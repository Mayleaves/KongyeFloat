package com.kongye.floatball;
import android.content.Context;
import android.graphics.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import com.kongye.paddle.PaddleBridge;

/** Two local engines, bounded image tiles; failures retain the other engine's result. */
final class EnhancedOcr {
    private EnhancedOcr(){}
    static List<TextRegion> recognize(Context c,Bitmap source,BooleanSupplier cancelled)throws Exception{
        List<TextRegion> ml=Collections.emptyList();Exception mlError=null;
        try{ml=OcrPipeline.recognizeRegions(source,cancelled);}catch(Exception error){mlError=error;}
        if(cancelled.getAsBoolean())throw new java.io.InterruptedIOException();
        try{List<TextRegion> paddle=recognizePaddle(c,source,cancelled);if(paddle.isEmpty()&&mlError!=null)throw mlError;return OcrEngineFusion.merge(paddle,ml);}
        catch(Exception|LinkageError|OutOfMemoryError error){if(cancelled.getAsBoolean())throw new java.io.InterruptedIOException();if(mlError!=null)throw mlError;return ml;}
    }
    static List<TextRegion> recognizePaddle(Context c,Bitmap source,BooleanSupplier cancelled)throws Exception{
        ArrayList<TextRegion> lines=new ArrayList<>();try(PaddleBridge engine=new PaddleBridge(c)){
            int step=1280,margin=96;
            for(int y=0;y<source.getHeight();y+=step)for(int x=0;x<source.getWidth();x+=step){if(cancelled.getAsBoolean())throw new java.io.InterruptedIOException();int l=Math.max(0,x-margin),t=Math.max(0,y-margin),r=Math.min(source.getWidth(),x+step+margin),b=Math.min(source.getHeight(),y+step+margin);Bitmap tile=Bitmap.createBitmap(source,l,t,r-l,b-t);try{
                for(PaddleBridge.Line line:engine.recognize(tile,cancelled)){int left=l+line.getLeft(),top=t+line.getTop(),right=l+line.getRight(),bottom=t+line.getBottom();int cx=(left+right)/2,cy=(top+bottom)/2;if(cx<x||cx>=x+step||cy<y||cy>=y+step)continue;
                    TextRegion candidate=new TextRegion(line.getText(),left,top,right,bottom,Collections.emptyList(),false,line.getConfidence());
                    // Keep the existing word/symbol geometry when fusing with exact native text.
                    lines.add(candidate);
                }
            }finally{if(tile!=source)tile.recycle();}}
        }return lines;
    }
}
