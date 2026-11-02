package com.kongye.floatball;

import java.util.Locale;

/** Release asset naming policy; a build for another CPU must never be selected. */
final class ApkArchitecture {
    private static final String[] KNOWN={"arm64-v8a","armeabi-v7a","x86_64","x86"};
    private ApkArchitecture(){}
    static int rank(String name,String[] supported){
        String lower=name.toLowerCase(Locale.ROOT);
        if(!lower.endsWith(".apk")||lower.contains("debug"))return -1;
        for(String abi:KNOWN)if(lower.endsWith("-"+abi+".apk")){
            for(int i=0;i<supported.length;i++)if(abi.equals(supported[i]))return 1000-i;
            return -1;
        }
        // Explicit universal is preferred to legacy unlabelled assets.
        if(lower.endsWith("-universal.apk"))return 20;
        return 10;
    }
}
