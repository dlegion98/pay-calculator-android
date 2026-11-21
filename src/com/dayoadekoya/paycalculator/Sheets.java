package com.dayoadekoya.paycalculator;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;

/** Floating One UI–style bottom sheets and pickers. */
public final class Sheets {
    private Sheets() {}

    public static final class Handle {
        public Dialog dialog;
        public LinearLayout body;
        public void dismiss() { try { dialog.dismiss(); } catch (Throwable ignored) {} }
    }

    public static Handle sheet(Activity a, String title, String message) {
        final Handle h = new Handle();
        final Dialog d = new Dialog(a);
        h.dialog = d;
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout outer = new FrameLayout(a);
        LinearLayout card = Ui.v();
        card.setBackground(new Glass.Shape(Theme.dark ? 0xFF0E0E0F : 0xFFF2F2F7, Theme.dark ? 0xFF0E0E0F : 0xFFF2F2F7, 22, 0f, 0, 0, 0));
        card.setPadding(Ui.dp(24), Ui.dp(10), Ui.dp(24), Ui.dp(20));

        View grip = new View(a);
        grip.setBackground(Ui.rr(Theme.dark ? 0xFF5A5A5F : 0xFFC7C7CC, 3));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Ui.dp(36), Ui.dp(5));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.bottomMargin = Ui.dp(14);
        card.addView(grip, gp);

        if (title != null) {
            TextView t = Ui.text(title, 20, 700, Theme.text);
            t.setPadding(Ui.dp(2), Ui.dp(4), 0, Ui.dp(message == null ? 14 : 6));
            card.addView(t);
        }
        if (message != null) {
            TextView m = Ui.text(message, 15, 400, Theme.text2);
            m.setPadding(Ui.dp(2), 0, 0, Ui.dp(16));
            m.setLineSpacing(0, 1.15f);
            card.addView(m);
        }
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        h.body = Ui.v();
        sv.addView(h.body);
        card.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        cp.setMargins(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
        outer.addView(card, cp);
        d.setContentView(outer);

        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int sw = a.getResources().getConfiguration().screenWidthDp;
            w.setLayout(sw > 600 ? Ui.dp(580) : ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(sw > 600 ? Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL : Gravity.BOTTOM);
            w.setDimAmount(Theme.dark ? 0.6f : 0.35f);
            // Frosted backdrop (Android 12+ cross-window blur), as in One UI / ColorOS / HyperOS
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                try {
                    if (a.getWindowManager().isCrossWindowBlurEnabled()) {
                        w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                        WindowManager.LayoutParams lp = w.getAttributes();
                        lp.setBlurBehindRadius(Ui.dp(28));
                        w.setAttributes(lp);
                        w.setDimAmount(Theme.dark ? 0.45f : 0.18f);
                    }
                } catch (Throwable ignored) {}
            }
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        // Constrain height to ~80% of the screen
        final int maxH = (int) (a.getResources().getDisplayMetrics().heightPixels * (a.getResources().getConfiguration().screenHeightDp < 560 ? 0.5f : 0.62f));
        sv.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (v.getHeight() > maxH) { v.getLayoutParams().height = maxH; v.requestLayout(); }
            }
        });
        return h;
    }

    public static void show(Handle h) {
        try { h.dialog.show(); } catch (Throwable t) { MainActivity.reportError(t); }
    }

    /** Pair of Cancel / Primary buttons at the bottom of a sheet. */
    public static void buttons(final Handle h, String primary, final Runnable onPrimary) {
        LinearLayout row = Ui.h();
        row.setPadding(0, Ui.dp(18), 0, 0);
        LinearLayout cancel = Ui.button("Cancel", null, Theme.surface, Theme.accent, new Runnable() {
            @Override public void run() { h.dismiss(); }
        });
        LinearLayout ok = Ui.primary(primary, null, new Runnable() {
            @Override public void run() { onPrimary.run(); }
        });
        row.addView(cancel, Ui.weight(1));
        row.addView(Ui.space(10));
        row.addView(ok, Ui.weight(1));
        h.body.addView(row);
    }

    public static void choose(Activity a, String title, final String[] options, int selected, final Ui.Fn<Integer> cb) {
        final Handle h = sheet(a, title, null);
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            LinearLayout r = Ui.h();
            r.setPadding(Ui.dp(16), Ui.dp(15), Ui.dp(14), Ui.dp(15));
            boolean sel = i == selected;
            r.setBackground(Ui.rr(sel ? Theme.accentSoft : 0x00000000, 18));
            TextView t = Ui.text(options[i], 16.5f, sel ? 650 : 500, sel ? Theme.accent : Theme.text);
            r.addView(t, Ui.weight(1));
            if (sel) r.addView(Ui.icon("check", Theme.accent, 22));
            Ui.tap(r, 18, new Runnable() {
                @Override public void run() { h.dismiss(); cb.on(idx); }
            });
            LinearLayout.LayoutParams p = Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 4);
            h.body.addView(r, p);
        }
        show(h);
    }

    public static void input(final Activity a, String title, String message, String hint, String initial,
                             final boolean numeric, final Ui.Fn<String> cb) {
        final Handle h = sheet(a, title, message);
        final EditText e = Ui.field(hint, initial, numeric);
        if (!numeric && title != null && title.toLowerCase(java.util.Locale.ROOT).startsWith("tax code")) {
            e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        }
        e.setSelectAllOnFocus(true);
        h.body.addView(e, Ui.lp(Ui.MATCH, Ui.WRAP));
        buttons(h, "Save", new Runnable() {
            @Override public void run() {
                h.dismiss();
                cb.on(e.getText().toString().trim());
            }
        });
        show(h);
        e.requestFocus();
        e.postDelayed(new Runnable() {
            @Override public void run() {
                InputMethodManager imm = (InputMethodManager) a.getSystemService(Activity.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(e, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 250);
    }

    public static void confirm(Activity a, String title, String message, String action, boolean destructive, final Runnable r) {
        final Handle h = sheet(a, title, message);
        LinearLayout row = Ui.h();
        LinearLayout cancel = Ui.button("Cancel", null, Theme.surface, Theme.accent, new Runnable() {
            @Override public void run() { h.dismiss(); }
        });
        LinearLayout ok = Ui.button(action, null, destructive ? Theme.red : Theme.accent,
                destructive ? 0xFFFFFFFF : Theme.onAccent, new Runnable() {
            @Override public void run() { h.dismiss(); r.run(); }
        });
        row.addView(cancel, Ui.weight(1));
        row.addView(Ui.space(10));
        row.addView(ok, Ui.weight(1));
        h.body.addView(row);
        show(h);
    }

    public static void info(Activity a, String title, String message) {
        final Handle h = sheet(a, title, message);
        h.body.addView(Ui.primary("OK", null, new Runnable() { @Override public void run() { h.dismiss(); } }));
        show(h);
    }

    public static void date(Activity a, LocalDate initial, final Ui.Fn<LocalDate> cb) {
        int theme = Theme.dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
        DatePickerDialog d = new DatePickerDialog(a, theme, new DatePickerDialog.OnDateSetListener() {
            @Override public void onDateSet(DatePicker view, int y, int m, int day) { cb.on(LocalDate.of(y, m + 1, day)); }
        }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth());
        d.show();
        try {
            d.getButton(DatePickerDialog.BUTTON_POSITIVE).setTextColor(Theme.accent);
            d.getButton(DatePickerDialog.BUTTON_NEGATIVE).setTextColor(Theme.accent);
        } catch (Throwable ignored) {}
    }

    /** Option card used in "add" hubs: tonal icon, title, subtitle, chevron. */
    public static View optionCard(String icon, int color, String title, String sub, Runnable r) {
        LinearLayout row = Ui.h();
        row.setBackground(Ui.rr(Theme.surface, 12));
        row.setPadding(Ui.dp(16), Ui.dp(16), Ui.dp(14), Ui.dp(16));
        row.addView(Ui.badge(icon, color, 46));
        row.addView(Ui.space(14));
        LinearLayout t = Ui.v();
        t.addView(Ui.text(title, 16.5f, 650, Theme.text));
        TextView s = Ui.text(sub, 13.5f, 400, Theme.text2);
        s.setPadding(0, Ui.dp(2), 0, 0);
        t.addView(s);
        row.addView(t, Ui.weight(1));
        ImageView ch = Ui.icon("chevron_right", Theme.text3, 22);
        row.addView(ch);
        Ui.tap(row, 22, r);
        row.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        return row;
    }
}
