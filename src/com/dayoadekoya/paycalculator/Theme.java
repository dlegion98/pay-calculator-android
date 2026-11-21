package com.dayoadekoya.paycalculator;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;

/**
 * Design tokens inspired by One UI 9 (calm neutral canvas, roomy white/graphite cards with
 * generous radii) and Android 17's Material 3 Expressive (dynamic colour, tonal containers,
 * bold numerics).
 */
public final class Theme {
    private Theme() {}

    public static boolean dark;
    public static boolean oled;
    public static int accent, onAccent, accentContainer, onAccentContainer, accentSoft;
    public static int bg, surface, surfaceHigh, field, text, text2, text3, divider, scrim;
    public static int green, red, orange, purple, teal, pink, indigo, yellow, blue, cyan;

    public static final String[] THEMES = {"Dynamic", "Blue", "Mint", "Red", "Green", "Purple", "Orange", "Night Shift (OLED)"};

    public static int namedColor(Context c, String name) {
        switch (name) {
            case "Red": return dark ? 0xFFFF453A : 0xFFFF3B30;
            case "Green": return dark ? 0xFF30D158 : 0xFF34C759;
            case "Mint": return dark ? 0xFF63E6E2 : 0xFF00C7BE;
            case "Purple": return dark ? 0xFFBF5AF2 : 0xFFAF52DE;
            case "Orange": return dark ? 0xFFFF9F0A : 0xFFFF9500;
            case "Night Shift (OLED)": return 0xFFFFFFFF;
            case "Blue": return dark ? 0xFF0A84FF : 0xFF007AFF;
            case "Dynamic":
            default:
                if (Build.VERSION.SDK_INT >= 31) {
                    try {
                        return c.getColor(dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600);
                    } catch (Throwable ignored) {}
                }
                return 0xFF3E7BFA;
        }
    }

    public static void apply(Context c) {
        String appearance = Store.prefs(c).getString("appearanceMode", "System");
        String theme = Store.prefs(c).getString("appThemeColor", "Blue");
        oled = "Night Shift (OLED)".equals(theme);
        if ("Light".equals(appearance)) dark = false;
        else if ("Dark".equals(appearance)) dark = true;
        else {
            int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            dark = night == Configuration.UI_MODE_NIGHT_YES;
        }
        if (oled) dark = true;

        if (dark) {
            // iOS dark: true-black grouped background with elevated #1C1C1E cells
            bg = 0xFF000000;
            surface = oled ? 0xFF111113 : 0xFF1C1C1E;
            surfaceHigh = oled ? 0xFF1C1C1E : 0xFF2C2C2E;
            field = oled ? 0xFF1C1C1E : 0xFF2C2C2E;
            text = 0xFFFFFFFF;
            text2 = 0xFF8E8E93;
            text3 = 0xFF5A5A5F;
            divider = 0xFF38383A;
            scrim = 0x99000000;
        } else {
            // iOS light: systemGroupedBackground with white cells
            bg = 0xFFF2F2F7;
            surface = 0xFFFFFFFF;
            surfaceHigh = 0xFFF2F2F7;
            field = 0xFFEEEEF0;
            text = 0xFF000000;
            text2 = 0xFF8A8A8E;
            text3 = 0xFFC4C4C7;
            divider = 0xFFD8D8DC;
            scrim = 0x66000000;
        }
        accent = namedColor(c, theme);
        heroBase = computeHeroBase(c, theme);
        onAccent = isLight(accent) ? 0xFF101010 : 0xFFFFFFFF;
        accentContainer = blend(accent, dark ? surface : 0xFFFFFFFF, dark ? 0.72f : 0.84f);
        onAccentContainer = dark ? blend(accent, 0xFFFFFFFF, 0.25f) : blend(accent, 0xFF000000, 0.35f);
        accentSoft = withAlpha(accent, dark ? 0.22f : 0.12f);

        // iOS system colours
        green = dark ? 0xFF30D158 : 0xFF34C759;
        red = dark ? 0xFFFF453A : 0xFFFF3B30;
        orange = dark ? 0xFFFF9F0A : 0xFFFF9500;
        purple = dark ? 0xFFBF5AF2 : 0xFFAF52DE;
        teal = dark ? 0xFF40C8E0 : 0xFF30B0C7;
        pink = dark ? 0xFFFF375F : 0xFFFF2D55;
        indigo = dark ? 0xFF5E5CE6 : 0xFF5856D6;
        yellow = dark ? 0xFFFFD60A : 0xFFFFCC00;
        blue = dark ? 0xFF0A84FF : 0xFF007AFF;
        cyan = dark ? 0xFF64D2FF : 0xFF32ADE6;
    }

    /**
     * Hero cards always use a deep, saturated version of the theme colour (in dark mode the
     * accent itself is a light pastel for contrast on black, which made hero cards wash out).
     */
    public static int heroBase;

    private static int computeHeroBase(Context c, String theme) {
        if ("Dynamic".equals(theme) && Build.VERSION.SDK_INT >= 31) {
            try { return c.getColor(dark ? android.R.color.system_accent1_700 : android.R.color.system_accent1_600); }
            catch (Throwable ignored) {}
        }
        int base = namedColorLight(theme);
        if (isLight(base, 0.58)) base = blend(base, 0xFF000000, 0.4f);
        return dark ? blend(base, 0xFF000000, 0.12f) : base;
    }

    private static int namedColorLight(String name) {
        switch (name) {
            case "Red": return 0xFFFF3B30;
            case "Green": return 0xFF28A745;
            case "Mint": return 0xFF00A89F;
            case "Purple": return 0xFFAF52DE;
            case "Orange": return 0xFFF08A00;
            default: return 0xFF007AFF;
        }
    }

    public static int heroStart() {
        if (oled) return 0xFF26262B;
        return heroBase;
    }

    public static int heroEnd() {
        if (oled) return 0xFF0E0E10;
        return blend(heroBase, dark ? 0xFF000000 : 0xFF1A1030, dark ? 0.38f : 0.30f);
    }

    /** Second hue used inside hero gradients. */
    public static int heroSecondary() {
        return oled ? 0xFF3A3A44 : Glass.hueShift(heroBase, 14);
    }

    public static int onHero() {
        if (oled) return 0xFFFFFFFF;
        return isLight(blend(heroStart(), heroEnd(), 0.5f), 0.6) ? 0xFF111111 : 0xFFFFFFFF;
    }

    public static boolean isLight(int color, double threshold) {
        double l = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return l > threshold;
    }

    public static boolean isLight(int color) {
        double l = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return l > 0.72;
    }

    public static int withAlpha(int color, float a) {
        return (Math.round(a * 255) << 24) | (color & 0x00FFFFFF);
    }

    /** Mix `a` toward `b` by `t` (0 = a, 1 = b). */
    public static int blend(int a, int b, float t) {
        int r = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.argb(255, r, g, bl);
    }
}
