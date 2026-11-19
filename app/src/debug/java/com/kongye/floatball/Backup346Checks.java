package com.kongye.floatball;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Generated backup data only, invoked on the dedicated test emulator. */
final class Backup346Checks {
    static void run(Context context) {
        new Thread(() -> {
            SharedPreferences preferences = Prefs.of(context);
            Map<String, ?> original = preferences.getAll();
            File art = BallArt.file(context);
            byte[] previous = null;
            ArrayList<String> passed = new ArrayList<>();
            String error = null;
            try {
                if (art.isFile()) previous = Files.readAllBytes(art.toPath());
                preferences.edit().clear().putString("theme", "dark").putInt("size_percent", 137)
                        .putBoolean("snap", false).putString("gesture_up", "notes")
                        .putString("home_tools", "[\"notes\",\"region\"]")
                        .putString("quick_actions", "[\"notes\",\"region\"]")
                        .putStringSet("favorites", Set.of("com.kongye.floatball"))
                        .putString("app_mode_com.kongye.fixture", "hidden")
                        .putString("note", "Generated backup note\n备份测试")
                        .putBoolean("ball_enabled", true).putString("vault_package", "private-test")
                        .putString("last_capture_uri", "generated://private").putString("qa_private", "excluded").commit();
                File image = new File(context.getCacheDir(), "backup-test.png");
                Bitmap bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888); bitmap.eraseColor(0xFF25553D);
                try (OutputStream out = new FileOutputStream(image)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
                bitmap.recycle(); BallArt.importFile(context, Uri.fromFile(image));
                byte[] expectedImage = Files.readAllBytes(art.toPath());
                String filename = BackupStore.filename();
                check(filename.matches("KongyeFloat_\\d{8}_\\d{6}\\.zip"), "filename", passed);
                File full = new File(context.getCacheDir(), "backup-full.zip");
                BackupStore.export(context, Uri.fromFile(full), true);
                BackupStore.Snapshot saved = BackupStore.inspect(context, Uri.fromFile(full));
                check(saved.includesNotes() && saved.settings().get("note").equals("Generated backup note\n备份测试"), "notes included", passed);
                check(!saved.settings().containsKey("ball_enabled") && !saved.settings().containsKey("vault_package")
                        && !saved.settings().containsKey("last_capture_uri") && !saved.settings().containsKey("qa_private"), "runtime and access data excluded", passed);
                preferences.edit().putString("theme", "light").putString("note", "changed")
                        .putString("app_mode_com.kongye.extra", "disabled").putBoolean("ball_enabled", false).commit();
                Files.delete(art.toPath());
                BackupStore.restore(context, saved);
                check(preferences.getString("theme", "").equals("dark") && preferences.getInt("size_percent", 0) == 137
                        && preferences.getString("gesture_up", "").equals("notes") && !preferences.getBoolean("snap", true), "settings round trip", passed);
                check(preferences.getStringSet("favorites", Set.of()).equals(Set.of("com.kongye.floatball"))
                        && preferences.getString("app_mode_com.kongye.fixture", "").equals("hidden")
                        && !preferences.contains("app_mode_com.kongye.extra"), "favorites and application rules", passed);
                check(Arrays.equals(expectedImage, Files.readAllBytes(art.toPath())) && preferences.getBoolean("custom_art", false), "custom image round trip", passed);
                check(preferences.getString("note", "").equals("Generated backup note\n备份测试")
                        && !preferences.getBoolean("ball_enabled", true), "note restored and current switch preserved", passed);
                File withoutNotes = new File(context.getCacheDir(), "backup-settings.zip");
                BackupStore.export(context, Uri.fromFile(withoutNotes), false);
                BackupStore.Snapshot settingsOnly = BackupStore.inspect(context, Uri.fromFile(withoutNotes));
                preferences.edit().putString("note", "Keep this note").commit();
                BackupStore.restore(context, settingsOnly);
                check(!settingsOnly.includesNotes() && !settingsOnly.settings().containsKey("note")
                        && preferences.getString("note", "").equals("Keep this note"), "settings-only restore preserves notes", passed);
                JSONObject manifest = manifest(full);
                reject(context, new JSONObject(manifest.toString()).put("format", "OtherApp"), "wrong application", passed);
                reject(context, new JSONObject(manifest.toString()).put("schema", 2), "future schema", passed);
                JSONObject invalid = new JSONObject(manifest.toString()); invalid.getJSONObject("settings").put("theme", 23);
                reject(context, invalid, "incorrect preference type", passed);
                invalid = new JSONObject(manifest.toString()); invalid.getJSONObject("settings").put("ball_enabled", true);
                reject(context, invalid, "runtime settings injection", passed);
                invalid = new JSONObject(manifest.toString()); invalid.getJSONObject("settings").put("size_percent", -1);
                reject(context, invalid, "out of range value", passed);
                reject(context, manifest, "missing image", passed);
                File broken = new File(context.getCacheDir(), "backup-broken.zip"); Files.write(broken.toPath(), new byte[]{1,2,3});
                try { BackupStore.inspect(context, Uri.fromFile(broken)); throw new AssertionError("invalid file accepted"); }
                catch (IOException expected) { passed.add("invalid file rejected"); }
                File blocker = new File(context.getCacheDir(), "backup-art-restore"); Files.deleteIfExists(blocker.toPath());
                if (!blocker.mkdir()) throw new IOException("test staging directory unavailable");
                try { BackupStore.restore(context, saved); throw new AssertionError("failed restore accepted"); }
                catch (IOException expected) {
                    check(preferences.getString("note", "").equals("Keep this note")
                            && Arrays.equals(expectedImage, Files.readAllBytes(art.toPath())), "failed restore keeps previous data", passed);
                } finally { blocker.delete(); }
            } catch (Exception | AssertionError failure) { error = failure.toString(); }
            finally {
                try {
                    if (previous == null) Files.deleteIfExists(art.toPath()); else Files.write(art.toPath(), previous);
                    SharedPreferences.Editor editor = preferences.edit().clear();
                    for (var entry : original.entrySet()) {
                        Object value = entry.getValue(); String key = entry.getKey();
                        if (value instanceof Boolean v) editor.putBoolean(key, v);
                        else if (value instanceof Integer v) editor.putInt(key, v);
                        else if (value instanceof Long v) editor.putLong(key, v);
                        else if (value instanceof Float v) editor.putFloat(key, v);
                        else if (value instanceof String v) editor.putString(key, v);
                        else if (value instanceof Set<?> v) editor.putStringSet(key, new HashSet<>((Set<String>)v));
                    }
                    editor.commit();
                    JSONObject result = new JSONObject().put("passed", new org.json.JSONArray(passed)).put("error", error == null ? "" : error);
                    preferences.edit().putString("qa_backup346", result.toString()).commit();
                } catch (Exception failure) { preferences.edit().putString("qa_backup346", failure.toString()).commit(); }
            }
        }, "Kongye-Backup-Checks").start();
    }

    private static void check(boolean value, String label, List<String> passed) {
        if (!value) throw new AssertionError(label); passed.add(label);
    }

    private static JSONObject manifest(File file) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(file))) {
            if (!zip.getNextEntry().getName().equals("backup.json")) throw new AssertionError("manifest entry");
            return new JSONObject(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static void reject(Context context, JSONObject manifest, String label, List<String> passed) throws Exception {
        File file = new File(context.getCacheDir(), "backup-invalid.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("backup.json")); zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        try { BackupStore.inspect(context, Uri.fromFile(file)); throw new AssertionError(label); }
        catch (IOException expected) { passed.add(label + " rejected"); }
    }
}
