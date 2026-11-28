package com.kongye.floatball;

final class ComicTypography {
    static int[] vertical(String text){return text.codePoints().filter(c->!Character.isWhitespace(c)).map(c->switch(c){
        case '。'->'︒';case '、'->'︑';case '！','!'->'︕';case '？','?'->'︖';
        case '（','('->'︵';case '）',')'->'︶';case '「'->'﹁';case '」'->'﹂';
        case '『'->'﹃';case '』'->'﹄';case '…'->'︙';case '—','ー'->'︱';default->c;
    }).toArray();}
    static boolean fits(int count,float width,float height,float font){
        return fits(count,width,height,font,font*1.2f);
    }
    static float characterStep(float font){return font*1.10f;}
    static float columnStep(float font){return font*1.38f;}
    static int rows(float height,float font,float glyphHeight){return height<glyphHeight?0:1+(int)((height-glyphHeight)/characterStep(font));}
    static int columns(float width,float font){return width<font?0:1+(int)((width-font)/columnStep(font));}
    static boolean fits(int count,float width,float height,float font,float glyphHeight){
        return font>0&&count<=(long)rows(height,font,glyphHeight)*columns(width,font);
    }
}
