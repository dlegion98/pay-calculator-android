package com.dayoadekoya.paycalculator;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dayoadekoya.paycalculator.Model.DailyPay;
import com.dayoadekoya.paycalculator.Model.PayEntry;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Read-only payslip breakdown — port of iOS PayDetailView. */
public class EntryDetailPage extends Page {
    private final String id;

    public EntryDetailPage(String id) { this.id = id; }

    private PayEntry entry() { return Store.find(id); }

    @Override public String title() {
        PayEntry e = entry();
        return e == null ? "Pay period" : e.displayJob();
    }

    @Override public String subtitle() {
        PayEntry e = entry();
        return e == null ? null : DashboardPage.rangeString(e) + " · " + e.payFrequency;
    }

    @Override protected float expandedDp() { return 70; }

    @Override protected void actions() {
        s.addAction("share", "Share summary", new Runnable() {
            @Override public void run() { PayEntry e = entry(); if (e != null) share(e); }
        });
        s.addAction("content_copy", "Duplicate", new Runnable() {
            @Override public void run() { PayEntry e = entry(); if (e != null) { DashboardPage.duplicate(a, e); a.pop(); } }
        });
        s.addAction("edit", "Edit", new Runnable() {
            @Override public void run() { a.push(new EntryEditorPage(id, null)); }
        });
    }

    /** Plain-text payslip summary for messages or notes (always real figures). */
    private void share(PayEntry e) {
        Ui.unmasked = true;
        try {
            StringBuilder b = new StringBuilder();
            b.append(e.displayJob()).append(" · ").append(DashboardPage.rangeString(e)).append(" (").append(e.payFrequency).append(")\n");
            b.append("Hours: ").append(Ui.hours(TaxEngine.totalHours(e))).append("\n");
            b.append("Gross: ").append(Ui.money(TaxEngine.effectiveGross(e))).append("\n");
            b.append("Income tax: −").append(Ui.money(TaxEngine.displayTax(e))).append(" (code ").append(e.taxCode).append(")\n");
            b.append("National Insurance: −").append(Ui.money(TaxEngine.displayNi(e))).append("\n");
            if (TaxEngine.displayPension(e) > 0) b.append("Pension: −").append(Ui.money(TaxEngine.displayPension(e))).append("\n");
            if (TaxEngine.studentLoan(e) > 0) b.append("Student loan: −").append(Ui.money(TaxEngine.studentLoan(e))).append("\n");
            b.append("Take-home: ").append(Ui.money(TaxEngine.net(e))).append(e.isActual() ? " (from payslip)" : " (estimate)").append("\n");
            b.append("\nSent from Pay Calculator");
            a.shareText("Pay summary · " + e.displayJob(), b.toString());
        } finally { Ui.unmasked = false; }
    }

    @Override public void refresh() {
        PayEntry e = entry();
        if (e != null) { s.setTitle(e.displayJob()); s.setSubtitle(subtitle()); }
        super.refresh();
    }

    @Override protected void build() {
        final PayEntry e = entry();
        if (e == null) {
            add(Ui.text("This pay period no longer exists.", 15, 500, Theme.text2));
            return;
        }
        double gross = TaxEngine.effectiveGross(e), tax = TaxEngine.displayTax(e), ni = TaxEngine.displayNi(e);
        double pension = TaxEngine.displayPension(e), loan = TaxEngine.studentLoan(e), extra = TaxEngine.displayExtra(e);
        double net = TaxEngine.net(e);

        // Hero
        int on = Theme.onHero();
        LinearLayout hero = Ui.v();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setBackground(new Glass.Hero(34));
        hero.setPadding(Ui.dp(20), Ui.dp(24), Ui.dp(20), Ui.dp(22));
        TextView cap = Ui.text(e.isActual() ? "NET PAY · FROM PAYSLIP" : "NET PAY · ESTIMATED", 12, 700, Theme.withAlpha(on, 0.85f));
        cap.setLetterSpacing(0.06f);
        hero.addView(cap);
        TextView big = Ui.single("", 46, 800, on);
        big.setText(Glass.bigMoney(net, on));
        big.setPadding(0, Ui.dp(4), 0, Ui.dp(4));
        big.setGravity(Gravity.CENTER);
        Ui.autosize(big, 24, 46);
        hero.addView(big);
        LocalDate d = TaxEngine.toDate(e.dateMillis);
        hero.addView(Ui.text(d.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.UK)), 13, 600, Theme.withAlpha(on, 0.85f)));
        // Compare with the previous pay period for the same job
        PayEntry prev = null;
        for (PayEntry o : Store.entries) {
            if (o == e || o.dateMillis >= e.dateMillis) continue;
            if (!o.displayJob().equalsIgnoreCase(e.displayJob())) continue;
            if (prev == null || o.dateMillis > prev.dateMillis) prev = o;
        }
        if (prev != null) {
            double diff = net - TaxEngine.net(prev);
            TextView cmp = Ui.text((diff >= 0 ? "▲ " : "▼ ") + Ui.money(Math.abs(diff)) + " vs previous " + e.displayJob() + " pay", 12.5f, 700, on);
            cmp.setBackground(Ui.rr(Theme.withAlpha(on, 0.18f), 50));
            cmp.setPadding(Ui.dp(12), Ui.dp(5), Ui.dp(12), Ui.dp(5));
            LinearLayout.LayoutParams cp = Ui.lpm(Ui.WRAP, Ui.WRAP, 0, 12, 0, 0);
            cp.gravity = Gravity.CENTER_HORIZONTAL;
            hero.addView(cmp, cp);
        }
        hero.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 14));
        final double fNet = net;
        hero.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { Ui.haptic(v); DashboardPage.copyAmount(a, fNet); return true; }
        });
        hero.setContentDescription("Net pay " + Ui.money(net) + ". Long-press to copy.");
        add(hero);

        // Estimated → nudge to confirm against the real payslip
        if (!e.isActual()) {
            LinearLayout cta = Ui.card();
            cta.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 14));
            LinearLayout r = Ui.h();
            r.addView(Ui.badge("receipt_long", Theme.purple, 34));
            r.addView(Ui.space(12));
            LinearLayout t = Ui.v();
            t.addView(Ui.text("Got your payslip?", 16, 650, Theme.text));
            t.addView(Ui.text("Add the real figures to replace this estimate and keep your totals exact.", 13.5f, 400, Theme.text2));
            r.addView(t, Ui.weight(1));
            r.addView(Ui.icon("chevron_right", Theme.text3, 20));
            cta.addView(r);
            Ui.tap(cta, 16, new Runnable() { @Override public void run() { a.push(new EntryEditorPage(id, "Payslip Totals")); } });
            add(cta);
        }

        // Breakdown
        if (gross > 0) {
            LinearLayout c = Ui.card();
            c.addView(Ui.caption("Where your gross went"));
            LinearLayout gr = Ui.h();
            gr.setPadding(0, Ui.dp(10), 0, Ui.dp(12));
            gr.addView(Ui.text("Gross pay", 15, 600, Theme.text2), Ui.weight(1));
            gr.addView(Ui.text(Ui.money(gross), 18, 750, Theme.text));
            c.addView(gr);
            double[] vals = {net, tax, ni, pension, loan, extra};
            int[] cols = {Theme.accent, Theme.red, Theme.orange, Theme.purple, Theme.blue, Theme.pink};
            String[] names = {"Take-home", "Income tax", "National Insurance", "Pension", "Student loan", "Other deductions"};
            Charts.Stack st = new Charts.Stack(a);
            st.set(vals, cols);
            c.addView(st, Ui.lpm(Ui.MATCH, Ui.dp(18), 0, 0, 0, 12));
            for (int i = 0; i < vals.length; i++) {
                if (vals[i] <= 0.004) continue;
                c.addView(legend(cols[i], names[i], vals[i], vals[i] / gross));
            }
            add(c);
        }

        // Summary
        add(Ui.sectionTitle("Earnings summary"));
        LinearLayout g = Ui.group();
        Ui.row(g, "payments", Theme.green, "Gross pay", e.actualGrossPay != null && e.actualGrossPay > 0 ? "From payslip" : "Calculated", Ui.money(gross), false, null);
        Ui.row(g, "account_balance", Theme.red, "Income tax", "Code " + e.taxCode, "−" + Ui.money(tax), false, null);
        Ui.row(g, "percent", Theme.orange, "National Insurance", null, "−" + Ui.money(ni), false, null);
        if (pension > 0) Ui.row(g, "savings", Theme.purple, "Pension", e.pensionRate > 0 ? Ui.num(e.pensionRate) + "%" + (e.isSalarySacrifice ? " · salary sacrifice" : "") : null, "−" + Ui.money(pension), false, null);
        if (loan > 0) Ui.row(g, "school", Theme.blue, "Student loan", TaxEngine.config.studentLoanPlan, "−" + Ui.money(loan), false, null);
        if (extra > 0) Ui.row(g, "remove", Theme.pink, "Other deductions", null, "−" + Ui.money(extra), false, null);
        TextView nv = Ui.row(g, "account_balance_wallet", Theme.accent, "Net pay", e.isActual() ? "Received" : "Estimated", Ui.money(net), false, null);
        if (nv != null) { nv.setTypeface(Ui.font(800)); nv.setTextSize(17); }
        add(g);

        // Details
        add(Ui.sectionTitle("Details"));
        LinearLayout info = Ui.group();
        double hours = TaxEngine.totalHours(e);
        Ui.row(info, "schedule", Theme.accent, "Hours worked", null, Ui.hours(hours), false, null);
        if (!e.isSalariedMode && e.hourlyRate > 0) Ui.row(info, "currency_pound", Theme.green, "Hourly rate", null, Ui.money(e.hourlyRate), false, null);
        if (e.isSalariedMode) Ui.row(info, "account_balance_wallet", Theme.teal, "Fixed gross", null, Ui.money(e.fixedMonthlyGross), false, null);
        if (hours > 0) Ui.row(info, "trending_up", Theme.teal, "Take-home per hour", null, Ui.money(net / hours), false, null);
        if (gross > 0) Ui.row(info, "pie_chart", Theme.indigo, "Kept of gross", null, String.format(Locale.UK, "%.1f%%", net / gross * 100), false, null);
        Ui.row(info, "autorenew", Theme.blue, "Frequency", null, e.payFrequency, false, null);
        if (!e.pensionProvider.isEmpty()) Ui.row(info, "savings", Theme.purple, "Pension provider", null, e.pensionProvider, false, null);
        add(info);

        // Days
        boolean anyDay = false;
        for (DailyPay dp : e.days) if (!dp.isEmpty()) { anyDay = true; break; }
        if (anyDay && !(e.days.size() == 1 && e.isActual())) {
            add(Ui.sectionTitle("Daily breakdown"));
            LinearLayout dg = Ui.group();
            for (DailyPay dp : e.days) {
                if (dp.isEmpty()) continue;
                LocalDate day = d.plusDays(dp.dayIndex);
                StringBuilder sub = new StringBuilder(Ui.hours(TaxEngine.dayHours(dp)));
                if (dp.isOvertime) sub.append(" · overtime");
                if (dp.unpaidBreakMinutes > 0) sub.append(" · ").append(Math.round(dp.unpaidBreakMinutes)).append(" min break");
                if (dp.travelPay > 0) sub.append(" · travel ").append(Ui.money(dp.travelPay, 0));
                if (dp.bonus > 0) sub.append(" · bonus ").append(Ui.money(dp.bonus, 0));
                Ui.row(dg, dp.isOvertime ? "bolt" : "today", dp.isOvertime ? Theme.orange : Theme.accent,
                        day.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.UK)), sub.toString(),
                        e.isSalariedMode ? null : Ui.money(TaxEngine.dayGross(dp, e.hourlyRate)), false, null);
            }
            add(dg);
        }

        if (!e.notes.trim().isEmpty()) {
            add(Ui.sectionTitle("Notes"));
            LinearLayout nc = Ui.card();
            nc.addView(Ui.text(e.notes, 15.5f, 400, Theme.text));
            add(nc);
        }

        add(Ui.space(10));
        LinearLayout btns = Ui.h();
        btns.addView(Ui.tonal("Edit", "edit", new Runnable() { @Override public void run() { a.push(new EntryEditorPage(id, null)); } }), Ui.weight(1));
        btns.addView(Ui.space(10));
        btns.addView(Ui.button("Delete", "delete", Theme.withAlpha(Theme.red, 0.12f), Theme.red, new Runnable() {
            @Override public void run() {
                Sheets.confirm(a, "Delete this pay period?", null, "Delete", true, new Runnable() {
                    @Override public void run() { a.pop(); DashboardPage.delete(a, e); }
                });
            }
        }), Ui.weight(1));
        add(btns);
    }

    private View legend(int color, String name, double value, double pct) {
        LinearLayout r = Ui.h();
        r.setPadding(0, Ui.dp(6), 0, Ui.dp(6));
        View dot = new View(a);
        dot.setBackground(Ui.oval(color));
        r.addView(dot, new LinearLayout.LayoutParams(Ui.dp(10), Ui.dp(10)));
        r.addView(Ui.space(12));
        r.addView(Ui.text(name, 15, 500, Theme.text), Ui.weight(1));
        r.addView(Ui.text(String.format(Locale.UK, "%.0f%%", pct * 100), 13, 600, Theme.text2));
        TextView v = Ui.text(Ui.money(value), 15, 650, Theme.text);
        v.setMinWidth(Ui.dp(92));
        v.setGravity(Gravity.END);
        r.addView(v);
        return r;
    }
}
