package com.kongye.floatball;

public final class ResponsiveLayoutCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static int controlPixels(int width, int height, float density) {
        return Math.round(48 * density * ResponsiveLayout.scale(width / density, height / density));
    }

    public static void main(String[] args) {
        int standard = controlPixels(1220, 2640, 3f);
        int enlarged = controlPixels(1220, 2640, 3.4f);
        check(standard == enlarged, "A higher display density must not enlarge the same phone layout");
        check(standard == controlPixels(2640, 1220, 3f), "Rotation must preserve the scale");
        check(ResponsiveLayout.scale(720, 1000) == 1f, "Wide layouts should keep their base sizes");
        check(ResponsiveLayout.scale(0, 0) == 1f, "Unspecified bounds should use base sizes");
        check(ResponsiveLayout.scale(240, 560) == .82f, "Very narrow windows should reflow instead of shrinking indefinitely");
        check(ResponsiveLayout.gridColumns(340, 68) == 4, "The home grid should keep four columns on a normal phone");
        check(ResponsiveLayout.gridColumns(230, 68) == 3, "The home grid should fit three columns in a narrow window");
        check(ResponsiveLayout.gridColumns(340, 90) == 3, "A larger font should get wider cells");
        check(ResponsiveLayout.gridColumns(0, 68) == 1, "The home grid must always have at least one column");
        System.out.println("9 responsive layout checks passed");
    }
}
