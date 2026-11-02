package com.kongye.floatball;

import java.util.*;

/** Immutable text and its rectangle in the same pixel coordinate system as its image. */
final class TextRegion {
    final String text;final int left,top,right,bottom;final List<TextRegion> parts;final boolean joinParts;final float confidence;
    TextRegion(String text,int left,int top,int right,int bottom){this(text,left,top,right,bottom,Collections.emptyList());}
    TextRegion(String text,int left,int top,int right,int bottom,List<TextRegion> parts){this(text,left,top,right,bottom,parts,false);}
    TextRegion(String text,int left,int top,int right,int bottom,List<TextRegion> parts,boolean joinParts){this(text,left,top,right,bottom,parts,joinParts,1);}
    TextRegion(String text,int left,int top,int right,int bottom,List<TextRegion> parts,boolean joinParts,float confidence){this.text=text;this.left=left;this.top=top;this.right=right;this.bottom=bottom;this.parts=Collections.unmodifiableList(new ArrayList<>(parts));this.joinParts=joinParts;this.confidence=confidence;}
    int width(){return Math.max(0,right-left);}int height(){return Math.max(0,bottom-top);}
    long area(){return (long)width()*height();}
}
