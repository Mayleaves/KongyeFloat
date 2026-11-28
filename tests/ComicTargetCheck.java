package com.kongye.floatball;
public class ComicTargetCheck {
 public static void main(String[] args){
  for(String pkg:new String[]{"mark.via","com.android.chrome","com.example.nondefaultbrowser","com.example.browser.imageviewer"})
   if(!ComicTargetPolicy.eligible(pkg,"com.kongye.floatball","com.miui.home"))throw new AssertionError(pkg);
  for(String pkg:new String[]{null,"","com.kongye.floatball","com.miui.home","com.android.systemui","com.android.settings","com.android.permissioncontroller","com.google.android.packageinstaller"})
   if(ComicTargetPolicy.eligible(pkg,"com.kongye.floatball","com.miui.home"))throw new AssertionError(pkg);
  System.out.println("Comic current-page target checks passed");
 }
}
