package com.kongye.floatball;

/** The user-selected page may be in a non-default browser or an image-viewer activity. */
final class ComicTargetPolicy {
    static boolean eligible(String pkg,String own,String launcher){
        return pkg!=null&&!pkg.isBlank()&&!pkg.equals(own)&&!pkg.equals(launcher)
                &&!pkg.equals("android")&&!pkg.equals("com.android.systemui")
                &&!pkg.equals("com.android.settings")&&!pkg.contains("permissioncontroller")
                &&!pkg.contains("packageinstaller");
    }
}
