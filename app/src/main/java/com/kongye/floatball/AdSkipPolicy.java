package com.kongye.floatball;

import java.util.regex.Pattern;

/** Deliberately narrow launch-ad rule. Never treats arbitrary close/confirm text as an ad. */
final class AdSkipPolicy {
    static final long WINDOW_MS = 10000;
    private static final Pattern LABEL = Pattern.compile(
            "(?i)(?:\\d+\\s*[s秒]?\\s*)?(?:跳过(?:广告)?|skip(?:\\s+ad)?)(?:\\s*[(（]?\\s*\\d+\\s*[s秒]?\\s*[)）]?)?");
    static boolean label(String text) {
        return text != null && LABEL.matcher(text.trim()).matches();
    }
    static boolean evidence(String text){
        return label(text)&&(text.contains("广告")||text.toLowerCase(java.util.Locale.ROOT).contains("ad")||text.matches(".*[0-9].*"));
    }
    static boolean target(String text,String viewId,boolean clickable){
        if(!clickable||!label(text))return false;
        var digits=Pattern.compile("\\d+").matcher(text);while(digits.find())try{if(Integer.parseInt(digits.group())>15)return false;}catch(NumberFormatException error){return false;}
        String id=viewId==null?"":viewId.toLowerCase(java.util.Locale.ROOT);
        return evidence(text)||id.matches(".*(?:ad[_-]?skip|skip[_-]?ad|splash[_-]?skip|skip[_-]?splash).*" );
    }
    static boolean corner(int l, int t, int r, int b, int width, int height) {
        return width > 0 && height > 0 && l >= width * .6f && t >= 0 && b <= height * .23f
                && r <= width && r > l && b > t && r - l <= width * .32f && b - t <= height * .07f;
    }
    static boolean within(long now, long started) { return now >= started && now - started < WINDOW_MS; }
    static String identity(CharSequence text,CharSequence description,String id){
        // A countdown ticking from 5 to 4 does not replace the skip control.
        return (String.valueOf(text)+"|"+String.valueOf(description)+"|"+String.valueOf(id)).replaceAll("[0-9]+","#");
    }
}
