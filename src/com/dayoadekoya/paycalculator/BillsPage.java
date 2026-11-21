package com.dayoadekoya.paycalculator;

import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dayoadekoya.paycalculator.Model.Bill;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;

/** Monthly bills — powers the "safe to spend" card on Home. */
public class BillsPage extends Page {
    static final String[] CATS = {"Rent / Mortgage", "Bills", "Phone & Internet", "Transport", "Subscriptions", "Savings", "Debt", "Other"};
    static final String[] ICONS = {"home", "receipt_long", "sync", "directions_car", "redeem", "savings", "account_balance", "info"};

    @Override public String title() { return "Bills & budget"; }
    @Override public String subtitle() { return "See what's safe to spend after your bills"; }

    @Override protected void actions() {
        s.addAction("add", "Add bill", new Runnable() { @Override public void run() { edit(null); } });
    }

    static String icon(String cat) {
        int i = Arrays.asList(CATS).indexOf(cat);
        return i >= 0 ? ICONS[i] : "receipt_long";
    }

    static int color(String cat) {
        switch (cat) {
            case "Rent / Mortgage": return Theme.indigo;
            case "Savings": return Theme.green;
            case "Debt": return Theme.red;
            case "Transport": return Theme.cyan;
            case "Subscriptions": return Theme.pink;
            case "Phone & Internet": return Theme.blue;
            default: return Theme.orange;
        }
    }

    public static double total() {
        double t = 0;
        for (Bill b : Store.bills) t += b.amount;
        return t;
    }

    @Override protected void build() {
        double total = total();
        Budget bd = Budget.now();
        int on = Theme.onHero();
        LinearLayout hero = Ui.v();
        hero.setBackground(new Glass.Hero(34));
        hero.setPadding(Ui.dp(22), Ui.dp(22), Ui.dp(22), Ui.dp(20));
        TextView cap = Ui.text("SAFE TO SPEND THIS MONTH", 12, 700, Theme.withAlpha(on, 0.85f));
        cap.setLetterSpacing(0.06f);
        hero.addView(cap);
        TextView big = Ui.single("", 38, 800, on);
        big.setText(Glass.bigMoney(bd.safe, on));
        Ui.autosize(big, 22, 38);
        hero.addView(big);
        hero.addView(Ui.text(Ui.money(bd.income, 0) + " take-home · " + Ui.money(total, 0) + " bills", 13, 600, Theme.withAlpha(on, 0.85f)));
        hero.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        add(hero);

        if (Store.bills.isEmpty()) {
            LinearLayout c = Ui.card();
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            c.setPadding(Ui.dp(24), Ui.dp(26), Ui.dp(24), Ui.dp(24));
            c.addView(Ui.badge("account_balance_wallet", Theme.accent, 60));
            TextView t = Ui.text("Add your regular bills", 18, 700, Theme.text);
            t.setPadding(0, Ui.dp(14), 0, Ui.dp(6));
            c.addView(t);
            TextView d = Ui.text("Rent, phone, subscriptions, savings… Home will show how much of this month's pay is left after them.", 14.5f, 400, Theme.text2);
            d.setGravity(Gravity.CENTER);
            c.addView(d);
            c.addView(Ui.space(16));
            c.addView(Ui.primary("Add bill", "add", new Runnable() { @Override public void run() { edit(null); } }), Ui.lp(Ui.MATCH, Ui.WRAP));
            add(c);
            return;
        }
        add(Ui.sectionTitle("Monthly bills · " + Ui.money(total)));
        LinearLayout g = Ui.group();
        int today = LocalDate.now().getDayOfMonth();
        for (final Bill b : Store.bills) {
            String due = b.dayOfMonth < today ? "Paid · due the " + ordinal(b.dayOfMonth) : (b.dayOfMonth == today ? "Due today" : "Due the " + ordinal(b.dayOfMonth));
            TextView v = Ui.row(g, icon(b.category), color(b.category), b.name, due + " · " + b.category, Ui.money(b.amount), false, new Runnable() {
                @Override public void run() { edit(b); }
            });
            if (v != null) v.setTextColor(b.dayOfMonth < today ? Theme.text2 : Theme.text);
        }
        add(g);
        TextView n = Ui.text("Safe to spend = take-home logged this month minus all your monthly bills. It grows as you log more pay.", 13, 400, Theme.text2);
        n.setPadding(Ui.dp(10), Ui.dp(4), Ui.dp(10), 0);
        add(n);
    }

    static String ordinal(int d) {
        if (d >= 11 && d <= 13) return d + "th";
        switch (d % 10) { case 1: return d + "st"; case 2: return d + "nd"; case 3: return d + "rd"; default: return d + "th"; }
    }

    private void edit(final Bill existing) {
        final Bill b = existing != null ? existing : new Bill();
        final Sheets.Handle h = Sheets.sheet(a, existing == null ? "Add bill" : "Edit bill", null);
        final EditText name = Ui.field("Name (e.g. Rent)", b.name, false);
        h.body.addView(name, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
        final EditText amount = Ui.field("Amount per month (" + Store.currency() + ")", b.amount > 0 ? Ui.num(b.amount) : "", true);
        h.body.addView(amount, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
        final EditText day = Ui.field("Day of month it's paid (1–31)", String.valueOf(b.dayOfMonth), true);
        day.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        h.body.addView(day, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        final String[] cat = {b.category};
        final LinearLayout g = Ui.group();
        g.setBackground(Ui.rr(Theme.dark ? Theme.field : Theme.surface, 12));
        final Runnable[] draw = new Runnable[1];
        draw[0] = new Runnable() {
            @Override public void run() {
                g.removeAllViews();
                Ui.row(g, icon(cat[0]), color(cat[0]), "Category", null, cat[0], true, new Runnable() {
                    @Override public void run() {
                        Sheets.choose(a, "Category", CATS, Arrays.asList(CATS).indexOf(cat[0]), new Ui.Fn<Integer>() {
                            @Override public void on(Integer i) { cat[0] = CATS[i]; draw[0].run(); }
                        });
                    }
                });
            }
        };
        draw[0].run();
        h.body.addView(g);
        Sheets.buttons(h, "Save", new Runnable() {
            @Override public void run() {
                double amt = Ui.parse(amount.getText().toString());
                if (amt <= 0) { a.snack("Enter an amount"); return; }
                b.name = name.getText().toString().trim().isEmpty() ? cat[0] : name.getText().toString().trim();
                b.amount = amt;
                b.dayOfMonth = (int) Math.max(1, Math.min(31, Ui.parse(day.getText().toString())));
                b.category = cat[0];
                if (existing == null) Store.bills.add(b);
                Store.changed();
                h.dismiss();
                render(false);
            }
        });
        if (existing != null) {
            h.body.addView(Ui.space(8));
            h.body.addView(Ui.button("Delete bill", "delete", Theme.withAlpha(Theme.red, 0.12f), Theme.red, new Runnable() {
                @Override public void run() { Store.bills.remove(existing); Store.changed(); h.dismiss(); render(false); }
            }));
        }
        Sheets.show(h);
    }

    /** This month's budget snapshot. */
    public static final class Budget {
        public double income, bills, safe, projected;
        public Bill nextBill;

        public static Budget now() {
            Budget b = new Budget();
            LocalDate t = LocalDate.now();
            for (Model.PayEntry e : Store.entries) {
                LocalDate d = TaxEngine.toDate(e.dateMillis);
                if (d.getYear() == t.getYear() && d.getMonthValue() == t.getMonthValue()) b.income += TaxEngine.net(e);
            }
            double frac = t.getDayOfMonth() / (double) t.lengthOfMonth();
            b.projected = frac > 0.15 ? b.income / frac : b.income;
            b.bills = total();
            b.safe = b.income - b.bills;
            int today = t.getDayOfMonth();
            for (Bill x : Store.bills) if (x.dayOfMonth >= today) { b.nextBill = x; break; }
            return b;
        }
    }

    static String money0(double v) { return Ui.masked() ? Store.currency() + Ui.MASK : String.format(Locale.UK, "%s%,.0f", Store.currency(), v); }
}
