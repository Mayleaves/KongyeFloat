package com.kongye.floatball;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Presentation-only title cleanup, note excerpts and safe tag URLs for release history. */
final class ReleaseSummary {
    static final String REPOSITORY = "https://github.com/Mayleaves/KongyeFloat";
    private static final Set<String> SECTION_LABELS = new HashSet<>(Arrays.asList(
            "更新内容", "更新说明", "本次更新", "主要更新", "用户可见变化",
            "下载", "下载安装", "安装与更新", "安装说明", "验证", "构建与验证", "功能", "修复", "改进",
            "changes", "changelog", "notes", "downloads"
    ));

    static String headline(ReleaseHistory.Release release) {
        if (release == null) return "";
        String stripped = stripAppAndVersion(release.title, release.tag);
        if (!stripped.isEmpty()) return stripped;
        String firstH1 = leadingH1(release.notes);
        if (!firstH1.isEmpty()) {
            String h1Stripped = stripAppAndVersion(firstH1, release.tag);
            if (!h1Stripped.isEmpty() && !isGenericSection(h1Stripped)) return h1Stripped;
        }
        String fallback = release.title == null ? "" : release.title.trim();
        return fallback.isEmpty() ? release.tag : fallback;
    }

    /** Removes a leading H1 line when it merely repeats the release tag or card headline. */
    static String detailNotes(ReleaseHistory.Release release) {
        if (release == null || release.notes == null) return "";
        String notes = release.notes.replace("\r\n", "\n").replace('\r', '\n');
        int start = 0;
        while (start < notes.length() && (notes.charAt(start) == '\n' || notes.charAt(start) == ' ' || notes.charAt(start) == '\t')) start++;
        if (start >= notes.length() || notes.charAt(start) != '#') return release.notes;
        int hashes = 0;
        while (start + hashes < notes.length() && notes.charAt(start + hashes) == '#') hashes++;
        if (hashes != 1 || start + hashes >= notes.length() || notes.charAt(start + hashes) != ' ') return release.notes;
        int lineEnd = notes.indexOf('\n', start);
        if (lineEnd < 0) lineEnd = notes.length();
        String h1 = notes.substring(start + 2, lineEnd).trim().replaceFirst("\\s+#+\\s*$", "");
        String cleanedH1 = stripAppAndVersion(h1, release.tag);
        String cardHeadline = headline(release);
        if (cleanedH1.isEmpty() || h1.equalsIgnoreCase(release.title.trim()) || h1.equalsIgnoreCase(cardHeadline) || cleanedH1.equalsIgnoreCase(cardHeadline)) {
            int rest = lineEnd;
            while (rest < notes.length() && notes.charAt(rest) == '\n') rest++;
            return notes.substring(rest);
        }
        return release.notes;
    }

    static String excerpt(ReleaseHistory.Release release, String rawQuery) {
        if (release == null || release.notes == null || release.notes.trim().isEmpty()) return "";
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        String body = detailNotes(release);
        if (query.isEmpty() && body.length() > 4096) body = body.substring(0, 4096);
        String plain = ReleaseMarkdown.parse(body).text;
        if (plain.isEmpty()) return "";
        String[] rawLines = plain.split("\n");
        String cardHeadline = headline(release);
        ArrayList<String> lines = new ArrayList<>();
        int matchLine = -1;
        for (String raw : rawLines) {
            String line = cleanPreviewLine(raw);
            if (line.isEmpty()) continue;
            if (!query.isEmpty() && matchLine < 0 && line.toLowerCase(Locale.ROOT).contains(query)) {
                matchLine = lines.size();
                lines.add(line);
                continue;
            }
            if (isBoilerplateLine(line, release.tag, cardHeadline)) continue;
            lines.add(line);
        }
        if (lines.isEmpty()) return "";
        int from = matchLine >= 0 ? matchLine : 0;
        StringBuilder out = new StringBuilder();
        for (int i = from; i < lines.size() && out.length() < 140; i++) {
            String part = lines.get(i);
            if (i == from && matchLine >= 0) part = windowMatch(part, query);
            if (out.length() > 0) {
                char prev = out.charAt(out.length() - 1);
                out.append("。！？；.!?;".indexOf(prev) >= 0 ? " " : " · ");
            }
            out.append(part);
        }
        return out.toString();
    }

    static String releaseUrl(String tag) {
        if (tag == null) return null;
        String value = tag.trim();
        if (value.isEmpty() || value.length() > 128 || value.contains("/") || value.contains("\\") || value.contains("..")) return null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) return null;
        }
        try {
            String encoded = URLEncoder.encode(value, "UTF-8").replace("+", "%20");
            return REPOSITORY + "/releases/tag/" + encoded;
        } catch (UnsupportedEncodingException impossible) {
            return null;
        }
    }

    private static String leadingH1(String notes) {
        if (notes == null || notes.isEmpty()) return "";
        String normalized = notes.replace("\r\n", "\n").replace('\r', '\n');
        for (String raw : normalized.split("\n", 8)) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("# ")) return line.substring(2).trim().replaceFirst("\\s+#+\\s*$", "");
            return "";
        }
        return "";
    }

    static String stripAppAndVersion(String text, String tag) {
        if (text == null) return "";
        String s = text.trim();
        if (s.isEmpty()) return "";
        // Strip leading product name only when followed by end, separator, whitespace or version token.
        for (String app : new String[]{"空叶悬浮球", "KongyeFloat", "Kongye Float", "空叶"}) {
            if (s.regionMatches(true, 0, app, 0, app.length())) {
                if (s.length() == app.length()) return "";
                char next = s.charAt(app.length());
                if (Character.isWhitespace(next) || "·•-—–:：/|vV0123456789".indexOf(next) >= 0) {
                    s = s.substring(app.length()).trim();
                }
                break;
            }
        }
        s = trimSeparators(s);
        // Strip leading tag or numeric version token when followed by end, whitespace or separator.
        String bareTag = tag == null ? "" : tag.trim().replaceFirst("^[vV]", "");
        if (!bareTag.isEmpty()) {
            for (String candidate : new String[]{"v" + bareTag, bareTag}) {
                if (s.regionMatches(true, 0, candidate, 0, candidate.length())) {
                    if (s.length() == candidate.length()) return "";
                    char next = s.charAt(candidate.length());
                    if (Character.isWhitespace(next) || "·•-—–:：/|".indexOf(next) >= 0) {
                        s = s.substring(candidate.length()).trim();
                    }
                    break;
                }
            }
        } else {
            s = s.replaceFirst("^[vV]?[0-9]+(?:\\.[0-9]+){1,3}(?=$|[\\s·•\\-—–:：/|])", "").trim();
        }
        return trimSeparators(s);
    }

    private static String trimSeparators(String text) {
        int i = 0;
        while (i < text.length() && " \t·•-—–:：/|".indexOf(text.charAt(i)) >= 0) i++;
        return text.substring(i).trim();
    }

    private static boolean isGenericSection(String text) {
        String key = text.trim().replaceAll("[：:]+$", "").toLowerCase(Locale.ROOT);
        return SECTION_LABELS.contains(key);
    }

    private static String cleanPreviewLine(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty() || "────────".equals(s)) return "";
        if (s.startsWith("• ") || s.startsWith("☐ ") || s.startsWith("☑ ")) s = s.substring(2).trim();
        else {
            int d = 0;
            while (d < s.length() && d < 6 && Character.isDigit(s.charAt(d))) d++;
            if (d > 0 && d + 1 < s.length() && (s.charAt(d) == '.' || s.charAt(d) == ')') && s.charAt(d + 1) == ' ') {
                s = s.substring(d + 2).trim();
            }
        }
        return s;
    }

    private static boolean isBoilerplateLine(String line, String tag, String cardHeadline) {
        if (isGenericSection(line)) return true;
        if (line.equalsIgnoreCase(cardHeadline)) return true;
        if (stripAppAndVersion(line, tag).isEmpty()) return true;
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.startsWith("sha-256") || lower.startsWith("sha256");
    }

    private static String windowMatch(String line, String lowerQuery) {
        int at = line.toLowerCase(Locale.ROOT).indexOf(lowerQuery);
        if (at <= 28) return line;
        int start = Math.max(0, at - 16);
        return "…" + line.substring(start);
    }

    private ReleaseSummary() { }
}
