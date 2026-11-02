package com.kongye.floatball;
public final class TranslationPolicyCheck {
 public static void main(String[] args){
  String[][] cases={{"sad","en"},{"Hello, world.","en"},{"吴玫","zh"},{"你好，世界！","zh"},{"日本語の文章","ja"},{"안녕하세요","ko"},{"123 😀",""},{"Привет",""},{"مرحبا",""}};
  for(String[] test:cases)if(!TranslationPolicy.scriptLanguage(test[0]).equals(test[1]))throw new AssertionError(test[0]);
  System.out.println(cases.length+" supported-language fallback checks passed");
 }
}
