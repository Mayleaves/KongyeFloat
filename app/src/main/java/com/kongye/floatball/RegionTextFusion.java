package com.kongye.floatball;

import java.text.Normalizer;
import java.util.*;

/** Source text wins in its own location; OCR adds text elsewhere, including mixed lines. */
final class RegionTextFusion {
    private RegionTextFusion(){}
    private static String key(String text){return Normalizer.normalize(text,Normalizer.Form.NFKC).replaceAll("\\s+","").toLowerCase(Locale.ROOT);}
    private static boolean usable(TextRegion r){return r!=null&&r.text!=null&&!r.text.trim().isEmpty()&&r.area()>0;}
    private static long intersection(TextRegion a,TextRegion b){return (long)Math.max(0,Math.min(a.right,b.right)-Math.max(a.left,b.left))*Math.max(0,Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top));}
    private static boolean contains(TextRegion a,TextRegion b){return a.left<=b.left&&a.top<=b.top&&a.right>=b.right&&a.bottom>=b.bottom;}
    private static boolean similar(String a,String b){
        if(a.equals(b))return true;if(Math.min(a.length(),b.length())<4)return false;
        int limit=Math.max(a.length(),b.length())/3;if(Math.abs(a.length()-b.length())>limit)return false;
        // Avoid quadratic work for large accessibility paragraphs.
        if(Math.max(a.length(),b.length())>512)return false;
        int[] old=new int[b.length()+1];for(int j=0;j<old.length;j++)old[j]=j;
        for(int i=1;i<=a.length();i++){int[] next=new int[b.length()+1];next[0]=i;for(int j=1;j<next.length;j++)next[j]=Math.min(Math.min(next[j-1]+1,old[j]+1),old[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));old=next;}
        return old[b.length()]<=limit;
    }
    static List<TextRegion> originals(List<TextRegion> supplied){
        ArrayList<TextRegion> result=new ArrayList<>();
        for(TextRegion candidate:supplied){if(!usable(candidate))continue;String value=key(candidate.text);boolean duplicate=false;
            for(Iterator<TextRegion> it=result.iterator();it.hasNext();){TextRegion prior=it.next();if(intersection(prior,candidate)==0)continue;String known=key(prior.text);
                if(contains(prior,candidate)&&known.contains(value)){duplicate=true;break;}
                if(contains(candidate,prior)&&value.contains(known)){it.remove();continue;}
                if(known.equals(value)&&intersection(prior,candidate)>=Math.min(prior.area(),candidate.area())*.65){duplicate=true;break;}
            }if(!duplicate)result.add(candidate);
        }return result;
    }
    private static boolean covered(TextRegion candidate,List<TextRegion> source){
        String value=key(candidate.text);if(value.isEmpty())return false;
        for(TextRegion original:source){long overlap=intersection(candidate,original);if(overlap==0)continue;float amount=overlap/(float)candidate.area();String known=key(original.text);
            if(amount>=.45f&&(known.contains(value)||original.height()<=candidate.height()*2.5f&&similar(known,value)))return true;
            // Prefer exact source even if OCR misread it, but only for a tightly matching text box.
            if(contains(original,candidate)&&amount>=.65f&&overlap>=original.area()*.30&&original.height()<=candidate.height()*2.5f&&known.length()>=value.length()*.55f&&known.length()<=value.length()*1.8f)return true;
            // Symbols in a mixed native/image line: do not let a wide, mostly empty node mask images.
            if(amount>=.75f&&value.codePointCount(0,value.length())<=2&&original.height()<=candidate.height()*2.5f&&original.width()<=known.codePointCount(0,known.length())*candidate.height()*2.1f)return true;
        }return false;
    }
    private static void supplement(TextRegion candidate,List<TextRegion> source,List<TextRegion> output){
        if(!usable(candidate)||covered(candidate,source))return;boolean intersects=false;for(TextRegion original:source)if(intersection(original,candidate)>0&&(original.height()<=candidate.height()*2.5f||key(candidate.text).contains(key(original.text)))){intersects=true;break;}
        if(intersects&&!candidate.parts.isEmpty()){
            if(!candidate.joinParts){for(TextRegion part:candidate.parts)supplement(part,source,output);return;}
            ArrayList<TextRegion> word=new ArrayList<>();for(TextRegion part:candidate.parts){ArrayList<TextRegion> remaining=new ArrayList<>();supplement(part,source,remaining);if(remaining.isEmpty()){appendWord(word,output);word.clear();}else word.addAll(remaining);}appendWord(word,output);return;
        }
        unique(candidate,output);
    }
    private static void appendWord(List<TextRegion> symbols,List<TextRegion> output){
        if(symbols.isEmpty())return;StringBuilder text=new StringBuilder();int l=Integer.MAX_VALUE,t=l,r=Integer.MIN_VALUE,b=r;
        for(TextRegion s:symbols){text.append(s.text);l=Math.min(l,s.left);t=Math.min(t,s.top);r=Math.max(r,s.right);b=Math.max(b,s.bottom);}unique(new TextRegion(text.toString(),l,t,r,b),output);
    }
    private static void unique(TextRegion candidate,List<TextRegion> output){
        for(TextRegion prior:output)if(intersection(candidate,prior)>=Math.min(candidate.area(),prior.area())*.65&&similar(key(candidate.text),key(prior.text)))return;
        output.add(candidate);
    }
    static List<TextRegion> merge(List<TextRegion> source,List<TextRegion> ocr){
        List<TextRegion> exact=originals(source);ArrayList<TextRegion> supplement=new ArrayList<>();for(TextRegion line:ocr)supplement(line,exact,supplement);
        ArrayList<TextRegion> all=new ArrayList<>(exact);all.addAll(supplement);return all;
    }
    private static boolean sameRow(TextRegion a,TextRegion b){int overlap=Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top);return Math.max(a.height(),b.height())<=Math.min(a.height(),b.height())*2.5&&overlap>=Math.min(a.height(),b.height())*.5&&Math.abs((a.top+a.bottom)-(b.top+b.bottom))<=Math.max(a.height(),b.height());}
    static String text(List<TextRegion> regions){
        ArrayList<TextRegion> sorted=new ArrayList<>();for(TextRegion r:regions)if(usable(r))sorted.add(r);sorted.sort(Comparator.comparingInt((TextRegion r)->r.top).thenComparingInt(r->r.left));
        ArrayList<ArrayList<TextRegion>> rows=new ArrayList<>();
        for(TextRegion r:sorted){ArrayList<TextRegion> row=rows.isEmpty()?null:rows.get(rows.size()-1);if(row==null||!sameRow(row.get(0),r)){row=new ArrayList<>();rows.add(row);}row.add(r);}
        StringBuilder out=new StringBuilder();for(ArrayList<TextRegion> row:rows){row.sort(Comparator.comparingInt(r->r.left));if(out.length()>0)out.append('\n');TextRegion previous=null;for(TextRegion r:row){String word=r.text.trim();if(previous!=null){int gap=r.left-previous.right;int last=out.codePointBefore(out.length()),first=word.codePointAt(0);boolean latin=Character.UnicodeScript.of(last)==Character.UnicodeScript.LATIN&&Character.UnicodeScript.of(first)==Character.UnicodeScript.LATIN;if(gap>Math.min(previous.height(),r.height())*.28||latin&&gap>1)out.append(' ');}out.append(word);previous=r;}}
        return out.toString();
    }
    static List<TextRegion> inCrop(List<TextRegion> screen,int screenWidth,int screenHeight,int imageWidth,int imageHeight,int left,int top,int right,int bottom){
        if(screenWidth<=0||screenHeight<=0||imageWidth<=0||imageHeight<=0||right<=left||bottom<=top)return Collections.emptyList();ArrayList<TextRegion> out=new ArrayList<>();
        for(TextRegion r:screen){int l=Math.round(r.left*(float)imageWidth/screenWidth),t=Math.round(r.top*(float)imageHeight/screenHeight),rr=Math.round(r.right*(float)imageWidth/screenWidth),b=Math.round(r.bottom*(float)imageHeight/screenHeight);
            if(l<left||t<top||rr>right||b>bottom)continue;out.add(new TextRegion(r.text,l-left,t-top,rr-left,b-top));}
        return out;
    }
}
