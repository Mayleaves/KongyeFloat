package com.kongye.floatball;
/** Fixed reference coordinates, mirrored at the screen edge; 12 choices plus one recent slot. */
public final class QuickOrbitLayout {
    private static final float[][] DESIGN={{40,-142},{90,-118},{34,-87},{79,-54},{130,-76},{150,-29},{97,1},{148,26},{78,53},{130,77},{91,120},{40,144},{35,87}};
    private QuickOrbitLayout(){}
    public static float scale(int width,int height,float unit){return Math.min(width/510f,(height-unit*48)/340f);}
    public static float diameter(int width,int height,float unit){return 44*scale(width,height,unit);}
    public static float scale(int width,int height,float anchorX,float anchorY,float unit){
        float factor=scale(width,height,unit);
        // Keep the origin at the ball. Fit by scaling instead of translating the fan.
        if(anchorY>0)factor=Math.min(factor,anchorY/164f);
        if(anchorY<height)factor=Math.min(factor,(height-anchorY)/166f);
        float inward=anchorX<width/2f?width-anchorX:anchorX;
        if(inward>0)factor=Math.min(factor,inward/172f);
        return Math.max(.01f,factor);
    }
    public static float diameter(int width,int height,float anchorX,float anchorY,float unit){return 44*scale(width,height,anchorX,anchorY,unit);}
    public static float[][] positions(int width,int height,float anchorX,float anchorY,float unit){
        float factor=scale(width,height,anchorX,anchorY,unit);boolean left=anchorX<width/2f;
        float[][] result=new float[13][2];for(int i=0;i<result.length;i++){result[i][0]=anchorX+(left?1:-1)*DESIGN[i][0]*factor;result[i][1]=anchorY+DESIGN[i][1]*factor;}return result;
    }
}
