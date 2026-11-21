package com.dayoadekoya.paycalculator;

import android.animation.TimeInterpolator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

/** Small view-building kit for the liquid-glass One UI 9 / ColorOS 17 / HyperOS 3 look. */
public final class Ui {
    private Ui() {}

    public interface Fn<T> { void on(T value); }

    public static Context c;
    public static float density = 3f;
    public static int insetTop, insetBottom;

    public static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    public static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    public static void init(Context ctx) {
        c = ctx;
        density = ctx.getResources().getDisplayMetrics().density;
    }

    public static int dp(float v) { return Math.round(v * density); }

    // ------------------------------------------------------------------ motion

    /** A gentle damped spring with a little overshoot — the M3 Expressive "spatial" feel. */
    public static final TimeInterpolator SPRING = new TimeInterpolator() {
        @Override public float getInterpolation(float t) {
            return (float) (1 - Math.exp(-6.0 * t) * Math.cos(7.5 * t));
        }
    };

    /** Critically-damped-ish emphasised decelerate. */
    public static final TimeInterpolator EMPHASIZED = new TimeInterpolator() {
        @Override public float getInterpolation(float t) {
            return (float) (1 - Math.pow(1 - t, 3.2));
        }
    };

    /** True when the user has turned animations off in Accessibility / Developer settings. */
    public static boolean reducedMotion() {
        try {
            return android.provider.Settings.Global.getFloat(c.getContentResolver(), android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (Throwable t) { return false; }
    }

    public static void enter(View v, int index) {
        if (reducedMotion()) { v.setAlpha(1f); v.setTranslationY(0); return; }
        v.setAlpha(0f);
        v.setTranslationY(dp(18));
        v.animate().alpha(1f).translationY(0).setStartDelay(Math.min(index, 8) * 35L)
                .setDuration(520).setInterpolator(SPRING).start();
    }

    public static void haptic(View v) {
        if (Store.ready() && !Store.bool("haptics", true)) return;
        try { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); } catch (Throwable ignored) {}
    }

    /** A firmer tick for confirmations (save, log, delete). */
    public static void confirmHaptic(View v) {
        if (v == null || (Store.ready() && !Store.bool("haptics", true))) return;
        try { v.performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.LONG_PRESS); } catch (Throwable ignored) {}
    }

    /** Hours typed as 7.5, 7,5, 7:30, 7h30 or 7h 30m. */
    public static double parseHours(String s) {
        if (s == null) return 0;
        String t = s.trim().toLowerCase(java.util.Locale.UK);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{1,2})\\s*[:h]\\s*(\\d{1,2})\\s*m?$").matcher(t);
        if (m.find()) return Integer.parseInt(m.group(1)) + Math.min(59, Integer.parseInt(m.group(2))) / 60.0;
        m = java.util.regex.Pattern.compile("^(\\d{1,2})\\s*h$").matcher(t);
        if (m.find()) return Integer.parseInt(m.group(1));
        return parse(t);
    }

    // ------------------------------------------------------------------ type

    private static final Typeface[] FONTS = new Typeface[10];

    public static Typeface font(int weight) {
        int idx = Math.max(1, Math.min(9, weight / 100));
        if (FONTS[idx] == null) {
            // Bundled Inter (an SF Pro-like grotesk) for the iOS look; system sans as a fallback.
            int w = idx <= 4 ? 400 : idx >= 8 ? 800 : idx * 100;
            try {
                FONTS[idx] = Typeface.createFromAsset(c.getAssets(), "fonts/inter_" + w + ".ttf");
            } catch (Throwable t) {
                try {
                    FONTS[idx] = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), idx * 100, false);
                } catch (Throwable t2) {
                    FONTS[idx] = idx >= 6 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
                }
            }
        }
        return FONTS[idx];
    }

    public static TextView text(String s, float sp, int weight, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(font(weight));
        t.setTextColor(color);
        t.setIncludeFontPadding(true);
        return t;
    }

    public static TextView single(String s, float sp, int weight, int color) {
        TextView t = text(s, sp, weight, color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** Shrinks text to fit its width instead of truncating (big numbers). */
    public static TextView autosize(TextView t, int minSp, int maxSp) {
        t.setSingleLine(true);
        t.setEllipsize(null);
        t.setLayoutParams(new LinearLayout.LayoutParams(MATCH, WRAP));
        try {
            t.setAutoSizeTextTypeUniformWithConfiguration(minSp, maxSp, 1, TypedValue.COMPLEX_UNIT_SP);
        } catch (Throwable ignored) {}
        return t;
    }

    public static TextView caption(String s) {
        TextView t = text(s.toUpperCase(Locale.UK), 12.5f, 500, Theme.text2);
        t.setLetterSpacing(0.03f);
        return t;
    }

    public static TextView sectionTitle(String s) {
        // iOS grouped-list section header
        TextView t = text(s.toUpperCase(Locale.UK), 13, 400, Theme.text2);
        t.setLetterSpacing(0.02f);
        t.setPadding(dp(16), dp(22), dp(16), dp(7));
        t.setTag("section");
        return t;
    }

    // ------------------------------------------------------------------ shapes

    public static GradientDrawable rr(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    public static GradientDrawable rrStroke(int fill, int stroke, float strokeDp, float radiusDp) {
        GradientDrawable g = rr(fill, radiusDp);
        g.setStroke(Math.max(1, dp(strokeDp)), stroke);
        return g;
    }

    /** Recessed input well. */
    public static Drawable well(float radiusDp) {
        return new Glass.Shape(Theme.field, Theme.field, radiusDp, 0f, 0, 0, 0);
    }

    /** Round glass button for headers (One UI 9 / HyperOS 3 floating header controls). */
    public static ImageView glassButton(String name, int color, Runnable r) {
        ImageView iv = icon(name, color, 22);
        iv.setPadding(dp(11), dp(11), dp(11), dp(11));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(44), dp(44));
        iv.setLayoutParams(p);
        iv.setBackground(ripple(Glass.lens(22), 22));
        press(iv);
        click(iv, r);
        return iv;
    }

    public static GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    public static GradientDrawable gradient(int a, int b, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    public static Drawable ripple(Drawable content, float radiusDp) {
        int rc = Theme.dark ? 0x33FFFFFF : 0x1A000000;
        return new RippleDrawable(ColorStateList.valueOf(rc), content, rr(0xFFFFFFFF, radiusDp));
    }

    // ------------------------------------------------------------------ layout

    public static LinearLayout v() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout h() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    public static LinearLayout.LayoutParams lpm(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, WRAP, w);
    }

    public static View space(int dpH) {
        View s = new View(c);
        s.setLayoutParams(new LinearLayout.LayoutParams(dp(dpH), dp(dpH)));
        return s;
    }

    public static View flex() {
        View s = new View(c);
        s.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return s;
    }

    public static View divider(int insetStartDp) {
        View d = new View(c);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, Math.max(1, dp(0.5f)));
        p.setMargins(dp(insetStartDp >= 72 ? 60 : insetStartDp >= 20 ? 16 : insetStartDp), 0, 0, 0);
        d.setLayoutParams(p);
        d.setBackgroundColor(Theme.divider);
        return d;
    }

    /** One UI style rounded card surface. */
    public static LinearLayout card() {
        LinearLayout l = v();
        l.setBackground(Glass.panel(16));
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setLayoutParams(lpm(MATCH, WRAP, 0, 0, 0, 12));
        return l;
    }

    /** A card meant to hold list rows (no internal padding). */
    public static LinearLayout group() {
        LinearLayout l = v();
        l.setBackground(Glass.panel(16));
        l.setPadding(0, 0, 0, 0);
        l.setLayoutParams(lpm(MATCH, WRAP, 0, 0, 0, 12));
        l.setClipToOutline(true);
        return l;
    }

    // ------------------------------------------------------------------ interaction

    @SuppressLint("ClickableViewAccessibility")
    public static void press(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(0.965f).scaleY(0.965f).setDuration(120).setInterpolator(EMPHASIZED).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(SPRING).start();
                        break;
                }
                return false;
            }
        });
    }

    public static void click(final View v, final Runnable r) {
        v.setClickable(true);
        v.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                haptic(view);
                try { r.run(); } catch (Throwable t) { MainActivity.reportError(t); }
            }
        });
    }

    public static void tap(View v, float radiusDp, Runnable r) {
        Drawable bg = v.getBackground();
        v.setBackground(ripple(bg != null ? bg : rr(0x00000000, radiusDp), radiusDp));
        press(v);
        click(v, r);
    }

    // ------------------------------------------------------------------ icons

    public static int iconResFor(Context ctx, String name) {
        int id = ctx.getResources().getIdentifier("ic_" + name, "drawable", ctx.getPackageName());
        return id != 0 ? id : R.drawable.ic_info;
    }

    public static int iconRes(String name) {
        int id = c.getResources().getIdentifier("ic_" + name, "drawable", c.getPackageName());
        return id != 0 ? id : R.drawable.ic_info;
    }

    public static ImageView icon(String name, int color, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(iconRes(name));
        iv.setImageTintList(ColorStateList.valueOf(color));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return iv;
    }

    /** Tonal squircle with a glyph, like One UI settings icons. */
    public static FrameLayout badge(String name, int color, int sizeDp) {
        FrameLayout f = new FrameLayout(c);
        // Solid, softly-graded squircle with a white glyph (HyperOS / ColorOS settings style)
        int base = Theme.isLight(color, 0.62) ? Theme.blend(color, 0xFF000000, 0.28f) : color;
        // Glossy squircle (HyperOS 3 / ColorOS 17 icon style): lit top edge and soft sheen
        // iOS Settings icon: flat colour squircle, white glyph
        f.setBackground(new Glass.Shape(base, base, sizeDp * 0.24f, 0f, 0, 0, 0));
        ImageView iv = icon(name, 0xFFFFFFFF, Math.round(sizeDp * 0.62f));
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(sizeDp * 0.62f), dp(sizeDp * 0.62f), Gravity.CENTER);
        f.addView(iv, p);
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return f;
    }

    public static LinearLayout chip(String iconName, String label, int tint) {
        LinearLayout l = h();
        l.setBackground(Glass.tinted(tint, 50));
        l.setPadding(dp(10), dp(5), dp(12), dp(5));
        if (iconName != null) {
            ImageView iv = icon(iconName, tint, 14);
            l.addView(iv);
            l.addView(space(5));
        }
        l.addView(text(label, 12, 700, tint));
        return l;
    }

    // ------------------------------------------------------------------ buttons

    public static LinearLayout button(String label, String iconName, int bg, int fg, Runnable r) {
        LinearLayout b = h();
        b.setGravity(Gravity.CENTER);
        // iOS filled / tinted rounded-rect buttons
        if (bg == Theme.accent) b.setBackground(Glass.gloss(Theme.accent, 14));
        else if (bg == Theme.accentContainer) b.setBackground(Glass.tinted(Theme.accent, 14));
        else if ((bg >>> 24) < 0x80) b.setBackground(Glass.tinted(fg, 14));
        else b.setBackground(Glass.gloss(bg, 14));
        b.setPadding(dp(20), dp(15), dp(20), dp(15));
        if (iconName != null) {
            b.addView(icon(iconName, fg, 20));
            b.addView(space(8));
        }
        b.addView(text(label, 17, 600, fg));
        tap(b, 14, r);
        return b;
    }

    public static LinearLayout primary(String label, String iconName, Runnable r) {
        return button(label, iconName, Theme.accent, Theme.onAccent, r);
    }

    public static LinearLayout tonal(String label, String iconName, Runnable r) {
        return button(label, iconName, Theme.accentContainer, Theme.onAccentContainer, r);
    }

    public static ImageView iconButton(String name, int color, Runnable r) {
        ImageView iv = icon(name, color, 24);
        iv.setPadding(dp(10), dp(10), dp(10), dp(10));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(46)));
        iv.setBackground(ripple(oval(0x00000000), 23));
        click(iv, r);
        return iv;
    }

    // ------------------------------------------------------------------ list rows

    /** Adds a One UI list row to a group card. Returns the value TextView (may be null). */
    public static TextView row(LinearLayout group, String iconName, int iconColor, String title,
                               String subtitle, String value, boolean chevron, Runnable onClick) {
        if (group.getChildCount() > 0) group.addView(divider(iconName != null ? 72 : 20));
        LinearLayout r = h();
        r.setPadding(dp(16), dp(subtitle != null ? 9 : 12), dp(14), dp(subtitle != null ? 9 : 12));
        r.setMinimumHeight(dp(50));
        if (iconName != null) {
            r.addView(badge(iconName, iconColor, 30));
            r.addView(space(14));
        }
        LinearLayout tx = v();
        tx.addView(text(title, 17, 400, Theme.text));
        if (subtitle != null) {
            TextView s = text(subtitle, 13, 400, Theme.text2);
            s.setPadding(0, dp(2), 0, 0);
            tx.addView(s);
        }
        r.addView(tx, weight(1));
        TextView val = null;
        if (value != null) {
            val = text(value, 17, 400, Theme.text2);
            val.setGravity(Gravity.END);
            val.setMaxWidth(dp(170));
            val.setPadding(dp(10), 0, 0, 0);
            r.addView(val);
        }
        if (chevron) { r.addView(space(4)); r.addView(icon("chevron_right", Theme.text3, 20)); }
        if (onClick != null) {
            r.setBackground(ripple(rr(0x00000000, 0), 0));
            click(r, onClick);
        }
        group.addView(r);
        return val;
    }

    public static Switch switchRow(LinearLayout group, String iconName, int iconColor, String title,
                                   String subtitle, boolean checked, final Fn<Boolean> onChange) {
        if (group.getChildCount() > 0) group.addView(divider(iconName != null ? 72 : 20));
        LinearLayout r = h();
        r.setPadding(dp(16), dp(subtitle != null ? 9 : 10), dp(12), dp(subtitle != null ? 9 : 10));
        r.setMinimumHeight(dp(50));
        if (iconName != null) {
            r.addView(badge(iconName, iconColor, 30));
            r.addView(space(14));
        }
        LinearLayout tx = v();
        tx.addView(text(title, 17, 400, Theme.text));
        if (subtitle != null) tx.addView(text(subtitle, 13, 400, Theme.text2));
        r.addView(tx, weight(1));
        final Switch sw = new Switch(c);
        sw.setChecked(checked);
        tintSwitch(sw);
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                haptic(b);
                onChange.on(v);
            }
        });
        r.addView(sw);
        r.setBackground(ripple(rr(0x00000000, 0), 0));
        click(r, new Runnable() { @Override public void run() { sw.toggle(); } });
        group.addView(r);
        return sw;
    }

    public static void tintSwitch(Switch sw) {
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{0xFFFFFFFF, 0xFFFFFFFF}));
        // iOS switch: green when on
        sw.setTrackTintList(new ColorStateList(states, new int[]{Theme.green, Theme.dark ? 0xFF39393D : 0xFFE9E9EA}));
        sw.setTrackTintMode(android.graphics.PorterDuff.Mode.SRC);
    }

    // ------------------------------------------------------------------ inputs

    public static EditText field(String hint, String value, boolean numeric) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setTypeface(font(500));
        e.setTextColor(Theme.text);
        e.setHintTextColor(Theme.text3);
        e.setBackground(well(12));
        e.setPadding(dp(16), dp(13), dp(16), dp(13));
        e.setSingleLine(true);
        if (numeric) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        else e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                e.setTextCursorDrawable(rr(Theme.accent, 2));
                GradientDrawable cur = rr(Theme.accent, 2);
                cur.setSize(dp(2), dp(20));
                e.setTextCursorDrawable(cur);
            }
        } catch (Throwable ignored) {}
        e.setHighlightColor(Theme.withAlpha(Theme.accent, 0.3f));
        return e;
    }

    /** Labelled inline numeric field row inside a group: "Label ........ [ £ 0.00 ]". */
    public static EditText fieldRow(LinearLayout group, String iconName, int iconColor, String label,
                                    String prefix, String value, String hint, final Fn<String> onChange) {
        if (group.getChildCount() > 0) group.addView(divider(iconName != null ? 72 : 20));
        LinearLayout r = h();
        r.setPadding(dp(16), dp(7), dp(12), dp(7));
        r.setMinimumHeight(dp(50));
        if (iconName != null) {
            r.addView(badge(iconName, iconColor, 30));
            r.addView(space(14));
        }
        r.addView(text(label, 17, 400, Theme.text), weight(1));
        LinearLayout box = h();
        box.setBackground(well(10));
        box.setPadding(dp(12), 0, dp(4), 0);
        if (prefix != null) box.addView(text(prefix, 15, 500, Theme.text2));
        final EditText e = new EditText(c);
        e.setBackground(null);
        e.setText(value);
        e.setHint(hint);
        e.setHintTextColor(Theme.text3);
        e.setTextColor(Theme.text);
        e.setTypeface(font(600));
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setSingleLine(true);
        e.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        e.setMinWidth(dp(70));
        e.setPadding(dp(6), dp(10), dp(8), dp(10));
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setSelectAllOnFocus(true);
        box.addView(e);
        r.addView(box);
        e.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(android.text.Editable s) { onChange.on(s.toString()); }
        });
        group.addView(r);
        return e;
    }

    public static double parse(String s) {
        if (s == null) return 0;
        String t = s.trim().replaceAll("[^0-9.,\\-]", "");
        if (t.contains(",") && t.contains(".")) {
            t = t.replace(",", "");                       // 1,234.56
        } else if (t.contains(",")) {
            int last = t.lastIndexOf(',');
            int decimals = t.length() - last - 1;
            if (t.indexOf(',') == last && decimals > 0 && decimals <= 2) t = t.replace(',', '.'); // 12,5 (decimal comma)
            else t = t.replace(",", "");                  // 1,234 (thousands)
        }
        if (t.isEmpty() || t.equals(".") || t.equals("-")) return 0;
        try { return Double.parseDouble(t); } catch (Exception ex) { return 0; }
    }

    public static Double parseOpt(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        return parse(s);
    }

    // ------------------------------------------------------------------ segmented button group

    /** Material 3 Expressive connected button group. */
    private static Drawable segThumb() {
        return Glass.lens(7);
    }

    public static LinearLayout segmented(final String[] options, int selected, final Fn<Integer> onSelect) {
        final LinearLayout l = h();
        // iOS segmented control
        int track = Theme.dark ? 0xFF1C1C1F : 0xFFE3E3E8;
        l.setBackground(new Glass.Shape(track, track, 9, 0f, 0, 0, 0));
        l.setPadding(dp(2), dp(2), dp(2), dp(2));
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            final TextView t = text(options[i], 13.5f, i == selected ? 600 : 500, i == selected ? Theme.text : Theme.text);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(6), dp(7), dp(6), dp(7));
            t.setSingleLine(true);
            t.setBackground(i == selected ? segThumb() : null);
            t.setElevation(i == selected && !Theme.dark ? dp(1.5f) : 0);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, WRAP, 1f);
            l.addView(t, p);
            click(t, new Runnable() {
                @Override public void run() {
                    for (int j = 0; j < l.getChildCount(); j++) {
                        TextView o = (TextView) l.getChildAt(j);
                        o.setBackground(j == idx ? segThumb() : null);
                        o.setTextColor(Theme.text);
                        o.setTypeface(font(j == idx ? 600 : 500));
                        o.setElevation(j == idx && !Theme.dark ? dp(1.5f) : 0);
                    }
                    t.setScaleX(0.92f); t.setScaleY(0.92f);
                    t.animate().scaleX(1f).scaleY(1f).setDuration(450).setInterpolator(SPRING).start();
                    haptic(t);
                    onSelect.on(idx);
                }
            });
        }
        return l;
    }

    // ------------------------------------------------------------------ formatting

    public static String money(double v) { return money(v, 2); }

    /** Set while exporting, so files always contain real figures even in privacy mode. */
    public static boolean unmasked;

    /** Privacy mode hides amounts on screen (and in widgets) for use in public. */
    public static boolean masked() {
        return !unmasked && Store.ready() && Store.bool("privacyMode", false);
    }

    public static final String MASK = "•••";

    public static String money(double v, int decimals) {
        String sym = Store.currency();
        if (masked()) return sym + MASK;
        String s = String.format(Locale.UK, "%,." + decimals + "f", Math.abs(v));
        return (v < -0.004 ? "-" : "") + sym + s;
    }

    public static String compact(double v) {
        String sym = Store.currency();
        if (masked()) return sym + MASK;
        double a = Math.abs(v);
        if (a >= 1_000_000) return sym + String.format(Locale.UK, "%.1fm", v / 1_000_000);
        if (a >= 10_000) return sym + String.format(Locale.UK, "%.0fk", v / 1000);
        if (a >= 1000) return sym + String.format(Locale.UK, "%.1fk", v / 1000);
        return sym + String.format(Locale.UK, "%.0f", v);
    }

    public static String num(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.005) return String.format(Locale.UK, "%.0f", v);
        return String.format(Locale.UK, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public static String hours(double h) { return num(Math.round(h * 10) / 10.0) + "h"; }

    // ------------------------------------------------------------------ job identity

    private static final int[] JOB_COLORS = {0xFF3E7BFA, 0xFF0BA572, 0xFF8B5CF6, 0xFFF07800, 0xFFE0337E, 0xFF0891B2, 0xFFE5372E, 0xFF4F5BD5, 0xFFB7791F, 0xFF16A34A};

    /** A stable colour per employer, so the same job looks the same everywhere. */
    public static int jobColor(String job) {
        String k = job == null || job.trim().isEmpty() ? "main job" : job.trim().toLowerCase(Locale.UK);
        return JOB_COLORS[Math.abs(k.hashCode()) % JOB_COLORS.length];
    }

    /** Gradient squircle with the job's initial. */
    public static FrameLayout avatar(String job, int sizeDp) {
        String name = job == null || job.trim().isEmpty() ? "Shift" : job.trim();
        int col = jobColor(name);
        FrameLayout f = new FrameLayout(c);
        f.setBackground(new Glass.Shape(col, col, sizeDp * 0.5f, 0f, 0, 0, 0));
        String initial = name.substring(0, 1).toUpperCase(Locale.UK);
        TextView t = text(initial, sizeDp * 0.42f, 800, 0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        f.addView(t, new FrameLayout.LayoutParams(MATCH, MATCH));
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return f;
    }

    /** Section header with optional trailing action text. */
    public static LinearLayout header(String title, String action, Runnable onAction) {
        LinearLayout r = h();
        r.setPadding(dp(4), dp(22), dp(0), dp(8));
        r.addView(text(title, 22, 700, Theme.text), weight(1));
        if (action != null) {
            TextView a = text(action, 16, 400, Theme.accent);
            a.setPadding(dp(12), dp(6), dp(12), dp(6));
            a.setBackground(ripple(rr(0x00000000, 50), 50));
            click(a, onAction);
            r.addView(a);
        }
        return r;
    }
}
