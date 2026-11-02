package com.kongye.floatball;

/** Restricted-language fallback when a very short string cannot be identified reliably. */
final class TranslationPolicy {
    private TranslationPolicy(){}
    static String scriptLanguage(String text){
        boolean han=false,latin=false;
        for(int i=0;i<text.length();){int cp=text.codePointAt(i);i+=Character.charCount(cp);Character.UnicodeScript script=Character.UnicodeScript.of(cp);
            if(script==Character.UnicodeScript.HIRAGANA||script==Character.UnicodeScript.KATAKANA)return "ja";
            if(script==Character.UnicodeScript.HANGUL)return "ko";
            if(script==Character.UnicodeScript.HAN)han=true;
            else if(script==Character.UnicodeScript.LATIN)latin=true;
            else if(Character.isLetter(cp))return "";
        }
        return han?"zh":latin?"en":"";
    }
}
