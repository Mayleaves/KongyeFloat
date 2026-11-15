package com.kongye.floatball;

/** Plain-JVM checks for release headline cleanup, note previews and tag URLs. */
public final class ReleaseSummaryCheck {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }

    public static void main(String[] args) {
        ReleaseHistory.Release titled = new ReleaseHistory.Release(
                "v3.1.1",
                "空叶悬浮球 3.1.1 · 取消通知授权，截图保存不等识别",
                "2026-11-13T00:00:00Z",
                "本版修复通知授权提示和截图保存等待文字识别的问题。\n\n## 更新内容\n\n- 不再申请通知权限\n- 图片保存独立执行"
        );
        check("取消通知授权，截图保存不等识别".equals(ReleaseSummary.headline(titled)), "strips redundant app+version prefix");
        check(ReleaseSummary.excerpt(titled, "").startsWith("本版修复通知授权提示"), "extracts opening summary");
        check(!ReleaseSummary.excerpt(titled, "").contains("更新内容"), "skips generic section heading");

        ReleaseHistory.Release bare = new ReleaseHistory.Release(
                "v3.0",
                "空叶悬浮球 3.0",
                "2026-11-10T00:00:00Z",
                "# 空叶悬浮球 3.0\n\n## 更新内容\n\n- 新增「填充密码」连接空叶文档\n- 横向拉出悬浮球立即跟手\n\nSHA-256：`abcd1234`"
        );
        check("空叶悬浮球 3.0".equals(ReleaseSummary.headline(bare)), "preserves version title when no subtitle");
        check(!ReleaseSummary.detailNotes(bare).startsWith("# 空叶悬浮球 3.0"), "strips duplicate leading H1 in detail view");
        String bareExcerpt = ReleaseSummary.excerpt(bare, "");
        check(bareExcerpt.contains("新增「填充密码」") && bareExcerpt.contains(" · 横向拉出"), "joins bullet items cleanly");
        check(!bareExcerpt.contains("SHA-256"), "omits checksum boilerplate in default excerpt");
        check(ReleaseSummary.excerpt(bare, "abcd1234").contains("abcd1234"), "surfaces checksum line when explicitly searched");

        ReleaseHistory.Release h1Subtitle = new ReleaseHistory.Release(
                "v3.2.0",
                "KongyeFloat 3.2.0",
                "2026-11-14T00:00:00Z",
                "# 3.2.0 密码填充引导与 Markdown 更新历史\n\n新增设置内更新历史页面。"
        );
        check("密码填充引导与 Markdown 更新历史".equals(ReleaseSummary.headline(h1Subtitle)), "promotes descriptive H1 subtitle");
        check("新增设置内更新历史页面。".equals(ReleaseSummary.detailNotes(h1Subtitle).trim()), "strips promoted H1 from detail body");

        String longNote = "这是一段非常长的前置说明文字用于测试搜索关键词截断窗口展示效果，在中间位置出现了字幕翻译悬浮窗锚点优化细节说明。";
        ReleaseHistory.Release deepMatch = new ReleaseHistory.Release("v2.0", "空叶悬浮球 2.0", "", longNote);
        String windowed = ReleaseSummary.excerpt(deepMatch, "字幕翻译");
        check(windowed.startsWith("…") && windowed.contains("字幕翻译"), "windows long line around search match");

        check("https://github.com/Mayleaves/KongyeFloat/releases/tag/v3.3.4".equals(ReleaseSummary.releaseUrl("v3.3.4")), "valid release tag URL");
        check(ReleaseSummary.releaseUrl("../evil") == null && ReleaseSummary.releaseUrl("v1/2") == null && ReleaseSummary.releaseUrl("") == null, "rejects path traversal in tag URL");

        ReleaseHistory.Release siblingApp = new ReleaseHistory.Release("v3.1.0", "空叶文档密码空间联动", "", "空叶悬浮球支持按需读取密码建议。");
        check("空叶文档密码空间联动".equals(ReleaseSummary.headline(siblingApp)), "does not eat 空叶 from 空叶文档");
        check("空叶悬浮球支持按需读取密码建议。".equals(ReleaseSummary.excerpt(siblingApp, "")), "keeps prose sentence starting with app name");

        System.out.println(checks + " release summary checks passed.");
    }
}
