package com.kongye.floatball;

/** Plain-JVM pagination, API cooldown and pre-connect cancellation contracts. */
public final class ReleaseHistoryCheck {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        String next = "<" + ReleaseHistory.API + "?per_page=10&page=2>; rel=\"next\", <" + ReleaseHistory.API + "?page=8>; rel=\"last\"";
        check(ReleaseHistory.nextPage(next, 1) == 2, "next Link page");
        check(ReleaseHistory.nextPage("<https://api.github.com/repositories/123/releases?page=3>; rel=\"next\"", 2) == 3, "GitHub canonical repository link");
        check(ReleaseHistory.nextPage(null, 1) == 0, "absent Link ends feed");
        for (String link : new String[]{"https://evil.example/releases?page=2", ReleaseHistory.API + "?page=1", ReleaseHistory.API + "?page=4", "http://api.github.com/repos/Mayleaves/KongyeFloat/releases?page=2", "https://user@api.github.com/repos/Mayleaves/KongyeFloat/releases?page=2", "https://api.github.com/repos/Other/Repo/releases?page=2"}) check(ReleaseHistory.nextPage("<" + link + ">; rel=\"next\"", 1) == 0, "reject wrong origin/page " + link);
        long now = 1_000_000L;
        check(ReleaseHistory.retryAt("120", null, null, now) == now + 120_000, "Retry-After seconds");
        check(ReleaseHistory.retryAt(null, "0", "2000", now) == 2_000_000, "exhausted primary quota reset");
        check(ReleaseHistory.retryAt("120", "0", "2000", now) == 2_000_000, "respect later of cooldowns");
        check(ReleaseHistory.retryAt(null, "1", "2000", now) == 0, "remaining quota does not block");
        check(ReleaseHistory.retryAt("invalid", "0", "bad", now) == 0, "malformed rate headers safe");
        check(ReleaseHistory.retryAt("Thu, 01 Jan 1970 00:20:00 GMT", null, null, now) == 1_200_000, "HTTP-date cooldown");
        ReleaseHistory.Request cancelled = new ReleaseHistory.Request(); cancelled.cancel(); boolean stopped = false;
        try { cancelled.load(1); } catch (java.io.InterruptedIOException expected) { stopped = true; }
        check(stopped, "cancellation checked before network");
        System.out.println(checks + " release history contract checks passed.");
    }
}
