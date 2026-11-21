package com.dayoadekoya.paycalculator;

import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dayoadekoya.paycalculator.Model.WorkExpense;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;

/** Work expenses & business mileage with an estimated tax-relief figure. */
public class ExpensesPage extends Page {
    private static final String[] CATS = {"Uniform & Workwear", "Tools & Equipment", "Travel", "Training", "Phone & Internet", "Professional Fees", "Working from Home", "Other"};

    @Override public String title() { return "Expenses & mileage"; }
    @Override public String subtitle() { return "Tax year " + TaxEngine.taxYearLabel(TaxEngine.taxYearStart(LocalDate.now())); }

    @Override protected void actions() {
        s.addAction("add", "Add expense", new Runnable() { @Override public void run() { edit(null); } });
    }

    private static double claimable(WorkExpense x) { return x.isMileage ? TaxEngine.mileageClaim(x.miles) : x.amount; }

    @Override protected void build() {
        int sy = TaxEngine.taxYearStart(LocalDate.now());
        double total = 0, miles = 0;
        for (WorkExpense x : Store.expenses) {
            if (TaxEngine.taxYearStart(TaxEngine.toDate(x.dateMillis)) != sy) continue;
            if (x.isMileage) miles += x.miles; else total += x.amount;
        }
        total += TaxEngine.mileageClaim(miles);
        TaxEngine.Totals ty = TaxEngine.taxYearTotals(Store.entries, sy);
        double proj = ty.gross / TaxEngine.taxYearElapsed(sy, LocalDate.now());
        double rate = TaxEngine.marginalRate(proj);
        if (rate == 0) rate = 0.20;
        double relief = total * rate;

        int on = Theme.onHero();
        LinearLayout hero = Ui.v();
        hero.setBackground(new Glass.Hero(34));
        hero.setPadding(Ui.dp(22), Ui.dp(22), Ui.dp(22), Ui.dp(20));
        TextView cap = Ui.text("TAX RELIEF THIS YEAR", 12, 700, Theme.withAlpha(on, 0.85f));
        cap.setLetterSpacing(0.06f);
        hero.addView(cap);
        LinearLayout r = Ui.h();
        r.setGravity(Gravity.BOTTOM);
        r.addView(Ui.text(Ui.money(relief), 38, 800, on));
        TextView est = Ui.text("  estimated", 14, 600, Theme.withAlpha(on, 0.8f));
        est.setPadding(0, 0, 0, Ui.dp(8));
        r.addView(est);
        hero.addView(r);
        LinearLayout chips = Ui.h();
        chips.setPadding(0, Ui.dp(12), 0, Ui.dp(10));
        chips.addView(Ui.chip("payments", Ui.money(total, 0) + " claimable", on));
        chips.addView(Ui.space(6));
        chips.addView(Ui.chip("percent", "at " + Math.round(rate * 100) + "% rate", on));
        hero.addView(chips);
        hero.addView(Ui.text("Allowable work expenses can reduce your tax. This is an estimate — check GOV.UK or an accountant before claiming.", 12.5f, 450, Theme.withAlpha(on, 0.85f)));
        hero.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        add(hero);

        if (Store.expenses.isEmpty()) {
            LinearLayout c = Ui.card();
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            c.setPadding(Ui.dp(24), Ui.dp(26), Ui.dp(24), Ui.dp(24));
            c.addView(Ui.badge("receipt_long", Theme.pink, 60));
            TextView t = Ui.text("No expenses yet", 18, 700, Theme.text);
            t.setPadding(0, Ui.dp(14), 0, Ui.dp(6));
            c.addView(t);
            TextView d = Ui.text("Log work expenses and business mileage to estimate the tax relief you may be owed.", 14.5f, 400, Theme.text2);
            d.setGravity(Gravity.CENTER);
            c.addView(d);
            c.addView(Ui.space(16));
            c.addView(Ui.primary("Add expense", "add", new Runnable() { @Override public void run() { edit(null); } }), Ui.lp(Ui.MATCH, Ui.WRAP));
            add(c);
            return;
        }
        add(Ui.sectionTitle("Logged"));
        LinearLayout g = Ui.group();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);
        for (final WorkExpense x : Store.expenses) {
            String sub = TaxEngine.toDate(x.dateMillis).format(f) + (x.isMileage ? " · " + Ui.num(x.miles) + " miles" : "") + (x.note.isEmpty() ? "" : " · " + x.note);
            Ui.row(g, x.isMileage ? "directions_car" : "receipt_long", x.isMileage ? Theme.cyan : Theme.pink,
                    x.isMileage ? "Business mileage" : x.category, sub, Ui.money(claimable(x)), false, new Runnable() {
                @Override public void run() { edit(x); }
            });
        }
        add(g);
        TextView m = Ui.text("Mileage is claimed at the HMRC approved rate of 45p per mile for the first 10,000 miles, then 25p.", 13, 400, Theme.text2);
        m.setPadding(Ui.dp(10), Ui.dp(4), Ui.dp(10), 0);
        add(m);
    }

    private void edit(final WorkExpense existing) {
        final WorkExpense x = existing != null ? existing : new WorkExpense();
        final Sheets.Handle h = Sheets.sheet(a, existing == null ? "Add expense" : "Edit expense", null);
        final boolean[] mileage = {x.isMileage};
        final String[] cat = {x.category == null ? "Other" : x.category};
        final LocalDate[] date = {TaxEngine.toDate(x.dateMillis)};
        final LinearLayout dyn = Ui.v();

        final EditText amount = Ui.field("Amount (" + Store.currency() + ")", x.amount > 0 ? Ui.num(x.amount) : "", true);
        final EditText miles = Ui.field("Miles driven", x.miles > 0 ? Ui.num(x.miles) : "", true);
        final EditText note = Ui.field("Note (optional)", x.note, false);

        final Runnable layout = new Runnable() {
            @Override public void run() {
                dyn.removeAllViews();
                LinearLayout g = Ui.group();
                g.setBackground(Ui.rr(Theme.dark ? Theme.field : Theme.surface, 12));
                if (!mileage[0]) {
                    Ui.row(g, "receipt_long", Theme.pink, "Category", null, cat[0], true, new Runnable() {
                        @Override public void run() {
                            Sheets.choose(a, "Category", CATS, Arrays.asList(CATS).indexOf(cat[0]), new Ui.Fn<Integer>() {
                                @Override public void on(Integer i) { cat[0] = CATS[i]; layoutRef[0].run(); }
                            });
                        }
                    });
                }
                Ui.row(g, "calendar_month", Theme.accent, "Date", null, date[0].format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)), true, new Runnable() {
                    @Override public void run() {
                        Sheets.date(a, date[0], new Ui.Fn<LocalDate>() { @Override public void on(LocalDate d) { date[0] = d; layoutRef[0].run(); } });
                    }
                });
                dyn.addView(g);
                removeParent(amount); removeParent(miles);
                dyn.addView(mileage[0] ? miles : amount, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 4, 0, 8));
            }
        };
        layoutRef[0] = layout;
        h.body.addView(Ui.segmented(new String[]{"Expense", "Mileage"}, mileage[0] ? 1 : 0, new Ui.Fn<Integer>() {
            @Override public void on(Integer i) { mileage[0] = i == 1; layout.run(); }
        }), Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        h.body.addView(dyn);
        h.body.addView(note);
        layout.run();
        Sheets.buttons(h, "Save", new Runnable() {
            @Override public void run() {
                x.isMileage = mileage[0];
                x.category = mileage[0] ? "Mileage" : cat[0];
                x.amount = Ui.parse(amount.getText().toString());
                x.miles = Ui.parse(miles.getText().toString());
                x.note = note.getText().toString().trim();
                x.dateMillis = TaxEngine.toMillis(date[0]);
                if ((x.isMileage ? x.miles : x.amount) <= 0) { a.snack("Enter an amount first"); return; }
                if (existing == null) Store.expenses.add(x);
                Store.changed();
                h.dismiss();
                render(false);
            }
        });
        if (existing != null) {
            h.body.addView(Ui.space(8));
            h.body.addView(Ui.button("Delete", "delete", Theme.withAlpha(Theme.red, 0.12f), Theme.red, new Runnable() {
                @Override public void run() { Store.expenses.remove(existing); Store.changed(); h.dismiss(); render(false); }
            }));
        }
        Sheets.show(h);
    }

    private final Runnable[] layoutRef = new Runnable[1];

    private static void removeParent(android.view.View v) {
        if (v.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) v.getParent()).removeView(v);
    }
}
