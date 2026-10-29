package com.kongye.floatball;

public class ReleaseVersionCheck {
    private static int checks;
    private static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    private static void invalid(String value){boolean failed=false;try{ReleaseVersion.compare(value,"1.0.0");}catch(IllegalArgumentException expected){failed=true;}check(failed,"invalid version: "+value);}
    public static void main(String[] args){check(ReleaseVersion.compare("v1.10.0","1.9.9")>0,"numeric comparison");check(ReleaseVersion.compare("1.15.0","1.16.0")<0,"older release");check(ReleaseVersion.compare("v1.16.0","1.16")==0,"missing zero component");check(ReleaseVersion.compare("V01.002.0","1.2")==0,"prefix and leading zeros");check(ReleaseVersion.compare("1.2.0.1","1.2")>0,"fourth component");check(ReleaseVersion.compare("99999999999999999999","2")>0,"large components");invalid(null);invalid("");invalid("1.2-beta");invalid("1.2.3.4.5");invalid("1..2");invalid("v1.2/evil");System.out.println(checks+" release version checks passed.");}
}
