package com.kongye.floatball;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Small, bounded Markdown-to-text parser. HTML is always inert text; images never load. */
final class ReleaseMarkdown {
    static final int MAX_SOURCE = 64 * 1024;
    static final int MAX_MARKS = 4096;
    private static final int MAX_DEPTH = 12;
    private static final String CUT = "\n\n（说明较长，已截取显示。）";
    enum Kind { HEADING, BOLD, ITALIC, STRIKE, CODE, CODE_BLOCK, QUOTE, LINK }
    static final class Mark {
        final Kind kind;
        final int start, end;
        final String value;
        Mark(Kind kind, int start, int end, String value) {
            this.kind = kind; this.start = start; this.end = end; this.value = value;
        }
    }
    static final class Document {
        final String text;
        final List<Mark> marks;
        Document(String text, List<Mark> marks) {
            this.text = text; this.marks = Collections.unmodifiableList(new ArrayList<>(marks));
        }
    }
    private final StringBuilder out = new StringBuilder();
    private final ArrayList<Mark> marks = new ArrayList<>();
    private final Map<String, String> references = new HashMap<>();
    private int budget = MAX_SOURCE * 32;
    private boolean exhausted;

    static String boundedSource(String source) {
        if (source == null) return "";
        if (source.length() <= MAX_SOURCE) return source;
        int end = MAX_SOURCE;
        if (Character.isHighSurrogate(source.charAt(end - 1))) end--;
        return source.substring(0, end) + CUT;
    }

    static Document parse(String source) {
        ReleaseMarkdown parser = new ReleaseMarkdown();
        String bounded = boundedSource(source).replace("\r\n", "\n").replace('\r', '\n').replace('\u0000', '\uFFFD');
        parser.blocks(bounded);
        if (parser.exhausted) parser.out.append(CUT);
        while (parser.out.length() > 0 && parser.out.charAt(parser.out.length() - 1) == '\n') parser.out.setLength(parser.out.length() - 1);
        // Paragraph spans must stay within the final text after trimming trailing newlines.
        int length = parser.out.length();
        ArrayList<Mark> clipped = new ArrayList<>();
        for (Mark mark : parser.marks) if (mark.start < length) clipped.add(new Mark(mark.kind, mark.start, Math.min(mark.end, length), mark.value));
        return new Document(parser.out.toString(), clipped);
    }

    /** Only explicit web links can leave the app. No file/content/intent/javascript schemes. */
    static String safeLink(String address) {
        if (address == null || address.length() > 2048) return null;
        String value = address.trim();
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i)) || Character.isWhitespace(value.charAt(i)) || value.charAt(i) == '\\') return null;
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http")) || uri.getHost() == null || uri.getUserInfo() != null) return null;
            return uri.toASCIIString();
        } catch (Exception invalid) { return null; }
    }

    private void mark(Kind kind, int start, String value) {
        if (start < out.length() && marks.size() < MAX_MARKS) marks.add(new Mark(kind, start, out.length(), value));
    }
    private boolean step(int cost) {
        budget -= cost;
        if (budget < 0) { exhausted = true; return false; }
        return true;
    }
    private void gap() {
        if (out.length() == 0) return;
        if (out.charAt(out.length() - 1) != '\n') out.append('\n');
        if (out.length() < 2 || out.charAt(out.length() - 2) != '\n') out.append('\n');
    }
    private static String ref(String key) { return key.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }

    private void blocks(String source) {
        String[] lines = source.split("\n", -1);
        // Definitions are resolved before inline rendering, including forward references.
        boolean[] definition = new boolean[lines.length];
        boolean fenced = false; char fenceChar = 0; int fenceLength = 0;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            int run = fenceRun(t);
            if (run >= 3) {
                if (!fenced) { fenced = true; fenceChar = t.charAt(0); fenceLength = run; }
                else if (t.charAt(0) == fenceChar && run >= fenceLength && t.substring(run).trim().isEmpty()) fenced = false;
            }
            if (!fenced && t.startsWith("[") && references.size() < 256) {
                int close = t.indexOf("]:");
                if (close > 1) {
                    String url = destination(t.substring(close + 2).trim());
                    if (url != null) { references.put(ref(t.substring(1, close)), url); definition[i] = true; }
                }
            }
        }
        for (int i = 0; i < lines.length && !exhausted; i++) {
            if (!step(1)) break;
            if (definition[i]) continue;
            String raw = lines[i], text = raw.trim();
            if (text.isEmpty()) { gap(); continue; }
            int run = fenceRun(text);
            if (run >= 3) {
                gap(); int start = out.length(); char marker = text.charAt(0);
                for (i++; i < lines.length; i++) {
                    String close = lines[i].trim(); int count = fenceRun(close);
                    if (count >= run && close.charAt(0) == marker && close.substring(count).trim().isEmpty()) break;
                    out.append(lines[i]).append('\n');
                }
                mark(Kind.CODE_BLOCK, start, ""); gap(); continue;
            }
            if (raw.startsWith("    ") && (out.length() == 0 || out.length() >= 2 && out.charAt(out.length() - 1) == '\n' && out.charAt(out.length() - 2) == '\n')) {
                int start = out.length();
                do { out.append(lines[i].substring(4)).append('\n'); i++; }
                while (i < lines.length && lines[i].startsWith("    "));
                i--; mark(Kind.CODE_BLOCK, start, ""); gap(); continue;
            }
            int heading = 0;
            while (heading < text.length() && text.charAt(heading) == '#') heading++;
            if (heading > 0 && heading <= 6 && (heading == text.length() || text.charAt(heading) == ' ')) {
                gap(); int start = out.length();
                inline(text.substring(heading).trim().replaceFirst("\\s+#+\\s*$", ""), 0);
                mark(Kind.HEADING, start, String.valueOf(heading)); gap(); continue;
            }
            if (i + 1 < lines.length && (lines[i + 1].trim().matches("=+") || lines[i + 1].trim().matches("-{3,}"))) {
                gap(); int start = out.length(); inline(text, 0);
                mark(Kind.HEADING, start, lines[++i].trim().charAt(0) == '=' ? "1" : "2"); gap(); continue;
            }
            if (text.matches("(?:\\*\\s*){3,}|(?:-\\s*){3,}|(?:_\\s*){3,}")) { gap(); out.append("────────\n"); gap(); continue; }
            // GFM tables become readable native rows; separator syntax is omitted.
            if (text.indexOf('|') >= 0 && i + 1 < lines.length && lines[i + 1].trim().matches("\\|?\\s*:?-{3,}:?\\s*(?:\\|\\s*:?-{3,}:?\\s*)+\\|?")) {
                gap(); int start = out.length(); inline(tableRow(text), 0); mark(Kind.BOLD, start, ""); out.append('\n'); i++;
                while (i + 1 < lines.length && lines[i + 1].contains("|") && !lines[i + 1].trim().isEmpty()) { inline(tableRow(lines[++i].trim()), 0); out.append('\n'); }
                gap(); continue;
            }
            int quoteStart = -1;
            if (text.startsWith(">")) {
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
                quoteStart = out.length();
                do { text = text.substring(1).trim(); } while (text.startsWith(">"));
            }
            int item = listPrefix(text);
            if (item > 0) {
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
                int spaces = 0; while (spaces < raw.length() && raw.charAt(spaces) == ' ') spaces++;
                int indent = Math.min(6, spaces / 2);
                for (int n = 0; n < indent; n++) out.append("  ");
                String prefix = Character.isDigit(text.charAt(0)) ? text.substring(0, item).trim() + " " : "• ";
                text = text.substring(item).trim();
                if (text.startsWith("[ ] ") || text.startsWith("[x] ") || text.startsWith("[X] ")) { prefix = text.charAt(1) == ' ' ? "☐ " : "☑ "; text = text.substring(4); }
                out.append(prefix);
            } else if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append(' ');
            boolean hardBreak = raw.endsWith("  ") || raw.endsWith("\\");
            if (raw.endsWith("\\") && text.endsWith("\\")) text = text.substring(0, text.length() - 1);
            inline(text, 0);
            if (quoteStart >= 0) { mark(Kind.QUOTE, quoteStart, ""); out.append('\n'); }
            else if (item > 0 || hardBreak) out.append('\n');
        }
    }
    private static String tableRow(String text) {
        if (text.startsWith("|")) text = text.substring(1);
        if (text.endsWith("|")) text = text.substring(0, text.length() - 1);
        return text.replaceAll("\\s*\\|\\s*", "   ·   ");
    }
    private static int fenceRun(String text) {
        if (text.isEmpty() || text.charAt(0) != '`' && text.charAt(0) != '~') return 0;
        int i = 1; while (i < text.length() && text.charAt(i) == text.charAt(0)) i++; return i;
    }
    private static int listPrefix(String text) {
        if (text.length() >= 2 && "-*+".indexOf(text.charAt(0)) >= 0 && text.charAt(1) == ' ') return 2;
        int i = 0; while (i < text.length() && i < 9 && Character.isDigit(text.charAt(i))) i++;
        return i > 0 && i + 1 < text.length() && (text.charAt(i) == '.' || text.charAt(i) == ')') && text.charAt(i + 1) == ' ' ? i + 2 : 0;
    }
    private int closing(String text, String token, int from) {
        for (int at = from; at <= text.length() - token.length(); at++) {
            if (!step(1)) return -1;
            if (text.charAt(at) == '\\') { at++; continue; }
            if (text.startsWith(token, at)) return at;
        }
        return -1;
    }
    private int emphasisClose(String text, char marker, int count, int from) {
        for (int at = from; at < text.length(); at++) {
            if (!step(1)) return -1;
            if (text.charAt(at) == '\\') { at++; continue; }
            if (text.charAt(at) != marker) continue;
            int run = 1; while (at + run < text.length() && text.charAt(at + run) == marker) run++;
            if ((run == count || run == 3 && count < 3) && at > from && !Character.isWhitespace(text.charAt(at - 1))) return at + run - count;
            at += run - 1;
        }
        return -1;
    }
    private static String repeated(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
    private static String destination(String text) {
        String value = text.trim();
        if (value.startsWith("<")) { int end = value.indexOf('>'); if (end < 0) return null; value = value.substring(1, end); }
        else { int space = value.indexOf(' '); if (space >= 0) value = value.substring(0, space); }
        return safeLink(value);
    }
    private void inline(String text, int depth) {
        if (depth >= MAX_DEPTH || marks.size() >= MAX_MARKS) { out.append(text); return; }
        for (int i = 0; i < text.length() && !exhausted;) {
            if (!step(1)) break;
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() && "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~".indexOf(text.charAt(i + 1)) >= 0) { out.append(text.charAt(i + 1)); i += 2; continue; }
            if (c == '`') {
                int count = 1; while (i + count < text.length() && text.charAt(i + count) == '`') count++;
                String token = repeated('`', count); int close = closing(text, token, i + count);
                if (close >= 0) {
                    int start = out.length(); String code = text.substring(i + count, close).replace('\n', ' ');
                    if (code.startsWith(" ") && code.endsWith(" ") && !code.trim().isEmpty()) code = code.substring(1, code.length() - 1);
                    out.append(code); mark(Kind.CODE, start, ""); i = close + count; continue;
                }
            }
            boolean image = c == '!' && i + 1 < text.length() && text.charAt(i + 1) == '[';
            if (c == '[' || image) {
                int open = image ? i + 1 : i; int close = closing(text, "]", open + 1);
                if (close >= 0) {
                    int end = close + 1; String url = null; boolean recognized = false;
                    if (end < text.length() && text.charAt(end) == '(') {
                        int nesting = 1, p = end + 1;
                        for (; p < text.length() && step(1); p++) {
                            char t = text.charAt(p); if (t == '\\') { p++; continue; }
                            if (t == '(') nesting++; else if (t == ')' && --nesting == 0) break;
                        }
                        if (p < text.length() && nesting == 0) { url = destination(text.substring(end + 1, p)); end = p + 1; recognized = true; }
                    } else {
                        String key = text.substring(open + 1, close);
                        if (end < text.length() && text.charAt(end) == '[') {
                            int refEnd = closing(text, "]", end + 1);
                            if (refEnd >= 0) { String explicit = text.substring(end + 1, refEnd); if (!explicit.isEmpty()) key = explicit; end = refEnd + 1; }
                        }
                        url = references.get(ref(key)); recognized = url != null;
                    }
                    if (recognized) {
                        int start = out.length(); if (image) out.append("图片：");
                        inline(text.substring(open + 1, close), depth + 1);
                        if (!image && url != null) mark(Kind.LINK, start, url);
                        i = end; continue;
                    }
                }
            }
            if (c == '<') {
                int close = text.indexOf('>', i + 1);
                if (close >= 0) {
                    String url = safeLink(text.substring(i + 1, close));
                    if (url != null) { int start = out.length(); out.append(text, i + 1, close); mark(Kind.LINK, start, url); i = close + 1; continue; }
                }
            }
            if (text.startsWith("https://", i) || text.startsWith("http://", i)) {
                int end = i; while (end < text.length() && !Character.isWhitespace(text.charAt(end)) && "<>".indexOf(text.charAt(end)) < 0) end++;
                while (end > i && ".,;:!?)]}".indexOf(text.charAt(end - 1)) >= 0) end--;
                String url = safeLink(text.substring(i, end));
                if (url != null) { int start = out.length(); out.append(text, i, end); mark(Kind.LINK, start, url); i = end; continue; }
            }
            if (c == '*' || c == '_' || c == '~') {
                int count = 1; while (i + count < text.length() && text.charAt(i + count) == c && count < 3) count++;
                if (c == '~' && count != 2 || c == '_' && i > 0 && Character.isLetterOrDigit(text.charAt(i - 1))) { out.append(c); i++; continue; }
                int content = i + count;
                if (content < text.length() && !Character.isWhitespace(text.charAt(content))) {
                    int close = emphasisClose(text, c, count, content);
                    if (close > content && !Character.isWhitespace(text.charAt(close - 1))) {
                        int start = out.length(); inline(text.substring(content, close), depth + 1);
                        mark(c == '~' ? Kind.STRIKE : count >= 2 ? Kind.BOLD : Kind.ITALIC, start, "");
                        if (count == 3) mark(Kind.ITALIC, start, "");
                        i = close + count; continue;
                    }
                }
            }
            // Decode common Markdown entities without ever interpreting HTML tags.
            if (c == '&') {
                int end = text.indexOf(';', i + 1);
                if (end > i && end - i <= 12) {
                    String entity = text.substring(i + 1, end); String decoded = entity(entity);
                    if (decoded != null) { out.append(decoded); i = end + 1; continue; }
                }
            }
            out.append(c); i++;
        }
    }
    private static String entity(String value) {
        switch (value) { case "amp": return "&"; case "lt": return "<"; case "gt": return ">"; case "quot": return "\""; case "apos": return "'"; case "nbsp": return " "; default: break; }
        try {
            int point = value.startsWith("#x") || value.startsWith("#X") ? Integer.parseInt(value.substring(2), 16) : value.startsWith("#") ? Integer.parseInt(value.substring(1)) : -1;
            if (Character.isValidCodePoint(point) && !Character.isISOControl(point) && !(point >= 0xD800 && point <= 0xDFFF)) return new String(Character.toChars(point));
        } catch (IllegalArgumentException ignored) { }
        return null;
    }
}
