package com.kongye.floatball;
import java.util.*;
/** Sorted disjoint character ranges; adding merges, subtracting can split a range. */
public final class MultiTextSelection {
    private final String text;private final ArrayList<int[]> ranges=new ArrayList<>();
    public MultiTextSelection(String text){this.text=text;}
    public List<int[]> ranges(){ArrayList<int[]> copy=new ArrayList<>();for(int[] r:ranges)copy.add(r.clone());return copy;}
    public void restore(List<int[]> source){ranges.clear();for(int[] r:source)ranges.add(r.clone());}
    public void clear(){ranges.clear();}
    public boolean contains(int at){for(int[] r:ranges)if(at>=r[0]&&at<r[1])return true;return false;}
    public void drag(int from,int to,boolean subtract){if(subtract)remove(from,to);else add(from,to);}
    public void add(int a,int b){int[] r=TextSelectionRange.normalize(text,a,b);if(r[0]==r[1])return;ranges.add(r);ranges.sort(Comparator.comparingInt(v->v[0]));ArrayList<int[]> merged=new ArrayList<>();for(int[] v:ranges){if(merged.isEmpty()||merged.get(merged.size()-1)[1]<v[0])merged.add(v.clone());else merged.get(merged.size()-1)[1]=Math.max(merged.get(merged.size()-1)[1],v[1]);}restore(merged);}
    public void remove(int a,int b){int[] remove=TextSelectionRange.normalize(text,a,b);ArrayList<int[]> next=new ArrayList<>();for(int[] r:ranges){if(r[1]<=remove[0]||r[0]>=remove[1])next.add(r.clone());else{if(r[0]<remove[0])next.add(new int[]{r[0],remove[0]});if(r[1]>remove[1])next.add(new int[]{remove[1],r[1]});}}restore(next);}
    public void toggleWord(int at){int[] word=TextSelectionRange.word(text,at);boolean overlaps=false;for(int[] r:ranges)if(r[0]<word[1]&&r[1]>word[0])overlaps=true;if(overlaps)remove(word[0],word[1]);else add(word[0],word[1]);}
    public String selectedText(){if(ranges.isEmpty())return text;StringBuilder out=new StringBuilder();for(int[] r:ranges){if(out.length()>0)out.append('\n');out.append(text,r[0],r[1]);}return out.toString();}
    public int count(){return ranges.size();}
}
