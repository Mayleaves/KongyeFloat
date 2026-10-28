package com.kongye.floatball;

public final class CropGeometry {
    public static final int MOVE=0,LEFT=1,TOP=2,RIGHT=4,BOTTOM=8;
    private CropGeometry(){}
    public static float[] adjust(float[] original,int handle,float dx,float dy,float width,float height,float minimum){
        float[] r=original.clone();float minW=Math.min(minimum,width),minH=Math.min(minimum,height);
        if(handle==MOVE){dx=Math.max(-r[0],Math.min(width-r[2],dx));dy=Math.max(-r[1],Math.min(height-r[3],dy));r[0]+=dx;r[2]+=dx;r[1]+=dy;r[3]+=dy;}
        else{if((handle&LEFT)!=0)r[0]=Math.max(0,Math.min(r[2]-minW,r[0]+dx));if((handle&RIGHT)!=0)r[2]=Math.min(width,Math.max(r[0]+minW,r[2]+dx));if((handle&TOP)!=0)r[1]=Math.max(0,Math.min(r[3]-minH,r[1]+dy));if((handle&BOTTOM)!=0)r[3]=Math.min(height,Math.max(r[1]+minH,r[3]+dy));}
        return r;
    }
}
