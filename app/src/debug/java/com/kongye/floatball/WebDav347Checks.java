package com.kongye.floatball;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import okhttp3.HttpUrl;
import org.json.*;

final class WebDav347Checks {
    static WebDavSettings.Config fixture(String folder) {
        return new WebDavSettings.Config("http://127.0.0.1:18767/dav/", "fixture-user", "fixture-password", folder);
    }

    static void configure(Context context) throws IOException { WebDavSettings.save(context, fixture("KongyeFloat")); }

    static void run(Context context) {
        new Thread(() -> {
            List<String> passed = new ArrayList<>(); String error = "";
            var preferences = context.getSharedPreferences("backup_webdav", Context.MODE_PRIVATE);
            String original = preferences.getString("encrypted", null);
            try {
                var config = fixture("空叶备份/子目录");
                WebDavSettings.save(context, fixture(WebDavSettings.FOLDER));
                check(WebDavSettings.load(context).equals(fixture(WebDavSettings.FOLDER)), "encrypted connection round trip", passed);
                check(!preferences.getString("encrypted", "").contains("fixture-password"), "credentials not stored as plaintext", passed);
                WebDavBackup client = new WebDavBackup(config);
                client.prepareDirectory(); client.test();
                check(client.list().isEmpty(), "nested Unicode directory creation and empty list", passed);
                File exported = new File(context.getCacheDir(), "dav-test-export.zip");
                BackupStore.export(context, Uri.fromFile(exported), true);
                var snapshot = BackupStore.inspect(context, Uri.fromFile(exported));
                check(!snapshot.settings().containsKey("encrypted") && !snapshot.settings().containsKey("backup_webdav"), "credentials excluded from backup", passed);
                String name = "KongyeFloat_test_" + UUID.randomUUID() + ".zip";
                client.upload(exported, name);
                var files = client.list();
                check(files.size() == 1 && files.get(0).name().equals(name) && files.get(0).size() == exported.length(), "PUT MOVE and directory listing", passed);
                File downloaded = new File(context.getCacheDir(), "dav-test-download.zip");
                client.download(files.get(0), downloaded);
                check(Arrays.equals(Files.readAllBytes(exported.toPath()), Files.readAllBytes(downloaded.toPath())), "downloaded ZIP bytes match upload", passed);
                check(BackupStore.inspect(context, Uri.fromFile(downloaded)).settings().equals(snapshot.settings()), "cloud archive accepted by restore reader", passed);
                var stale = files.get(0);
                try { client.download(new WebDavBackup.Entry(stale.name(), stale.url(), stale.size(), stale.modified(), "\"changed\""), downloaded); throw new AssertionError("stale accepted"); }
                catch (IOException expected) { passed.add("changed remote file rejected"); }
                try { client.upload(exported, name); throw new AssertionError("overwritten"); }
                catch (IOException expected) { passed.add("existing backup is not overwritten"); }
                check(client.list().size() == 1, "failed overwrite staging file removed", passed);
                client.delete(files.get(0));
                check(client.list().isEmpty(), "deleted backup disappears from listing", passed);
                try { new WebDavBackup(new WebDavSettings.Config(config.server(), "wrong", "wrong", config.folder())).test(); throw new AssertionError("auth accepted"); }
                catch (IOException expected) { passed.add("authentication failure reported"); }
                for (String folder : List.of("redirect", "malformed", "invalidxml")) {
                    try { new WebDavBackup(fixture(folder)).test(); throw new AssertionError(folder); }
                    catch (IOException expected) { passed.add(folder + " rejected"); }
                }
                WebDavBackup failed = new WebDavBackup(fixture("failedmove"));
                try { failed.upload(exported, "KongyeFloat_failed.zip"); throw new AssertionError("failed MOVE accepted"); }
                catch (IOException expected) { passed.add("failed publication reported"); }
                check(failed.list().isEmpty(), "failed upload leaves no listed partial archive", passed);
                String xml = "<d:multistatus xmlns:d=\"DAV:\">";
                for (String href : List.of("/dav/KongyeFloat_safe.zip", "http://other.invalid/dav/KongyeFloat_leak.zip",
                        "/outside/KongyeFloat_wrong.zip", "/dav/child/KongyeFloat_wrong.zip", "/dav/unrelated.zip")) {
                    xml += "<d:response><d:href>" + href + "</d:href><d:propstat><d:prop/><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>";
                }
                xml += "</d:multistatus>";
                check(WebDavBackup.parse(xml.getBytes(StandardCharsets.UTF_8), HttpUrl.get(config.server())).size() == 1,
                        "foreign origins paths and unrelated files ignored", passed);
                try { new WebDavBackup(new WebDavSettings.Config("http://user:password@example.com/", "", "", "")); throw new AssertionError("URL credentials accepted"); }
                catch (IOException expected) { passed.add("credentials in URL rejected"); }
                try { new WebDavBackup(fixture("../outside")); throw new AssertionError("traversal accepted"); }
                catch (IOException expected) { passed.add("directory traversal rejected"); }
            } catch (Exception | AssertionError failure) { error = failure.toString(); }
            finally {
                if (original == null) preferences.edit().remove("encrypted").commit();
                else preferences.edit().putString("encrypted", original).commit();
                try { Prefs.of(context).edit().putString("qa_webdav347", new JSONObject().put("passed", new JSONArray(passed)).put("error", error).toString()).commit(); }
                catch (JSONException ignored) { }
            }
        }, "Kongye-WebDAV-Checks").start();
    }

    private static void check(boolean ok, String label, List<String> passed) {
        if (!ok) throw new AssertionError(label); passed.add(label);
    }
}
