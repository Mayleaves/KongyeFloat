package com.kongye.floatball;

final class ApkArchitectureCheck {
    private static int checks;
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
    public static void main(String[] args){
        String[] arm={"arm64-v8a","armeabi-v7a"};
        check(ApkArchitecture.rank("KongyeFloat-1.30.9-arm64-v8a.apk",arm)>ApkArchitecture.rank("KongyeFloat-1.30.9-universal.apk",arm),"prefer exact architecture to universal");
        check(ApkArchitecture.rank("KongyeFloat-1.30.9-arm64-v8a.apk",arm)>ApkArchitecture.rank("KongyeFloat-1.30.9-armeabi-v7a.apk",arm),"prefer 64-bit to supported 32-bit");
        check(ApkArchitecture.rank("KongyeFloat-1.30.9-x86_64.apk",arm)<0,"never select emulator-only ABI for ARM");
        check(ApkArchitecture.rank("KongyeFloat-1.30.9-x86.apk",new String[]{"x86_64","x86"})>0,"x86 supported on emulator");
        check(ApkArchitecture.rank("KongyeFloat-1.30.9-arm64-v8a.apk",new String[]{"armeabi-v7a"})<0,"32-bit phone cannot use ARM64");
        check(ApkArchitecture.rank("KongyeFloat-1.30.9-universal.apk",arm)>ApkArchitecture.rank("KongyeFloat-1.30.9.apk",arm),"explicit universal fallback before historical unlabelled");
        check(ApkArchitecture.rank("KongyeFloat-1.30.6.apk",arm)>0,"historical universal downloads still available");
        check(ApkArchitecture.rank("KongyeFloat-1.30.9-debug.apk",arm)<0,"debug excluded");
        check(ApkArchitecture.rank("KongyeFloat-1.30.9.apk.sha256",arm)<0,"checksum is not an APK");
        System.out.println(checks+" architecture selection checks passed");
    }
}
