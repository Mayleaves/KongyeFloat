package com.kongye.floatball;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Versioned, portable user settings. Runtime state and access grants stay on the device. */
final class BackupStore {
    private static final int MAX_IMAGE = 20 * 1024 * 1024;
    private static final int MAX_JSON = 1024 * 1024;
    private static final Set<String> BOOLEANS = Set.of(
            "custom_art", "fade", "snap", "haptic", "enhanced_overlay", "hide_from_recents",
            "edge_interaction_v2", "drag_center_region", "home_tools_subtitle_added",
            "ocr_keep_lines", "ocr_chinese_punctuation", "record_hide",
            "subtitle_frame", "subtitle_cover", "subtitle_follow_original", "subtitle_caption_below",
            "subtitle_follow_background", "subtitle_follow_text_color");
    private static final Set<String> INTEGERS = Set.of(
            "color", "size", "size_percent", "opacity", "edge_gap", "edge_percent", "x", "y",
            "dark_start", "dark_end", "quick_opacity", "record_resolution", "gif_resolution", "gif_fps",
            "record_bitrate", "subtitle_alignment", "subtitle_background", "subtitle_opacity",
            "subtitle_cover_opacity", "subtitle_original_opacity", "subtitle_font_scale");
    private static final Set<String> STRINGS = Set.of(
            "theme", "browser_engine", "quick_actions", "home_tools", "recording_mode",
            "subtitle_source", "subtitle_target", "gesture_tap", "gesture_double", "gesture_long",
            "gesture_up", "gesture_down");
    private static final String PACKAGE_PATTERN = "[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+";

    record Snapshot(Map<String, Object> settings, boolean includesNotes, byte[] image,
                    String createdAt, String appVersion) {}

    static String filename() {
        return "KongyeFloat_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".zip";
    }

    private static boolean setting(String key) {
        return BOOLEANS.contains(key) || INTEGERS.contains(key) || STRINGS.contains(key)
                || key.equals("favorites") || key.startsWith("app_mode_") && key.substring(9).matches(PACKAGE_PATTERN);
    }

    static synchronized void export(Context context, Uri destination, boolean includeNotes) throws IOException {
        Map<String, ?> current = Prefs.of(context).getAll();
        JSONObject values = new JSONObject();
        try {
            for (var entry : new TreeMap<>(current).entrySet()) {
                String key = entry.getKey();
                if (!setting(key) && !(includeNotes && key.equals("note"))) continue;
                Object value = entry.getValue();
                values.put(key, value instanceof Set<?> set ? new JSONArray(new TreeSet<>(set)) : value);
            }
            if (includeNotes && !values.has("note")) values.put("note", "");
            byte[] image = null;
            if (BallArt.file(context).isFile()) {
                try (InputStream in = new FileInputStream(BallArt.file(context))) { image = read(in, MAX_IMAGE); }
                validateImage(image);
            } else values.put("custom_art", false);
            JSONObject manifest = new JSONObject();
            manifest.put("format", "KongyeFloat").put("schema", 1)
                    .put("created_at", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date()))
                    .put("app_version", version(context)).put("includes_notes", includeNotes)
                    .put("settings", values).put("image_sha256", image == null ? "" : sha256(image));
            byte[] json = manifest.toString(2).getBytes(StandardCharsets.UTF_8);
            if (json.length > MAX_JSON) throw new IOException("设置内容过大，无法备份");
            try (OutputStream stream = context.getContentResolver().openOutputStream(destination, "wt")) {
                if (stream == null) throw new IOException("无法写入所选位置，请重新选择");
                try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(stream))) {
                    writeEntry(zip, "backup.json", json);
                    if (image != null) writeEntry(zip, "ball-art", image);
                }
            }
        } catch (JSONException | RuntimeException failure) {
            throw new IOException("无法生成备份，请重新选择保存位置", failure);
        }
    }

    static Snapshot inspect(Context context, Uri source) throws IOException {
        byte[] json = null, image = null;
        try (InputStream stream = context.getContentResolver().openInputStream(source)) {
            if (stream == null) throw new IOException("无法读取所选文件");
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(stream))) {
                for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                    switch (entry.getName()) {
                        case "backup.json" -> {
                            if (json != null) throw new IOException("备份中有重复的设置文件");
                            json = read(zip, MAX_JSON);
                        }
                        case "ball-art" -> {
                            if (image != null) throw new IOException("备份中有重复的图片文件");
                            image = read(zip, MAX_IMAGE);
                        }
                        default -> throw new IOException("文件内容不符合 KongyeFloat 备份格式");
                    }
                    zip.closeEntry();
                }
            }
            if (json == null) throw new IOException("请选择 KongyeFloat 导出的备份文件");
            JSONObject manifest = new JSONObject(new String(json, StandardCharsets.UTF_8));
            if (!"KongyeFloat".equals(manifest.getString("format"))) throw new IOException("请选择 KongyeFloat 导出的备份文件");
            if (manifest.getInt("schema") != 1) throw new IOException("此备份需要更新版本的空叶悬浮球");
            boolean notes = manifest.getBoolean("includes_notes");
            JSONObject values = manifest.getJSONObject("settings");
            if (values.length() > 3000) throw new IOException("备份中的设置数量过多");
            Map<String, Object> settings = new LinkedHashMap<>();
            for (Iterator<String> keys = values.keys(); keys.hasNext();) {
                String key = keys.next();
                if (!setting(key) && !(notes && key.equals("note"))) throw new IOException("备份中包含不支持的设置");
                Object value = values.get(key);
                settings.put(key, validate(key, value));
            }
            if (notes && !settings.containsKey("note")) throw new IOException("备份缺少便签内容");
            String digest = manifest.getString("image_sha256");
            if (image == null ? !digest.isEmpty() : !sha256(image).equals(digest)) throw new IOException("备份图片不完整，请重新选择备份");
            if (image != null) validateImage(image);
            if (Boolean.TRUE.equals(settings.get("custom_art")) && image == null) throw new IOException("备份缺少自定义悬浮球图片");
            String createdAt = manifest.getString("created_at"), appVersion = manifest.getString("app_version");
            if (createdAt.length() > 80 || appVersion.length() > 40) throw new IOException("备份信息格式不正确");
            return new Snapshot(Collections.unmodifiableMap(settings), notes, image, createdAt, appVersion);
        } catch (java.util.zip.ZipException failure) {
            throw new IOException("备份文件已损坏，请重新选择备份", failure);
        } catch (JSONException | RuntimeException failure) {
            throw new IOException("备份格式不正确，无法恢复", failure);
        }
    }

    private static Object validate(String key, Object value) throws IOException, JSONException {
        boolean valid;
        if (BOOLEANS.contains(key)) valid = value instanceof Boolean;
        else if (INTEGERS.contains(key)) {
            valid = value instanceof Number number && number.doubleValue() == number.intValue();
            if (valid) { int number = ((Number) value).intValue(); validateNumber(key, number); return number; }
        } else if (key.equals("favorites")) {
            if (!(value instanceof JSONArray array) || array.length() > 2000) throw new IOException("常用应用数据不正确");
            Set<String> packages = new LinkedHashSet<>();
            for (int i = 0; i < array.length(); i++) {
                String pkg = array.getString(i);
                if (!pkg.matches(PACKAGE_PATTERN)) throw new IOException("常用应用数据不正确");
                packages.add(pkg);
            }
            return packages;
        } else {
            valid = value instanceof String text && text.length() <= (key.equals("note") ? 100000 : 32000);
            if (valid) validateString(key, (String) value);
        }
        if (!valid) throw new IOException("备份中的设置类型不正确");
        return value;
    }

    private static void validateNumber(String key, int value) throws IOException {
        int minimum = 0, maximum = 100000;
        switch (key) {
            case "color", "subtitle_background" -> { return; }
            case "size_percent" -> { minimum = 30; maximum = 500; }
            case "edge_percent" -> { minimum = 30; maximum = 200; }
            case "opacity", "quick_opacity", "subtitle_opacity", "subtitle_cover_opacity", "subtitle_original_opacity" -> maximum = 100;
            case "subtitle_font_scale" -> { minimum = 60; maximum = 160; }
            case "subtitle_alignment" -> maximum = 2;
            case "dark_start", "dark_end" -> maximum = 1439;
            case "gif_fps" -> { minimum = 1; maximum = 60; }
            case "record_resolution", "gif_resolution" -> { minimum = 1; maximum = 4096; }
            case "record_bitrate" -> maximum = 100000000;
            case "x", "y" -> minimum = -100000;
            default -> { }
        }
        if (value < minimum || value > maximum) throw new IOException("备份中的设置数值不正确");
    }

    private static void validateString(String key, String value) throws IOException, JSONException {
        if (key.startsWith("app_mode_") && !Set.of("normal", "hidden", "disabled").contains(value)) throw new IOException("应用显示规则不正确");
        if (key.equals("theme") && !Arrays.asList(ThemePolicy.MODES).contains(value)) throw new IOException("主题设置不正确");
        if (key.startsWith("gesture_") && !Arrays.asList(Prefs.ACTIONS).contains(Prefs.canonicalAction(value))
                && !(value.startsWith("app:") && value.substring(4).matches(PACKAGE_PATTERN))) throw new IOException("手势设置不正确");
        if (key.equals("home_tools") || key.equals("quick_actions")) {
            JSONArray actions = new JSONArray(value);
            if (actions.length() > 100) throw new IOException("工具数量过多");
            for (int i = 0; i < actions.length(); i++) if (!(actions.get(i) instanceof String)) throw new IOException("工具配置不正确");
        }
        if (key.startsWith("subtitle_") && !value.matches("auto|[a-z]{2,3}(?:-[A-Za-z]{2,4})?")) throw new IOException("字幕语言设置不正确");
    }

    static synchronized void restore(Context context, Snapshot snapshot) throws IOException {
        SharedPreferences preferences = Prefs.of(context);
        Map<String, ?> before = preferences.getAll();
        byte[] previousImage = null;
        long previousModified = BallArt.file(context).lastModified();
        if (BallArt.file(context).isFile()) {
            try (InputStream in = new FileInputStream(BallArt.file(context))) { previousImage = read(in, MAX_IMAGE); }
        }
        File staged = new File(context.getCacheDir(), "backup-art-restore");
        try {
            if (snapshot.image() != null) {
                Files.write(staged.toPath(), snapshot.image());
                BallArt.importFile(context, Uri.fromFile(staged));
            }
            SharedPreferences.Editor edit = preferences.edit();
            for (String key : before.keySet()) if (setting(key)) edit.remove(key);
            for (var entry : snapshot.settings().entrySet()) put(edit, entry.getKey(), entry.getValue());
            if (snapshot.image() == null) edit.putBoolean("custom_art", false);
            // Keep the image bounds calculated by BallArt.importFile for the restored file.
            if (!edit.commit()) throw new IOException("保存设置失败，已尝试还原原有数据");
        } catch (IOException | RuntimeException failure) {
            try {
                if (previousImage == null) Files.deleteIfExists(BallArt.file(context).toPath());
                else {
                    Files.write(BallArt.file(context).toPath(), previousImage);
                    BallArt.file(context).setLastModified(previousModified);
                }
                SharedPreferences.Editor rollback = preferences.edit();
                for (String key : preferences.getAll().keySet()) if (setting(key) || key.equals("note") || key.startsWith("art_")) rollback.remove(key);
                for (var entry : before.entrySet()) if (setting(entry.getKey()) || entry.getKey().equals("note") || entry.getKey().startsWith("art_")) put(rollback, entry.getKey(), entry.getValue());
                if (!rollback.commit()) throw new IOException("恢复失败，请检查手机存储空间");
            } catch (IOException recoveryFailure) { failure.addSuppressed(recoveryFailure); }
            throw new IOException("恢复失败，请检查手机存储空间后重试", failure);
        } finally { staged.delete(); }
    }

    private static void put(SharedPreferences.Editor edit, String key, Object value) {
        if (value instanceof Boolean flag) edit.putBoolean(key, flag);
        else if (value instanceof Integer number) edit.putInt(key, number);
        else if (value instanceof Long number) edit.putLong(key, number);
        else if (value instanceof Float number) edit.putFloat(key, number);
        else if (value instanceof String text) edit.putString(key, text);
        else if (value instanceof Set<?> set) {
            Set<String> strings = new HashSet<>();
            for (Object item : set) strings.add((String) item);
            edit.putStringSet(key, strings);
        }
    }

    private static void validateImage(byte[] image) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(image, 0, image.length, options);
        if (options.outWidth < 1 || options.outHeight < 1 || options.outWidth > 4096 || options.outHeight > 4096
                || (long) options.outWidth * options.outHeight > 16000000) throw new IOException("备份中的悬浮球图片无效");
    }

    private static byte[] read(InputStream stream, int maximum) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int count; (count = stream.read(buffer)) != -1;) {
            if (count > maximum - bytes.size()) throw new IOException("备份文件过大，无法读取");
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] value) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(value); zip.closeEntry();
    }

    private static String sha256(byte[] value) throws IOException {
        try {
            StringBuilder text = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(value)) text.append(String.format(Locale.US, "%02x", b & 255));
            return text.toString();
        } catch (java.security.NoSuchAlgorithmException failure) { throw new IOException("无法校验备份", failure); }
    }

    static String version(Context context) {
        try { return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException failure) { return ""; }
    }
}
