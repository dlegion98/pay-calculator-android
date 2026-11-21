package com.dayoadekoya.paycalculator;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.Locale;

/** Tax-year totals at a glance — port of iOS TaxYearSummaryView. */
public class TaxSummaryPage extends Page {
    private int startYear = TaxEngine.startYearFor(Store.str("selectedTaxYear", "Current"), LocalDate.now());

    @Override public String title() { return "Tax year " + TaxEngine.taxYearLabel(startYear); }
    @Override public String subtitle() { return "6 April " + startYear + " – 5 April " + (startYear + 1); }
    @Override protected float expandedDp() { return 70; }

    @Override protected void actions() {
        s.addAction("event", "Change tax year", new Runnable() {
            @Override public void run() {
                final java.util.List<Integer> years = TaxEngine.availableTaxYears(Store.entries, LocalDate.now());
                String[] opts = new String[years.size()];
                for (int i = 0; i < opts.length; i++) opts[i] = "Tax year " + TaxEngine.taxYearLabel(years.get(i));
                Sheets.choose(a, "Tax year", opts, years.indexOf(startYear), new Ui.Fn<Integer>() {
                    @Override public void on(Integer i) { startYear = years.get(i); s.setTitle(title()); s.setSubtitle(subtitle()); render(true); }
                });
            }
        });
    }

    /** How much of the tax-free personal allowance this year's pay has used. */
    static View allowanceCard(MainActivity a, int startYear) {
        TaxEngine.Totals t = TaxEngine.taxYearTotals(Store.entries, startYear);
        if (t.gross <= 0 || "Flat %".equals(Store.str("taxCalculationMode", "HMRC Bands"))) return null;
        String code = Store.str("defaultTaxCode", "1257L");
        long latest = 0;
        for (com.dayoadekoya.paycalculator.Model.PayEntry e : Store.entries) {
            if (TaxEngine.inTaxYear(e, startYear) && e.dateMillis > latest) { latest = e.dateMillis; code = e.taxCode; }
        }
        double allowance = TaxEngine.codeAllowance(code);
        if (allowance <= 0 || code.toUpperCase(Locale.UK).startsWith("K")) return null;
        double used = Math.min(1, t.gross / allowance);
        LinearLayout c = Ui.card();
        c.addView(Ui.caption("Personal allowance"));
        TextView v = Ui.text(Ui.money(Math.min(t.gross, allowance), 0) + " of " + Ui.money(allowance, 0) + " used", 20, 750, Theme.text);
        v.setPadding(0, Ui.dp(4), 0, 0);
        c.addView(v);
        Charts.Progress p = new Charts.Progress(a);
        p.color = used >= 1 ? Theme.orange : Theme.green;
        p.track = Theme.field;
        p.set((float) used);
        c.addView(p, Ui.lpm(Ui.MATCH, Ui.dp(8), 0, 12, 0, 8));
        String note = t.gross < allowance
                ? Ui.money(allowance - t.gross, 0) + " more you can earn this tax year before income tax applies (tax code " + code + ")."
                : "You've earned past your tax-free amount, so income tax now applies on each extra pound (tax code " + code + ").";
        c.addView(Ui.text(note, 13, 400, Theme.text2));
        return c;
    }

    /** Statutory holiday accrual for irregular-hours / part-year workers: 12.07% of hours worked. */
    static View holidayCard(MainActivity a, int startYear) {
        double hours = 0, pay = 0;
        for (com.dayoadekoya.paycalculator.Model.PayEntry e : Store.entries) {
            if (!TaxEngine.inTaxYear(e, startYear) || e.isSalariedMode || e.hourlyRate <= 0) continue;
            double h = TaxEngine.totalHours(e);
            hours += h;
            pay += h * e.hourlyRate;
        }
        if (hours < 1) return null;
        double accrued = hours * 0.1207;
        double rate = pay / hours;
        LinearLayout c = Ui.card();
        LinearLayout r = Ui.h();
        r.addView(Ui.badge("beach_access", Theme.cyan, 40));
        r.addView(Ui.space(14));
        LinearLayout t = Ui.v();
        t.addView(Ui.caption("Holiday accrued this tax year"));
        t.addView(Ui.text(Ui.hours(accrued) + " · about " + Ui.money(accrued * rate, 0), 20, 750, Theme.text));
        r.addView(t, Ui.weight(1));
        c.addView(r);
        TextView n = Ui.text("12.07% of the " + Ui.hours(hours) + " you've logged. This is the statutory rate for irregular-hours and part-year workers; check your contract for your actual entitlement.", 12.5f, 400, Theme.text2);
        n.setPadding(0, Ui.dp(10), 0, 0);
        c.addView(n);
        return c;
    }

    @Override protected void build() {
        TaxEngine.Totals t = TaxEngine.taxYearTotals(Store.entries, startYear);
        LinearLayout c = Ui.card();
        c.addView(Ui.caption("Take-home vs deductions"));
        FrameLayout f = new FrameLayout(a);
        Charts.Donut d = new Charts.Donut(a);
        d.thicknessDp = 26;
        double[] v = {t.net, t.tax, t.ni, t.pension, t.studentLoan, t.extra};
        int[] cols = {Theme.accent, Theme.red, Theme.orange, Theme.purple, Theme.blue, Theme.text3};
        d.set(v, cols);
        f.addView(d, new FrameLayout.LayoutParams(Ui.dp(210), Ui.dp(210), Gravity.CENTER));
        LinearLayout ct = Ui.v();
        ct.setGravity(Gravity.CENTER);
        ct.addView(Ui.text("Take-home", 13, 600, Theme.text2));
        ct.addView(Ui.text(Ui.money(t.net, 0), 24, 800, Theme.text));
        f.addView(ct, new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
        c.addView(f, Ui.lpm(Ui.MATCH, Ui.dp(230), 0, 10, 0, 6));
        add(c);
        View tcCard = AnalyticsPage.taxCheckCard(a, startYear);
        if (tcCard != null) add(tcCard);
        View al = allowanceCard(a, startYear);
        if (al != null) add(al);
        View hol = holidayCard(a, startYear);
        if (hol != null) add(hol);

        LinearLayout g = Ui.group();
        Ui.row(g, "payments", Theme.green, "Gross pay", null, Ui.money(t.gross), false, null);
        Ui.row(g, "account_balance", Theme.red, "Income tax", null, Ui.money(t.tax), false, null);
        Ui.row(g, "percent", Theme.orange, "National Insurance", null, Ui.money(t.ni), false, null);
        Ui.row(g, "savings", Theme.purple, "Pension", null, Ui.money(t.pension), false, null);
        if (t.studentLoan > 0) Ui.row(g, "school", Theme.blue, "Student loan", null, Ui.money(t.studentLoan), false, null);
        if (t.extra > 0) Ui.row(g, "remove", Theme.text2, "Other deductions", null, Ui.money(t.extra), false, null);
        Ui.row(g, "account_balance_wallet", Theme.accent, "Take-home", null, Ui.money(t.net), false, null);
        add(g);

        LinearLayout k = Ui.group();
        Ui.row(k, "receipt_long", Theme.indigo, "Pay periods", null, String.valueOf(t.periods), false, null);
        Ui.row(k, "schedule", Theme.accent, "Hours", null, Ui.hours(t.hours), false, null);
        Ui.row(k, "pie_chart", Theme.red, "Effective deduction rate", null, t.gross > 0 ? String.format(Locale.UK, "%.1f%%", t.deductions() / t.gross * 100) : "—", false, null);
        double proj = t.gross / TaxEngine.taxYearElapsed(startYear, LocalDate.now());
        Ui.row(k, "trending_up", Theme.teal, "Projected annual gross", TaxEngine.bandName(proj), Ui.money(proj, 0), false, null);
        // Best and average month
        java.util.Map<String, Double> months = new java.util.LinkedHashMap<String, Double>();
        for (com.dayoadekoya.paycalculator.Model.PayEntry e : Store.entries) {
            if (!TaxEngine.inTaxYear(e, startYear)) continue;
            String key = TaxEngine.toDate(e.dateMillis).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK));
            Double mv = months.get(key);
            months.put(key, (mv == null ? 0 : mv) + TaxEngine.net(e));
        }
        if (months.size() >= 2) {
            String bestK = null; double bestV = -1, sum = 0;
            for (java.util.Map.Entry<String, Double> m : months.entrySet()) { sum += m.getValue(); if (m.getValue() > bestV) { bestV = m.getValue(); bestK = m.getKey(); } }
            Ui.row(k, "celebration", Theme.yellow, "Best month", bestK, Ui.money(bestV, 0), false, null);
            Ui.row(k, "calendar_month", Theme.blue, "Average month", months.size() + " months with pay", Ui.money(sum / months.size(), 0), false, null);
        }
        add(k);
        TextView n = Ui.text("Based on the pay periods logged for this tax year. Payslip figures are used where entered; everything else is an HMRC-band estimate.", 13, 400, Theme.text2);
        n.setPadding(Ui.dp(10), Ui.dp(4), Ui.dp(10), 0);
        add(n);
    }
}
