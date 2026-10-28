package com.kongye.floatball;

/** Estimates scroll displacement from overlapping pixels. No screen data leaves the device. */
public final class FrameStitcher {
    private FrameStitcher(){}
    public static int findShift(int[] previous,int[] current,int width,int height){
        if(width<8||height<32||previous.length!=width*height||current.length!=previous.length)return -1;
        if(score(previous,current,width,height,0)<.25)return 0;
        int step=Math.max(1,height/500),best=-1;double error=Double.MAX_VALUE;
        for(int shift=step;shift<=height*6/7;shift+=step){double value=score(previous,current,width,height,shift);if(value<error){error=value;best=shift;}}
        int center=best;
        for(int shift=Math.max(1,center-step);shift<=Math.min(height*6/7,center+step);shift++){double value=score(previous,current,width,height,shift);if(value<error){error=value;best=shift;}}
        return error<=12?best:-1;
    }
    private static double score(int[] a,int[] b,int width,int height,int shift){
        long total=0;int count=0,overlap=height-shift;
        for(int sy=0;sy<64;sy++){int y=(sy*overlap)/64;for(int sx=0;sx<24;sx++){int x=width/16+(sx*(width-width/8))/24;int left=a[(y+shift)*width+x],right=b[y*width+x];total+=Math.abs((left&255)-(right&255))+Math.abs(((left>>8)&255)-((right>>8)&255))+Math.abs(((left>>16)&255)-((right>>16)&255));count+=3;}}
        return (double)total/Math.max(count,1);
    }
}
