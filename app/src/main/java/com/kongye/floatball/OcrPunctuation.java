package com.kongye.floatball;
import java.util.*;

/** Conservative OCR-only typography cleanup. Exact accessible source is never modified. */
final class OcrPunctuation {
    private OcrPunctuation(){}
    static boolean han(int cp){return Character.UnicodeScript.of(cp)==Character.UnicodeScript.HAN;}
    static boolean containsHan(String text){return text.codePoints().anyMatch(OcrPunctuation::han);}
    private static boolean chineseBefore(String text,int index){
        for(int i=index-1;i>=0;i--){char c=text.charAt(i);if(Character.isWhitespace(c)||"”’）】》」』\"'".indexOf(c)>=0)continue;return han(c);}return false;
    }
    private static boolean chineseAfter(String text,int index){
        for(int i=index+1;i<text.length();i++){char c=text.charAt(i);if(Character.isWhitespace(c)||"“‘（【《「『\"'".indexOf(c)>=0)continue;return han(c);}return false;
    }
    static String normalize(String text){
        if(!containsHan(text)){
            // A pure Latin line should not receive Chinese typography from the model.
            return text.replace('，',',').replace('：',':').replace('；',';').replace('！','!').replace('？','?');
        }
        StringBuilder result=new StringBuilder(text);
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);boolean before=chineseBefore(text,i),after=chineseAfter(text,i);
            if(c=='：'&&i>0&&i+1<text.length()&&Character.isDigit(text.charAt(i-1))&&Character.isDigit(text.charAt(i+1))){result.setCharAt(i,':');continue;}
            if(before||after){switch(c){case ',' -> result.setCharAt(i,'，');case ':' -> result.setCharAt(i,'：');case ';' -> result.setCharAt(i,'；');case '?' -> result.setCharAt(i,'？');case '!' -> result.setCharAt(i,'！');default -> {}}}
            if(c=='.'&&before&&(i==0||text.charAt(i-1)!='.')&&(i+1==text.length()||text.charAt(i+1)!='.'))result.setCharAt(i,'。');
        }
        // Correct direction only when two existing quote marks enclose Chinese; never invent a missing mark.
        for(String pair:new String[]{"\"“”","'‘’"}){int opening=-1;for(int i=0;i<text.length();i++){if(pair.indexOf(text.charAt(i))<0)continue;if(opening<0)opening=i;else{if(containsHan(text.substring(opening+1,i))){result.setCharAt(opening,pair.charAt(1));result.setCharAt(i,pair.charAt(2));}opening=-1;}}}
        return result.toString();
    }
    static TextRegion normalize(TextRegion r){return withText(r,normalize(r.text));}
    private static TextRegion withText(TextRegion r,String text){
        ArrayList<TextRegion> parts=new ArrayList<>();int cursor=0;for(TextRegion part:r.parts){int at=r.text.indexOf(part.text,cursor);if(at>=0&&at+part.text.length()<=text.length()){parts.add(withText(part,text.substring(at,at+part.text.length())));cursor=at+part.text.length();}else parts.add(normalize(part));}
        return new TextRegion(text,r.left,r.top,r.right,r.bottom,parts,r.joinParts,r.confidence);
    }
}
