package com.kongye.floatball;

/** Shared size rules for activities and floating windows. */
final class ResponsiveLayout {
    private static final float PHONE_REFERENCE_DP = 432f;
    private static final float MINIMUM_SCALE = 0.82f;

    private ResponsiveLayout() {}

    static float scale(float widthDp, float heightDp) {
        if (widthDp <= 0 || heightDp <= 0) return 1f;
        float shortSide = Math.min(widthDp, heightDp);
        return Math.max(MINIMUM_SCALE, Math.min(1f, shortSide / PHONE_REFERENCE_DP));
    }

    static int gridColumns(int availableWidth, int minimumTileWidth) {
        return Math.max(1, Math.min(4, availableWidth / Math.max(1, minimumTileWidth)));
    }
}
