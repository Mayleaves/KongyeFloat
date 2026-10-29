package com.kongye.floatball;

/** Compare numeric release versions without integer overflow. */
final class ReleaseVersion {
    private static String[] parts(String value){if(value==null||!value.matches("[vV]?[0-9]+(\\.[0-9]+){0,3}"))throw new IllegalArgumentException("版本格式不受支持");return value.replaceFirst("^[vV]","").split("\\.");}
    private static String normal(String value){String result=value.replaceFirst("^0+(?!$)","");return result.isEmpty()?"0":result;}
    static int compare(String first,String second){String[] a=parts(first),b=parts(second);for(int i=0;i<Math.max(a.length,b.length);i++){String x=normal(i<a.length?a[i]:"0"),y=normal(i<b.length?b[i]:"0");if(x.length()!=y.length())return Integer.compare(x.length(),y.length());int result=x.compareTo(y);if(result!=0)return Integer.signum(result);}return 0;}
}
