package com.kongye.floatball;
public class AdSkipCheck {
    public static void main(String[] args){
        for(String s:new String[]{"跳过","跳过广告","跳过 5 秒","5s 跳过","Skip ad","SKIP (3)","跳过（3秒）"})check(AdSkipPolicy.label(s),s);
        for(String s:new String[]{"跳过登录","不跳过","关闭","立即下载","Skip tutorial","Skip purchase",null,"跳过广告并领取奖励"})check(!AdSkipPolicy.label(s),s);
        check(AdSkipPolicy.corner(850,60,1020,120,1080,2400),"corner");
        check(!AdSkipPolicy.corner(0,60,300,100,1080,2400),"left");
        check(!AdSkipPolicy.corner(850,1600,1020,1700,1080,2400),"bottom");
        check(!AdSkipPolicy.corner(540,0,1080,800,1080,2400),"oversized ancestor");
        check(AdSkipPolicy.within(9999,0)&&!AdSkipPolicy.within(10000,0)&&!AdSkipPolicy.within(0,1),"bounded window");
        check(!AdSkipPolicy.evidence("跳过")&&!AdSkipPolicy.evidence("Skip"),"bare skip needs ad context");
        check(AdSkipPolicy.evidence("跳过广告")&&AdSkipPolicy.evidence("跳过 5 秒")&&AdSkipPolicy.evidence("Skip ad"),"explicit ad/countdown");
        check(!AdSkipPolicy.target("跳过 5 秒","skip",false),"never click non-clickable child through parent");
        check(!AdSkipPolicy.target("跳过","login_skip",true),"bare skip is insufficient");
        check(AdSkipPolicy.target("跳过","tt_splash_skip_btn",true),"explicit ad skip id");
        check(AdSkipPolicy.target("跳过 5 秒",null,true),"independent countdown button");
        check(!AdSkipPolicy.target("跳过 99 秒",null,true),"non-ad timer");
        check(!AdSkipPolicy.corner(600,50,1040,180,1080,2400),"large clickable banner");
        check(AdSkipPolicy.identity("跳过 5 秒",null,"ad_skip").equals(AdSkipPolicy.identity("跳过 4 秒",null,"ad_skip")),"countdown remains same control");
        check(!AdSkipPolicy.identity("跳过 5 秒",null,"ad_skip").equals(AdSkipPolicy.identity("查看详情",null,"ad_skip")),"changed action is a different control");
        System.out.println("Ad skip positive/negative/time/geometry/countdown checks passed");
    }
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
}
