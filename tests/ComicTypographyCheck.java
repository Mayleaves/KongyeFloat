package com.kongye.floatball;
public class ComicTypographyCheck {
    public static void main(String[] args){
        int[] text=ComicTypography.vertical("你 好。\n（嗯？）😀");
        // Emoji occupies one cell and punctuation uses its upright vertical form.
        String actual=new String(text,0,text.length);
        if(text.length!=8||!actual.equals("你好︒︵嗯︖︶😀"))throw new AssertionError(actual);
        if(!ComicTypography.fits(10,24,120,10)||ComicTypography.fits(100,24,120,10))throw new AssertionError("fit");
        if(ComicTypography.vertical(" \n\t").length!=0)throw new AssertionError("whitespace");
        if(ComicTypography.fits(1,9,100,10)||ComicTypography.fits(1,100,11,10))throw new AssertionError("glyph must fit");
        for(float height:new float[]{12,23,47,100,219}){
            int rows=ComicTypography.rows(height,10,12);
            if(rows>0&&12+(rows-1)*ComicTypography.characterStep(10)>height+.001f)throw new AssertionError("last glyph clipped");
        }
        System.out.println("Comic vertical punctuation, Unicode and bounds checks passed");
    }
}
