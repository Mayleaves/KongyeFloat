package com.kongye.floatball;
import java.util.*;

/** Paddle detects/recognizes whole lines; retain ML Kit-only locations and reliable Latin typography. */
final class OcrEngineFusion {
    private OcrEngineFusion(){}
    static float overlap(TextRegion a,TextRegion b){long area=(long)Math.max(0,Math.min(a.right,b.right)-Math.max(a.left,b.left))*Math.max(0,Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top));return area/(float)Math.max(1,Math.min(a.area(),b.area()));}
    private static String key(String text){return text.replaceAll("\\s+","");}
    static List<TextRegion> merge(List<TextRegion> primary,List<TextRegion> fallback){
        if(primary.isEmpty())return new ArrayList<>(fallback);ArrayList<TextRegion> result=new ArrayList<>();boolean[] used=new boolean[fallback.size()];
        for(TextRegion p:primary){TextRegion chosen=p;float best=0;int match=-1;
            for(int i=0;i<fallback.size();i++){TextRegion m=fallback.get(i);float hit=overlap(p,m);if(hit>.45f){used[i]=true;if(hit>best){best=hit;match=i;}}}
            if(match>=0){TextRegion m=fallback.get(match);if(p.confidence<.72f&&m.confidence>=.9f)chosen=m;
                else if(!OcrPunctuation.containsHan(p.text)&&!OcrPunctuation.containsHan(m.text)&&m.confidence>=.94f&&key(OcrPunctuation.normalize(p.text)).equals(key(m.text)))chosen=m;}
            if(chosen==p&&match>=0)chosen=OcrTextAlignment.apply(p,fallback.get(match));result.add(chosen);
        }
        for(int i=0;i<fallback.size();i++)if(!used[i]&&fallback.get(i).confidence>=.5f)result.add(fallback.get(i));
        return result;
    }
}
