package com.kongye.floatball;
import java.net.URLEncoder;
import java.util.Locale;
/** Pure address/search routing; Bing is the default and Google is an explicit choice. */
final class BrowserSearch {
    static String home(String engine){return "google".equals(engine)?"https://www.google.com/":"https://www.bing.com/";}
    static String url(String input,String engine){String value=input.trim();if(value.isEmpty())return null;if(value.contains("://"))return value.toLowerCase(Locale.ROOT).startsWith("https://")?value:null;if(value.matches("[^\\s/:]+\\.[^\\s]+"))return "https://"+value;try{return home(engine)+"search?q="+URLEncoder.encode(value,"UTF-8").replace("+","%20");}catch(java.io.UnsupportedEncodingException impossible){throw new AssertionError(impossible);}}
}
