package com.kongye.floatball;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Plain JVM checks for all-page matching, resumable scans and local pagination. */
public final class ReleaseHistorySearchCheck {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static ReleaseHistory.Release release(String tag, String title, String notes) {
        return new ReleaseHistory.Release(tag, title, "", notes);
    }
    public static void main(String[] args) {
        ReleaseHistorySearch search = new ReleaseHistorySearch(); search.query("fix");
        for (int page = 1; page <= 3; page++) {
            List<ReleaseHistory.Release> releases = new ArrayList<>();
            for (int i = 0; i < 10; i++) releases.add(release("v" + ((page - 1) * 10 + i), "FIX title", page == 3 ? "older-only" : ""));
            search.add(page, new ReleaseHistory.Page(releases, page == 3 ? 0 : page + 1, 0));
            check(search.loadedPages() == page, "incremental progress");
        }
        check(search.complete() && !search.capped() && search.releaseCount() == 30, "reaches actual end");
        check(search.matchCount() == 30 && search.pageCount() == 3, "30 results on three local pages");
        check(search.page(1).size() == 10 && search.page(2).get(0).tag.equals("v10") && search.page(3).get(9).tag.equals("v29"), "stable ordered pages");
        search.query("older-only"); check(search.matchCount() == 10 && search.pageCount() == 1, "notes match only on oldest API page");
        search.query(" V29 "); check(search.matchCount() == 1, "trimmed case-insensitive version match");
        search.query("missing"); check(search.matchCount() == 0 && search.pageCount() == 1 && search.page(1).isEmpty(), "empty search results");
        search.query(""); check(search.matchCount() == 0 && search.complete(), "clearing query retains catalog");
        ReleaseHistorySearch partial = new ReleaseHistorySearch(); partial.query("fix");
        partial.add(1, new ReleaseHistory.Page(Collections.singletonList(release("v1", "fix", "")), 2, 0));
        check(!partial.complete() && partial.nextPage() == 2, "cancel/pause preserves pending cursor");
        partial.query("OLD");
        partial.add(2, new ReleaseHistory.Page(Collections.emptyList(), 3, 0));
        check(!partial.complete() && partial.nextPage() == 3, "filtered-empty API page still advances");
        partial.add(3, new ReleaseHistory.Page(java.util.Arrays.asList(release("v1", "duplicate", "old"), release("v2", "old", "")), 0, 0));
        check(partial.releaseCount() == 2 && partial.matchCount() == 1, "dedup tags across shifted API pages");
        boolean rejected = false;
        try { new ReleaseHistorySearch().add(2, new ReleaseHistory.Page(Collections.emptyList(), 0, 0)); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "cannot silently skip history pages");
        ReleaseHistorySearch bounded = new ReleaseHistorySearch();
        for (int page = 1; page <= ReleaseHistorySearch.MAX_PAGES; page++) bounded.add(page, new ReleaseHistory.Page(Collections.emptyList(), page + 1, 0));
        check(bounded.capped() && !bounded.complete(), "bounded catalog does not claim complete history");
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            ReleaseHistorySearch locale = new ReleaseHistorySearch(); locale.query("fix");
            locale.add(1, new ReleaseHistory.Page(Collections.singletonList(release("v1", "FIX", "")), 0, 0));
            check(locale.matchCount() == 1, "locale-independent case folding");
        } finally { Locale.setDefault(original); }
        System.out.println(checks + " release history search checks passed.");
    }
}
