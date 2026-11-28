package com.kongye.floatball;
import java.util.*;
import java.util.function.IntBinaryOperator;
/** Connected light paper regions at half resolution keep one bubble's columns together. */
final class ComicBubbleMap {
    private final int width,height,sourceWidth,sourceHeight;
    private final int[] labels;
    private final ArrayList<int[]> bounds=new ArrayList<>();
    ComicBubbleMap(int w,int h,IntBinaryOperator pixel){
        sourceWidth=w;sourceHeight=h;width=(w+1)/2;height=(h+1)/2;labels=new int[width*height];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){int p=pixel.applyAsInt(Math.min(w-1,x*2),Math.min(h-1,y*2));if(Math.min((p>>16)&255,Math.min((p>>8)&255,p&255))>215)labels[y*width+x]=-1;}
        bounds.add(new int[5]);int[] queue=new int[width*height];
        for(int i=0;i<labels.length;i++)if(labels[i]==-1){
            int id=bounds.size(),read=0,end=1;queue[0]=i;labels[i]=id;int left=width,top=height,right=0,bottom=0;
            while(read<end){int pos=queue[read++],x=pos%width,y=pos/width;left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
                if(x>0&&labels[pos-1]==-1){labels[pos-1]=id;queue[end++]=pos-1;}
                if(x+1<width&&labels[pos+1]==-1){labels[pos+1]=id;queue[end++]=pos+1;}
                if(y>0&&labels[pos-width]==-1){labels[pos-width]=id;queue[end++]=pos-width;}
                if(y+1<height&&labels[pos+width]==-1){labels[pos+width]=id;queue[end++]=pos+width;}
            }bounds.add(new int[]{left*2,top*2,Math.min(w,right*2+2),Math.min(h,bottom*2+2),end});
        }
    }
    int bubble(int l,int t,int r,int b){
        HashMap<Integer,Integer> votes=new HashMap<>();
        for(int y=t-2;y<=b+2;y+=3)for(int x:new int[]{l-2,l+1,r-1,r+2})vote(votes,x,y);
        for(int x=l-2;x<=r+2;x+=3)for(int y:new int[]{t-2,t+1,b-1,b+2})vote(votes,x,y);
        int best=0,count=0;for(var v:votes.entrySet())if(v.getValue()>count){best=v.getKey();count=v.getValue();}
        if(best==0)return 0;int[] box=bounds.get(best);
        if(box[2]-box[0]>sourceWidth*.78f||box[3]-box[1]>sourceHeight*.78f||box[4]<12)return 0;
        if(box[0]>l+4||box[1]>t+4||box[2]<r-4||box[3]<b-4)return 0;
        return best;
    }
    private void vote(Map<Integer,Integer> votes,int x,int y){if(x<0||y<0||x>=sourceWidth||y>=sourceHeight)return;int id=labels[(y/2)*width+x/2];if(id>0)votes.merge(id,1,Integer::sum);}
    List<int[]> spans(int id,int l,int t,int r,int b){
        ArrayList<int[]> spans=new ArrayList<>();
        for(int y=Math.max(0,t/2);y<Math.min(height,(b+1)/2);y++){
            int left=width,right=-1;for(int x=Math.max(0,l/2);x<Math.min(width,(r+1)/2);x++)if(labels[y*width+x]==id){left=Math.min(left,x);right=x;}
            if(right>=left)spans.add(new int[]{Math.max(l,left*2+1),Math.max(t,y*2),Math.min(r,right*2+1),Math.min(b,y*2+2)});
        }return spans;
    }
    static int[] innerBounds(List<int[]> spans){
        int[] best=null;long area=0;
        for(int start=0;start<spans.size();start++){int l=0,r=Integer.MAX_VALUE,bottom=spans.get(start)[1];
            for(int end=start;end<spans.size();end++){int[] row=spans.get(end);if(row[1]>bottom)break;bottom=row[3];l=Math.max(l,row[0]);r=Math.min(r,row[2]);if(r<=l)break;
                long candidate=(long)(r-l)*(bottom-spans.get(start)[1]);if(candidate>area){area=candidate;best=new int[]{l,spans.get(start)[1],r,bottom};}
            }
        }return best;
    }
}
