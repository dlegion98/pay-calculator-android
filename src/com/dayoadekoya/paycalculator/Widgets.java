package com.dayoadekoya.paycalculator;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.dayoadekoya.paycalculator.Model.PayEntry;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Home-screen widgets (Android counterparts of the iOS WidgetKit widgets). */
public final class Widgets {
    private Widgets() {}

    public static final String ACTION_LOG = "com.dayoadekoya.paycalculator.WIDGET_LOG";
    public static final String EXTRA_OPEN_ADD = "openAdd";

    // ------------------------------------------------------------------ snapshot (mirrors iOS PayWidgetSnapshot)

    static final class Snap {
        double monthGross, monthNet, monthHours; int monthShifts;
        double weekNet;
        double tyGross, tyNet, tyTaxNi; int tyStart;
        String latestJob = ""; double latestNet; String latestRange = ""; boolean hasLatest;
        double nextPay; LocalDate nextDate; boolean hasNext;
        double goal;
        double[] trend = new double[0];

        double goalFraction() { return goal > 0 ? Math.min(1, Math.max(0, monthNet / goal)) : 0; }
        double goalRemaining() { return Math.max(0, goal - monthNet); }
    }

    static Snap snapshot() {
        Snap s = new Snap();
        LocalDate now = LocalDate.now();
        LocalDate weekStart = now.with(DayOfWeek.MONDAY);
        s.tyStart = TaxEngine.taxYearStart(now);
        for (PayEntry e : Store.entries) {
            LocalDate d = TaxEngine.toDate(e.dateMillis);
            double net = TaxEngine.net(e);
            if (d.getYear() == now.getYear() && d.getMonthValue() == now.getMonthValue()) {
                s.monthGross += TaxEngine.effectiveGross(e);
                s.monthNet += net;
                s.monthHours += TaxEngine.totalHours(e);
                s.monthShifts++;
            }
            if (!d.isBefore(weekStart) && !d.isAfter(weekStart.plusDays(6))) s.weekNet += net;
            if (TaxEngine.inTaxYear(e, s.tyStart)) {
                s.tyGross += TaxEngine.effectiveGross(e);
                s.tyNet += net;
                s.tyTaxNi += TaxEngine.displayTax(e) + TaxEngine.displayNi(e);
            }
        }
        if (!Store.entries.isEmpty()) {
            PayEntry l = Store.entries.get(0);
            s.hasLatest = true;
            s.latestJob = l.displayJob();
            s.latestNet = TaxEngine.net(l);
            s.latestRange = DashboardPage.rangeString(l);
            s.nextPay = s.latestNet;
        }
        s.nextDate = Payday.next();
        s.hasNext = s.nextDate != null && s.hasLatest;
        s.goal = Store.dbl("monthlyEarningsGoal", 2000);
        int n = Math.min(Store.entries.size(), 10);
        s.trend = new double[n];
        for (int i = 0; i < n; i++) s.trend[n - 1 - i] = TaxEngine.net(Store.entries.get(i));
        return s;
    }

    // ------------------------------------------------------------------ colours

    static final class Palette {
        int accentL, accentD, onAccentL, onAccentD, tonalL, tonalD, textL, textD;
        boolean nowDark;
    }

    static Palette palette(Context c) {
        Palette p = new Palette();
        p.nowDark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        String theme = Store.prefs(c).getString("appThemeColor", "Blue");
        if ("Dynamic".equals(theme) && Build.VERSION.SDK_INT >= 31) {
            try {
                p.accentL = c.getColor(android.R.color.system_accent1_600);
                p.accentD = c.getColor(android.R.color.system_accent1_200);
            } catch (Throwable t) { p.accentL = p.accentD = 0xFF3E7BFA; }
        } else if ("Night Shift (OLED)".equals(theme)) {
            p.accentL = 0xFF111114; p.accentD = 0xFFFFFFFF;
        } else {
            int base;
            switch (theme) {
                case "Red": base = 0xFFF2453D; break;
                case "Green": base = 0xFF2DB55D; break;
                case "Mint": base = 0xFF0BA572; break;
                case "Purple": base = 0xFF8B5CF6; break;
                case "Orange": base = 0xFFFF8A00; break;
                default: base = 0xFF3E7BFA;
            }
            p.accentL = base;
            p.accentD = Theme.blend(base, 0xFFFFFFFF, 0.2f);
        }
        p.onAccentL = Theme.isLight(p.accentL) ? 0xFF111111 : 0xFFFFFFFF;
        p.onAccentD = Theme.isLight(p.accentD) ? 0xFF111111 : 0xFFFFFFFF;
        p.tonalL = Theme.blend(p.accentL, 0xFFFFFFFF, 0.86f);
        p.tonalD = Theme.blend(p.accentD, 0xFF000000, 0.74f);
        p.textL = 0xFF111114; p.textD = 0xFFF5F5F7;
        return p;
    }

    static int accent(Palette p) { return p.nowDark ? p.accentD : p.accentL; }

    static void color(RemoteViews rv, int id, String method, int light, int dark, boolean nowDark) {
        if (Build.VERSION.SDK_INT >= 31) {
            try { rv.setColorInt(id, method, light, dark); return; } catch (Throwable ignored) {}
        }
        rv.setInt(id, method, nowDark ? dark : light);
    }

    /** Solid accent squircle with a white glyph (HyperOS / ColorOS style). */
    static void header(RemoteViews rv, Palette p) {
        color(rv, R.id.h_badge, "setColorFilter", p.accentL, p.accentD, p.nowDark);
        color(rv, R.id.h_icon, "setColorFilter", p.onAccentL, p.onAccentD, p.nowDark);
    }

    // ------------------------------------------------------------------ glass & luminous styles

    static final int SMALL = 0, WIDE = 1, LARGE = 2, STRIP = 3;

    static int[] sizeDp(int kind) {
        switch (kind) {
            case WIDE: return new int[]{340, 170};
            case LARGE: return new int[]{340, 340};
            case STRIP: return new int[]{340, 76};
            default: return new int[]{170, 170};
        }
    }

    static void ensureTheme(Context c) {
        Ui.density = density(c);
        Theme.apply(c);
    }

    /** Glass widget: the night-aware XML base plus a soft aurora glow layer in the theme colours. */
    static void glass(RemoteViews rv, Context c, int kind) {
        // iOS widgets: clean solid surface, no glow layer
        if (true) { try { rv.setImageViewBitmap(R.id.glow, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)); } catch (Throwable ignored) {} return; }
        try {
            int[] sz = sizeDp(kind);
            float d = Math.min(density(c), 2.5f);
            int w = (int) (sz[0] * d), h = (int) (sz[1] * d);
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            if (!Theme.oled) {
                Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
                float r1 = Math.max(w, h) * 0.75f;
                pt.setShader(new android.graphics.RadialGradient(w * 0.05f, 0, r1, Theme.withAlpha(Theme.accent, 0.26f), 0, Shader.TileMode.CLAMP));
                cv.drawRect(0, 0, w, h, pt);
                pt.setShader(new android.graphics.RadialGradient(w * 1.0f, h * 0.15f, r1 * 0.85f, Theme.withAlpha(Glass.secondary(), 0.20f), 0, Shader.TileMode.CLAMP));
                cv.drawRect(0, 0, w, h, pt);
                pt.setShader(new android.graphics.RadialGradient(w * 0.35f, h * 1.05f, r1 * 0.7f, Theme.withAlpha(Glass.tertiary(), 0.12f), 0, Shader.TileMode.CLAMP));
                cv.drawRect(0, 0, w, h, pt);
            }
            // liquid-glass specular sheen across the top and a lit top-left corner
            Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
            boolean night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            sh.setShader(new android.graphics.LinearGradient(0, 0, 0, h * 0.5f, night ? 0x14FFFFFF : 0x4DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, sh);
            sh.setShader(new android.graphics.RadialGradient(0, 0, Math.min(w, h) * 0.6f, night ? 0x1FFFFFFF : 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, sh);
            rv.setImageViewBitmap(R.id.glow, b);
        } catch (Throwable t) { t.printStackTrace(); }
    }

    /** Luminous widget: full gradient "hero" surface with light text. */
    static void luminous(RemoteViews rv, Context c, int kind, int[] primary, int[] secondary) {
        try {
            int[] sz = sizeDp(kind);
            float d = Math.min(density(c), 2.5f);
            int w = (int) (sz[0] * d), h = (int) (sz[1] * d);
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            float old = Ui.density;
            Ui.density = d;
            Glass.Hero hero = new Glass.Hero(22);
            hero.setBounds(0, 0, w, h);
            hero.draw(cv);
            Ui.density = old;
            rv.setImageViewBitmap(R.id.glow, b);
            rv.setInt(android.R.id.background, "setBackgroundResource", 0);
            int on = Theme.onHero();
            for (int id : primary) rv.setTextColor(id, on);
            for (int id : secondary) rv.setTextColor(id, Theme.withAlpha(on, 0.78f));
            rv.setInt(R.id.h_badge, "setColorFilter", Theme.withAlpha(on, 0.24f));
            rv.setInt(R.id.h_icon, "setColorFilter", on);
        } catch (Throwable t) { t.printStackTrace(); }
    }

    /** Big money with a smaller £ and pence (sizes only, so colours stay night-aware). */
    static CharSequence big(double v) {
        String sym = Store.currency();
        String num = String.format(Locale.UK, "%,.2f", Math.abs(v));
        if (Ui.masked()) num = "••••.••";
        int dot = num.lastIndexOf('.');
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        if (v < -0.004) sb.append("-");
        int s0 = sb.length();
        sb.append(sym);
        sb.setSpan(new android.text.style.RelativeSizeSpan(0.62f), s0, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(num.substring(0, dot));
        int d0 = sb.length();
        sb.append(num.substring(dot));
        sb.setSpan(new android.text.style.RelativeSizeSpan(0.58f), d0, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    static void accentText(RemoteViews rv, int id, Palette p) {
        color(rv, id, "setTextColor", p.accentL, p.accentD, p.nowDark);
    }

    // ------------------------------------------------------------------ bitmaps

    static float density(Context c) { return c.getResources().getDisplayMetrics().density; }

    static Bitmap ring(Context c, float frac, int color) { return ring(c, frac, color, 0x33888890); }

    static Bitmap ring(Context c, float frac, int color, int track) {
        float d = density(c);
        int size = (int) (96 * d);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        float sw = 10 * d;
        p.setStrokeWidth(sw);
        RectF r = new RectF(sw / 2, sw / 2, size - sw / 2, size - sw / 2);
        p.setColor(track);
        cv.drawArc(r, 0, 360, false, p);
        if (frac > 0.001f) {
            p.setColor(color);
            cv.drawArc(r, -90, 360 * Math.min(1f, frac), false, p);
        }
        return b;
    }

    static Bitmap stackBar(Context c, double[] values, int[] colors, int widthDp) {
        float d = density(c);
        int w = (int) (widthDp * d), h = (int) (10 * d);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path clip = new Path();
        clip.addRoundRect(new RectF(0, 0, w, h), h / 2f, h / 2f, Path.Direction.CW);
        cv.clipPath(clip);
        p.setColor(0x33888890);
        cv.drawRect(0, 0, w, h, p);
        double total = 0;
        for (double v : values) total += Math.max(0, v);
        if (total > 0) {
            float x = 0;
            for (int i = 0; i < values.length; i++) {
                float seg = (float) (Math.max(0, values[i]) / total * w);
                p.setColor(colors[i]);
                cv.drawRect(x, 0, x + Math.max(0, seg - 2 * d), h, p);
                x += seg;
            }
        }
        return b;
    }

    static Bitmap spark(Context c, double[] v, int color, int widthDp, int heightDp) {
        float d = density(c);
        int w = (int) (widthDp * d), h = (int) (heightDp * d);
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        if (v.length < 2) return b;
        Canvas cv = new Canvas(b);
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double x : v) { min = Math.min(min, x); max = Math.max(max, x); }
        double pad = Math.max(1, (max - min) * 0.15);
        min -= pad; max += pad;
        float in = 4 * d;
        Path path = new Path();
        float[] xs = new float[v.length], ys = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            xs[i] = in + (w - 2 * in) * i / (float) (v.length - 1);
            ys[i] = (float) (h - in - (h - 2 * in) * (v[i] - min) / (max - min));
            if (i == 0) path.moveTo(xs[i], ys[i]);
            else { float cx = (xs[i - 1] + xs[i]) / 2; path.cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i]); }
        }
        Path area = new Path(path);
        area.lineTo(xs[v.length - 1], h);
        area.lineTo(xs[0], h);
        area.close();
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(0, 0, 0, h, Theme.withAlpha(color, 0.35f), Theme.withAlpha(color, 0f), Shader.TileMode.CLAMP));
        cv.drawPath(area, fill);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.5f * d);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setColor(color);
        cv.drawPath(path, line);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(color);
        cv.drawCircle(xs[v.length - 1], ys[v.length - 1], 4 * d, dot);
        return b;
    }

    // ------------------------------------------------------------------ helpers

    static String money(double v, int dp) { return Ui.money(v, dp); }

    static String nextDateText(Snap s) {
        if (!s.hasNext) return "Log a shift to see";
        long days = s.nextDate.toEpochDay() - LocalDate.now().toEpochDay();
        String when = days <= 0 ? "Today" : (days == 1 ? "Tomorrow" : "in " + days + " days");
        return s.nextDate.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)) + " · " + when;
    }

    static PendingIntent openApp(Context c, boolean add, int code) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (add) i.putExtra(EXTRA_OPEN_ADD, true);
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void ensureStore(Context c) {
        if (!Store.ready()) Store.init(c);
        else Store.syncConfig();
    }

    /** Refresh every placed widget (called whenever data or theme changes). */
    public static void updateAll(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            Class<?>[] all = {NextPay.class, Month.class, Goal.class, TaxYear.class, Overview.class, QuickLog.class,
                    StripNext.class, StripMonth.class, StripGoal.class, StripTax.class, StripTrio.class};
            for (Class<?> k : all) {
                int[] ids = m.getAppWidgetIds(new ComponentName(c, k));
                if (ids == null || ids.length == 0) continue;
                Base b = (Base) k.newInstance();
                for (int id : ids) b.update(c, m, id);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    // ------------------------------------------------------------------ base provider

    public abstract static class Base extends AppWidgetProvider {
        abstract RemoteViews build(Context c, Snap s, Palette p, boolean wide);
        boolean responsive() { return false; }

        void update(Context c, AppWidgetManager m, int id) {
            try {
                ensureStore(c);
                ensureTheme(c);
                Snap s = snapshot();
                Palette p = palette(c);
                RemoteViews rv;
                if (responsive() && Build.VERSION.SDK_INT >= 31) {
                    Map<SizeF, RemoteViews> map = new HashMap<SizeF, RemoteViews>();
                    map.put(new SizeF(100f, 100f), build(c, s, p, false));
                    map.put(new SizeF(220f, 100f), build(c, s, p, true));
                    rv = new RemoteViews(map);
                } else {
                    boolean wide = false;
                    if (responsive()) {
                        Bundle o = m.getAppWidgetOptions(id);
                        wide = o != null && o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) >= 220;
                    }
                    rv = build(c, s, p, wide);
                }
                m.updateAppWidget(id, rv);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
            for (int id : ids) update(c, m, id);
        }

        @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
            update(c, m, id);
        }
    }

    // ------------------------------------------------------------------ Next pay

    public static class NextPay extends Base {
        @Override boolean responsive() { return true; }

        @Override RemoteViews build(Context c, Snap s, Palette p, boolean wide) {
            RemoteViews rv = new RemoteViews(c.getPackageName(), wide ? R.layout.widget_next_pay_wide : R.layout.widget_next_pay);
            header(rv, p);
            if (s.hasLatest) {
                rv.setTextViewText(R.id.amount, big(s.nextPay));
                rv.setTextViewText(R.id.sub, "estimated take-home");
            } else {
                rv.setTextViewText(R.id.amount, "—");
                rv.setTextViewText(R.id.sub, "Log a shift to see your next pay");
            }
            rv.setTextViewText(R.id.date, s.hasNext ? nextDateText(s) : (s.hasLatest ? "Set your payday in Settings" : ""));
            if (wide) {
                long days = s.hasNext ? Math.max(0, s.nextDate.toEpochDay() - LocalDate.now().toEpochDay()) : -1;
                rv.setTextViewText(R.id.countdown, days < 0 ? "—" : (days == 0 ? "Today" : String.valueOf(days)));
                rv.setTextViewText(R.id.countdown_label, days < 0 ? "no payday yet" : (days == 0 ? "it's payday 💸" : (days == 1 ? "day to go" : "days to go")));
                rv.setTextViewText(R.id.date, s.hasNext ? s.nextDate.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.UK)) : "");
                rv.setTextViewText(R.id.latest, s.hasLatest ? s.latestJob + " · " + s.latestRange : "");
                luminous(rv, c, WIDE, new int[]{R.id.amount, R.id.countdown, R.id.date}, new int[]{R.id.h_title, R.id.sub, R.id.countdown_label, R.id.latest});
            } else {
                luminous(rv, c, SMALL, new int[]{R.id.amount, R.id.date}, new int[]{R.id.h_title, R.id.sub});
            }
            rv.setOnClickPendingIntent(R.id.root, openApp(c, false, 10));
            return rv;
        }
    }

    // ------------------------------------------------------------------ This month

    public static class Month extends Base {
        @Override boolean responsive() { return true; }

        @Override RemoteViews build(Context c, Snap s, Palette p, boolean wide) {
            RemoteViews rv = new RemoteViews(c.getPackageName(), wide ? R.layout.widget_month_wide : R.layout.widget_month);
            header(rv, p);
            String month = LocalDate.now().getMonth().getDisplayName(TextStyle.FULL, Locale.UK);
            rv.setTextViewText(R.id.h_title, month);
            rv.setTextViewText(R.id.net, big(s.monthNet));
            rv.setTextViewText(R.id.sub, "take-home this month");
            glass(rv, c, wide ? WIDE : SMALL);
            rv.setTextViewText(R.id.hours, Ui.hours(s.monthHours));
            rv.setTextViewText(R.id.shifts, String.valueOf(s.monthShifts));
            if (wide) {
                rv.setTextViewText(R.id.gross, money(s.monthGross, 0));
                rv.setTextViewText(R.id.avg, s.monthShifts > 0 ? money(s.monthNet / s.monthShifts, 0) : "—");
            }
            rv.setOnClickPendingIntent(R.id.root, openApp(c, false, 11));
            return rv;
        }
    }

    // ------------------------------------------------------------------ Goal

    public static class Goal extends Base {
        @Override RemoteViews build(Context c, Snap s, Palette p, boolean wide) {
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_goal);
            header(rv, p);
            float f = (float) s.goalFraction();
            int on = Theme.onHero();
            rv.setImageViewBitmap(R.id.ring, ring(c, f, on, Theme.withAlpha(on, 0.25f)));
            rv.setTextViewText(R.id.pct, Math.round(f * 100) + "%");
            luminous(rv, c, SMALL, new int[]{R.id.pct}, new int[]{R.id.h_title, R.id.sub});
            if (s.goal <= 0) rv.setTextViewText(R.id.sub, "Set a goal in Settings");
            else if (f >= 1) rv.setTextViewText(R.id.sub, "Goal reached 🎉");
            else rv.setTextViewText(R.id.sub, money(s.goalRemaining(), 0) + " to go");
            rv.setOnClickPendingIntent(R.id.root, openApp(c, false, 12));
            return rv;
        }
    }

    // ------------------------------------------------------------------ Tax year

    public static class TaxYear extends Base {
        @Override RemoteViews build(Context c, Snap s, Palette p, boolean wide) {
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_tax_year);
            header(rv, p);
            rv.setTextViewText(R.id.h_title, "Tax year " + TaxEngine.taxYearLabel(s.tyStart));
            rv.setTextViewText(R.id.net, big(s.tyNet));
            rv.setTextViewText(R.id.sub, "take-home so far");
            glass(rv, c, WIDE);
            rv.setTextViewText(R.id.gross, money(s.tyGross, 0));
            rv.setTextViewText(R.id.taxni, money(s.tyTaxNi, 0));
            rv.setTextViewText(R.id.rate, s.tyGross > 0 ? String.format(Locale.UK, "%.1f%%", (s.tyGross - s.tyNet) / s.tyGross * 100) : "—");
            double other = Math.max(0, s.tyGross - s.tyNet - s.tyTaxNi);
            rv.setImageViewBitmap(R.id.bar, stackBar(c, new double[]{s.tyNet, s.tyTaxNi, other},
                    new int[]{accent(p), 0xFFF2453D, 0xFF8B5CF6}, 300));
            rv.setOnClickPendingIntent(R.id.root, openApp(c, false, 13));
            return rv;
        }
    }

    // ------------------------------------------------------------------ Overview

    public static class Overview extends Base {
        @Override RemoteViews build(Context c, Snap s, Palette p, boolean wide) {
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_overview);
            header(rv, p);
            rv.setTextViewText(R.id.updated, "Updated " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.UK)));
            glass(rv, c, LARGE);
            rv.setTextViewText(R.id.net, big(s.monthNet));
            rv.setTextViewText(R.id.sub, money(s.monthGross, 0) + " gross · " + s.monthShifts + (s.monthShifts == 1 ? " shift" : " shifts"));
            float f = (float) s.goalFraction();
            rv.setImageViewBitmap(R.id.ring, ring(c, f, accent(p)));
            rv.setTextViewText(R.id.pct, Math.round(f * 100) + "%");
            rv.setImageViewBitmap(R.id.spark, spark(c, s.trend, accent(p), 300, 70));
            rv.setTextViewText(R.id.week, money(s.weekNet, 0));
            rv.setTextViewText(R.id.week_sub, "take-home");
            rv.setTextViewText(R.id.next, s.hasLatest ? money(s.nextPay, 0) : "—");
            rv.setTextViewText(R.id.next_sub, s.hasNext ? nextDateText(s) : "estimate");
            rv.setTextViewText(R.id.ty, money(s.tyNet, 0));
            rv.setTextViewText(R.id.ty_sub, TaxEngine.taxYearLabel(s.tyStart));
            rv.setTextViewText(R.id.hrs, Ui.hours(s.monthHours));
            rv.setTextViewText(R.id.hrs_sub, s.goal > 0 ? money(s.goalRemaining(), 0) + " to goal" : "");
            accentText(rv, R.id.net, p);
            rv.setOnClickPendingIntent(R.id.root, openApp(c, false, 14));
            return rv;
        }
    }

    // ------------------------------------------------------------------ Quick log

    public static class QuickLog extends Base {
        private static final int[] BTN = {R.id.b4, R.id.b8, R.id.b12};
        private static final int[] BG = {R.id.b4_bg, R.id.b8_bg, R.id.b12_bg};
        private static final int[] TXT = {R.id.b4_text, R.id.b8_text, R.id.b12_text};
        private static final double[] HOURS = {4, 8, 12};

        @Override RemoteViews build(Context c, Snap s, Palette p, boolean wide) {
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_quick_log);
            rv.setTextViewText(R.id.sub, s.monthShifts + " this month · " + money(s.monthNet, 0));
            glass(rv, c, STRIP);
            for (int i = 0; i < 3; i++) {
                color(rv, BG[i], "setColorFilter", p.tonalL, p.tonalD, p.nowDark);
                color(rv, TXT[i], "setTextColor", p.accentL, p.accentD, p.nowDark);
                Intent in = new Intent(c, QuickLog.class);
                in.setAction(ACTION_LOG);
                in.putExtra("hours", HOURS[i]);
                rv.setOnClickPendingIntent(BTN[i], PendingIntent.getBroadcast(c, 100 + i, in,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
            color(rv, R.id.badd_bg, "setColorFilter", p.accentL, p.accentD, p.nowDark);
            color(rv, R.id.badd_text, "setTextColor", p.onAccentL, p.onAccentD, p.nowDark);
            rv.setOnClickPendingIntent(R.id.badd, openApp(c, true, 15));
            rv.setOnClickPendingIntent(R.id.title, openApp(c, false, 16));
            return rv;
        }

        @Override public void onReceive(Context c, Intent intent) {
            if (ACTION_LOG.equals(intent.getAction())) {
                try {
                    ensureStore(c);
                    double h = intent.getDoubleExtra("hours", 8);
                    PayEntry e = Store.quickShift(h, null);
                    Store.entries.add(e);
                    Store.changed();
                    Toast.makeText(c, "Logged " + Ui.num(h) + "h shift · " + Ui.money(TaxEngine.net(e)) + " take-home", Toast.LENGTH_SHORT).show();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                return;
            }
            super.onReceive(c, intent);
        }
    }

    // ------------------------------------------------------------------ 4x1 strips

    static RemoteViews strip(Context c, Palette p, String icon, String title, String sub, String value, int code) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_strip);
        header(rv, p);
        rv.setImageViewResource(R.id.h_icon, Ui.iconResFor(c, icon));
        rv.setTextViewText(R.id.h_title, title);
        rv.setTextViewText(R.id.sub, sub);
        rv.setTextViewText(R.id.value, value);
        accentText(rv, R.id.value, p);
        glass(rv, c, STRIP);
        rv.setOnClickPendingIntent(R.id.root, openApp(c, false, code));
        return rv;
    }

    public static class StripNext extends Base {
        @Override RemoteViews build(Context c, Snap s, Palette p, boolean wide) {
            RemoteViews rv = strip(c, p, "payments", "Next pay",
                    s.hasNext ? nextDateText(s) : (s.hasLatest ? "Set your payday in Settings" : "Log a shift to see"),
                    s.hasLatest ? money(s.nextPay, 2) : "—", 20);
            if (s.hasLatest) rv.setTextViewText(R.id.value, big(s.nextPay));
            luminous(rv, c, STRIP, new int[]{R.id.h_title, R.id.value}, new int[]{R.id.sub});
            return rv;
        }
    }

    public static class StripMonth extends Base {
        @Override RemoteViews build(Context c, Snap s, Palette p, boolean wide) {
            String month = LocalDate.now().getMonth().getDisplayName(TextStyle.FULL, Locale.UK);
            return strip(c, p, "calendar_month", month,
                    s.monthShifts + (s.monthShifts == 1 ? " shift · " : " shifts · ") + Ui.hours(s.monthHours) + " · " + money(s.monthGross, 0) + " gross",
                    money(s.monthNet, 0), 21);
        }
    }

    public static class StripGoal extends Base {
        @Override RemoteViews build(Context c, Snap s, Palette p, boolean wide) {
            float f = (float) s.goalFraction();
            String sub = s.goal <= 0 ? "Set a goal in Settings" : (f >= 1 ? "Goal reached 🎉" : money(s.goalRemaining(), 0) + " to go of " + money(s.goal, 0));
            RemoteViews rv = strip(c, p, "flag", "Monthly goal", sub, Math.round(f * 100) + "%", 22);
            rv.setViewVisibility(R.id.bar, android.view.View.VISIBLE);
            rv.setImageViewBitmap(R.id.bar, stackBar(c, new double[]{f, 1 - f}, new int[]{accent(p), 0x00000000}, 220));
            return rv;
        }
    }

    public static class StripTax extends Base {
        @Override RemoteViews build(Context c, Snap s, Palette p, boolean wide) {
            return strip(c, p, "account_balance", "Tax year " + TaxEngine.taxYearLabel(s.tyStart),
                    money(s.tyTaxNi, 0) + " tax & NI · " + money(s.tyGross, 0) + " gross", money(s.tyNet, 0), 23);
        }
    }

    public static class StripTrio extends Base {
        @Override RemoteViews build(Context c, Snap s, Palette p, boolean wide) {
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_strip_trio);
            rv.setTextViewText(R.id.v1, money(s.weekNet, 0));
            rv.setTextViewText(R.id.v2, money(s.monthNet, 0));
            rv.setTextViewText(R.id.v3, money(s.tyNet, 0));
            accentText(rv, R.id.v2, p);
            glass(rv, c, STRIP);
            rv.setOnClickPendingIntent(R.id.root, openApp(c, false, 24));
            return rv;
        }
    }
}
