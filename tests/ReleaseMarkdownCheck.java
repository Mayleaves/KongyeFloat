package com.kongye.floatball;

/** Plain-JVM fixtures; does not require Gradle, Android, network, or user data. */
public final class ReleaseMarkdownCheck {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static boolean has(ReleaseMarkdown.Document doc, ReleaseMarkdown.Kind kind, String text) {
        for (ReleaseMarkdown.Mark mark : doc.marks) if (mark.kind == kind && doc.text.substring(mark.start, mark.end).equals(text)) return true;
        return false;
    }
    public static void main(String[] args) {
        ReleaseMarkdown.Document doc = ReleaseMarkdown.parse("# 更新 3.2\n\n- **修复**密码\n- [x] 完成\n1. *说明*与 `code`\n\n[项目](https://github.com/Mayleaves/KongyeFloat)\n\n```java\n**原样** <script>\n```\n> 引用\n\n~~过时~~");
        check(has(doc, ReleaseMarkdown.Kind.HEADING, "更新 3.2"), "heading style");
        check(doc.text.contains("• 修复密码\n☑ 完成\n1. 说明与 code"), "bullet, task and ordered lists");
        check(has(doc, ReleaseMarkdown.Kind.BOLD, "修复"), "bold style");
        check(has(doc, ReleaseMarkdown.Kind.ITALIC, "说明"), "italic style");
        check(has(doc, ReleaseMarkdown.Kind.CODE, "code"), "inline code");
        check(has(doc, ReleaseMarkdown.Kind.LINK, "项目"), "clickable link label");
        check(has(doc, ReleaseMarkdown.Kind.CODE_BLOCK, "**原样** <script>\n"), "fenced code stays literal");
        check(has(doc, ReleaseMarkdown.Kind.QUOTE, "引用"), "quote style");
        check(has(doc, ReleaseMarkdown.Kind.STRIKE, "过时"), "strikethrough");
        ReleaseMarkdown.Document nested = ReleaseMarkdown.parse("*outer **inner** end* and **bold *italic* end**");
        check(nested.text.equals("outer inner end and bold italic end"), "nested emphasis text");
        check(has(nested, ReleaseMarkdown.Kind.BOLD, "inner") && has(nested, ReleaseMarkdown.Kind.ITALIC, "outer inner end"), "nested spans");
        check(ReleaseMarkdown.parse("snake_case_identifier").text.equals("snake_case_identifier"), "identifier underscores stay literal");
        check(ReleaseMarkdown.parse("\\*literal\\* &amp; &#x4F60;").text.equals("*literal* & 你"), "escapes and entities");
        ReleaseMarkdown.Document reference = ReleaseMarkdown.parse("[项目][home]\n\n[home]: https://example.org \"title\"");
        check(reference.text.equals("项目") && has(reference, ReleaseMarkdown.Kind.LINK, "项目"), "forward reference link");
        ReleaseMarkdown.Document unsafe = ReleaseMarkdown.parse("[点击](javascript:alert(1)) ![照片](https://example.org/a.png) <script>alert(1)</script>");
        check(unsafe.text.equals("点击 图片：照片 <script>alert(1)</script>"), "unsafe URL omitted; HTML inert; image label only");
        check(unsafe.marks.isEmpty(), "no unsafe or image click target");
        for (String address : new String[]{"javascript:alert(1)", "intent://app", "content://local/secret", "file:///secret", "data:text/html,x", "https://user@example.org", "https://example.org/\nattack", "https://example.org\\attack", "//example.org"}) check(ReleaseMarkdown.safeLink(address) == null, "reject unsafe link " + address);
        check(ReleaseMarkdown.safeLink("https://example.org/a?q=1#part") != null, "web link allowed");
        check(ReleaseMarkdown.parse(null).text.isEmpty(), "null body");
        StringBuilder huge = new StringBuilder(); for (int i = 0; i < ReleaseMarkdown.MAX_SOURCE + 1024; i++) huge.append('a');
        check(ReleaseMarkdown.parse(huge.toString()).text.contains("已截取显示"), "visible source bound");
        StringBuilder hostile = new StringBuilder(); for (int i = 0; i < 18000; i++) hostile.append("[a ");
        long start = System.nanoTime(); ReleaseMarkdown.Document bounded = ReleaseMarkdown.parse(hostile.toString());
        check(System.nanoTime() - start < 5_000_000_000L, "malformed input has bounded work");
        check(bounded.marks.size() <= ReleaseMarkdown.MAX_MARKS, "span count bound");
        for (ReleaseMarkdown.Mark mark : doc.marks) check(mark.start >= 0 && mark.end <= doc.text.length() && mark.end > mark.start, "valid span offsets");
        System.out.println(checks + " Markdown fixture checks passed.");
    }
}
