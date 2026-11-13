package com.kongye.floatball;

import java.util.*;

/** 需求文档 v2 第 5 节：悬浮球的页面识别与空叶文档一致（S10 登录页带"自动注册"、注册、改密码、验证码、兜底账号）。 */
public final class PasswordPageCheck {
    private static int passed;
    private static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); passed++; }
    private static final int TEXT = PageRules.CLASS_TEXT, PHONE = PageRules.CLASS_PHONE;

    private static List<PageRules.Type> types(Object... fields) {
        ArrayList<PageRules.Type> raw = new ArrayList<>();
        for (int i = 0; i < fields.length; i += 3) raw.add(PageRules.classify((String) fields[i], (Boolean) fields[i + 1], (Integer) fields[i + 2]));
        return PageRules.classifyAll(raw);
    }
    private static List<PageRules.Text> texts(Object... t) {
        ArrayList<PageRules.Text> out = new ArrayList<>();
        for (int i = 0; i < t.length; i += 2) out.add(new PageRules.Text((String) t[i], (Boolean) t[i + 1]));
        return out;
    }

    public static void main(String[] args) {
        // S10：登录页上写着"未注册手机号验证通过后将自动注册"，仍是登录页
        List<PageRules.Type> login = types("请输入手机号", false, TEXT, "请输入密码", true, TEXT);
        check(login.equals(List.of(PageRules.Type.PHONE, PageRules.Type.PASSWORD)), "login fields " + login);
        check(PageRules.kindOf(login, texts("添加账号", false, "未注册手机号验证通过后将自动注册", false, "登录", true, "忘记密码", false)) == PageRules.Kind.LOGIN, "S10 stays login");

        List<PageRules.Type> register = types("用户名", false, TEXT, "设置密码", true, TEXT, "确认密码", true, TEXT);
        check(register.equals(List.of(PageRules.Type.USERNAME, PageRules.Type.NEW, PageRules.Type.CONFIRM)), "register fields " + register);
        check(PageRules.kindOf(register, texts("注册", true)) == PageRules.Kind.REGISTER, "register kind");

        List<PageRules.Type> unlabelled = types("邮箱", false, TEXT, "", true, TEXT, "", true, TEXT);
        check(unlabelled.equals(List.of(PageRules.Type.EMAIL, PageRules.Type.NEW, PageRules.Type.CONFIRM)), "two plain password fields " + unlabelled);

        List<PageRules.Type> change = types("原密码", true, TEXT, "新密码", true, TEXT, "确认新密码", true, TEXT);
        check(change.equals(List.of(PageRules.Type.CURRENT, PageRules.Type.NEW, PageRules.Type.CONFIRM)), "change fields " + change);
        check(PageRules.kindOf(change, texts()) == PageRules.Kind.CHANGE, "change kind");

        check(PageRules.classify("短信验证码", true, TEXT) == PageRules.Type.OTP, "otp even when masked");
        check(PageRules.classify("再次输入手机号", false, TEXT) == PageRules.Type.PHONE, "confirm only for password fields");
        check(PageRules.classify("", false, PHONE) == PageRules.Type.PHONE, "phone input class");
        check(PageRules.classify("", false, TEXT | PageRules.VARIATION_EMAIL) == PageRules.Type.EMAIL, "email input type");
        check(types("", false, TEXT, "", true, TEXT).equals(List.of(PageRules.Type.USERNAME, PageRules.Type.PASSWORD)), "fallback account field");
        // "密码" + "确认密码"：前一个是新密码
        check(types("手机号", false, PHONE, "密码", true, TEXT, "确认密码", true, TEXT).get(1) == PageRules.Type.NEW, "password before confirm is new");
        System.out.println("Password page checks passed: " + passed);
    }
}
