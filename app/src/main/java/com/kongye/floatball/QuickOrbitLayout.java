package com.kongye.floatball;
/** Fixed reference coordinates, mirrored at the screen edge; 12 choices plus one recent slot. */
public final class QuickOrbitLayout {
    private QuickOrbitLayout(){}
    public static float scale(int width,int height,float unit){return Math.min(width/510f,(height-unit*48)/340f);}
    public static float diameter(int width,int height,float unit){return 44*scale(width,height,unit);}
    public static float[][] positions(int width,int height,float anchorX,float anchorY,float unit){
        float[][] design={{40,-142},{90,-118},{34,-87},{79,-54},{130,-76},{150,-29},{97,1},{148,26},{78,53},{130,77},{91,120},{40,144},{35,87}};
        float factor=scale(width,height,unit),cy=Math.max(168*factor,Math.min(height-170*factor,anchorY));boolean left=anchorX<width/2f;
        float[][] result=new float[13][2];for(int i=0;i<result.length;i++){result[i][0]=left?design[i][0]*factor:width-design[i][0]*factor;result[i][1]=cy+design[i][1]*factor;}return result;
    }
}
