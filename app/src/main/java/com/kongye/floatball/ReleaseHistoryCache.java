package com.kongye.floatball;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded local persistence for release history so visits and page turns work without re-fetching. */
final class ReleaseHistoryCache {
    static final int MAX_CATALOG_BYTES = 4 * 1024 * 1024;
    static final int MAX_CATALOG_ITEMS = ReleaseHistory.CATALOG_PAGE_SIZE * ReleaseHistory.MAX_CATALOG_PAGES;
    private static volatile CatalogSnapshot memoryCatalog;

    static final class Snapshot {
        final ReleaseHistory.Page page;
        final long savedAt;
        Snapshot(ReleaseHistory.Page page, long savedAt) { this.page = page; this.savedAt = savedAt; }
    }

    static final class CatalogSnapshot {
        final List<ReleaseHistory.Release> releases;
        final boolean complete;
        final long savedAt;
        CatalogSnapshot(List<ReleaseHistory.Release> releases, boolean complete, long savedAt) {
            this.releases = Collections.unmodifiableList(new ArrayList<>(releases));
            this.complete = complete;
            this.savedAt = savedAt;
        }
    }

    static CatalogSnapshot peekMemory() { return memoryCatalog; }

    static CatalogSnapshot readCatalog(File directory) {
        CatalogSnapshot mem = memoryCatalog;
        if (mem != null && mem.complete) return mem;
        File file = new File(directory, "release-catalog.json");
        if (file.isFile() && file.length() <= MAX_CATALOG_BYTES) {
            try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted() || bytes.size() + count > MAX_CATALOG_BYTES) return null;
                    bytes.write(buffer, 0, count);
                }
                JSONObject object = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                if (object.optInt("schema", 0) == 2) {
                    JSONArray arr = object.getJSONArray("releases");
                    if (arr.length() <= MAX_CATALOG_ITEMS) {
                        ReleaseHistory.Page parsed = ReleaseHistory.parsePage(arr.toString(), 1, MAX_CATALOG_ITEMS, null, 0);
                        CatalogSnapshot snap = new CatalogSnapshot(parsed.releases, object.optBoolean("complete", true), object.optLong("saved_at", 0));
                        memoryCatalog = snap;
                        return snap;
                    }
                }
            } catch (Exception ignored) { }
        }
        Snapshot legacy = read(directory);
        if (legacy != null) {
            CatalogSnapshot migrated = new CatalogSnapshot(legacy.page.releases, legacy.page.nextPage == 0, legacy.savedAt);
            memoryCatalog = migrated;
            return migrated;
        }
        return null;
    }

    static void writeCatalog(File directory, List<ReleaseHistory.Release> releases, boolean complete) {
        long now = System.currentTimeMillis();
        List<ReleaseHistory.Release> bounded = releases.size() > MAX_CATALOG_ITEMS ? releases.subList(0, MAX_CATALOG_ITEMS) : releases;
        memoryCatalog = new CatalogSnapshot(bounded, complete, now);
        File temporary = null;
        try {
            JSONArray array = new JSONArray();
            for (ReleaseHistory.Release release : bounded) {
                array.put(new JSONObject().put("tag_name", release.tag).put("name", release.title).put("published_at", release.published).put("body", release.notes));
            }
            byte[] bytes = new JSONObject()
                    .put("schema", 2)
                    .put("saved_at", now)
                    .put("complete", complete)
                    .put("releases", array)
                    .toString()
                    .getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_CATALOG_BYTES || Thread.currentThread().isInterrupted()) return;
            temporary = File.createTempFile("release-catalog-", ".tmp", directory);
            try (FileOutputStream output = new FileOutputStream(temporary)) { output.write(bytes); output.getFD().sync(); }
            if (Thread.currentThread().isInterrupted()) return;
            Files.move(temporary.toPath(), new File(directory, "release-catalog.json").toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            // Keep first-page snapshot updated as well.
            List<ReleaseHistory.Release> firstPage = bounded.subList(0, Math.min(ReleaseHistory.PAGE_SIZE, bounded.size()));
            write(directory, new ReleaseHistory.Page(firstPage, bounded.size() > ReleaseHistory.PAGE_SIZE ? 2 : 0, 0));
        } catch (Exception ignored) {
            /* A failed cache must never turn a successful request into an error. */
        } finally {
            if (temporary != null && temporary.exists()) temporary.delete();
        }
    }

    static Snapshot read(File directory) {
        File file = new File(directory, "release-history.json");
        if (!file.isFile() || file.length() > ReleaseHistory.MAX_RESPONSE) return null;
        try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted() || bytes.size() + count > ReleaseHistory.MAX_RESPONSE) return null;
                bytes.write(buffer, 0, count);
            }
            JSONObject object = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            if (object.optInt("schema", 0) != 1) return null;
            int next = object.optInt("next", 0);
            if (next != 0 && next != 2) return null;
            ReleaseHistory.Page parsed = ReleaseHistory.parse(object.getJSONArray("releases").toString(), 1, null, 0);
            return new Snapshot(new ReleaseHistory.Page(parsed.releases, next, 0), object.optLong("saved_at", 0));
        } catch (Exception invalid) { return null; }
    }

    static void write(File directory, ReleaseHistory.Page page) {
        File temporary = null;
        try {
            JSONArray array = new JSONArray();
            for (ReleaseHistory.Release release : page.releases) array.put(new JSONObject().put("tag_name", release.tag).put("name", release.title).put("published_at", release.published).put("body", release.notes));
            byte[] bytes = new JSONObject().put("schema", 1).put("saved_at", System.currentTimeMillis()).put("next", page.nextPage).put("releases", array).toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > ReleaseHistory.MAX_RESPONSE || Thread.currentThread().isInterrupted()) return;
            temporary = File.createTempFile("release-history-", ".tmp", directory);
            try (FileOutputStream output = new FileOutputStream(temporary)) { output.write(bytes); output.getFD().sync(); }
            if (Thread.currentThread().isInterrupted()) return;
            Files.move(temporary.toPath(), new File(directory, "release-history.json").toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ignored) { /* A failed cache must never turn a successful request into an error. */ }
        finally { if (temporary != null && temporary.exists()) temporary.delete(); }
    }

    private ReleaseHistoryCache() { }
}
