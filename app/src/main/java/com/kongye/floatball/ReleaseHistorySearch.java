package com.kongye.floatball;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Visit-local, sequential search index. No Android, disk or network dependencies. */
final class ReleaseHistorySearch {
    // At most 500 bounded release bodies; do not crawl an unbounded remote history.
    static final int MAX_PAGES = 50;
    static final int PAGE_SIZE = 10;
    private final LinkedHashMap<String, ReleaseHistory.Release> releases = new LinkedHashMap<>();
    private final ArrayList<ReleaseHistory.Release> matches = new ArrayList<>();
    private String query = "";
    private int nextPage = 1, loadedPages;

    void query(String value) {
        query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        matches.clear();
        for (ReleaseHistory.Release release : releases.values()) if (matches(release)) matches.add(release);
    }
    private boolean matches(ReleaseHistory.Release release) {
        return !query.isEmpty() && (release.tag.toLowerCase(Locale.ROOT).contains(query)
                || release.title.toLowerCase(Locale.ROOT).contains(query)
                || release.notes.toLowerCase(Locale.ROOT).contains(query));
    }
    void add(int page, ReleaseHistory.Page result) {
        if (complete() || capped() || page != nextPage) throw new IllegalArgumentException("Non-sequential search page");
        if (result.nextPage != 0 && result.nextPage != page + 1) throw new IllegalArgumentException("Invalid next page");
        for (ReleaseHistory.Release release : result.releases) {
            // A release may shift across API pages while a new version is published.
            if (!releases.containsKey(release.tag)) {
                releases.put(release.tag, release);
                if (matches(release)) matches.add(release);
            }
        }
        loadedPages++;
        nextPage = result.nextPage;
    }
    int nextPage() { return nextPage; }
    int loadedPages() { return loadedPages; }
    int releaseCount() { return releases.size(); }
    int matchCount() { return matches.size(); }
    int pageCount() { return Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE); }
    boolean complete() { return nextPage == 0; }
    boolean capped() { return !complete() && loadedPages >= MAX_PAGES; }
    List<ReleaseHistory.Release> page(int number) {
        int start = (Math.max(1, Math.min(number, pageCount())) - 1) * PAGE_SIZE;
        return Collections.unmodifiableList(new ArrayList<>(matches.subList(start, Math.min(start + PAGE_SIZE, matches.size()))));
    }
}
