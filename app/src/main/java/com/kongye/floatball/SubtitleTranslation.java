package com.kongye.floatball;

import java.text.BreakIterator;
import java.util.*;
import java.util.regex.Pattern;

/** Keep visual paragraphs intact while sending bounded, complete sentences to offline MT. */
final class SubtitleTranslation {
    static final int MAX_PIECE = 160;
    private static final Pattern I_TOKEN = Pattern.compile("(?<![A-Za-z0-9_'’])I(?![A-Za-z0-9_'’])");
    private static final Pattern LITERAL_I = Pattern.compile("(?i)(?:press|tap|key|button|type|chapter|part|volume|world war|class|grade|rank|level|category|group|stage|letter)\\s+I\\b|[\"“‘]I[\"”’]");
    private static final Pattern MEAN_ASIDE = Pattern.compile("(?:^|[.!?]\\s+)I mean[,:]?\\s+(?:[A-Z]|you\\b|he\\b|she\\b|it\\b|we\\b|they\\b|this\\b|that\\b)");
    record Plan(List<String> pieces, String opening, String closing, String sourceLanguage) {
        String join(List<String> results, String target) {
            if(results.size()!=pieces.size())throw new IllegalArgumentException("Incomplete subtitle translation");
            StringBuilder text=new StringBuilder(opening);
            for(int i=0;i<results.size();i++) {
                String result=repair(pieces.get(i),results.get(i),sourceLanguage,target);
                if(result.isBlank())throw new IllegalArgumentException("Empty subtitle translation");
                if("en".equals(sourceLanguage)&&"zh".equals(target)) {
                    String piece=pieces.get(i);
                    char end=piece.charAt(piece.length()-1),last=result.charAt(result.length()-1);
                    if(".!?".indexOf(end)>=0&&(Character.isLetterOrDigit(last)||Character.UnicodeScript.of(last)==Character.UnicodeScript.HAN))
                        result+=end=='?'?"？":end=='!'?"！":"。";
                }
                if(i>0&&!"zh".equals(target)&&!"ja".equals(target))text.append(' ');
                text.append(result);
            }
            text.append(closing);
            return OcrPunctuation.formatTranslatedSubtitle(text.toString(),target);
        }
    }
    static Plan plan(String original,String language) {
        String text=original.replaceAll("\\s+"," ").strip();
        if(!"en".equals(language))return new Plan(List.of(text),"","",language);
        text=text.replaceAll("(?<![A-Za-z0-9_])(?:l|\\|)(?= (?:am|was|have|had|do|did|can|could|will|would|should|must|mean|think|know|need|want|feel|hope|wish|guess|see|thought|said|went|came)\\b)","I");
        // Do not send unmatched thought-paragraph brackets to separate model requests.
        String opening="",closing="";
        if(outerParentheses(text)) {opening="(";closing=")";text=text.substring(1,text.length()-1).strip();}
        ArrayList<String> pieces=new ArrayList<>();
        BreakIterator sentences=BreakIterator.getSentenceInstance(Locale.ENGLISH);
        sentences.setText(text);
        int start=sentences.first();
        for(int end=sentences.next();end!=BreakIterator.DONE;start=end,end=sentences.next()) {
            splitBounded(text.substring(start,end).strip(),pieces);
        }
        if(pieces.isEmpty())pieces.add(text);
        return new Plan(List.copyOf(pieces),opening,closing,language);
    }
    private static void splitBounded(String text,List<String> pieces) {
        while(text.length()>MAX_PIECE) {
            int cut=-1;
            for(int i=MAX_PIECE-1;i>=MAX_PIECE/2;i--)if(",;:".indexOf(text.charAt(i))>=0&&i+1<text.length()&&text.charAt(i+1)==' '){cut=i+1;break;}
            if(cut<0)cut=text.lastIndexOf(' ',MAX_PIECE);
            if(cut<MAX_PIECE/3)cut=MAX_PIECE;
            if(Character.isHighSurrogate(text.charAt(cut-1)))cut--;
            pieces.add(text.substring(0,cut).strip());text=text.substring(cut).strip();
        }
        if(!text.isBlank())pieces.add(text);
    }
    private static boolean outerParentheses(String text) {
        if(!text.startsWith("(")||!text.endsWith(")"))return false;
        int depth=0;
        for(int i=0;i<text.length();i++){if(text.charAt(i)=='(')depth++;if(text.charAt(i)==')')depth--;if(depth==0&&i<text.length()-1)return false;}
        return depth==0;
    }
    static String repair(String original,String translated,String source,String target) {
        String result=OcrPunctuation.formatTranslatedSubtitle(translated,target);
        if(!"en".equals(source)||!"zh".equals(target)||!OcrPunctuation.containsHan(result)
                ||!I_TOKEN.matcher(original).find()||LITERAL_I.matcher(original).find())return result;
        if(MEAN_ASIDE.matcher(original).find())result=result.replaceAll("(?<![A-Za-z0-9_])I\\h*(?:意味着|意味著|的意思是|是说)","我的意思是");
        var matcher=I_TOKEN.matcher(result);
        StringBuilder fixed=new StringBuilder();int cursor=0;
        while(matcher.find()) {
            int at=matcher.start(),end=matcher.end();
            char before=at==0?0:result.charAt(at-1),after=end==result.length()?0:result.charAt(end);
            boolean quoted="\"“‘".indexOf(before)>=0||"\"”’".indexOf(after)>=0;
            boolean roman="型级类期章号".indexOf(after)>=0;
            fixed.append(result,cursor,at).append(quoted||roman?"I":"我");cursor=end;
        }
        return OcrPunctuation.formatTranslatedSubtitle(fixed.append(result,cursor,result.length()).toString(),target);
    }
}
