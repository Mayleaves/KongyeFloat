package com.kongye.floatball;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 页面识别规则（需求文档 v2 第 5 节），与空叶文档的自动填充服务一致：字段分类、整页修正、登录 / 注册 / 改密码打分。
 * 纯 Java，不依赖 Android，tests/PasswordPageCheck.java 直接检查。
 */
final class PageRules {
    private PageRules(){}
    // android.text.InputType 的常量（编译期常量，抄过来以免依赖 Android）
    static final int MASK_CLASS=0x0f,MASK_VARIATION=0xff0,CLASS_TEXT=1,CLASS_NUMBER=2,CLASS_PHONE=3;
    static final int VARIATION_EMAIL=0x20,VARIATION_WEB_EMAIL=0xd0;

    enum Type{USERNAME,PHONE,EMAIL,PASSWORD,NEW,CONFIRM,CURRENT,OTP,OTHER;
        boolean account(){return this==USERNAME||this==PHONE||this==EMAIL;}
        boolean secret(){return this==PASSWORD||this==NEW||this==CONFIRM||this==CURRENT;}
        String key(){return switch(this){case PHONE->"phone";case EMAIL->"email";default->"username";};}
    }
    enum Kind{LOGIN,REGISTER,CHANGE}
    record Text(String text,boolean button){}

    private static Pattern re(String p){return Pattern.compile(p,Pattern.CASE_INSENSITIVE);}
    private static final Pattern OTP=re("验证码|校验码|动态码|动态密码|短信码|captcha|\\botp\\b|verif(?:y|ication)?\\s*code|one.?time");
    private static final Pattern CONFIRM=re("确认|再次|重复|再输|confirm|repeat|again|re-?enter|retype");
    private static final Pattern NEW=re("新密码|设置.{0,4}密码|设定密码|创建密码|new\\s*-?password|create\\s*-?password|choose\\s+a\\s+password|set\\s*-?password");
    private static final Pattern CURRENT=re("原密码|旧密码|当前密码|current\\s*-?password|old\\s*-?password");
    private static final Pattern PHONE=re("手机|电话|mobile|phone|\\btel\\b");
    private static final Pattern EMAIL=re("邮箱|e-?mail|\\bmail\\b");
    private static final Pattern USER=re("账号|帐号|账户|帐户|用户名|登录名|会员名|user\\s*-?name|\\buser\\b|account|login\\s*-?id|\\blogin\\b");

    /** 第 5.1 节：先命中的算数 */
    static Type classify(String label,boolean password,int inputType){
        int cls=inputType&MASK_CLASS,variation=inputType&MASK_VARIATION;
        if(OTP.matcher(label).find())return Type.OTP;
        if(password&&CONFIRM.matcher(label).find())return Type.CONFIRM;
        if(password&&CURRENT.matcher(label).find())return Type.CURRENT;
        if(password&&NEW.matcher(label).find())return Type.NEW;
        if(password)return Type.PASSWORD;
        if(cls==CLASS_PHONE||PHONE.matcher(label).find())return Type.PHONE;
        if(cls==CLASS_TEXT&&(variation==VARIATION_EMAIL||variation==VARIATION_WEB_EMAIL)||EMAIL.matcher(label).find())return Type.EMAIL;
        if(USER.matcher(label).find())return Type.USERNAME;
        return Type.OTHER;
    }

    /** 整页修正：没标的连续密码框、"密码 + 确认密码"、没有明确账号框时的兜底 */
    static List<Type> classifyAll(List<Type> in){
        ArrayList<Type> t=new ArrayList<>(in);
        ArrayList<Integer> plain=new ArrayList<>();
        int labelled=0;
        for(int i=0;i<t.size();i++){if(t.get(i)==Type.PASSWORD)plain.add(i);if(t.get(i)==Type.NEW||t.get(i)==Type.CONFIRM||t.get(i)==Type.CURRENT)labelled++;}
        if(labelled==0){
            if(plain.size()==2){t.set(plain.get(0),Type.NEW);t.set(plain.get(1),Type.CONFIRM);}
            else if(plain.size()==3){t.set(plain.get(0),Type.CURRENT);t.set(plain.get(1),Type.NEW);t.set(plain.get(2),Type.CONFIRM);}
        }else if(plain.size()==1&&t.contains(Type.CONFIRM)&&!t.contains(Type.NEW))t.set(plain.get(0),Type.NEW);
        else if(plain.size()==1&&t.contains(Type.NEW)&&!t.contains(Type.CURRENT)&&plain.get(0)<t.indexOf(Type.NEW))t.set(plain.get(0),Type.CURRENT);
        boolean hasAccount=false;for(Type x:t)if(x.account())hasAccount=true;
        if(!hasAccount){
            int firstSecret=-1;for(int i=0;i<t.size();i++)if(t.get(i).secret()){firstSecret=i;break;}
            for(int i=firstSecret-1;i>=0;i--)if(t.get(i)==Type.OTHER){t.set(i,Type.USERNAME);break;}
        }
        return t;
    }

    private static final Pattern SUBMIT_REGISTER=re("^(?:立即|免费|快速)?注册(?:账号|帐号)?$|创建(?:账号|帐号|账户)|完成注册|^sign\\s*up$|^register$|create\\s+(?:an\\s+)?account|^join$");
    private static final Pattern TITLE_REGISTER=re("注册|创建(?:账号|帐号|账户)|新用户|sign\\s*up|register|join\\s+now");
    private static final Pattern FORGOT=re("忘记密码|找回密码|forgot\\s*(?:your\\s+)?password");
    private static final Pattern SUBMIT_LOGIN=re("^登\\s*录$|^log\\s*in$|^sign\\s*in$|^登录/注册$");
    private static final Pattern CHANGE_TITLE=re("修改密码|更改密码|重置密码|设置新密码|change\\s+password|reset\\s+password");

    /** 第 5.2 节的打分：字段信号为主，页面文字只算弱信号 */
    static int registerScore(List<Type> t,List<Text> texts){
        int s=0,secrets=0;for(Type x:t)if(x.secret())secrets++;
        if(t.contains(Type.NEW))s+=3;
        if(t.contains(Type.CONFIRM))s+=3;
        if(secrets>=2)s+=2;
        for(Text x:texts)if(x.button()&&SUBMIT_REGISTER.matcher(x.text().trim()).find()){s+=2;break;}
        for(Text x:texts)if(!x.button()&&x.text().length()<=30&&TITLE_REGISTER.matcher(x.text()).find()){s+=1;break;}
        if(secrets==1)for(Text x:texts)if(FORGOT.matcher(x.text()).find()){s-=2;break;}
        for(Text x:texts)if(x.button()&&SUBMIT_LOGIN.matcher(x.text().trim()).find()){s-=2;break;}
        return s;
    }
    static Kind kindOf(List<Type> t,List<Text> texts){
        int secrets=0;for(Type x:t)if(x.secret())secrets++;
        boolean titled=false;for(Text x:texts)if(x.text().length()<=30&&CHANGE_TITLE.matcher(x.text()).find())titled=true;
        if(t.contains(Type.CURRENT)&&(t.contains(Type.NEW)||t.contains(Type.CONFIRM))||titled&&secrets>=2)return Kind.CHANGE;
        return registerScore(t,texts)>=3?Kind.REGISTER:Kind.LOGIN;
    }
}
