package com.kongye.floatball;

import java.util.*;

public final class SubtitleTranslationCheck {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args) {
        String source="(But who knows how long this situation will last? I don't see the Captain getting along with these Barbarians if they meet. I need to find a way to escape, where I don't get hunted down by that Gold bitch.)";
        var plan=SubtitleTranslation.plan(source,"en");
        check(plan.pieces().size()==3,"all three sentences are requested separately");
        check(String.join(" ",plan.pieces()).equals(source.substring(1,source.length()-1)),"every source word including the final sentence survives");
        check(plan.pieces().stream().noneMatch(p->p.startsWith("(")||p.endsWith(")")),"outer thought brackets stay out of partial model inputs");
        String joined=plan.join(List.of("但谁知道这种情况会持续多久？","我不觉得队长能与他们相处。","我得想办法逃走。"),"zh");
        check(joined.equals("（但谁知道这种情况会持续多久？我不觉得队长能与他们相处。我得想办法逃走。）"),"sentences rejoin as one original thought paragraph");
        String longSentence="I need "+"to keep moving and avoid the guards ".repeat(15)+"until I get home.";
        var bounded=SubtitleTranslation.plan(longSentence,"en");
        check(bounded.pieces().stream().allMatch(p->p.length()<=SubtitleTranslation.MAX_PIECE),"every model request is bounded");
        check(String.join(" ",bounded.pieces()).equals(longSentence),"long clauses lose no tail text");
        check(SubtitleTranslation.repair("I think I should leave.","I想I应该离开。","en","zh").equals("我想我应该离开。"),"every untranslated pronoun in Chinese is repaired");
        check(SubtitleTranslation.repair("Violet and I should leave.","紫罗兰和I应该离开。","en","zh").equals("紫罗兰和我应该离开。"),"pronouns inside a sentence are repaired too");
        check(SubtitleTranslation.repair("I mean Violet knew the way.","I 意味着紫罗兰知道路。","en","zh").equals("我的意思是紫罗兰知道路。"),"I mean is handled as a discourse aside using the source");
        check(SubtitleTranslation.repair("I saw him.","I看见他了。","en","zh").equals("我看见他了。"),"the fix does not depend on the screenshot phrase");
        check(SubtitleTranslation.repair("I think I should go.","I 想 I 应该走。","en","zh").equals("我想我应该走。"),"repaired pronouns do not leave Chinese word gaps");
        check(SubtitleTranslation.repair("Press I to open the menu.","按I打开菜单。","en","zh").equals("按I打开菜单。"),"key names stay literal");
        check(SubtitleTranslation.repair("Chapter I begins here.","第I章从这里开始。","en","zh").equals("第I章从这里开始。"),"roman chapter numbers stay literal");
        check(SubtitleTranslation.repair("I have type I diabetes.","I患有I型糖尿病。","en","zh").equals("I患有I型糖尿病。"),"mixed literal I is left unchanged when alignment is ambiguous");
        check(SubtitleTranslation.repair("I wrote the letter.","我写了字母“I”。","en","zh").equals("我写了字母“I”。"),"quoted letters are preserved");
        check(SubtitleTranslation.repair("Violet left.","Violet离开了。","en","zh").equals("Violet离开了。"),"English names are preserved");
        check(SubtitleTranslation.repair("I saw him.","I saw him.","en","en").equals("I saw him."),"non-Chinese targets are untouched");
        check(SubtitleTranslation.plan("l think I should leave.","en").pieces().get(0).startsWith("I think"),"common OCR lowercase-l confusion is normalized in a pronoun context");
        boolean failed=false;try{plan.join(List.of("只有第一句"),"zh");}catch(IllegalArgumentException expected){failed=true;}
        check(failed,"a partial result is never accepted as a complete paragraph");
        System.out.println(checks+" subtitle translation checks passed.");
    }
}
