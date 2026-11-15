package com.kongye.floatball;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Read-only official release feed. A request fetches one page and never follows server URLs. */
final class ReleaseHistory {
    static final String API = "https://api.github.com/repos/Mayleaves/KongyeFloat/releases";
    static final int PAGE_SIZE = 10;
    static final int CATALOG_PAGE_SIZE = 100;
    static final int MAX_CATALOG_PAGES = 5;
    static final int MAX_RESPONSE = 2 * 1024 * 1024;
    static final class Release {
        final String tag, title, published, notes;
        Release(String tag, String title, String published, String notes) {
            this.tag = limit(tag, 256);
            this.title = limit(title == null || title.trim().isEmpty() ? tag : title, 512);
            this.published = limit(published, 64);
            this.notes = ReleaseMarkdown.boundedSource(notes);
        }
        String date() {
            try { return Instant.parse(published).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(); }
            catch (RuntimeException invalid) { return "发布日期未知"; }
        }
    }
    static final class Page {
        final List<Release> releases;
        final int nextPage;
        final long retryAt;
        Page(List<Release> releases, int nextPage, long retryAt) {
            this.releases = Collections.unmodifiableList(new ArrayList<>(releases)); this.nextPage = nextPage; this.retryAt = retryAt;
        }
    }
    static final class Catalog {
        final List<Release> releases;
        final boolean complete;
        final long retryAt;
        Catalog(List<Release> releases, boolean complete, long retryAt) {
            this.releases = Collections.unmodifiableList(new ArrayList<>(releases));
            this.complete = complete;
            this.retryAt = retryAt;
        }
    }
    static final class RateLimited extends IOException {
        final long retryAt;
        RateLimited(long retryAt) { super("GitHub 请求次数受限，请在提示时间后重试。"); this.retryAt = retryAt; }
    }
    private static String limit(String value, int max) { return value == null ? "" : value.substring(0, Math.min(value.length(), max)); }

    static Page parse(String json, int page, String link, long retryAt) throws IOException {
        return parsePage(json, page, PAGE_SIZE, link, retryAt);
    }

    static Page parsePage(String json, int page, int maxItems, String link, long retryAt) throws IOException {
        if (page < 1 || maxItems < 1 || json == null || json.length() > MAX_RESPONSE) throw new IOException("发布信息过大或页码无效");
        try {
            JSONArray array = new JSONArray(json);
            if (array.length() > maxItems) throw new IOException("发布列表超出单页限制");
            ArrayList<Release> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null || item.optBoolean("draft", false) || item.optBoolean("prerelease", false)) continue;
                String tag = item.optString("tag_name", "");
                if (tag.trim().isEmpty()) continue;
                result.add(new Release(tag, item.isNull("name") ? tag : item.optString("name", tag), item.optString("published_at", ""), item.isNull("body") ? "" : item.optString("body", "")));
            }
            return new Page(result, nextPage(link, page), retryAt);
        } catch (JSONException invalid) { throw new IOException("发布信息格式无法识别", invalid); }
    }

    static int nextPage(String link, int current) {
        if (link == null || link.length() > 8192) return 0;
        for (String part : link.split(",")) {
            if (!part.matches("(?s).*;\\s*rel=\"?next\"?(?:\\s*;.*)?\\s*")) continue;
            int start = part.indexOf('<'), end = part.indexOf('>');
            if (start < 0 || end <= start) continue;
            try {
                URI uri = new URI(part.substring(start + 1, end));
                String path = uri.getPath();
                if (!"https".equals(uri.getScheme()) || !"api.github.com".equals(uri.getHost()) || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getFragment() != null) continue;
                if (!"/repos/Mayleaves/KongyeFloat/releases".equals(path) && !path.matches("/repositories/[0-9]+/releases")) continue;
                String query = uri.getRawQuery(); if (query == null) continue;
                for (String pair : query.split("&")) if (pair.startsWith("page=")) {
                    int page = Integer.parseInt(pair.substring(5));
                    if (page == current + 1 && page > 1) return page;
                }
            } catch (Exception ignored) { }
        }
        return 0;
    }

    static long retryAt(String retryAfter, String remaining, String reset, long now) {
        long retry = 0;
        if (retryAfter != null) {
            try { long seconds = Long.parseLong(retryAfter.trim()); if (seconds >= 0) retry = now + Math.min(seconds, 7 * 86400L) * 1000L; }
            catch (NumberFormatException date) {
                try { retry = ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli(); }
                catch (RuntimeException ignored) { }
            }
        }
        if ("0".equals(remaining == null ? null : remaining.trim()) && reset != null) {
            try { long seconds = Long.parseLong(reset.trim()); if (seconds > 0 && seconds < Long.MAX_VALUE / 1000) retry = Math.max(retry, seconds * 1000L); }
            catch (NumberFormatException ignored) { }
        }
        return retry > now ? retry : 0;
    }

    static final class Request {
        private volatile boolean cancelled;
        private volatile HttpURLConnection connection;
        private volatile InputStream stream;
        synchronized void cancel() {
            if (cancelled) return;
            cancelled = true;
            HttpURLConnection active = connection;
            InputStream input = stream;
            if (active == null && input == null) return;
            // Closing an HTTPS body can write TLS data, so lifecycle callbacks must not do it.
            Thread cleanup = new Thread(() -> {
                try { if (active != null) active.disconnect(); }
                finally { if (input != null) try { input.close(); } catch (IOException ignored) { } }
            }, "release-history-cancel");
            cleanup.setDaemon(true);
            cleanup.start();
        }
        private void cancelled() throws InterruptedIOException {
            if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("已取消加载");
        }
        Page load(int page) throws IOException {
            return loadPage(page, PAGE_SIZE);
        }
        Catalog loadCatalog() throws IOException {
            java.util.LinkedHashMap<String, Release> all = new java.util.LinkedHashMap<>();
            int page = 1, fetched = 0;
            long retry = 0;
            while (page > 0 && fetched < MAX_CATALOG_PAGES) {
                Page current = loadPage(page, CATALOG_PAGE_SIZE);
                for (Release r : current.releases) if (!all.containsKey(r.tag)) all.put(r.tag, r);
                retry = Math.max(retry, current.retryAt);
                fetched++;
                page = current.nextPage;
                if (page > 0 && retry > System.currentTimeMillis()) break;
            }
            return new Catalog(new ArrayList<>(all.values()), page == 0, retry);
        }
        private Page loadPage(int page, int pageSize) throws IOException {
            if (page < 1) throw new IOException("无效的发布页码");
            cancelled();
            HttpURLConnection conn = (HttpURLConnection) new URL(API + "?per_page=" + pageSize + "&page=" + page).openConnection();
            connection = conn;
            try {
                cancelled();
                conn.setConnectTimeout(8000); conn.setReadTimeout(8000); conn.setInstanceFollowRedirects(false); conn.setUseCaches(false);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
                conn.setRequestProperty("User-Agent", "KongyeFloat-Android");
                int status = conn.getResponseCode(); cancelled();
                long now = System.currentTimeMillis();
                long retry = retryAt(conn.getHeaderField("Retry-After"), conn.getHeaderField("X-RateLimit-Remaining"), conn.getHeaderField("X-RateLimit-Reset"), now);
                if (status == 429 || status == 403 && (retry > 0 || "0".equals(conn.getHeaderField("X-RateLimit-Remaining")))) throw new RateLimited(retry > now ? retry : now + 60000);
                if (status != 200) throw new IOException(status == 404 ? "暂未找到正式发布记录" : "GitHub 暂时无法访问（" + status + "），请稍后重试。");
                if (conn.getContentLengthLong() > MAX_RESPONSE) throw new IOException("单页发布信息过大，请稍后重试。");
                try (InputStream input = conn.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    stream = input; cancelled(); byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        cancelled(); if (bytes.size() + count > MAX_RESPONSE) throw new IOException("单页发布信息过大，请稍后重试。");
                        bytes.write(buffer, 0, count);
                    }
                    cancelled();
                    return parsePage(new String(bytes.toByteArray(), StandardCharsets.UTF_8), page, pageSize, conn.getHeaderField("Link"), retry);
                }
            } catch (java.net.SocketTimeoutException | java.net.UnknownHostException | java.net.ConnectException | javax.net.ssl.SSLException unavailable) {
                cancelled(); throw new IOException("无法连接 GitHub，请检查网络后重试。", unavailable);
            } finally { stream = null; conn.disconnect(); connection = null; }
        }
    }
    private ReleaseHistory() { }
}
