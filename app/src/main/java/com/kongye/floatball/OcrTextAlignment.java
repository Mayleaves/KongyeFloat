package com.kongye.floatball;
import java.util.*;

/** Reuse ML Kit's symbol geometry when a second engine improves the same line's text. */
final class OcrTextAlignment {
    private OcrTextAlignment(){}
    private static void flatten(TextRegion r,List<TextRegion> out){
        if(!r.parts.isEmpty()){for(TextRegion part:r.parts)flatten(part,out);return;}
        int[] cps=r.text.codePoints().toArray();for(int i=0;i<cps.length;i++){if(Character.isWhitespace(cps[i]))continue;out.add(new TextRegion(new String(Character.toChars(cps[i])),r.left+r.width()*i/cps.length,r.top,r.left+r.width()*(i+1)/cps.length,r.bottom));}
    }
    static TextRegion apply(TextRegion improved,TextRegion positioned){
        ArrayList<TextRegion> cells=new ArrayList<>();for(TextRegion part:positioned.parts)flatten(part,cells);if(cells.isEmpty()||cells.size()>512)return improved;
        int[] chars=improved.text.codePoints().filter(c->!Character.isWhitespace(c)).toArray();if(chars.length==0||chars.length>512)return improved;
        int n=chars.length,m=cells.size();int[][] cost=new int[n+1][m+1];for(int i=0;i<=n;i++)cost[i][0]=i;for(int j=0;j<=m;j++)cost[0][j]=j;
        for(int i=1;i<=n;i++)for(int j=1;j<=m;j++)cost[i][j]=Math.min(cost[i-1][j-1]+(chars[i-1]==cells.get(j-1).text.codePointAt(0)?0:1),Math.min(cost[i-1][j],cost[i][j-1])+1);
        if(cost[n][m]>Math.max(n,m)*.35f)return improved;int[] mapped=new int[n];Arrays.fill(mapped,-1);int i=n,j=m;
        while(i>0||j>0){if(i>0&&j>0&&cost[i][j]==cost[i-1][j-1]+(chars[i-1]==cells.get(j-1).text.codePointAt(0)?0:1)){mapped[--i]=--j;}else if(i>0&&cost[i][j]==cost[i-1][j]+1)i--;else j--;}
        ArrayList<TextRegion> parts=new ArrayList<>();int k=0;for(int cp:improved.text.codePoints().toArray()){
            if(Character.isWhitespace(cp)){parts.add(new TextRegion(" ",0,0,0,0));continue;}int at=mapped[k];TextRegion box;
            if(at>=0)box=cells.get(at);else{int before=k-1,after=k+1;while(before>=0&&mapped[before]<0)before--;while(after<n&&mapped[after]<0)after++;int left=before>=0?cells.get(mapped[before]).right:improved.left,right=after<n?cells.get(mapped[after]).left:improved.right;int width=Math.max(1,(right-left)/(after-before));left+=width*(k-before-1);box=new TextRegion("",left,improved.top,left+width,improved.bottom);}
            parts.add(new TextRegion(new String(Character.toChars(cp)),box.left,box.top,box.right,box.bottom));k++;
        }
        return new TextRegion(improved.text,improved.left,improved.top,improved.right,improved.bottom,parts,true,improved.confidence);
    }
}
