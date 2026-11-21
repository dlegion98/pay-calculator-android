package com.dayoadekoya.paycalculator;

import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.dayoadekoya.paycalculator.Model.DailyPay;
import com.dayoadekoya.paycalculator.Model.PayEntry;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Insights — port of iOS AnalyticsView (Overview / Breakdown / What-If tools). */
public class AnalyticsPage extends Page {
    private int section = 0;
    private String range = "Tax Year";
    private int metric = 0; // 0 net, 1 gross, 2 hours
    private double extraHours = 0, rateDelta = 0, simPension = -1, targetNet = -1;

    private static final String[] RANGES = {"Tax Year", "3M", "6M", "1Y", "All"};

    @Override public String title() { return "Insights"; }
    @Override public String subtitle() { return "Trends, tax and what-ifs"; }

    // ------------------------------------------------------------------ summary model

    private static final class Summary {
        List<PayEntry> scoped = new ArrayList<PayEntry>();
        double net, gross, tax, ni, pension, loan, hours;
        TreeMap<YearMonth, Double> monthly = new TreeMap<YearMonth, Double>();
        Map<String, Double> byJob = new LinkedHashMap<String, Double>();
        double base, overtime, premium, bonus, travel;
        double baseRate, baseWeeklyHours;
        String taxCode;
        double elapsed, projGross, projNet;
    }

    private Summary summary() {
        Summary s = new Summary();
        LocalDate now = LocalDate.now();
        int startYear = TaxEngine.startYearFor(Store.str("selectedTaxYear", "Current"), now);
        for (PayEntry e : Store.entries) {
            LocalDate d = TaxEngine.toDate(e.dateMillis);
            boolean in;
            switch (range) {
                case "Tax Year": in = TaxEngine.inTaxYear(e, startYear); break;
                case "3M": in = !d.isBefore(now.minusMonths(3)); break;
                case "6M": in = !d.isBefore(now.minusMonths(6)); break;
                case "1Y": in = !d.isBefore(now.minusMonths(12)); break;
                default: in = true;
            }
            if (in) s.scoped.add(e);
        }
        List<Double> rates = new ArrayList<Double>(), weekly = new ArrayList<Double>();
        for (PayEntry e : s.scoped) {
            double n = TaxEngine.net(e), g = TaxEngine.effectiveGross(e), h = TaxEngine.totalHours(e);
            s.net += n; s.gross += g; s.tax += TaxEngine.displayTax(e); s.ni += TaxEngine.displayNi(e);
            s.pension += TaxEngine.displayPension(e); s.loan += TaxEngine.studentLoan(e); s.hours += h;
            if (e.hourlyRate > 0) rates.add(e.hourlyRate);
            if (h > 0 && "Weekly".equals(e.payFrequency)) weekly.add(h);
            YearMonth ym = YearMonth.from(TaxEngine.toDate(e.dateMillis));
            double mv = metric == 0 ? n : (metric == 1 ? g : h);
            Double prev = s.monthly.get(ym);
            s.monthly.put(ym, (prev == null ? 0 : prev) + mv);
            String job = e.jobName.isEmpty() ? "Main Job" : e.jobName;
            Double pj = s.byJob.get(job);
            s.byJob.put(job, (pj == null ? 0 : pj) + n);
            if (e.isSalariedMode) s.base += e.fixedMonthlyGross;
            else for (DailyPay d : e.days) {
                double nh = TaxEngine.paidHours(d);
                s.base += nh * e.hourlyRate;
                if (d.isOvertime) s.overtime += nh * e.hourlyRate * 0.5;
                s.premium += d.premiumHours * e.hourlyRate * d.premiumMultiplier;
                s.bonus += d.bonus;
                s.travel += d.travelPay;
            }
        }
        double def = Store.dbl("defaultHourlyRate", 0);
        if (def > 0) s.baseRate = def;
        else if (!rates.isEmpty()) { double t = 0; for (double r : rates) t += r; s.baseRate = t / rates.size(); }
        else s.baseRate = TaxEngine.minimumWage(Store.integer("defaultAge", 25), now);
        if (!weekly.isEmpty()) { double t = 0; for (double w : weekly) t += w; s.baseWeeklyHours = t / weekly.size(); }
        else s.baseWeeklyHours = 37.5;
        String code = !s.scoped.isEmpty() ? s.scoped.get(0).taxCode : (!Store.entries.isEmpty() ? Store.entries.get(0).taxCode : Store.str("defaultTaxCode", "1257L"));
        s.taxCode = code == null ? "1257L" : code.trim().toUpperCase(Locale.UK);
        s.elapsed = "Tax Year".equals(range) ? TaxEngine.taxYearElapsed(startYear, now) : 1.0;
        s.projGross = s.gross / s.elapsed;
        s.projNet = s.net / s.elapsed;
        return s;
    }

    // ------------------------------------------------------------------ build

    @Override protected void build() {
        View seg = Ui.segmented(new String[]{"Overview", "Breakdown", "What-if"}, section, new Ui.Fn<Integer>() {
            @Override public void on(Integer i) { section = i; render(true); }
        });
        seg.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        add(span(seg));

        if (Store.entries.isEmpty()) {
            LinearLayout c = Ui.card();
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            c.setPadding(Ui.dp(24), Ui.dp(30), Ui.dp(24), Ui.dp(30));
            c.addView(Ui.badge("insights", Theme.accent, 64));
            TextView t = Ui.text("No pay data recorded yet", 19, 700, Theme.text);
            t.setPadding(0, Ui.dp(16), 0, Ui.dp(6));
            c.addView(t);
            TextView s2 = Ui.text("Log shifts to view tax bands, take-home trends, and earnings breakdowns.", 14.5f, 400, Theme.text2);
            s2.setGravity(Gravity.CENTER);
            c.addView(s2);
            add(span(c));
            if (section == 2) simulatorSection(summary());
            return;
        }

        if (section != 2) {
            LinearLayout chips = Ui.h();
            chips.setPadding(0, 0, 0, Ui.dp(12));
            for (final String r : RANGES) {
                boolean sel = r.equals(range);
                TextView t = Ui.text(r, 13.5f, 700, sel ? Theme.onAccent : Theme.text2);
                t.setGravity(Gravity.CENTER);
                t.setBackground(Ui.rr(sel ? Theme.accent : Theme.surface, 50));
                t.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(8));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.WRAP, r.equals("Tax Year") ? 1.6f : 1f);
                p.setMargins(0, 0, Ui.dp(6), 0);
                chips.addView(t, p);
                Ui.tap(t, 50, new Runnable() { @Override public void run() { range = r; render(false); } });
            }
            add(span(chips));
        }
        Summary sm = summary();
        if (section == 0) overview(sm);
        else if (section == 1) breakdown(sm);
        else simulatorSection(sm);
    }

    // ------------------------------------------------------------------ overview

    private void overview(Summary sm) {
        LinearLayout r1 = Ui.h();
        r1.addView(kpi("account_balance_wallet", Theme.accent, "Take-home", Ui.money(sm.net, 0)), Ui.weight(1));
        r1.addView(Ui.space(10));
        r1.addView(kpi("payments", Theme.green, "Gross", Ui.money(sm.gross, 0)), Ui.weight(1));
        add(span(r1));
        LinearLayout r2 = Ui.h();
        r2.setPadding(0, Ui.dp(10), 0, Ui.dp(12));
        r2.addView(kpi("schedule", Theme.orange, "Hours", Ui.hours(sm.hours)), Ui.weight(1));
        r2.addView(Ui.space(10));
        r2.addView(kpi("trending_up", Theme.purple, "Per hour (net)", sm.hours > 0 ? Ui.money(sm.net / sm.hours) : "—"), Ui.weight(1));
        add(span(r2));

        add(smartSummary(sm));
        add(trendCard(sm));
        add(forecastCard(sm));
        if (sm.byJob.size() > 0) add(jobsCard(sm));
    }

    private View kpi(String icon, int color, String label, String value) {
        LinearLayout c = Ui.v();
        c.setBackground(Glass.panel(24));
        c.setPadding(Ui.dp(16), Ui.dp(16), Ui.dp(16), Ui.dp(16));
        c.addView(Ui.badge(icon, color, 36));
        TextView v = Ui.single(value, 22, 800, Theme.text);
        v.setPadding(0, Ui.dp(12), 0, 0);
        c.addView(v);
        c.addView(Ui.text(label, 13, 500, Theme.text2));
        return c;
    }

    private View smartSummary(Summary sm) {
        LinearLayout c = Ui.card();
        c.setBackground(Ui.rr(Theme.accentContainer, 26));
        LinearLayout h = Ui.h();
        h.addView(Ui.icon("auto_awesome", Theme.onAccentContainer, 20));
        h.addView(Ui.space(8));
        TextView cap = Ui.text("SMART SUMMARY", 12, 750, Theme.onAccentContainer);
        cap.setLetterSpacing(0.06f);
        h.addView(cap, Ui.weight(1));
        h.addView(Ui.text("On-device", 11, 600, Theme.withAlpha(Theme.onAccentContainer, 0.7f)));
        c.addView(h);
        StringBuilder b = new StringBuilder();
        String phrase = "Tax Year".equals(range) ? "this tax year" : ("All".equals(range) ? "in total" : "over the last " + range.replace("M", " months").replace("1Y", "year"));
        b.append("You've taken home ").append(Ui.money(sm.net, 0)).append(' ').append(phrase);
        if (sm.gross > 0) b.append(String.format(Locale.UK, ", keeping %.0f%% of your gross pay", sm.net / sm.gross * 100));
        b.append(". ");
        if (sm.monthly.size() >= 2) {
            Map.Entry<YearMonth, Double> best = null;
            for (Map.Entry<YearMonth, Double> e : sm.monthly.entrySet()) if (best == null || e.getValue() > best.getValue()) best = e;
            if (best != null && metric == 0) b.append("Your best month was ").append(best.getKey().getMonth().getDisplayName(TextStyle.FULL, Locale.UK)).append(" at ").append(Ui.money(best.getValue(), 0)).append(". ");
        }
        if ("Tax Year".equals(range) && sm.elapsed < 1) {
            b.append("At this pace you're on track for ").append(Ui.money(sm.projNet, 0)).append(" take-home — ").append(TaxEngine.bandName(sm.projGross).toLowerCase(Locale.UK)).append(" territory.");
        } else if (sm.hours > 0) {
            b.append("Every hour worked put ").append(Ui.money(sm.net / sm.hours)).append(" in your pocket.");
        }
        TextView t = Ui.text(b.toString(), 15.5f, 450, Theme.onAccentContainer);
        t.setLineSpacing(0, 1.2f);
        t.setPadding(0, Ui.dp(10), 0, 0);
        c.addView(t);
        return c;
    }

    private View trendCard(final Summary sm) {
        LinearLayout c = Ui.card();
        LinearLayout h = Ui.h();
        h.addView(Ui.caption("Earnings trend"), Ui.weight(1));
        c.addView(h);
        final TextView valueLbl = Ui.text("", 24, 800, Theme.text);
        valueLbl.setPadding(0, Ui.dp(8), 0, 0);
        c.addView(valueLbl);
        final TextView sub = Ui.text("", 13, 500, Theme.text2);
        c.addView(sub);
        final List<YearMonth> keys = new ArrayList<YearMonth>(sm.monthly.keySet());
        final double[] vals = new double[keys.size()];
        String[] labels = new String[keys.size()];
        double total = 0;
        for (int i = 0; i < keys.size(); i++) {
            vals[i] = sm.monthly.get(keys.get(i));
            labels[i] = keys.get(i).getMonth().getDisplayName(TextStyle.SHORT, Locale.UK);
            total += vals[i];
        }
        final double avg = keys.isEmpty() ? 0 : total / keys.size();
        valueLbl.setText(fmtMetric(avg));
        sub.setText("Average per month");
        if (keys.isEmpty()) {
            TextView t = Ui.text("No data available for this range", 14, 500, Theme.text2);
            t.setPadding(0, Ui.dp(20), 0, Ui.dp(20));
            c.addView(t);
        } else {
            Charts.Bars bars = new Charts.Bars(a);
            bars.color = Theme.accent;
            bars.average = avg;
            bars.set(vals, labels, new Charts.Scrub() {
                @Override public void on(int i) {
                    if (i < 0) { valueLbl.setText(fmtMetric(avg)); sub.setText("Average per month"); return; }
                    valueLbl.setText(fmtMetric(vals[i]));
                    YearMonth ym = keys.get(i);
                    sub.setText(ym.getMonth().getDisplayName(TextStyle.FULL, Locale.UK) + " " + ym.getYear());
                }
            });
            c.addView(bars, Ui.lpm(Ui.MATCH, Ui.dp(170), 0, 14, 0, 12));
        }
        View seg = Ui.segmented(new String[]{"Take-home", "Gross", "Hours"}, metric, new Ui.Fn<Integer>() {
            @Override public void on(Integer i) { metric = i; render(false); }
        });
        c.addView(seg);
        return c;
    }

    private String fmtMetric(double v) { return metric == 2 ? Ui.hours(v) : Ui.money(v, 0); }

    private View forecastCard(Summary sm) {
        LinearLayout c = Ui.card();
        c.addView(Ui.caption("Run-rate forecast"));
        LinearLayout r = Ui.h();
        r.setPadding(0, Ui.dp(12), 0, Ui.dp(12));
        LinearLayout g = Ui.v();
        g.addView(Ui.text("Projected gross", 13, 500, Theme.text2));
        g.addView(Ui.text(Ui.money(sm.projGross, 0), 22, 800, Theme.text));
        r.addView(g, Ui.weight(1));
        LinearLayout n = Ui.v();
        n.addView(Ui.text("Projected net", 13, 500, Theme.text2));
        n.addView(Ui.text(Ui.money(sm.projNet, 0), 22, 800, Theme.accent));
        r.addView(n, Ui.weight(1));
        c.addView(r);
        int bandColor = sm.projGross < 12570 ? Theme.green : (sm.projGross < 50270 ? Theme.accent : (sm.projGross < 125140 ? Theme.orange : Theme.red));
        c.addView(Ui.chip("account_balance", TaxEngine.bandName(sm.projGross), bandColor));
        if ("Tax Year".equals(range)) {
            LinearLayout pr = Ui.h();
            pr.setPadding(0, Ui.dp(14), 0, Ui.dp(6));
            pr.addView(Ui.text("Year progress", 13, 500, Theme.text2), Ui.weight(1));
            pr.addView(Ui.text(String.format(Locale.UK, "%.0f%%", sm.elapsed * 100), 13, 700, Theme.text));
            c.addView(pr);
            Charts.Progress p = new Charts.Progress(a);
            p.color = Theme.accent;
            p.track = Theme.field;
            p.set((float) sm.elapsed);
            c.addView(p, Ui.lp(Ui.MATCH, Ui.dp(10)));
        }
        return c;
    }

    private View jobsCard(Summary sm) {
        LinearLayout c = Ui.card();
        c.addView(Ui.caption("Take-home by employer"));
        List<Map.Entry<String, Double>> list = new ArrayList<Map.Entry<String, Double>>(sm.byJob.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
            @Override public int compare(Map.Entry<String, Double> x, Map.Entry<String, Double> y) { return Double.compare(y.getValue(), x.getValue()); }
        });
        double max = list.isEmpty() ? 1 : Math.max(1, list.get(0).getValue());
        int[] cols = {Theme.accent, Theme.purple, Theme.orange, Theme.teal, Theme.pink, Theme.blue};
        int i = 0;
        for (Map.Entry<String, Double> e : list) {
            LinearLayout r = Ui.h();
            r.setPadding(0, Ui.dp(12), 0, Ui.dp(6));
            r.addView(Ui.single(e.getKey(), 15, 600, Theme.text), Ui.weight(1));
            r.addView(Ui.text(Ui.money(e.getValue(), 0), 15, 700, Theme.text));
            c.addView(r);
            Charts.Progress p = new Charts.Progress(a);
            p.color = cols[i++ % cols.length];
            p.track = Theme.field;
            p.set((float) (e.getValue() / max));
            c.addView(p, Ui.lp(Ui.MATCH, Ui.dp(8)));
        }
        return c;
    }

    // ------------------------------------------------------------------ breakdown

    /** Refund / underpayment estimate card (shared with the tax year summary). */
    static View taxCheckCard(MainActivity a, int startYear) {
        TaxEngine.TaxCheck tc = TaxEngine.taxCheck(Store.entries, startYear, java.time.LocalDate.now(), Store.str("defaultTaxCode", "1257L"));
        if (!tc.meaningful) return null;
        LinearLayout c = Ui.card();
        LinearLayout h = Ui.h();
        double d = tc.difference;
        boolean refund = d > 20, owe = d < -20;
        int col = refund ? Theme.green : (owe ? Theme.orange : Theme.accent);
        h.addView(Ui.badge(refund ? "savings" : (owe ? "info" : "check_circle"), col, 40));
        h.addView(Ui.space(12));
        LinearLayout t = Ui.v();
        t.addView(Ui.caption("Tax check · " + TaxEngine.taxYearLabel(startYear)));
        String head = refund ? "You may be due about " + Ui.money(d, 0) + " back"
                : (owe ? "You may have underpaid about " + Ui.money(-d, 0) : "Your tax looks on track");
        t.addView(Ui.text(head, 17, 700, Theme.text));
        h.addView(t, Ui.weight(1));
        c.addView(h);
        LinearLayout r = Ui.h();
        r.setPadding(0, Ui.dp(14), 0, Ui.dp(8));
        LinearLayout p1 = Ui.v();
        p1.addView(Ui.text("Tax deducted", 12.5f, 500, Theme.text2));
        p1.addView(Ui.text(Ui.money(tc.paid), 18, 800, Theme.text));
        r.addView(p1, Ui.weight(1));
        LinearLayout p2 = Ui.v();
        p2.addView(Ui.text("Expected so far", 12.5f, 500, Theme.text2));
        p2.addView(Ui.text(Ui.money(tc.expected), 18, 800, Theme.text));
        r.addView(p2, Ui.weight(1));
        c.addView(r);
        TextView n = Ui.text(refund ? "Common after emergency tax codes (e.g. 1257L M1) or a job change. HMRC usually adjusts your code or refunds after the tax year ends."
                : (owe ? "This can happen with more than one job or a wrong tax code. HMRC normally collects it through your tax code next year."
                : "Based on HMRC's cumulative basis with your latest tax code."), 13, 400, Theme.text2);
        n.setLineSpacing(0, 1.15f);
        c.addView(n);
        return c;
    }

    private void breakdown(Summary sm) {
        if ("Tax Year".equals(range)) {
            View tc = taxCheckCard(a, TaxEngine.startYearFor(Store.str("selectedTaxYear", "Current"), LocalDate.now()));
            if (tc != null) add(tc);
            int sy = TaxEngine.startYearFor(Store.str("selectedTaxYear", "Current"), LocalDate.now());
            View al = TaxSummaryPage.allowanceCard(a, sy);
            if (al != null) add(al);
            View hol = TaxSummaryPage.holidayCard(a, sy);
            if (hol != null) add(hol);
        }
        // Where gross went
        LinearLayout c = Ui.card();
        c.addView(Ui.caption("Where your gross went"));
        double[] v = {sm.net, sm.tax, sm.ni, sm.pension, sm.loan};
        int[] cols = {Theme.accent, Theme.red, Theme.orange, Theme.purple, Theme.blue};
        String[] names = {"Take-home", "Income tax", "National Insurance", "Pension", "Student loan"};
        c.addView(donutBlock(v, cols, names, sm.gross, "Gross"));
        add(c);

        // Composition
        LinearLayout c2 = Ui.card();
        c2.addView(Ui.caption("Earnings composition"));
        double[] v2 = {sm.base, sm.overtime, sm.premium, sm.bonus, sm.travel};
        int[] cols2 = {Theme.accent, Theme.orange, Theme.purple, Theme.pink, Theme.teal};
        String[] names2 = {"Base pay", "Overtime uplift", "Premium / unsocial", "Bonuses", "Travel"};
        double tot = 0;
        for (double x : v2) tot += x;
        if (tot <= 0) {
            TextView t = Ui.text("Composition appears once you log shifts with hours (payslip-total entries don't include a split).", 14, 400, Theme.text2);
            t.setPadding(0, Ui.dp(10), 0, 0);
            c2.addView(t);
        } else {
            Charts.Stack st = new Charts.Stack(a);
            st.set(v2, cols2);
            c2.addView(st, Ui.lpm(Ui.MATCH, Ui.dp(18), 0, 14, 0, 10));
            for (int i = 0; i < v2.length; i++) if (v2[i] > 0.005) c2.addView(legendRow(cols2[i], names2[i], v2[i], v2[i] / tot));
        }
        add(c2);

        // Holiday pay
        double holHours = sm.hours * 0.1207;
        double avgRate = sm.hours > 0 ? sm.gross / sm.hours : 0;
        LinearLayout c3 = Ui.card();
        LinearLayout hh = Ui.h();
        hh.addView(Ui.badge("wb_twilight", Theme.orange, 40));
        hh.addView(Ui.space(12));
        hh.addView(Ui.caption("Accrued holiday pay (12.07%)"), Ui.weight(1));
        c3.addView(hh);
        LinearLayout hr = Ui.h();
        hr.setPadding(0, Ui.dp(14), 0, Ui.dp(10));
        hr.addView(stat("Accrued value", Ui.money(holHours * avgRate, 0), Theme.text), Ui.weight(1.3f));
        hr.addView(stat("Hours", Ui.hours(holHours), Theme.text), Ui.weight(1));
        hr.addView(stat("Days", String.format(Locale.UK, "%.1f", holHours / 7.5), Theme.text), Ui.weight(1));
        c3.addView(hr);
        c3.addView(Ui.text("Calculated using the statutory UK 12.07% accrual rate for variable-hour workers (7.5h days).", 12.5f, 400, Theme.text2));
        add(c3);

        // Tax code
        List<String> ex = TaxEngine.explainTaxCode(sm.taxCode, Store.currency());
        LinearLayout c4 = Ui.card();
        LinearLayout th = Ui.h();
        th.addView(Ui.badge("account_balance", Theme.red, 40));
        th.addView(Ui.space(12));
        LinearLayout tt = Ui.v();
        tt.addView(Ui.caption("Tax code " + sm.taxCode));
        tt.addView(Ui.text(ex.get(0), 17, 700, Theme.text));
        th.addView(tt, Ui.weight(1));
        c4.addView(th);
        for (int i = 1; i < ex.size(); i++) {
            LinearLayout p = Ui.h();
            p.setGravity(Gravity.TOP);
            p.setPadding(0, Ui.dp(10), 0, 0);
            p.addView(Ui.icon("check_circle", Theme.accent, 18));
            p.addView(Ui.space(10));
            TextView t = Ui.text(ex.get(i), 14.5f, 400, Theme.text);
            t.setLineSpacing(0, 1.15f);
            p.addView(t, Ui.weight(1));
            c4.addView(p);
        }
        add(c4);
    }

    private View stat(String label, String value, int color) {
        LinearLayout l = Ui.v();
        l.addView(Ui.text(label, 12.5f, 500, Theme.text2));
        l.addView(Ui.single(value, 19, 800, color));
        return l;
    }

    private View donutBlock(double[] v, int[] cols, String[] names, double total, String centre) {
        LinearLayout wrap = Ui.v();
        FrameLayout f = new FrameLayout(a);
        Charts.Donut d = new Charts.Donut(a);
        d.set(v, cols);
        f.addView(d, new FrameLayout.LayoutParams(Ui.dp(190), Ui.dp(190), Gravity.CENTER));
        LinearLayout ct = Ui.v();
        ct.setGravity(Gravity.CENTER);
        ct.addView(Ui.text(centre, 12.5f, 600, Theme.text2));
        ct.addView(Ui.text(Ui.money(total, 0), 22, 800, Theme.text));
        f.addView(ct, new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
        wrap.addView(f, Ui.lpm(Ui.MATCH, Ui.dp(210), 0, 10, 0, 8));
        for (int i = 0; i < v.length; i++) if (v[i] > 0.005) wrap.addView(legendRow(cols[i], names[i], v[i], total > 0 ? v[i] / total : 0));
        return wrap;
    }

    private View legendRow(int color, String name, double value, double pct) {
        LinearLayout r = Ui.h();
        r.setPadding(0, Ui.dp(7), 0, Ui.dp(7));
        View dot = new View(a);
        dot.setBackground(Ui.oval(color));
        r.addView(dot, new LinearLayout.LayoutParams(Ui.dp(10), Ui.dp(10)));
        r.addView(Ui.space(12));
        r.addView(Ui.text(name, 15, 500, Theme.text), Ui.weight(1));
        r.addView(Ui.text(String.format(Locale.UK, "%.0f%%  ", pct * 100), 13, 600, Theme.text2));
        r.addView(Ui.text(Ui.money(value, 0), 15, 700, Theme.text));
        return r;
    }

    // ------------------------------------------------------------------ simulator

    private SeekBar seek(int max, int progress) {
        SeekBar sb = new SeekBar(a);
        sb.setMax(max);
        sb.setProgress(progress);
        sb.setProgressTintList(ColorStateList.valueOf(Theme.accent));
        sb.setThumbTintList(ColorStateList.valueOf(Theme.accent));
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(Theme.text3));
        sb.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        return sb;
    }

    private interface Change { void on(int p); }

    private SeekBar.OnSeekBarChangeListener listener(final Change c) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) { if (user) c.on(p); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { Ui.haptic(s); }
        };
    }

    private void simulatorSection(final Summary sm) {
        final int age = Store.integer("defaultAge", 25);
        final double defPension = Store.dbl("defaultPensionRate", 5.0);
        if (simPension < 0) simPension = defPension;
        final String code = sm.taxCode;
        final TaxEngine.Scenario base = TaxEngine.weeklyScenario(sm.baseRate, sm.baseWeeklyHours, defPension, age, code);

        // Live shift simulator
        LinearLayout c = Ui.card();
        c.addView(Ui.caption("Live shift simulator"));
        TextView basis = Ui.text("Starting from " + Ui.hours(sm.baseWeeklyHours) + " a week at " + Ui.money(sm.baseRate) + "/h", 13.5f, 400, Theme.text2);
        basis.setPadding(0, Ui.dp(4), 0, Ui.dp(10));
        c.addView(basis);

        final TextView lh = Ui.text("", 15, 600, Theme.text);
        final TextView lr = Ui.text("", 15, 600, Theme.text);
        final TextView lp = Ui.text("", 15, 600, Theme.text);
        final TextView weekly = Ui.text("", 26, 800, Theme.text);
        final TextView annual = Ui.text("", 26, 800, Theme.green);
        final TextView bd = Ui.text("", 13, 500, Theme.text2);

        final Runnable update = new Runnable() {
            @Override public void run() {
                TaxEngine.Scenario s2 = TaxEngine.weeklyScenario(sm.baseRate + rateDelta, sm.baseWeeklyHours + extraHours, simPension, age, code);
                lh.setText("Extra weekly hours: " + (extraHours >= 0 ? "+" : "") + Ui.num(extraHours) + "h");
                lr.setText("Rate change: " + (rateDelta >= 0 ? "+" : "−") + Ui.money(Math.abs(rateDelta)) + "/h");
                lp.setText("Pension: " + Ui.num(simPension) + "%");
                weekly.setText(Ui.money(s2.net));
                double diff = (s2.net - base.net) * 52;
                annual.setText((diff >= 0 ? "+" : "−") + Ui.money(Math.abs(diff), 0));
                annual.setTextColor(diff >= 0 ? Theme.green : Theme.red);
                bd.setText("Gross " + Ui.money(s2.gross) + " · tax " + Ui.money(s2.tax) + " · NI " + Ui.money(s2.ni) + " · pension " + Ui.money(s2.pension));
            }
        };
        c.addView(lh);
        SeekBar sh = seek(40, (int) (extraHours + 20));
        sh.setOnSeekBarChangeListener(listener(new Change() { @Override public void on(int p) { extraHours = p - 20; update.run(); } }));
        c.addView(sh);
        c.addView(lr);
        SeekBar sr = seek(60, (int) Math.round(rateDelta * 4 + 20));
        sr.setOnSeekBarChangeListener(listener(new Change() { @Override public void on(int p) { rateDelta = (p - 20) / 4.0; update.run(); } }));
        c.addView(sr);
        c.addView(lp);
        SeekBar spn = seek(30, (int) Math.round(simPension * 2));
        spn.setOnSeekBarChangeListener(listener(new Change() { @Override public void on(int p) { simPension = p / 2.0; update.run(); } }));
        c.addView(spn);

        LinearLayout res = Ui.h();
        res.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        res.setBackground(Ui.rr(Theme.dark ? Theme.field : Theme.surface, 12));
        LinearLayout w = Ui.v();
        w.addView(Ui.text("Weekly take-home", 12.5f, 500, Theme.text2));
        w.addView(weekly);
        res.addView(w, Ui.weight(1));
        LinearLayout an = Ui.v();
        an.addView(Ui.text("Annual impact", 12.5f, 500, Theme.text2));
        an.addView(annual);
        res.addView(an, Ui.weight(1));
        c.addView(res, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 12, 0, 8));
        c.addView(bd);
        update.run();
        add(c);

        // Reverse rate solver
        if (targetNet < 0) targetNet = Math.max(100, Math.round(base.net / 10.0) * 10 + 50);
        LinearLayout c2 = Ui.card();
        c2.addView(Ui.caption("Reverse rate solver"));
        TextView d = Ui.text("Find the hourly rate you'd need to hit a weekly take-home at " + Ui.hours(sm.baseWeeklyHours) + " a week.", 13.5f, 400, Theme.text2);
        d.setPadding(0, Ui.dp(4), 0, Ui.dp(10));
        c2.addView(d);
        final TextView tl = Ui.text("", 15, 600, Theme.text);
        final TextView req = Ui.text("", 30, 800, Theme.accent);
        final TextView cmp = Ui.text("", 13, 500, Theme.text2);
        final Runnable up2 = new Runnable() {
            @Override public void run() {
                tl.setText("Target weekly net: " + Ui.money(targetNet, 0));
                double r = TaxEngine.requiredRate(targetNet, sm.baseWeeklyHours, simPension, age, code);
                req.setText(Ui.money(r) + "/h");
                double diff = r - sm.baseRate;
                cmp.setText((diff >= 0 ? "+" : "−") + Ui.money(Math.abs(diff)) + " vs your current " + Ui.money(sm.baseRate) + "/h");
            }
        };
        c2.addView(tl);
        SeekBar st = seek(190, (int) Math.max(0, Math.min(190, (targetNet - 100) / 10)));
        st.setOnSeekBarChangeListener(listener(new Change() { @Override public void on(int p) { targetNet = 100 + p * 10; up2.run(); } }));
        c2.addView(st);
        LinearLayout box = Ui.v();
        box.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        box.setBackground(Ui.rr(Theme.accentSoft, 20));
        box.addView(Ui.text("Required hourly rate", 12.5f, 500, Theme.text2));
        box.addView(req);
        box.addView(cmp);
        c2.addView(box, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        up2.run();
        add(c2);

        // Minimum wage reference
        LinearLayout c3 = Ui.card();
        c3.addView(Ui.caption("UK minimum wage · from April 2026"));
        LocalDate now = LocalDate.now();
        LinearLayout mw = Ui.h();
        mw.setPadding(0, Ui.dp(12), 0, 0);
        mw.addView(stat("21 and over", Ui.money(TaxEngine.minimumWage(21, now)), Theme.text), Ui.weight(1));
        mw.addView(stat("18–20", Ui.money(TaxEngine.minimumWage(19, now)), Theme.text), Ui.weight(1));
        mw.addView(stat("Under 18", Ui.money(TaxEngine.minimumWage(17, now)), Theme.text), Ui.weight(1));
        c3.addView(mw);
        add(c3);
    }
}
