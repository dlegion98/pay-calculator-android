package com.dayoadekoya.paycalculator;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dayoadekoya.paycalculator.Model.DailyPay;
import com.dayoadekoya.paycalculator.Model.PayEntry;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Earnings heatmap calendar — port of iOS CalendarHeatmapView. */
public class CalendarPage extends Page {
    private YearMonth month = YearMonth.now();
    private LocalDate selected = null;

    private static final class Act {
        double hours, net;
        List<PayEntry> entries = new ArrayList<PayEntry>();
    }

    /** Detects a horizontal swipe across its children (day cells stay tappable). */
    private static final class SwipeFrame extends FrameLayout {
        private float x0, y0;
        private boolean swiping;
        private final int slop;
        Ui.Fn<Integer> onSwipe;

        SwipeFrame(android.content.Context c) { super(c); slop = android.view.ViewConfiguration.get(c).getScaledTouchSlop() * 2; }

        @Override public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN: x0 = e.getX(); y0 = e.getY(); swiping = false; break;
                case android.view.MotionEvent.ACTION_MOVE:
                    float dx = e.getX() - x0, dy = e.getY() - y0;
                    if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        swiping = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                    break;
            }
            return false;
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) { x0 = e.getX(); y0 = e.getY(); swiping = false; return true; }
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_MOVE && !swiping) {
                float mx = e.getX() - x0, my = e.getY() - y0;
                if (Math.abs(mx) > slop && Math.abs(mx) > Math.abs(my) * 1.5f) { swiping = true; getParent().requestDisallowInterceptTouchEvent(true); }
            }
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP && swiping) {
                swiping = false;
                float dx = e.getX() - x0;
                if (Math.abs(dx) > getWidth() * 0.15f && onSwipe != null) onSwipe.on(dx < 0 ? 1 : -1);
                return true;
            }
            return swiping || super.onTouchEvent(e);
        }
    }

    @Override public String title() { return "Calendar"; }
    @Override public String subtitle() { return "See the days you worked light up"; }

    @Override protected void actions() {
        s.addAction("today", "Jump to today", new Runnable() {
            @Override public void run() { month = YearMonth.now(); selected = LocalDate.now(); render(true); }
        });
    }

    /** Spread each entry's net across the actual days worked (same maths as iOS). */
    private static Map<LocalDate, Act> activity(YearMonth ym) {
        Map<LocalDate, Act> map = new HashMap<LocalDate, Act>();
        for (PayEntry e : Store.entries) {
            LocalDate start = TaxEngine.toDate(e.dateMillis);
            double totalGross = TaxEngine.grossCalculated(e);
            double net = TaxEngine.net(e);
            List<DailyPay> active = new ArrayList<DailyPay>();
            for (DailyPay d : e.days) if (TaxEngine.dayGross(d, e.hourlyRate) > 0 || TaxEngine.dayHours(d) > 0) active.add(d);
            if (e.isSalariedMode || totalGross <= 0 || active.isEmpty()) {
                put(map, ym, start, TaxEngine.totalHours(e), net, e);
            } else {
                for (DailyPay d : active) {
                    double share = TaxEngine.dayGross(d, e.hourlyRate) / totalGross;
                    put(map, ym, start.plusDays(d.dayIndex), TaxEngine.dayHours(d), net * share, e);
                }
            }
        }
        return map;
    }

    private static void put(Map<LocalDate, Act> map, YearMonth ym, LocalDate d, double h, double n, PayEntry e) {
        if (!YearMonth.from(d).equals(ym)) return;
        Act a = map.get(d);
        if (a == null) { a = new Act(); map.put(d, a); }
        a.hours += h; a.net += n;
        if (!a.entries.contains(e)) a.entries.add(e);
    }

    @Override protected void build() {
        final Map<LocalDate, Act> act = activity(month);
        Map<LocalDate, Act> prev = activity(month.minusMonths(1));
        Set<LocalDate> paydays = Payday.inMonth(month.getYear(), month.getMonthValue());
        double maxNet = 1, total = 0, hours = 0, prevTotal = 0;
        LocalDate bestDay = null; double best = 0;
        for (Map.Entry<LocalDate, Act> en : act.entrySet()) {
            Act x = en.getValue();
            total += x.net; hours += x.hours;
            maxNet = Math.max(maxNet, x.net);
            if (x.net > best) { best = x.net; bestDay = en.getKey(); }
        }
        for (Act x : prev.values()) prevTotal += x.net;

        // Month card
        LinearLayout card = Ui.card();
        card.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(16));
        LinearLayout head = Ui.h();
        head.setPadding(Ui.dp(6), 0, 0, Ui.dp(10));
        LinearLayout ht = Ui.v();
        ht.addView(Ui.text(month.getMonth().getDisplayName(TextStyle.FULL, Locale.UK) + " " + month.getYear(), 21, 700, Theme.text));
        ht.addView(Ui.text(Ui.money(total) + " earned · " + Ui.hours(hours), 13.5f, 500, Theme.text2));
        head.addView(ht, Ui.weight(1));
        head.addView(navBtn("chevron_left", -1));
        head.addView(navBtn("chevron_right", 1));
        card.addView(head);

        GridLayout grid = new GridLayout(a);
        grid.setColumnCount(7);
        String[] wd = {"M", "T", "W", "T", "F", "S", "S"};
        for (String w : wd) {
            TextView t = Ui.text(w, 12, 700, Theme.text3);
            t.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams p = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            p.width = 0;
            p.height = Ui.dp(26);
            grid.addView(t, p);
        }
        LocalDate first = month.atDay(1);
        int offset = first.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
        for (int i = 0; i < offset; i++) grid.addView(new View(a), cellLp());
        LocalDate today = LocalDate.now();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            final LocalDate date = month.atDay(day);
            Act x = act.get(date);
            boolean pay = paydays.contains(date);
            grid.addView(cell(date, x, maxNet, pay, date.equals(today), date.equals(selected)), cellLp());
        }
        SwipeFrame swipe = new SwipeFrame(a);
        swipe.onSwipe = new Ui.Fn<Integer>() {
            @Override public void on(Integer dir) { Ui.haptic(s); month = month.plusMonths(dir); selected = null; render(false); slide(dir); }
        };
        swipe.addView(grid, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        card.addView(swipe);

        LinearLayout legend = Ui.h();
        legend.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        legend.setPadding(0, Ui.dp(12), Ui.dp(6), 0);
        legend.addView(Ui.text("💸 payday", 12, 600, Theme.text2));
        legend.addView(Ui.flex());
        legend.addView(Ui.text("Less ", 12, 600, Theme.text2));
        float[] steps = {0.2f, 0.45f, 0.7f, 1f};
        for (float f : steps) {
            View sw = new View(a);
            sw.setBackground(Ui.rr(Theme.withAlpha(Theme.accent, f), 4));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(14), Ui.dp(14));
            p.setMargins(Ui.dp(2), 0, Ui.dp(2), 0);
            legend.addView(sw, p);
        }
        legend.addView(Ui.text(" More", 12, 600, Theme.text2));
        card.addView(legend);
        add(card);

        // Selected day
        if (selected != null && YearMonth.from(selected).equals(month)) add(selectedCard(selected, act.get(selected), paydays.contains(selected)));

        // Summary
        add(Ui.sectionTitle("Month summary"));
        if (act.isEmpty()) {
            LinearLayout c = Ui.card();
            c.addView(Ui.text("No shifts logged this month", 16, 650, Theme.text));
            TextView t = Ui.text("Add a pay period to see the exact days you worked light up the calendar.", 14, 400, Theme.text2);
            t.setPadding(0, Ui.dp(4), 0, 0);
            c.addView(t);
            add(c);
            return;
        }
        LinearLayout tiles = Ui.h();
        tiles.addView(tile("payments", Theme.green, "Earned", Ui.money(total, 0)), Ui.weight(1));
        tiles.addView(Ui.space(10));
        tiles.addView(tile("schedule", Theme.accent, "Hours", Ui.hours(hours)), Ui.weight(1));
        add(tiles);
        LinearLayout tiles2 = Ui.h();
        tiles2.setPadding(0, Ui.dp(10), 0, 0);
        tiles2.addView(tile("event", Theme.orange, "Days worked", String.valueOf(act.size())), Ui.weight(1));
        tiles2.addView(Ui.space(10));
        tiles2.addView(tile("auto_awesome", Theme.purple, "Best day", bestDay == null ? "—" : Ui.money(best, 0)), Ui.weight(1));
        add(follow(tiles2));
        LinearLayout tiles3 = Ui.h();
        tiles3.setPadding(0, Ui.dp(10), 0, 0);
        tiles3.addView(tile("today", Theme.teal, "Avg per day worked", Ui.money(total / Math.max(1, act.size()), 0)), Ui.weight(1));
        tiles3.addView(Ui.space(10));
        tiles3.addView(tile("trending_up", Theme.pink, "Take-home per hour", hours > 0 ? Ui.money(total / hours) : "—"), Ui.weight(1));
        add(follow(tiles3));
        TextView hint = Ui.text("Tip: swipe the calendar to change month, and long-press a day to log a shift on it.", 12.5f, 400, Theme.text2);
        hint.setPadding(Ui.dp(6), Ui.dp(10), Ui.dp(6), 0);
        add(follow(hint));
        if (prevTotal > 0) {
            double ch = (total - prevTotal) / prevTotal * 100;
            LinearLayout c = Ui.card();
            c.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
            LinearLayout r = Ui.h();
            r.addView(Ui.badge(ch >= 0 ? "trending_up" : "trending_down", ch >= 0 ? Theme.green : Theme.red, 40));
            r.addView(Ui.space(14));
            LinearLayout t = Ui.v();
            t.addView(Ui.text(String.format(Locale.UK, "%s%.0f%% vs last month", ch >= 0 ? "+" : "", ch), 16, 650, Theme.text));
            t.addView(Ui.text(Ui.money(prevTotal, 0) + " in " + month.minusMonths(1).getMonth().getDisplayName(TextStyle.FULL, Locale.UK), 13.5f, 400, Theme.text2));
            r.addView(t, Ui.weight(1));
            c.addView(r);
            add(follow(c));
        }
    }

    private GridLayout.LayoutParams cellLp() {
        GridLayout.LayoutParams p = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        p.width = 0;
        p.height = Ui.dp(48);
        p.setMargins(Ui.dp(2.5f), Ui.dp(2.5f), Ui.dp(2.5f), Ui.dp(2.5f));
        return p;
    }

    private View navBtn(String icon, final int dir) {
        android.widget.ImageView b = Ui.iconButton(icon, Theme.text, new Runnable() {
            @Override public void run() { month = month.plusMonths(dir); selected = null; render(false); slide(dir); }
        });
        b.setBackground(Ui.ripple(Ui.oval(Theme.field), 23));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(42), Ui.dp(42));
        p.setMargins(Ui.dp(6), 0, 0, 0);
        b.setLayoutParams(p);
        b.setPadding(Ui.dp(9), Ui.dp(9), Ui.dp(9), Ui.dp(9));
        return b;
    }

    private void slide(int dir) {
        if (s.body.getChildCount() == 0) return;
        View v = s.body.getChildAt(0);
        v.setTranslationX(Ui.dp(40) * dir);
        v.setAlpha(0.4f);
        v.animate().translationX(0).alpha(1).setDuration(420).setInterpolator(Ui.SPRING).start();
    }

    private View cell(final LocalDate date, Act x, double maxNet, boolean payday, boolean today, boolean sel) {
        FrameLayout f = new FrameLayout(a);
        int bg;
        int fg = Theme.text;
        if (x != null && x.net > 0) {
            float intensity = (float) Math.max(0.25, Math.min(1, x.net / maxNet));
            bg = Theme.withAlpha(Theme.accent, intensity * (Theme.dark ? 0.85f : 0.9f));
            fg = intensity > 0.55f ? Theme.onAccent : Theme.text;
        } else {
            bg = Theme.dark ? Theme.surfaceHigh : Theme.surfaceHigh;
        }
        if (sel) f.setBackground(Ui.rrStroke(bg, Theme.text, 2, 14));
        else if (today) f.setBackground(Ui.rrStroke(bg, Theme.accent, 2, 14));
        else f.setBackground(Ui.rr(bg, 14));
        TextView t = Ui.text(String.valueOf(date.getDayOfMonth()), 14.5f, today || sel ? 800 : 600, fg);
        t.setGravity(Gravity.CENTER);
        f.addView(t, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        if (payday) {
            TextView p = Ui.text("💸", 10, 400, Theme.text);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.TOP | Gravity.END);
            lp.setMargins(0, Ui.dp(1), Ui.dp(3), 0);
            f.addView(p, lp);
        }
        f.setContentDescription(date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK))
                + (x != null ? ", earned " + Ui.money(x.net) + ", " + Ui.hours(x.hours) : ", nothing logged") + (payday ? ", payday" : "") + (today ? ", today" : ""));
        // Long-press any day to log a shift on it
        f.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { Ui.haptic(v); a.push(EntryEditorPage.forDay(date)); return true; }
        });
        Ui.press(f);
        Ui.click(f, new Runnable() {
            @Override public void run() {
                selected = date.equals(selected) ? null : date;
                render(false);
            }
        });
        return f;
    }

    private View selectedCard(final LocalDate date, Act x, boolean payday) {
        LinearLayout c = Ui.card();
        c.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 0));
        c.addView(Ui.caption(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK))));
        c.addView(Ui.space(10));
        if (payday) {
            LinearLayout r = Ui.h();
            r.addView(Ui.badge("payments", Theme.green, 40));
            r.addView(Ui.space(12));
            r.addView(Ui.text("Payday 💸", 16, 650, Theme.text));
            r.setPadding(0, 0, 0, Ui.dp(8));
            c.addView(r);
        }
        if (x == null) {
            c.addView(Ui.text("No shifts logged for this day.", 15, 400, Theme.text2));
        } else {
            LinearLayout r = Ui.h();
            r.addView(Ui.text(Ui.money(x.net), 26, 800, Theme.text), Ui.weight(1));
            r.addView(Ui.chip("schedule", Ui.hours(x.hours), Theme.accent));
            c.addView(r);
            for (final PayEntry e : x.entries) {
                LinearLayout row = Ui.h();
                row.setPadding(0, Ui.dp(12), 0, Ui.dp(4));
                row.addView(Ui.avatar(e.displayJob(), 36));
                row.addView(Ui.space(12));
                LinearLayout t = Ui.v();
                t.addView(Ui.text(e.displayJob(), 15.5f, 600, Theme.text));
                t.addView(Ui.text(DashboardPage.rangeString(e) + " · " + Ui.money(TaxEngine.net(e)) + " period", 13, 400, Theme.text2));
                row.addView(t, Ui.weight(1));
                row.addView(Ui.icon("chevron_right", Theme.text3, 20));
                Ui.click(row, new Runnable() { @Override public void run() { a.push(new EntryDetailPage(e.id)); } });
                c.addView(row);
            }
        }
        c.addView(Ui.space(14));
        c.addView(Ui.tonal(x == null ? "Log a shift on this day" : "Log another shift", "add", new Runnable() {
            @Override public void run() { a.push(EntryEditorPage.forDay(date)); }
        }), Ui.lp(Ui.MATCH, Ui.WRAP));
        return c;
    }

    private View tile(String icon, int color, String label, String value) {
        LinearLayout c = Ui.v();
        c.setBackground(Glass.panel(24));
        c.setPadding(Ui.dp(16), Ui.dp(16), Ui.dp(16), Ui.dp(16));
        c.addView(Ui.badge(icon, color, 36));
        TextView v = Ui.single(value, 21, 800, Theme.text);
        v.setPadding(0, Ui.dp(12), 0, 0);
        c.addView(v);
        c.addView(Ui.text(label, 13, 500, Theme.text2));
        return c;
    }
}
