package com.dayoadekoya.paycalculator;

import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dayoadekoya.paycalculator.Model.Bill;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Subscriptions & bills: every recurring payment, when it's next due, what it costs a month and a
 * year, free-trial warnings and reminders. Also powers the "safe to spend" card on Home.
 */
public class BillsPage extends Page {
    static final String[] CATS = {"Streaming", "Music", "Gaming", "Software & cloud", "News & magazines", "Fitness",
            "Shopping & memberships", "Rent / Mortgage", "Utilities", "Council tax", "Phone & internet", "Insurance",
            "Transport", "Loans & credit", "Savings", "Bills", "Subscriptions", "Other"};
    static final String[] ICONS = {"movie", "music_note", "sports_esports", "cloud", "newspaper", "fitness_center",
            "shopping_bag", "home", "bolt", "account_balance", "wifi", "shield",
            "directions_car", "credit_card", "savings", "receipt_long", "subscriptions", "info"};
    static final String[] FREQS = {"Weekly", "Fortnightly", "Monthly", "Quarterly", "Yearly"};
    /** Per-payment reminder: -1 follows the default setting, -3 is off, 0+ is days before. */
    static final int REMIND_DEFAULT = -1, REMIND_OFF = -3;
    static final String[] LEAD = {"On the day", "1 day before", "2 days before", "3 days before", "1 week before"};
    static final int[] LEAD_DAYS = {0, 1, 2, 3, 7};
    static final String[] HOURS = {"7am", "8am", "9am", "10am", "12pm", "6pm", "8pm"};
    static final int[] HOUR_VALUES = {7, 8, 9, 10, 12, 18, 20};

    static String leadLabel(int days) {
        for (int i = 0; i < LEAD_DAYS.length; i++) if (LEAD_DAYS[i] == days) return LEAD[i];
        return days + " days before";
    }

    static String hourLabel(int h) {
        for (int i = 0; i < HOUR_VALUES.length; i++) if (HOUR_VALUES[i] == h) return HOURS[i];
        return h + ":00";
    }

    // ------------------------------------------------------------------ credit cards & loans

    public static boolean isCredit(Bill b) { return "Loans & credit".equals(b.category); }

    /** Payoff projection at the current payment: months to clear, total interest, or never. */
    static final class Payoff {
        int months; double interest, monthlyInterest; boolean never, cleared;
    }

    static Payoff payoff(Bill b, double payment) {
        Payoff p = new Payoff();
        if (b.balance <= 0) { p.cleared = true; return p; }
        if (b.payInFull) { p.months = 1; return p; }
        double r = Math.max(0, b.apr) / 100.0 / 12.0;
        p.monthlyInterest = b.balance * r;
        if (payment <= p.monthlyInterest + 0.005) { p.never = true; return p; }
        double bal = b.balance;
        while (bal > 0.005 && p.months < 600) {
            double i = bal * r;
            p.interest += i;
            bal = bal + i - Math.min(payment, bal + i);
            p.months++;
        }
        if (p.months >= 600) p.never = true;
        return p;
    }

    static String duration(int months) {
        if (months <= 1) return "1 month";
        int y = months / 12, m = months % 12;
        if (y == 0) return months + " months";
        return y + (y == 1 ? " yr" : " yrs") + (m > 0 ? " " + m + " mo" : "");
    }

    /** One-line status for a card or loan. */
    static String creditLine(Bill b, double payment) {
        if (b.payInFull) return "Paid in full monthly · no interest";
        if (b.balance <= 0) return "Add the balance to see when it's cleared";
        Payoff p = payoff(b, payment);
        if (p.never) return Ui.money(b.balance, 0) + " owed · payment doesn't cover the interest";
        return Ui.money(b.balance, 0) + " owed · clear in " + duration(p.months) + (p.interest >= 1 ? " · " + Ui.money(p.interest, 0) + " interest" : "");
    }

    public static boolean remindersOn() { return Store.bool("billReminders", false); }

    /** Days before the payment to notify, or -1 for no reminder. */
    public static int effectiveRemind(Bill b) {
        if (b.paused || b.remindDays == REMIND_OFF) return -1;
        if (b.remindDays >= 0) return b.remindDays;
        return remindersOn() ? Store.integer("billRemindDays", 1) : -1;
    }

    /** Master switch + lead time + time of day (shown on the Subs tab and in Settings). */
    static void reminderRows(final MainActivity a, LinearLayout g, final Runnable redraw) {
        Ui.switchRow(g, "notifications", Theme.orange, "Payment reminders", "Notify me before subscriptions and bills are taken", remindersOn(), new Ui.Fn<Boolean>() {
            @Override public void on(Boolean v) {
                Store.put("billReminders", v);
                if (v) a.requestNotificationPermission();
                Reminders.reschedule(a);
                // Today's reminder time already passed? Send anything due today now rather than skipping it
                if (v && java.time.LocalTime.now().getHour() >= Store.integer("billRemindHour", 9)) {
                    try { Reminders.notifyBills(a); } catch (Throwable ignored) {}
                }
                a.snack(v ? "You'll get a reminder " + leadLabel(Store.integer("billRemindDays", 1)).toLowerCase(Locale.UK) + " each payment, at " + hourLabel(Store.integer("billRemindHour", 9)) : "Payment reminders off");
                redraw.run();
            }
        });
        if (!remindersOn()) return;
        final int lead = Store.integer("billRemindDays", 1);
        Ui.row(g, "event_repeat", Theme.blue, "When", null, leadLabel(lead), true, new Runnable() {
            @Override public void run() {
                int sel = 1;
                for (int i = 0; i < LEAD_DAYS.length; i++) if (LEAD_DAYS[i] == lead) sel = i;
                Sheets.choose(a, "Remind me", LEAD, sel, new Ui.Fn<Integer>() {
                    @Override public void on(Integer i) { Store.put("billRemindDays", LEAD_DAYS[i]); Reminders.reschedule(a); redraw.run(); }
                });
            }
        });
        final int hour = Store.integer("billRemindHour", 9);
        Ui.row(g, "schedule", Theme.purple, "Time", null, hourLabel(hour), true, new Runnable() {
            @Override public void run() {
                int sel = 2;
                for (int i = 0; i < HOUR_VALUES.length; i++) if (HOUR_VALUES[i] == hour) sel = i;
                Sheets.choose(a, "Reminder time", HOURS, sel, new Ui.Fn<Integer>() {
                    @Override public void on(Integer i) { Store.put("billRemindHour", HOUR_VALUES[i]); Reminders.reschedule(a); redraw.run(); }
                });
            }
        });
    }

    /** Popular services: name, category, frequency, subscription? */
    static final String[][] PRESETS = {
            {"Netflix", "Streaming", "Monthly", "1"}, {"Disney+", "Streaming", "Monthly", "1"},
            {"Amazon Prime", "Shopping & memberships", "Monthly", "1"}, {"Spotify", "Music", "Monthly", "1"},
            {"Apple Music", "Music", "Monthly", "1"}, {"YouTube Premium", "Streaming", "Monthly", "1"},
            {"Apple TV+", "Streaming", "Monthly", "1"}, {"NOW", "Streaming", "Monthly", "1"},
            {"iCloud+", "Software & cloud", "Monthly", "1"}, {"Google One", "Software & cloud", "Monthly", "1"},
            {"Microsoft 365", "Software & cloud", "Yearly", "1"}, {"Xbox Game Pass", "Gaming", "Monthly", "1"},
            {"PlayStation Plus", "Gaming", "Monthly", "1"}, {"Gym", "Fitness", "Monthly", "1"},
            {"Rent", "Rent / Mortgage", "Monthly", "0"}, {"Council tax", "Council tax", "Monthly", "0"},
            {"Energy", "Utilities", "Monthly", "0"}, {"Water", "Utilities", "Monthly", "0"},
            {"Broadband", "Phone & internet", "Monthly", "0"}, {"Phone contract", "Phone & internet", "Monthly", "0"},
            {"TV Licence", "Bills", "Yearly", "0"}, {"Car insurance", "Insurance", "Yearly", "0"},
    };

    private String filter = "All";

    @Override public String title() { return "Subscriptions & bills"; }
    @Override public String subtitle() { return "Every recurring payment in one place"; }

    /** Opens the add sheet (used by the floating + button on this tab). */
    public void addNew() { edit(null); }

    @Override protected void actions() {
        s.addAction("add", "Add a recurring payment", new Runnable() { @Override public void run() { edit(null); } });
    }

    // ------------------------------------------------------------------ schedule maths

    static String icon(String cat) {
        int i = Arrays.asList(CATS).indexOf(cat);
        return i >= 0 ? ICONS[i] : "receipt_long";
    }

    static int color(String cat) {
        switch (cat) {
            case "Streaming": return Theme.red;
            case "Music": return Theme.pink;
            case "Gaming": return Theme.green;
            case "Software & cloud": return Theme.blue;
            case "News & magazines": return Theme.text2;
            case "Fitness": return Theme.orange;
            case "Shopping & memberships": return Theme.yellow;
            case "Rent / Mortgage": return Theme.indigo;
            case "Utilities": return Theme.orange;
            case "Council tax": return Theme.purple;
            case "Phone & internet": return Theme.blue;
            case "Insurance": return Theme.teal;
            case "Transport": return Theme.cyan;
            case "Loans & credit": return Theme.red;
            case "Savings": return Theme.green;
            case "Subscriptions": return Theme.pink;
            default: return Theme.orange;
        }
    }

    /** A real payment date the schedule repeats from. */
    static LocalDate anchor(Bill b) {
        if (b.startMillis > 0) return TaxEngine.toDate(b.startMillis);
        LocalDate t = LocalDate.now().withDayOfMonth(1);
        return t.withDayOfMonth(Math.min(Math.max(1, b.dayOfMonth), t.lengthOfMonth()));
    }

    /** The k-th payment after the anchor (k may be negative). Month-based schedules keep the anchor's day. */
    static LocalDate nth(Bill b, LocalDate anchor, long k) {
        switch (b.frequency) {
            case "Weekly": return anchor.plusWeeks(k);
            case "Fortnightly": return anchor.plusWeeks(2 * k);
            case "Quarterly": return anchor.plusMonths(3 * k);
            case "Yearly": return anchor.plusYears(k);
            default: return anchor.plusMonths(k);
        }
    }

    private static long approxIndex(Bill b, LocalDate anchor, LocalDate from) {
        switch (b.frequency) {
            case "Weekly": return ChronoUnit.WEEKS.between(anchor, from);
            case "Fortnightly": return ChronoUnit.WEEKS.between(anchor, from) / 2;
            case "Quarterly": return ChronoUnit.MONTHS.between(anchor, from) / 3;
            case "Yearly": return ChronoUnit.YEARS.between(anchor, from);
            default: return ChronoUnit.MONTHS.between(anchor, from);
        }
    }

    /** First payment on or after {@code from}. For a free trial, never before the first charge. */
    public static LocalDate nextDue(Bill b, LocalDate from) {
        LocalDate a = anchor(b);
        if (!from.isAfter(a)) return a;
        long k = Math.max(0, approxIndex(b, a, from) - 1);
        LocalDate d = nth(b, a, k);
        int guard = 0;
        while (d.isBefore(from) && guard++ < 10) d = nth(b, a, ++k);
        return d;
    }

    /** Payments falling within [start, end]. */
    static List<LocalDate> occurrences(Bill b, LocalDate start, LocalDate end) {
        List<LocalDate> out = new ArrayList<LocalDate>();
        if (b.paused) return out;
        LocalDate a = anchor(b);
        LocalDate d = nextDue(b, start);
        long k = Math.max(0, approxIndex(b, a, d));
        while (nth(b, a, k).isBefore(d)) k++;
        int guard = 0;
        while (!d.isAfter(end) && guard++ < 60) {
            out.add(d);
            d = nth(b, a, ++k);
        }
        return out;
    }

    /** Average cost per month. */
    public static double monthly(Bill b) {
        if (b.paused) return 0;
        switch (b.frequency) {
            case "Weekly": return b.amount * 52 / 12;
            case "Fortnightly": return b.amount * 26 / 12;
            case "Quarterly": return b.amount / 3;
            case "Yearly": return b.amount / 12;
            default: return b.amount;
        }
    }

    /** Monthly cost of everything active. */
    public static double total() {
        double t = 0;
        for (Bill b : Store.bills) t += monthly(b);
        return t;
    }

    static String per(Bill b) {
        switch (b.frequency) {
            case "Weekly": return "/wk";
            case "Fortnightly": return "/2wk";
            case "Quarterly": return "/qtr";
            case "Yearly": return "/yr";
            default: return "/mo";
        }
    }

    static String when(LocalDate d) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), d);
        if (days == 0) return "Today";
        if (days == 1) return "Tomorrow";
        if (days > 1 && days < 7) return "In " + days + " days";
        return d.format(DateTimeFormatter.ofPattern(d.getYear() == LocalDate.now().getYear() ? "EEE d MMM" : "d MMM yyyy", Locale.UK));
    }

    static String ordinal(int d) {
        if (d >= 11 && d <= 13) return d + "th";
        switch (d % 10) { case 1: return d + "st"; case 2: return d + "nd"; case 3: return d + "rd"; default: return d + "th"; }
    }

    /** Average monthly take-home over the last three months with pay. */
    static double avgMonthlyIncome() {
        Map<String, Double> m = new LinkedHashMap<String, Double>();
        LocalDate cutoff = LocalDate.now().minusMonths(3).withDayOfMonth(1);
        for (Model.PayEntry e : Store.entries) {
            LocalDate d = TaxEngine.toDate(e.dateMillis);
            if (d.isBefore(cutoff)) continue;
            String k = d.getYear() + "-" + d.getMonthValue();
            Double v = m.get(k);
            m.put(k, (v == null ? 0 : v) + TaxEngine.net(e));
        }
        if (m.isEmpty()) return 0;
        double s = 0;
        for (double v : m.values()) s += v;
        return s / m.size();
    }

    // ------------------------------------------------------------------ page

    private List<Bill> visible() {
        List<Bill> out = new ArrayList<Bill>();
        for (Bill b : Store.bills) {
            if ("Subscriptions".equals(filter) && (!b.subscription || isCredit(b))) continue;
            if ("Bills".equals(filter) && (b.subscription || isCredit(b))) continue;
            if ("Credit".equals(filter) && !isCredit(b)) continue;
            out.add(b);
        }
        final LocalDate t = LocalDate.now();
        Collections.sort(out, new Comparator<Bill>() {
            @Override public int compare(Bill x, Bill y) {
                if (x.paused != y.paused) return x.paused ? 1 : -1;
                return nextDue(x, t).compareTo(nextDue(y, t));
            }
        });
        return out;
    }

    @Override protected void build() {
        if (Store.bills.isEmpty()) { emptyState(); return; }
        LocalDate today = LocalDate.now();
        Budget bd = Budget.now();
        double month = 0, subs = 0;
        int active = 0;
        for (Bill b : Store.bills) {
            if (b.paused) continue;
            active++;
            month += monthly(b);
            if (b.subscription) subs += monthly(b);
        }

        // Hero: what recurring payments cost
        int on = Theme.onHero();
        LinearLayout hero = Ui.v();
        hero.setBackground(new Glass.Hero(22));
        hero.setPadding(Ui.dp(22), Ui.dp(22), Ui.dp(22), Ui.dp(20));
        TextView cap = Ui.text("RECURRING PAYMENTS · PER MONTH", 12, 700, Theme.withAlpha(on, 0.85f));
        cap.setLetterSpacing(0.06f);
        hero.addView(cap);
        TextView big = Ui.single("", 40, 800, on);
        big.setText(Glass.bigMoney(month, on));
        Ui.autosize(big, 22, 40);
        hero.addView(big);
        hero.addView(Ui.text(Ui.money(month * 12, 0) + " a year · " + active + " active" + (subs > 0 ? " · " + Ui.money(subs, 0) + "/mo on subscriptions" : ""),
                13, 600, Theme.withAlpha(on, 0.85f)));
        LinearLayout stats = Ui.h();
        stats.setPadding(0, Ui.dp(16), 0, 0);
        stats.addView(heroStat(Ui.money(bd.stillToPay, 0), "Still to pay this month", on), Ui.weight(1));
        stats.addView(heroStat(Ui.money(bd.safe, 0), "Safe to spend", on), Ui.weight(1));
        hero.addView(stats);
        hero.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 14));
        add(span(hero));

        // Payment reminders
        LinearLayout rg = Ui.group();
        reminderRows(a, rg, new Runnable() { @Override public void run() { render(false); } });
        rg.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 14));
        add(rg);

        // Free trials ending soon
        for (Bill b : Store.bills) {
            if (!b.trial || b.paused) continue;
            long days = ChronoUnit.DAYS.between(today, anchor(b));
            if (days < 0 || days > 7) continue;
            add(span(trialCard(b, days)));
        }

        // Share of income
        double inc = avgMonthlyIncome();
        if (inc > 0 && month > 0) {
            LinearLayout c = Ui.card();
            c.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 14));
            c.addView(Ui.caption("Share of your take-home"));
            double pct = month / inc;
            TextView pv = Ui.text(String.format(Locale.UK, "%.0f%%", pct * 100) + " of a typical month's pay", 18, 750, pct > 0.6 ? Theme.red : Theme.text);
            pv.setPadding(0, Ui.dp(4), 0, 0);
            c.addView(pv);
            Charts.Progress p = new Charts.Progress(a);
            p.color = pct > 0.6 ? Theme.red : pct > 0.4 ? Theme.orange : Theme.green;
            p.track = Theme.field;
            p.set((float) Math.min(1, pct));
            c.addView(p, Ui.lpm(Ui.MATCH, Ui.dp(8), 0, 10, 0, 8));
            c.addView(Ui.text("Based on your average take-home of " + Ui.money(inc, 0) + " over the last 3 months." + (subs > 0 ? " Subscriptions alone are " + String.format(Locale.UK, "%.0f%%", subs / inc * 100) + "." : ""), 13, 400, Theme.text2));
            add(c);
        }

        // Coming up (next 30 days)
        List<Object[]> upcoming = new ArrayList<Object[]>();
        for (Bill b : Store.bills) for (LocalDate d : occurrences(b, today, today.plusDays(30))) upcoming.add(new Object[]{d, b});
        Collections.sort(upcoming, new Comparator<Object[]>() {
            @Override public int compare(Object[] x, Object[] y) { return ((LocalDate) x[0]).compareTo((LocalDate) y[0]); }
        });
        if (!upcoming.isEmpty()) {
            double due30 = 0;
            for (Object[] u : upcoming) due30 += ((Bill) u[1]).amount;
            add(Ui.sectionTitle("Next 30 days · " + Ui.money(due30, 0)));
            LinearLayout g = Ui.group();
            int n = 0;
            for (Object[] u : upcoming) {
                if (n++ >= 8) break;
                if (g.getChildCount() > 0) g.addView(Ui.divider(72));
                g.addView(upcomingRow((LocalDate) u[0], (Bill) u[1]));
            }
            add(follow(g));
        }

        // By category
        Map<String, Double> cats = new LinkedHashMap<String, Double>();
        for (Bill b : Store.bills) {
            if (b.paused) continue;
            Double v = cats.get(b.category);
            cats.put(b.category, (v == null ? 0 : v) + monthly(b));
        }
        if (cats.size() >= 2) {
            List<Map.Entry<String, Double>> list = new ArrayList<Map.Entry<String, Double>>(cats.entrySet());
            Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
                @Override public int compare(Map.Entry<String, Double> x, Map.Entry<String, Double> y) { return Double.compare(y.getValue(), x.getValue()); }
            });
            LinearLayout c = Ui.card();
            c.addView(Ui.caption("Monthly cost by category"));
            LinearLayout r = Ui.h();
            r.setPadding(0, Ui.dp(10), 0, 0);
            FrameLayout f = new FrameLayout(a);
            Charts.Donut d = new Charts.Donut(a);
            d.thicknessDp = 16;
            double[] vals = new double[list.size()];
            int[] cols = new int[list.size()];
            for (int i = 0; i < list.size(); i++) { vals[i] = list.get(i).getValue(); cols[i] = color(list.get(i).getKey()); }
            d.set(vals, cols);
            f.addView(d, new FrameLayout.LayoutParams(Ui.dp(110), Ui.dp(110), Gravity.CENTER));
            r.addView(f, new LinearLayout.LayoutParams(Ui.dp(116), Ui.dp(116)));
            r.addView(Ui.space(14));
            LinearLayout leg = Ui.v();
            for (int i = 0; i < Math.min(6, list.size()); i++) {
                LinearLayout lr = Ui.h();
                lr.setPadding(0, Ui.dp(3), 0, Ui.dp(3));
                View dot = new View(a);
                dot.setBackground(Ui.oval(cols[i]));
                lr.addView(dot, new LinearLayout.LayoutParams(Ui.dp(9), Ui.dp(9)));
                lr.addView(Ui.space(8));
                lr.addView(Ui.single(list.get(i).getKey(), 13.5f, 500, Theme.text), Ui.weight(1));
                lr.addView(Ui.text(Ui.money(vals[i], 0), 13.5f, 650, Theme.text));
                leg.addView(lr);
            }
            r.addView(leg, Ui.weight(1));
            c.addView(r);
            c.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 14, 0, 0));
            add(c);
        }

        // Credit cards & loans
        View cc = creditCard();
        if (cc != null) add(span(cc));

        // All payments
        add(Ui.sectionTitle("All payments"));
        final String[] filters = {"All", "Subscriptions", "Bills", "Credit"};
        LinearLayout seg = Ui.segmented(new String[]{"All", "Subs", "Bills", "Credit"}, Arrays.asList(filters).indexOf(filter), new Ui.Fn<Integer>() {
            @Override public void on(Integer i) { filter = filters[i]; render(false); }
        });
        seg.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        add(span(seg));
        List<Bill> vis = visible();
        if (vis.isEmpty()) {
            TextView t = Ui.text("Nothing here yet.", 15, 500, Theme.text2);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(20), 0, Ui.dp(20));
            add(span(t));
        } else {
            LinearLayout g = Ui.group();
            for (Bill b : vis) {
                if (g.getChildCount() > 0) g.addView(Ui.divider(72));
                g.addView(paymentRow(b));
            }
            add(span(g));
        }
        TextView n = Ui.text("Tap a payment to edit it, long-press to pause, duplicate or delete. Safe to spend = take-home logged this month minus the payments due this month.", 13, 400, Theme.text2);
        n.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), 0);
        add(span(n));
    }

    /** Total owed, monthly interest and payoff per card/loan, plus an overpayment tip. */
    private View creditCard() {
        List<Bill> cards = new ArrayList<Bill>();
        for (Bill b : Store.bills) if (isCredit(b) && !b.paused && (b.balance > 0 || b.payInFull)) cards.add(b);
        if (cards.isEmpty()) return null;
        double owed = 0, pay = 0, interest = 0;
        Bill worst = null;
        for (Bill b : cards) {
            owed += b.balance;
            pay += monthly(b);
            if (!b.payInFull) {
                interest += b.balance * b.apr / 100.0 / 12.0;
                if (b.balance > 0 && b.apr > 0 && (worst == null || b.apr > worst.apr)) worst = b;
            }
        }
        add(Ui.sectionTitle("Credit cards & loans"));
        LinearLayout c = Ui.card();
        LinearLayout top = Ui.h();
        LinearLayout l1 = Ui.v();
        l1.addView(Ui.caption("Total owed"));
        l1.addView(Ui.text(Ui.money(owed, 0), 24, 800, Theme.text));
        top.addView(l1, Ui.weight(1));
        LinearLayout l2 = Ui.v();
        l2.setGravity(Gravity.END);
        l2.addView(Ui.caption("Interest / month"));
        l2.addView(Ui.text(Ui.money(interest), 18, 750, interest > 0 ? Theme.red : Theme.green));
        top.addView(l2);
        c.addView(top);
        c.addView(Ui.text("Paying " + Ui.money(pay, 0) + " a month across " + cards.size() + (cards.size() == 1 ? " account" : " accounts"), 13, 500, Theme.text2));
        for (Bill b : cards) {
            LinearLayout r = Ui.h();
            r.setPadding(0, Ui.dp(12), 0, 0);
            r.addView(Ui.badge("credit_card", color(b.category), 32));
            r.addView(Ui.space(12));
            LinearLayout t = Ui.v();
            t.addView(Ui.single(b.name + (b.apr > 0 && !b.payInFull ? " · " + Ui.num(b.apr) + "% APR" : ""), 15, 600, Theme.text));
            Payoff p = payoff(b, b.amount);
            t.addView(Ui.text(creditLine(b, b.amount), 12.5f, 500, p.never ? Theme.red : Theme.text2));
            r.addView(t, Ui.weight(1));
            if (b.balance > 0) r.addView(Ui.text(Ui.money(b.balance, 0), 15, 700, Theme.text));
            final Bill fb = b;
            Ui.tap(r, 10, new Runnable() { @Override public void run() { edit(fb); } });
            c.addView(r);
        }
        if (worst != null) {
            Payoff now = payoff(worst, worst.amount), more = payoff(worst, worst.amount + 50);
            if (!more.never && (now.never || now.months - more.months >= 1)) {
                TextView tip = Ui.text(now.never
                        ? "Tip: " + worst.name + " needs more than " + Ui.money(now.monthlyInterest) + " a month just to cover interest. At " + Ui.money(worst.amount + 50, 0) + " it's clear in " + duration(more.months) + "."
                        : "Tip: paying " + Ui.money(50, 0) + " more on " + worst.name + " (your highest rate) clears it " + duration(now.months - more.months) + " sooner and saves about " + Ui.money(now.interest - more.interest, 0) + " in interest.",
                        13, 500, Theme.text);
                tip.setBackground(Ui.rr(Theme.withAlpha(Theme.green, 0.12f), 10));
                tip.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
                c.addView(tip, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 14, 0, 0));
            }
        }
        TextView n = Ui.text("Estimates use your balance, APR and monthly payment. Update the balance from your latest statement.", 12, 400, Theme.text2);
        n.setPadding(0, Ui.dp(10), 0, 0);
        c.addView(n);
        return c;
    }

    private View heroStat(String value, String label, int on) {
        LinearLayout l = Ui.v();
        l.addView(Ui.text(value, 17, 750, on));
        l.addView(Ui.text(label, 12, 500, Theme.withAlpha(on, 0.8f)));
        return l;
    }

    private void emptyState() {
        LinearLayout c = Ui.card();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(Ui.dp(24), Ui.dp(26), Ui.dp(24), Ui.dp(24));
        c.addView(Ui.badge("event_repeat", Theme.accent, 60));
        TextView t = Ui.text("Track your subscriptions and bills", 18, 700, Theme.text);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(14), 0, Ui.dp(6));
        c.addView(t);
        TextView d = Ui.text("Netflix, rent, phone, gym, insurance… See what's due next, what it all costs a month and a year, get reminders before payments, and a heads-up before free trials start charging.", 14.5f, 400, Theme.text2);
        d.setGravity(Gravity.CENTER);
        c.addView(d);
        c.addView(Ui.space(16));
        c.addView(Ui.primary("Add a payment", "add", new Runnable() { @Override public void run() { edit(null); } }), Ui.lp(Ui.MATCH, Ui.WRAP));
        add(c);
    }

    private View trialCard(final Bill b, long days) {
        LinearLayout c = Ui.card();
        c.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 14));
        LinearLayout r = Ui.h();
        r.addView(Ui.badge("hourglass_top", Theme.orange, 36));
        r.addView(Ui.space(12));
        LinearLayout t = Ui.v();
        t.addView(Ui.text(b.name + " free trial ends " + (days == 0 ? "today" : days == 1 ? "tomorrow" : "in " + days + " days"), 16, 650, Theme.text));
        t.addView(Ui.text("It then charges " + Ui.money(b.amount) + per(b) + ". Cancel before " + anchor(b).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)) + " if you don't want to keep it.", 13.5f, 400, Theme.text2));
        r.addView(t, Ui.weight(1));
        c.addView(r);
        Ui.tap(c, 16, new Runnable() { @Override public void run() { edit(b); } });
        return c;
    }

    private View upcomingRow(LocalDate d, final Bill b) {
        LinearLayout r = Ui.h();
        r.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(16), Ui.dp(10));
        r.setMinimumHeight(Ui.dp(56));
        // calendar-style date tile
        LinearLayout tile = Ui.v();
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(Ui.rr(Theme.withAlpha(color(b.category), Theme.dark ? 0.22f : 0.12f), 10));
        TextView mo = Ui.text(d.format(DateTimeFormatter.ofPattern("MMM", Locale.UK)).toUpperCase(Locale.UK), 9.5f, 700, color(b.category));
        tile.addView(mo);
        tile.addView(Ui.text(String.valueOf(d.getDayOfMonth()), 16, 800, Theme.text));
        r.addView(tile, new LinearLayout.LayoutParams(Ui.dp(42), Ui.dp(42)));
        r.addView(Ui.space(14));
        LinearLayout t = Ui.v();
        t.addView(Ui.single(b.name, 16.5f, 500, Theme.text));
        t.addView(Ui.text(when(d) + (b.trial && d.equals(anchor(b)) ? " · first charge after trial" : "") + " · " + b.frequency, 13, 400, Theme.text2));
        r.addView(t, Ui.weight(1));
        r.addView(Ui.text(Ui.money(b.amount), 16, 650, Theme.text));
        r.setContentDescription(b.name + ", " + Ui.money(b.amount) + ", " + when(d));
        Ui.tap(r, 0, new Runnable() { @Override public void run() { edit(b); } });
        return r;
    }

    private View paymentRow(final Bill b) {
        LinearLayout r = Ui.h();
        r.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(14), Ui.dp(10));
        r.setMinimumHeight(Ui.dp(60));
        r.addView(Ui.badge(isCredit(b) ? "credit_card" : icon(b.category), b.paused ? Theme.text3 : color(b.category), 40));
        r.addView(Ui.space(14));
        LinearLayout t = Ui.v();
        LinearLayout nameRow = Ui.h();
        nameRow.addView(Ui.single(b.name, 16.5f, 500, b.paused ? Theme.text2 : Theme.text), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        if (b.trial && !b.paused && !anchor(b).isBefore(LocalDate.now())) nameRow.addView(Ui.chip("hourglass_top", "Trial", Theme.orange));
        if (b.paused) nameRow.addView(Ui.chip("pause", "Paused", Theme.text2));
        if (effectiveRemind(b) >= 0) { nameRow.addView(Ui.space(4)); nameRow.addView(Ui.icon("notifications", Theme.text3, 15)); }
        t.addView(nameRow);
        String w = when(nextDue(b, LocalDate.now()));
        String sub = b.paused ? b.category + " · " + b.frequency
                : isCredit(b) && (b.balance > 0 || b.payInFull) ? (w.matches("Today|Tomorrow|In .*") ? w : "Next " + w) + " · " + creditLine(b, b.amount)
                : (w.matches("Today|Tomorrow|In .*") ? w : "Next " + w) + " · " + b.category;
        t.addView(Ui.single(sub, 13, 400, Theme.text2));
        r.addView(t, Ui.weight(1));
        LinearLayout v = Ui.v();
        v.setGravity(Gravity.END);
        v.addView(Ui.text(Ui.money(b.amount), 16, 650, b.paused ? Theme.text2 : Theme.text));
        TextView pr = Ui.text(b.frequency.toLowerCase(Locale.UK), 12, 500, Theme.text2);
        v.addView(pr);
        r.addView(v);
        r.setContentDescription(b.name + ", " + Ui.money(b.amount) + " " + b.frequency.toLowerCase(Locale.UK) + (b.paused ? ", paused" : ", next " + when(nextDue(b, LocalDate.now()))));
        Ui.tap(r, 0, new Runnable() { @Override public void run() { edit(b); } });
        r.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { Ui.haptic(v); menu(b); return true; }
        });
        return r;
    }

    private void menu(final Bill b) {
        final String[] opts = {b.paused ? "Resume" : "Pause", "Duplicate", "Delete"};
        Sheets.choose(a, b.name, opts, -1, new Ui.Fn<Integer>() {
            @Override public void on(Integer i) {
                if (i == 0) { b.paused = !b.paused; Store.changed(); a.snack(b.name + (b.paused ? " paused · not counted in your totals" : " resumed")); render(false); }
                else if (i == 1) {
                    Bill c = copy(b);
                    c.name = b.name + " (copy)";
                    Store.bills.add(c); Store.changed(); render(false);
                } else delete(b);
            }
        });
    }

    private View suggestionRow(String title, String cat, String sub, Runnable onTap) {
        LinearLayout r = Ui.h();
        r.setPadding(Ui.dp(12), Ui.dp(9), Ui.dp(12), Ui.dp(9));
        r.addView(Ui.badge(icon(cat), color(cat), 30));
        r.addView(Ui.space(12));
        LinearLayout t = Ui.v();
        t.addView(Ui.single(title, 15.5f, 600, Theme.text));
        t.addView(Ui.single(sub, 12.5f, 400, Theme.text2));
        r.addView(t, Ui.weight(1));
        r.addView(Ui.icon("chevron_right", Theme.text3, 18));
        r.setContentDescription("Suggestion: " + title + ", " + sub);
        Ui.tap(r, 12, onTap);
        return r;
    }

    private static Bill copy(Bill b) {
        Bill c = new Bill();
        c.name = b.name; c.amount = b.amount; c.dayOfMonth = b.dayOfMonth; c.category = b.category;
        c.frequency = b.frequency; c.startMillis = b.startMillis; c.subscription = b.subscription;
        c.remindDays = b.remindDays; c.paused = b.paused; c.trial = b.trial; c.notes = b.notes;
        return c;
    }

    private void delete(final Bill b) {
        final int idx = Store.bills.indexOf(b);
        Store.bills.remove(b);
        Store.changed();
        render(false);
        a.snack("Deleted " + b.name, "Undo", new Runnable() {
            @Override public void run() { Store.bills.add(Math.max(0, Math.min(idx, Store.bills.size())), b); Store.changed(); render(false); }
        });
    }

    // ------------------------------------------------------------------ editor

    private void edit(final Bill existing) {
        final Bill b = existing != null ? copy(existing) : new Bill();
        if (existing == null) { b.frequency = "Monthly"; b.subscription = true; b.category = "Streaming"; b.remindDays = REMIND_DEFAULT; }
        if (existing != null) b.id = existing.id;
        final LocalDate[] due = {existing != null ? nextDue(existing, LocalDate.now()) : LocalDate.now().plusDays(1)};
        final Sheets.Handle h = Sheets.sheet(a, existing == null ? "Add a recurring payment" : "Edit payment", null);
        final EditText name = Ui.field("Name (e.g. Netflix, Rent)", b.name, false);
        final EditText amount = Ui.field("Amount each time (" + Store.currency() + ")", b.amount > 0 ? Ui.num(b.amount) : "", true);
        final LinearLayout g = Ui.group();
        g.setBackground(Ui.rr(Theme.dark ? Theme.field : Theme.surface, 12));
        final Runnable[] draw = new Runnable[1];

        // Type-ahead: matching services (and your own payments) as you type, then plan prices
        final LinearLayout suggest = Ui.v();
        suggest.setBackground(Ui.rr(Theme.dark ? Theme.field : Theme.surface, 12));
        suggest.setVisibility(View.GONE);
        final LinearLayout plansBox = Ui.v();
        plansBox.setVisibility(View.GONE);
        final FrameLayout kindBox = new FrameLayout(a);
        final boolean[] applying = {false};
        final Runnable[] showPlans = new Runnable[1];
        final Runnable drawKind = new Runnable() {
            @Override public void run() {
                kindBox.removeAllViews();
                int sel = isCredit(b) ? 2 : b.subscription ? 0 : 1;
                kindBox.addView(Ui.segmented(new String[]{"Subscription", "Bill", "Credit card"}, sel, new Ui.Fn<Integer>() {
                    @Override public void on(Integer i) {
                        boolean wasCredit = isCredit(b);
                        if (i == 2) { b.subscription = false; b.category = "Loans & credit"; }
                        else {
                            b.subscription = i == 0;
                            if (wasCredit) b.category = i == 0 ? "Subscriptions" : "Bills";
                        }
                        if (wasCredit != (i == 2)) draw[0].run();
                    }
                }));
            }
        };
        final Ui.Fn<Catalog.Service> apply = new Ui.Fn<Catalog.Service>() {
            @Override public void on(Catalog.Service sv) {
                applying[0] = true;
                name.setText(sv.name);
                name.setSelection(sv.name.length());
                applying[0] = false;
                b.category = sv.category; b.frequency = sv.frequency; b.subscription = sv.subscription;
                suggest.setVisibility(View.GONE);
                Ui.haptic(name);
                draw[0].run();
                drawKind.run();
                showPlans[0].run();
                if (sv.plans.isEmpty()) amount.requestFocus();
            }
        };
        showPlans[0] = new Runnable() {
            @Override public void run() {
                plansBox.removeAllViews();
                String typed = name.getText().toString().trim();
                Catalog.Service sv = Catalog.exact(typed);
                List<Object[]> chips = new ArrayList<Object[]>();   // label, price, frequency
                if (sv != null) for (Catalog.Plan pl : sv.plans) chips.add(new Object[]{pl.label, pl.price, pl.frequency});
                for (Bill o : Store.bills) {
                    if (existing != null && o.id.equals(existing.id)) continue;
                    if (!typed.isEmpty() && o.name.equalsIgnoreCase(typed)) chips.add(new Object[]{"You pay", o.amount, o.frequency});
                }
                if (chips.isEmpty()) { plansBox.setVisibility(View.GONE); return; }
                plansBox.addView(Ui.caption("Tap a plan to fill the price"), Ui.lpm(Ui.MATCH, Ui.WRAP, 4, 0, 0, 6));
                HorizontalScrollView hs = new HorizontalScrollView(a);
                hs.setHorizontalScrollBarEnabled(false);
                LinearLayout row = Ui.h();
                for (final Object[] c : chips) {
                    final double price = (Double) c[1];
                    final String freq = (String) c[2];
                    String suffix = "Yearly".equals(freq) ? "/yr" : "Weekly".equals(freq) ? "/wk" : "Quarterly".equals(freq) ? "/qtr" : "/mo";
                    View ch = Ui.chip("payments", c[0] + " · " + Store.currency() + String.format(Locale.UK, "%.2f", price) + suffix, color(b.category));
                    ch.setContentDescription(c[0] + ", " + Ui.money(price) + " " + freq.toLowerCase(Locale.UK) + ". Tap to use this price");
                    Ui.tap(ch, 50, new Runnable() {
                        @Override public void run() {
                            amount.setText(Ui.num(price));
                            amount.setSelection(amount.getText().length());
                            b.frequency = freq;
                            Ui.haptic(amount);
                            draw[0].run();
                        }
                    });
                    row.addView(ch, Ui.lpm(Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
                }
                hs.addView(row);
                plansBox.addView(hs);
                if (sv != null && !sv.plans.isEmpty()) {
                    TextView note = Ui.text("Typical UK prices (" + Catalog.PRICES_AS_OF + "). Prices change, so check yours.", 11.5f, 400, Theme.text2);
                    note.setPadding(Ui.dp(4), Ui.dp(6), 0, 0);
                    plansBox.addView(note);
                }
                plansBox.setVisibility(View.VISIBLE);
            }
        };
        // Live payoff estimate while typing the monthly payment for a card or loan
        amount.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence x, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence x, int st, int bf, int c) {}
            @Override public void afterTextChanged(android.text.Editable x) { if (isCredit(b) && b.balance > 0 && draw[0] != null) draw[0].run(); }
        });
        name.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence x, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence x, int st, int bf, int c) {}
            @Override public void afterTextChanged(android.text.Editable x) {
                if (applying[0]) return;
                String q = x.toString().trim();
                suggest.removeAllViews();
                showPlans[0].run();
                if (q.isEmpty() || Catalog.exact(q) != null) { suggest.setVisibility(View.GONE); return; }
                // Your own payments first (handy for a second phone line or another gym)
                final String ql = q.toLowerCase(Locale.UK);
                int shown = 0;
                for (final Bill o : Store.bills) {
                    if (shown >= 2) break;
                    if (existing != null && o.id.equals(existing.id)) continue;
                    if (!o.name.toLowerCase(Locale.UK).contains(ql)) continue;
                    if (suggest.getChildCount() > 0) suggest.addView(Ui.divider(56));
                    suggest.addView(suggestionRow(o.name, o.category, "Yours · " + Ui.money(o.amount) + " " + o.frequency.toLowerCase(Locale.UK), new Runnable() {
                        @Override public void run() {
                            applying[0] = true; name.setText(o.name); name.setSelection(o.name.length()); applying[0] = false;
                            b.category = o.category; b.frequency = o.frequency; b.subscription = o.subscription;
                            amount.setText(Ui.num(o.amount));
                            suggest.setVisibility(View.GONE);
                            Ui.haptic(name);
                            draw[0].run(); drawKind.run(); showPlans[0].run();
                        }
                    }));
                    shown++;
                }
                for (final Catalog.Service sv : Catalog.search(q, 6 - shown)) {
                    if (suggest.getChildCount() > 0) suggest.addView(Ui.divider(56));
                    double from = sv.from();
                    String sub = sv.category + (from > 0 ? (sv.plans.size() > 1 ? " · from " : " · ") + Store.currency() + String.format(Locale.UK, "%.2f", from) + ("Yearly".equals(sv.frequency) ? "/yr" : "/mo")
                            : sv.plans.isEmpty() ? " · enter your price" : "");
                    suggest.addView(suggestionRow(sv.name, sv.category, sub, new Runnable() { @Override public void run() { apply.on(sv); } }));
                }
                suggest.setVisibility(suggest.getChildCount() > 0 ? View.VISIBLE : View.GONE);
            }
        });

        // Quick picks for popular services
        if (existing == null) {
            h.body.addView(Ui.caption("Popular"), Ui.lpm(Ui.MATCH, Ui.WRAP, 4, 0, 0, 8));
            HorizontalScrollView hs = new HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout row = Ui.h();
            for (final String[] p : PRESETS) {
                View ch = Ui.chip(icon(p[1]), p[0], color(p[1]));
                Ui.tap(ch, 50, new Runnable() {
                    @Override public void run() {
                        Catalog.Service sv = Catalog.exact(p[0]);
                        if (sv != null) { apply.on(sv); return; }
                        applying[0] = true; name.setText(p[0]); applying[0] = false;
                        b.category = p[1]; b.frequency = p[2]; b.subscription = "1".equals(p[3]);
                        Ui.haptic(name);
                        draw[0].run(); drawKind.run(); showPlans[0].run();
                        amount.requestFocus();
                    }
                });
                row.addView(ch, Ui.lpm(Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
            }
            hs.addView(row);
            h.body.addView(hs, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        }
        h.body.addView(name, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
        h.body.addView(suggest, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
        h.body.addView(plansBox, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        h.body.addView(amount, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        drawKind.run();
        h.body.addView(kindBox, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        showPlans[0].run();

        draw[0] = new Runnable() {
            @Override public void run() {
                g.removeAllViews();
                if (isCredit(b)) {
                    Ui.row(g, "credit_card", Theme.red, "Balance owed", null, b.balance > 0 ? Ui.money(b.balance) : "Add", true, new Runnable() {
                        @Override public void run() {
                            Sheets.input(a, "Balance owed", "From your latest statement or banking app.", "e.g. 1250", b.balance > 0 ? Ui.num(b.balance) : "", true, new Ui.Fn<String>() {
                                @Override public void on(String v) { b.balance = Math.max(0, Ui.parse(v)); b.balanceMillis = System.currentTimeMillis(); draw[0].run(); }
                            });
                        }
                    });
                    Ui.row(g, "percent", Theme.orange, "Interest rate (APR)", null, b.apr > 0 ? Ui.num(b.apr) + "%" : "Add", true, new Runnable() {
                        @Override public void run() {
                            Sheets.input(a, "Interest rate (APR %)", "Your purchase or balance APR. Use 0 for an interest-free period.", "e.g. 24.9", b.apr > 0 ? Ui.num(b.apr) : "", true, new Ui.Fn<String>() {
                                @Override public void on(String v) { b.apr = Math.max(0, Math.min(99, Ui.parse(v))); draw[0].run(); }
                            });
                        }
                    });
                    Ui.switchRow(g, "check", Theme.green, "Pay in full each month", "No interest; the amount varies", b.payInFull, new Ui.Fn<Boolean>() {
                        @Override public void on(Boolean v) { b.payInFull = v; draw[0].run(); }
                    });
                    double payNow = Ui.parse(amount.getText().toString());
                    if (b.balance > 0 && !b.payInFull) {
                        Payoff p = payoff(b, payNow);
                        String v = payNow <= 0 ? "Add your monthly payment" : p.never ? "Never at this payment" : duration(p.months);
                        Ui.row(g, "flag", p.never ? Theme.red : Theme.teal, "Cleared in", payNow > 0 && !p.never ? Ui.money(p.interest, 0) + " interest in total" : null, v, false, null);
                    }
                }
                Ui.row(g, "autorenew", Theme.blue, "Repeats", null, b.frequency, true, new Runnable() {
                    @Override public void run() {
                        Sheets.choose(a, "Repeats", FREQS, Arrays.asList(FREQS).indexOf(b.frequency), new Ui.Fn<Integer>() {
                            @Override public void on(Integer i) { b.frequency = FREQS[i]; draw[0].run(); }
                        });
                    }
                });
                Ui.row(g, "calendar_month", Theme.red, b.trial ? "First charge (trial ends)" : "Next payment", null,
                        due[0].format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.UK)), true, new Runnable() {
                    @Override public void run() {
                        Sheets.date(a, due[0], new Ui.Fn<LocalDate>() { @Override public void on(LocalDate d) { due[0] = d; draw[0].run(); } });
                    }
                });
                Ui.row(g, icon(b.category), color(b.category), "Category", null, b.category, true, new Runnable() {
                    @Override public void run() {
                        Sheets.choose(a, "Category", CATS, Arrays.asList(CATS).indexOf(b.category), new Ui.Fn<Integer>() {
                            @Override public void on(Integer i) { b.category = CATS[i]; if (isCredit(b)) b.subscription = false; draw[0].run(); drawKind.run(); }
                        });
                    }
                });
                // Reminder: follow the default, turn off, or pick a specific lead time
                final String defLabel = remindersOn() ? "Default (" + leadLabel(Store.integer("billRemindDays", 1)).toLowerCase(Locale.UK) + ")" : "Default (reminders off)";
                final String[] opts = new String[LEAD.length + 2];
                final int[] vals = new int[LEAD.length + 2];
                opts[0] = defLabel; vals[0] = REMIND_DEFAULT;
                opts[1] = "Off for this payment"; vals[1] = REMIND_OFF;
                for (int i = 0; i < LEAD.length; i++) { opts[i + 2] = LEAD[i]; vals[i + 2] = LEAD_DAYS[i]; }
                int ri = 0;
                for (int i = 0; i < vals.length; i++) if (vals[i] == b.remindDays) ri = i;
                final int fri = ri;
                String shown = b.remindDays == REMIND_DEFAULT ? (remindersOn() ? leadLabel(Store.integer("billRemindDays", 1)) : "Off (default)")
                        : b.remindDays == REMIND_OFF ? "Off" : leadLabel(b.remindDays);
                Ui.row(g, "notifications", Theme.orange, "Remind me", null, shown, true, new Runnable() {
                    @Override public void run() {
                        Sheets.choose(a, "Remind me", opts, fri, new Ui.Fn<Integer>() {
                            @Override public void on(Integer i) {
                                b.remindDays = vals[i];
                                if (b.remindDays >= 0 || (b.remindDays == REMIND_DEFAULT && remindersOn())) a.requestNotificationPermission();
                                draw[0].run();
                            }
                        });
                    }
                });
                Ui.switchRow(g, "hourglass_top", Theme.orange, "Free trial", "Warns you before the first charge", b.trial, new Ui.Fn<Boolean>() {
                    @Override public void on(Boolean v) { b.trial = v; if (v && b.remindDays == REMIND_DEFAULT && !remindersOn()) b.remindDays = 3; draw[0].run(); }
                });
                Ui.switchRow(g, "pause", Theme.text2, "Paused", "Not counted in totals or reminders", b.paused, new Ui.Fn<Boolean>() {
                    @Override public void on(Boolean v) { b.paused = v; }
                });
            }
        };
        draw[0].run();
        h.body.addView(g);
        final EditText notes = Ui.field("Notes (account, how to cancel…)", b.notes, false);
        h.body.addView(notes, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));

        Sheets.buttons(h, "Save", new Runnable() {
            @Override public void run() {
                double amt = Ui.parse(amount.getText().toString());
                if (amt <= 0) { a.snack("Enter an amount"); return; }
                String nm = name.getText().toString().trim();
                b.name = nm.isEmpty() ? b.category : nm;
                b.amount = amt;
                b.startMillis = TaxEngine.toMillis(due[0]);
                b.dayOfMonth = due[0].getDayOfMonth();
                b.notes = notes.getText().toString().trim();
                if (existing != null) {
                    int idx = Store.bills.indexOf(existing);
                    if (idx >= 0) Store.bills.set(idx, b); else Store.bills.add(b);
                } else Store.bills.add(b);
                Store.changed();
                Ui.confirmHaptic(h.body);
                h.dismiss();
                render(false);
                a.snack((existing == null ? "Added " : "Saved ") + b.name + " · " + Ui.money(monthly(b), 2) + " a month");
            }
        });
        if (existing != null) {
            h.body.addView(Ui.space(8));
            h.body.addView(Ui.button("Delete", "delete", Theme.withAlpha(Theme.red, 0.12f), Theme.red, new Runnable() {
                @Override public void run() { h.dismiss(); delete(existing); }
            }));
        }
        Sheets.show(h);
    }

    // ------------------------------------------------------------------ budget (Home card)

    /** This month's budget snapshot. */
    public static final class Budget {
        public double income, bills, safe, projected, stillToPay;
        public Bill nextBill;
        public LocalDate nextDate;

        public static Budget now() {
            Budget b = new Budget();
            LocalDate t = LocalDate.now();
            for (Model.PayEntry e : Store.entries) {
                LocalDate d = TaxEngine.toDate(e.dateMillis);
                if (d.getYear() == t.getYear() && d.getMonthValue() == t.getMonthValue()) b.income += TaxEngine.net(e);
            }
            double frac = t.getDayOfMonth() / (double) t.lengthOfMonth();
            b.projected = frac > 0.15 ? b.income / frac : b.income;
            LocalDate first = t.withDayOfMonth(1), last = t.withDayOfMonth(t.lengthOfMonth());
            for (Bill x : Store.bills) {
                for (LocalDate d : occurrences(x, first, last)) {
                    b.bills += x.amount;
                    if (!d.isBefore(t)) b.stillToPay += x.amount;
                }
                if (x.paused) continue;
                LocalDate n = nextDue(x, t);
                if (b.nextDate == null || n.isBefore(b.nextDate)) { b.nextDate = n; b.nextBill = x; }
            }
            b.safe = b.income - b.bills;
            return b;
        }
    }

    static String money0(double v) { return Ui.masked() ? Store.currency() + Ui.MASK : String.format(Locale.UK, "%s%,.0f", Store.currency(), v); }
}
