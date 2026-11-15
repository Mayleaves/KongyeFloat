package com.kongye.floatball;

import android.content.Context;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Generated release/cache fixtures only. Call run(context) on the instrumentation thread. */
public final class ReleaseNotesChecks {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static int run(Context context) throws Exception {
        int passed = 0;
        String next = "<" + ReleaseHistory.API + "?per_page=10&page=2>; rel=\"next\"";
        JSONArray array = new JSONArray()
            .put(new JSONObject().put("tag_name", "v3.2.0").put("name", "正式版本").put("published_at", "2026-11-14T00:00:00Z").put("body", "# 更新\n\n- **修复**\n\n[链接](https://example.org)"))
            .put(new JSONObject().put("tag_name", "v9").put("draft", true))
            .put(new JSONObject().put("tag_name", "v4-beta").put("prerelease", true))
            .put(new JSONObject().put("tag_name", "v3.1.0").put("name", JSONObject.NULL).put("body", JSONObject.NULL))
            .put(new JSONObject().put("tag_name", ""));
        ReleaseHistory.Page page = ReleaseHistory.parse(array.toString(), 1, next, 0);
        check(page.releases.size() == 2, "draft/prerelease/invalid tag filtering"); passed++;
        check(page.releases.get(0).tag.equals("v3.2.0") && page.releases.get(0).title.equals("正式版本"), "version/title preserved"); passed++;
        check(page.releases.get(0).date().matches("2026-11-1[34]"), "published date formatting"); passed++;
        check(page.releases.get(1).title.equals("v3.1.0") && page.releases.get(1).notes.isEmpty(), "null title/body fallback"); passed++;
        check(page.nextPage == 2, "pagination retained after filtering"); passed++;
        ReleaseHistory.Page empty = ReleaseHistory.parse("[{\"tag_name\":\"v4\",\"prerelease\":true}]", 1, next, 0);
        check(empty.releases.isEmpty() && empty.nextPage == 2, "all-prerelease page still navigates"); passed++;
        check(ReleaseHistory.parse("[]", 1, null, 0).nextPage == 0, "empty final page"); passed++;
        boolean invalid = false; try { ReleaseHistory.parse("not-json", 1, null, 0); } catch (java.io.IOException expected) { invalid = true; }
        check(invalid, "invalid JSON is retryable error"); passed++;
        GitHubUpdate.Result current = GitHubUpdate.parse(array.getJSONObject(0).put("assets", new JSONArray()).toString(), "3.2.0", new String[]{"arm64-v8a"});
        check(current.comparison == 0 && current.notes.contains("**修复**"), "current result retains Markdown source"); passed++;
        GitHubUpdate.Result ahead = GitHubUpdate.parse(array.getJSONObject(0).toString(), "3.3.0", new String[]{"arm64-v8a"});
        check(ahead.comparison < 0 && ahead.notes.equals(current.notes), "ahead result retains official notes"); passed++;
        final File directory = new File(context.getCacheDir(), "release-notes-fixture-" + UUID.randomUUID());
        check(directory.mkdir(), "create generated cache directory"); passed++;
        try {
            check(ReleaseHistoryCache.read(directory) == null, "missing cache empty state"); passed++;
            ReleaseHistoryCache.write(directory, page);
            ReleaseHistoryCache.Snapshot snapshot = ReleaseHistoryCache.read(directory);
            check(snapshot != null && snapshot.page.releases.size() == 2 && snapshot.page.nextPage == 2, "offline cache roundtrip and pagination"); passed++;
            check(snapshot.page.releases.get(0).notes.equals(current.notes) && snapshot.savedAt > 0, "cache preserves Markdown and timestamp"); passed++;
            File file = new File(directory, "release-history.json");
            try (FileOutputStream output = new FileOutputStream(file)) { output.write("{broken".getBytes(StandardCharsets.UTF_8)); }
            check(ReleaseHistoryCache.read(directory) == null, "corrupt cache ignored"); passed++;
            try (RandomAccessFile oversized = new RandomAccessFile(file, "rw")) { oversized.setLength(ReleaseHistory.MAX_RESPONSE + 1L); }
            check(ReleaseHistoryCache.read(directory) == null, "oversized cache ignored"); passed++;
            ReleaseHistoryCache.write(directory, empty);
            snapshot = ReleaseHistoryCache.read(directory);
            check(snapshot != null && snapshot.page.releases.isEmpty() && snapshot.page.nextPage == 2, "empty filtered cache preserves next page"); passed++;
        } finally {
            File[] files = directory.listFiles(); if (files != null) for (File file : files) file.delete(); directory.delete();
        }
        // TextView work belongs to the UI thread, even when run by Instrumentation.onStart().
        java.util.concurrent.FutureTask<Void> rendered = new java.util.concurrent.FutureTask<>(() -> {
            TextView text = ReleaseMarkdownView.create(context, "# 标题\n\n**粗体** [链接](https://example.org) [危险](javascript:alert(1))");
            check(text.getText() instanceof Spanned, "native formatted text");
            Spanned spans = (Spanned) text.getText();
            check(spans.getSpans(0, spans.length(), ClickableSpan.class).length == 1, "only safe link clickable");
            check(spans.getSpans(0, spans.length(), StyleSpan.class).length >= 2, "heading and bold native styles");
            check(!text.getText().toString().contains("**"), "Markdown markers removed");
            TextView rich = ReleaseMarkdownView.create(context, "## 更新内容\n\n- 第一项 `KongyeFloat.apk`\n- [x] 第二项\n1. 第三项\n\n> 引用说明\n\n---\n\n```text\n代码块\n```");
            Spanned richSpans = (Spanned) rich.getText();
            check(richSpans.getSpans(0, richSpans.length(), android.text.style.LeadingMarginSpan.class).length >= 5, "section, list, quote and code block margins");
            rich.measure(android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
            rich.layout(0, 0, rich.getMeasuredWidth(), rich.getMeasuredHeight());
            android.graphics.Bitmap surface = android.graphics.Bitmap.createBitmap(720, Math.max(1, rich.getMeasuredHeight()), android.graphics.Bitmap.Config.ARGB_8888);
            try { rich.draw(new android.graphics.Canvas(surface)); } finally { surface.recycle(); }
            return null;
        });
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) rendered.run();
        else new android.os.Handler(android.os.Looper.getMainLooper()).post(rendered);
        rendered.get(5, java.util.concurrent.TimeUnit.SECONDS); passed += 6;
        return passed;
    }
    private ReleaseNotesChecks() { }
}
