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
        // Planned one-off payments land in the month they're scheduled for
        Map<Integer, Double> extra = new java.util.HashMap<Integer, Double>();
        LocalDate today = LocalDate.now();
        for (Model.CardPayment cp : b.payments) {
            if (!cp.planned) continue;
            int m = (int) Math.max(0, ChronoUnit.MONTHS.between(today.withDayOfMonth(1), TaxEngine.toDate(cp.dateMillis).withDayOfMonth(1)));
            Double v = extra.get(m);
            extra.put(m, (v == null ? 0 : v) + cp.amount);
        }
        double bal = b.balance;
        while (bal > 0.005 && p.months < 600) {
            double i = bal * r;
            p.interest += i;
            Double ex = extra.get(p.months);
            double pay = payment + (ex == null ? 0 : ex);
            bal = bal + i - Math.min(pay, bal + i);
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

    // ------------------------------------------------------------------ smart credit updates

    static final String[] MIN_RULES = {"Enter it myself", "1% of balance + interest", "2% of balance", "2.5% of balance", "3% of balance"};
    static final String[] PAY_MODES = {"The minimum (updates automatically)", "A fixed amount", "The full balance"};

    static int periodsPerYear(Bill b) {
        switch (b.frequency) { case "Weekly": return 52; case "Fortnightly": return 26; case "Quarterly": return 4; case "Yearly": return 1; default: return 12; }
    }

    /** Interest added each payment period at the current balance. */
    static double periodInterest(Bill b) { return b.payInFull || b.balance <= 0 ? 0 : b.balance * Math.max(0, b.apr) / 100.0 / periodsPerYear(b); }

    /** The card's minimum payment from its rule (the usual UK formulas), never below the floor or above the balance. */
    static double computeMin(Bill b) {
        if (b.balance <= 0.005) return 0;
        double m;
        switch (b.minRule) {
            case 1: m = b.balance * 0.01 + b.balance * Math.max(0, b.apr) / 1200.0; break;
            case 2: m = b.balance * 0.02; break;
            case 3: m = b.balance * 0.025; break;
            case 4: m = b.balance * 0.03; break;
            default: return b.minPayment;
        }
        m = Math.max(m, b.minFloor);
        m = Math.min(m, b.balance);
        return Math.ceil(m * 100) / 100.0;
    }

    /** Re-derives the minimum and (if you pay the minimum or in full) the monthly payment from the balance. */
    static void recalc(Bill b) {
        if (!isCredit(b)) return;
        if (b.balance <= 0.005) {
            b.balance = 0;
            if (b.minRule != 0) b.minPayment = 0;
            if (b.followMinimum || b.payInFull) b.amount = 0;
            return;
        }
        if (b.minRule != 0) b.minPayment = computeMin(b);
        if (b.payInFull) b.amount = Math.round(b.balance * 100) / 100.0;
        else if (b.followMinimum && b.minPayment > 0) b.amount = b.minPayment;
    }

    /** Before/after snapshot so a payment can say what changed. */
    static final class Snap {
        final double bal, interest, min, pay; final Payoff po;
        Snap(Bill b) { bal = b.balance; interest = periodInterest(b); min = b.minPayment; pay = b.amount; po = payoff(b, b.amount); }
    }

    static String changes(Bill b, Snap was) {
        if (was.bal > 0 && b.balance <= 0) return b.name + " is paid off 🎉 No more interest" + (b.followMinimum || b.payInFull ? " and no payment due" : "");
        List<String> parts = new ArrayList<String>();
        parts.add("Balance " + Ui.money(b.balance));
        double i = periodInterest(b);
        if (Math.abs(i - was.interest) >= 0.01) parts.add("interest " + Ui.money(i) + "/mo (was " + Ui.money(was.interest) + ")");
        if (b.minRule != 0 && Math.abs(b.minPayment - was.min) >= 0.01) parts.add("minimum " + Ui.money(b.minPayment) + " (was " + Ui.money(was.min) + ")");
        if ((b.followMinimum || b.payInFull) && Math.abs(b.amount - was.pay) >= 0.01) parts.add("next payment " + Ui.money(b.amount));
        Payoff now = payoff(b, b.amount);
        if (!was.po.never && !now.never && was.po.months > now.months) parts.add("clears " + duration(was.po.months - now.months) + " sooner");
        else if (was.po.never && !now.never && b.balance > 0) parts.add("now clears in " + duration(now.months));
        return android.text.TextUtils.join(" · ", parts);
    }

    /**
     * Rolls cards forward to today: on each payment date since the last roll, adds that period's
     * interest, takes off the regular payment (logged as automatic) and recalculates the minimum.
     * @return true if anything changed (caller saves).
     */
    public static boolean rollAll() {
        boolean changed = false;
        LocalDate today = LocalDate.now();
        for (Bill b : Store.bills) {
            if (!isCredit(b) || b.paused || !b.autoRoll) continue;
            if (b.rolledMillis <= 0) { b.rolledMillis = TaxEngine.toMillis(today); changed = true; continue; }
            LocalDate from = TaxEngine.toDate(b.rolledMillis).plusDays(1);
            if (from.isAfter(today)) continue;
            if (b.balance > 0) {
                for (LocalDate d : occurrences(b, from, today)) {
                    if (b.balance <= 0.005) break;
                    b.balance += periodInterest(b);
                    double pay = b.payInFull ? b.balance : Math.min(b.amount, b.balance);
                    if (pay <= 0) continue;
                    b.balance = Math.max(0, b.balance - pay);
                    Model.CardPayment cp = new Model.CardPayment();
                    cp.amount = Math.round(pay * 100) / 100.0;
                    cp.dateMillis = TaxEngine.toMillis(d);
                    cp.note = "Regular payment (automatic)";
                    b.payments.add(0, cp);
                    recalc(b);
                }
                b.balanceMillis = System.currentTimeMillis();
            }
            b.rolledMillis = TaxEngine.toMillis(today);
            changed = true;
        }
        return changed;
    }

    static double available(Bill b) { return b.creditLimit > 0 ? b.creditLimit - b.balance : 0; }

    static double plannedTotal(Bill b) {
        double t = 0;
        for (Model.CardPayment p : b.payments) if (p.planned) t += p.amount;
        return t;
    }

    /** Planned extra payments in [start, end]: {date, bill, payment}. */
    static List<Object[]> extrasIn(LocalDate start, LocalDate end) {
        List<Object[]> out = new ArrayList<Object[]>();
        for (Bill b : Store.bills) {
            if (b.paused) continue;
            for (Model.CardPayment p : b.payments) {
                if (!p.planned) continue;
                LocalDate d = TaxEngine.toDate(p.dateMillis);
                if (!d.isBefore(start) && !d.isAfter(end)) out.add(new Object[]{d, b, p});
            }
        }
        return out;
    }

    /** A stand-in Bill for showing an extra payment wherever a list of Bills is expected (calendar). */
    static Bill extraAsBill(Bill b, Model.CardPayment p) {
        Bill x = new Bill();
        x.id = b.id; x.name = b.name + " · extra payment"; x.amount = p.amount; x.category = b.category;
        x.frequency = "One-off"; x.remindDays = REMIND_OFF; x.startMillis = p.dateMillis;
        return x;
    }

    /** Records a payment made today/earlier (lowers the balance) or plans one for later. */
    static void addPayment(Bill b, double amount, LocalDate date, String note) {
        Model.CardPayment p = new Model.CardPayment();
        p.amount = amount; p.dateMillis = TaxEngine.toMillis(date); p.note = note == null ? "" : note;
        p.planned = date.isAfter(LocalDate.now());
        if (!p.planned) { b.balance = Math.max(0, b.balance - amount); b.balanceMillis = System.currentTimeMillis(); }
        b.payments.add(p);
        recalc(b);
        Collections.sort(b.payments, new Comparator<Model.CardPayment>() {
            @Override public int compare(Model.CardPayment x, Model.CardPayment y) { return Long.compare(y.dateMillis, x.dateMillis); }
        });
    }

    static void confirmPlanned(Bill b, Model.CardPayment p, boolean paid) {
        if (paid) { p.planned = false; b.balance = Math.max(0, b.balance - p.amount); b.balanceMillis = System.currentTimeMillis(); recalc(b); }
        else b.payments.remove(p);
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
        if (isCredit(b) && b.amount <= 0.004) return out;   // cleared card: nothing to pay
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

        // Planned extra payments whose date has arrived: did you pay?
        for (final Bill b : new ArrayList<Bill>(Store.bills)) {
            for (final Model.CardPayment p : new ArrayList<Model.CardPayment>(b.payments)) {
                if (!p.planned || TaxEngine.toDate(p.dateMillis).isAfter(today)) continue;
                LinearLayout c = Ui.card();
                c.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 14));
                LinearLayout r = Ui.h();
                r.addView(Ui.badge("credit_card", Theme.green, 36));
                r.addView(Ui.space(12));
                LinearLayout t = Ui.v();
                t.addView(Ui.text("Did you pay " + Ui.money(p.amount) + " extra to " + b.name + "?", 16, 650, Theme.text));
                t.addView(Ui.text("Planned for " + TaxEngine.toDate(p.dateMillis).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)) + ". Confirm it to take it off your balance.", 13, 400, Theme.text2));
                r.addView(t, Ui.weight(1));
                c.addView(r);
                LinearLayout btns = Ui.h();
                btns.setPadding(0, Ui.dp(12), 0, 0);
                btns.addView(Ui.primary("Yes, paid", "check", new Runnable() { @Override public void run() {
                    Snap was = new Snap(b);
                    confirmPlanned(b, p, true); Store.changed(); Ui.confirmHaptic(s);
                    a.snack(changes(b, was)); render(false);
                } }), Ui.weight(1));
                btns.addView(Ui.space(10));
                btns.addView(Ui.tonal("Didn't pay", "close", new Runnable() { @Override public void run() {
                    confirmPlanned(b, p, false); Store.changed(); render(false);
                } }), Ui.weight(1));
                c.addView(btns);
                add(span(c));
            }
        }

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
        for (Bill b : Store.bills) for (LocalDate d : occurrences(b, today, today.plusDays(30))) upcoming.add(new Object[]{d, b, b.amount, null});
        for (Object[] x : extrasIn(today, today.plusDays(30))) upcoming.add(new Object[]{x[0], x[1], ((Model.CardPayment) x[2]).amount, "Extra payment"});
        Collections.sort(upcoming, new Comparator<Object[]>() {
            @Override public int compare(Object[] x, Object[] y) { return ((LocalDate) x[0]).compareTo((LocalDate) y[0]); }
        });
        if (!upcoming.isEmpty()) {
            double due30 = 0;
            for (Object[] u : upcoming) due30 += (Double) u[2];
            add(Ui.sectionTitle("Next 30 days · " + Ui.money(due30, 0)));
            LinearLayout g = Ui.group();
            int n = 0;
            for (Object[] u : upcoming) {
                if (n++ >= 8) break;
                if (g.getChildCount() > 0) g.addView(Ui.divider(72));
                g.addView(upcomingRow((LocalDate) u[0], (Bill) u[1], (Double) u[2], (String) u[3]));
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
        double lim = 0, avail = 0;
        for (Bill b : cards) if (b.creditLimit > 0) { lim += b.creditLimit; avail += available(b); }
        c.addView(Ui.text("Paying " + Ui.money(pay, 0) + " a month across " + cards.size() + (cards.size() == 1 ? " account" : " accounts")
                + (lim > 0 ? " · " + Ui.money(avail, 0) + " available of " + Ui.money(lim, 0) : ""), 13, 500, Theme.text2));
        c.addView(Ui.text("Tap a card to make a payment or plan an extra one.", 12.5f, 500, Theme.accent));
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
            LinearLayout rv = Ui.v();
            rv.setGravity(Gravity.END);
            if (b.balance > 0) rv.addView(Ui.text(Ui.money(b.balance, 0), 15, 700, Theme.text));
            if (b.creditLimit > 0) rv.addView(Ui.text(Ui.money(available(b), 0) + " available", 12, 600, Theme.green));
            r.addView(rv);
            final Bill fb = b;
            Ui.tap(r, 10, new Runnable() { @Override public void run() { cardSheet(fb); } });
            c.addView(r);
            if (b.creditLimit > 0) {
                Charts.Progress up = new Charts.Progress(a);
                double u = Math.min(1, b.balance / b.creditLimit);
                up.color = u <= 0.3 ? Theme.green : u <= 0.75 ? Theme.orange : Theme.red;
                up.track = Theme.field;
                up.set((float) u);
                c.addView(up, Ui.lpm(Ui.MATCH, Ui.dp(5), 44, 6, 0, 0));
            }
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
        TextView n = Ui.text("Estimates use your balance, APR, monthly payment and any extra payments you've planned. Update the balance from your latest statement now and then.", 12, 400, Theme.text2);
        n.setPadding(0, Ui.dp(10), 0, 0);
        c.addView(n);
        return c;
    }

    /** Everything about one card: balance, available credit, payoff, payments made and planned. */
    void cardSheet(final Bill b) {
        final Sheets.Handle h = Sheets.sheet(a, b.name, null);
        LinearLayout top = Ui.h();
        LinearLayout l1 = Ui.v();
        l1.addView(Ui.caption("Balance"));
        l1.addView(Ui.text(Ui.money(b.balance), 24, 800, Theme.text));
        top.addView(l1, Ui.weight(1));
        if (b.creditLimit > 0) {
            LinearLayout l2 = Ui.v();
            l2.setGravity(Gravity.END);
            l2.addView(Ui.caption("Available"));
            l2.addView(Ui.text(Ui.money(available(b)), 20, 750, Theme.green));
            top.addView(l2);
        }
        h.body.addView(top);
        if (b.creditLimit > 0) {
            double u = Math.min(1, b.balance / b.creditLimit);
            Charts.Progress up = new Charts.Progress(a);
            up.color = u <= 0.3 ? Theme.green : u <= 0.75 ? Theme.orange : Theme.red;
            up.track = Theme.field;
            up.set((float) u);
            h.body.addView(up, Ui.lpm(Ui.MATCH, Ui.dp(8), 0, 10, 0, 6));
            h.body.addView(Ui.text(String.format(Locale.UK, "%.0f%%", u * 100) + " of your " + Ui.money(b.creditLimit, 0) + " limit used"
                    + (u > 0.3 ? ". Keeping it under 30% can help your credit score." : ""), 13, 500, Theme.text2));
        }
        StringBuilder payTxt = new StringBuilder();
        if (b.minPayment > 0) payTxt.append("Minimum ").append(Ui.money(b.minPayment)).append(b.minRule != 0 ? " (auto)" : "").append(" · ");
        payTxt.append(b.payInFull ? "you pay the full balance (" + Ui.money(b.amount) + ")"
                : b.followMinimum ? "you pay the minimum"
                : "you pay " + Ui.money(b.amount) + " " + b.frequency.toLowerCase(Locale.UK));
        if (b.balance > 0 && !b.payInFull) payTxt.append("\nInterest about ").append(Ui.money(periodInterest(b))).append(" a month at ").append(Ui.num(b.apr)).append("% APR");
        TextView pt = Ui.text(payTxt.toString(), 14, 600, Theme.text);
        pt.setPadding(0, Ui.dp(12), 0, Ui.dp(2));
        h.body.addView(pt);
        if (b.minPayment > 0 && !b.payInFull && b.amount < b.minPayment)
            h.body.addView(Ui.text("Your usual payment is below the minimum. Pay at least " + Ui.money(b.minPayment) + " to avoid fees.", 13, 600, Theme.red));
        Payoff po = payoff(b, b.amount);
        h.body.addView(Ui.text(creditLine(b, b.amount), 13, 500, po.never ? Theme.red : Theme.text2));
        if (b.autoRoll && b.balance > 0)
            h.body.addView(Ui.text("Smart updates on: each payment date adds interest, takes off your payment and recalculates the minimum.", 12, 500, Theme.accent));
        if (b.balanceMillis > 0) {
            long days = ChronoUnit.DAYS.between(TaxEngine.toDate(b.balanceMillis), LocalDate.now());
            h.body.addView(Ui.text("Balance updated " + (days == 0 ? "today" : days == 1 ? "yesterday" : days + " days ago"), 12, 400, Theme.text3));
        }

        LinearLayout btns = Ui.h();
        btns.setPadding(0, Ui.dp(14), 0, Ui.dp(4));
        btns.addView(Ui.primary("Make a payment", "payments", new Runnable() { @Override public void run() { h.dismiss(); paymentSheet(b, false); } }), Ui.weight(1));
        btns.addView(Ui.space(10));
        btns.addView(Ui.tonal("Plan extra", "event_repeat", new Runnable() { @Override public void run() { h.dismiss(); paymentSheet(b, true); } }), Ui.weight(1));
        h.body.addView(btns);
        LinearLayout btns2 = Ui.h();
        btns2.addView(Ui.tonal("Update balance", "edit", new Runnable() { @Override public void run() {
            h.dismiss();
            Sheets.input(a, "Balance owed", "From your latest statement or banking app.", "e.g. 1250", Ui.num(b.balance), true, new Ui.Fn<String>() {
                @Override public void on(String v) {
                    Snap was = new Snap(b);
                    b.balance = Math.max(0, Ui.parse(v)); b.balanceMillis = System.currentTimeMillis(); b.rolledMillis = TaxEngine.toMillis(LocalDate.now());
                    recalc(b); Store.changed(); render(false); cardSheet(b); a.snack(changes(b, was));
                }
            });
        } }), Ui.weight(1));
        btns2.addView(Ui.space(10));
        btns2.addView(Ui.tonal("Edit card", "tune", new Runnable() { @Override public void run() { h.dismiss(); edit(b); } }), Ui.weight(1));
        h.body.addView(btns2, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));

        // Planned and recent payments
        List<Model.CardPayment> planned = new ArrayList<Model.CardPayment>(), made = new ArrayList<Model.CardPayment>();
        for (Model.CardPayment p : b.payments) (p.planned ? planned : made).add(p);
        if (!planned.isEmpty()) {
            h.body.addView(Ui.caption("Planned extra payments · " + Ui.money(plannedTotal(b))), Ui.lpm(Ui.MATCH, Ui.WRAP, 4, 18, 0, 6));
            LinearLayout g = Ui.group();
            g.setBackground(Ui.rr(Theme.dark ? Theme.field : Theme.surface, 12));
            for (final Model.CardPayment p : planned) {
                if (g.getChildCount() > 0) g.addView(Ui.divider(16));
                g.addView(paymentLine(p, true, new Runnable() { @Override public void run() {
                    b.payments.remove(p); Store.changed(); h.dismiss(); render(false); cardSheet(b); a.snack("Planned payment removed");
                } }));
            }
            h.body.addView(g);
        }
        if (!made.isEmpty()) {
            h.body.addView(Ui.caption("Payments you've recorded"), Ui.lpm(Ui.MATCH, Ui.WRAP, 4, 18, 0, 6));
            LinearLayout g = Ui.group();
            g.setBackground(Ui.rr(Theme.dark ? Theme.field : Theme.surface, 12));
            int n = 0;
            for (final Model.CardPayment p : made) {
                if (n++ >= 6) break;
                if (g.getChildCount() > 0) g.addView(Ui.divider(16));
                g.addView(paymentLine(p, false, new Runnable() { @Override public void run() {
                    // undo a recorded payment: add it back to the balance
                    b.balance += p.amount; b.payments.remove(p); recalc(b); Store.changed(); h.dismiss(); render(false); cardSheet(b);
                    a.snack("Payment removed · balance back to " + Ui.money(b.balance));
                } }));
            }
            h.body.addView(g);
        }
        Sheets.show(h);
    }

    private View paymentLine(Model.CardPayment p, boolean planned, Runnable onRemove) {
        LinearLayout r = Ui.h();
        r.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(6), Ui.dp(10));
        LinearLayout t = Ui.v();
        t.addView(Ui.text(TaxEngine.toDate(p.dateMillis).format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.UK)), 15, 500, Theme.text));
        if (!p.note.isEmpty()) t.addView(Ui.single(p.note, 12.5f, 400, Theme.text2));
        r.addView(t, Ui.weight(1));
        r.addView(Ui.text(Ui.money(p.amount), 15, 650, planned ? Theme.accent : Theme.green));
        android.widget.ImageView x = Ui.iconButton("close", Theme.text3, onRemove);
        x.setContentDescription(planned ? "Cancel planned payment" : "Remove recorded payment");
        r.addView(x, new LinearLayout.LayoutParams(Ui.dp(36), Ui.dp(36)));
        return r;
    }

    /** Make a payment now (any amount, e.g. more than the minimum) or plan one for a future date. */
    void paymentSheet(final Bill b, final boolean plan) {
        final Sheets.Handle h = Sheets.sheet(a, plan ? "Plan an extra payment" : "Make a payment",
                plan ? "Schedule a one-off payment on top of your usual " + Ui.money(b.amount) + ". It's counted in your payoff estimate and shown in upcoming payments."
                        : "Record a payment you've made. It comes straight off your " + b.name + " balance.");
        final EditText amt = Ui.field("Amount (" + Store.currency() + ")", "", true);
        h.body.addView(amt, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        // Quick amounts
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.h();
        List<Object[]> quick = new ArrayList<Object[]>();
        if (b.minPayment > 0 && !plan) quick.add(new Object[]{"Minimum", b.minPayment});
        if (b.amount > 0 && !plan) quick.add(new Object[]{"Usual", b.amount});
        quick.add(new Object[]{"+" + Ui.money(50, 0), 50.0});
        quick.add(new Object[]{"+" + Ui.money(100, 0), 100.0});
        quick.add(new Object[]{"+" + Ui.money(250, 0), 250.0});
        if (b.balance > 0) quick.add(new Object[]{"Whole balance", b.balance});
        for (final Object[] q : quick) {
            View ch = Ui.chip("payments", q[0] + (((String) q[0]).startsWith("+") ? "" : " · " + Ui.money((Double) q[1])), Theme.accent);
            Ui.tap(ch, 50, new Runnable() { @Override public void run() { amt.setText(Ui.num((Double) q[1])); amt.setSelection(amt.getText().length()); Ui.haptic(amt); } });
            row.addView(ch, Ui.lpm(Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        }
        hs.addView(row);
        h.body.addView(hs, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        // Date
        LocalDate t = LocalDate.now();
        final LocalDate[] date = {plan ? nextDue(b, t.plusDays(1)) : t};
        final LinearLayout dg = Ui.group();
        dg.setBackground(Ui.rr(Theme.dark ? Theme.field : Theme.surface, 12));
        final Runnable[] drawDate = new Runnable[1];
        drawDate[0] = new Runnable() { @Override public void run() {
            dg.removeAllViews();
            Ui.row(dg, "calendar_month", Theme.red, plan ? "Pay on" : "Paid on", date[0].isAfter(LocalDate.now()) ? "In the future, so it's saved as planned" : null,
                    date[0].format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.UK)), true, new Runnable() {
                @Override public void run() { Sheets.date(a, date[0], new Ui.Fn<LocalDate>() { @Override public void on(LocalDate d) { date[0] = d; drawDate[0].run(); } }); }
            });
        } };
        drawDate[0].run();
        h.body.addView(dg);
        if (plan) {
            LinearLayout dq = Ui.h();
            dq.setPadding(0, Ui.dp(8), 0, 0);
            final LocalDate nm = t.plusMonths(1);
            final LocalDate nd = nextDue(b, t.plusDays(1));
            View c1 = Ui.chip("event_repeat", "Next payment date", Theme.text2);
            Ui.tap(c1, 50, new Runnable() { @Override public void run() { date[0] = nd; drawDate[0].run(); } });
            View c2 = Ui.chip("event_repeat", "This time next month", Theme.text2);
            Ui.tap(c2, 50, new Runnable() { @Override public void run() { date[0] = nm; drawDate[0].run(); } });
            dq.addView(c1, Ui.lpm(Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
            dq.addView(c2);
            h.body.addView(dq);
        }
        final EditText note = Ui.field("Note (optional, e.g. bonus, overtime)", "", false);
        h.body.addView(note, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        Sheets.buttons(h, plan ? "Plan payment" : "Record payment", new Runnable() {
            @Override public void run() {
                double v = Ui.parse(amt.getText().toString());
                if (v <= 0) { a.snack("Enter an amount"); return; }
                Payoff before = payoff(b, b.amount);
                Snap was = new Snap(b);
                addPayment(b, v, date[0], note.getText().toString().trim());
                Store.changed();
                Ui.confirmHaptic(h.body);
                h.dismiss();
                render(false);
                Payoff after = payoff(b, b.amount);
                String gain = !before.never && !after.never && before.months > after.months
                        ? " · clears " + duration(before.months - after.months) + " sooner" : "";
                if (date[0].isAfter(LocalDate.now()))
                    a.snack("Planned " + Ui.money(v) + " on " + date[0].format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)) + gain);
                else
                    a.snack("Paid " + Ui.money(v) + " · " + changes(b, was));
            }
        });
        Sheets.show(h);
        amt.requestFocus();
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

    private View upcomingRow(LocalDate d, final Bill b) { return upcomingRow(d, b, b.amount, null); }

    private View upcomingRow(LocalDate d, final Bill b, double amt, String label) {
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
        t.addView(Ui.text(when(d) + (label != null ? " · " + label : (b.trial && d.equals(anchor(b)) ? " · first charge after trial" : "") + " · " + b.frequency), 13, 400, label != null ? Theme.green : Theme.text2));
        r.addView(t, Ui.weight(1));
        r.addView(Ui.text(Ui.money(amt), 16, 650, Theme.text));
        r.setContentDescription(b.name + ", " + Ui.money(b.amount) + ", " + when(d));
        Ui.tap(r, 0, new Runnable() { @Override public void run() { if (isCredit(b)) cardSheet(b); else edit(b); } });
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
        Ui.tap(r, 0, new Runnable() { @Override public void run() { if (isCredit(b)) cardSheet(b); else edit(b); } });
        r.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { Ui.haptic(v); menu(b); return true; }
        });
        return r;
    }

    private void menu(final Bill b) {
        if (isCredit(b)) {
            final String[] co = {"Make a payment", "Plan an extra payment", "Edit card", b.paused ? "Resume" : "Pause", "Delete"};
            Sheets.choose(a, b.name, co, -1, new Ui.Fn<Integer>() {
                @Override public void on(Integer i) {
                    if (i == 0) paymentSheet(b, false);
                    else if (i == 1) paymentSheet(b, true);
                    else if (i == 2) edit(b);
                    else if (i == 3) { b.paused = !b.paused; Store.changed(); render(false); }
                    else delete(b);
                }
            });
            return;
        }
        final String[] opts = {b.paused ? "Resume" : "Pause", "Duplicate", "Delete"};
        Sheets.choose(a, b.name, opts, -1, new Ui.Fn<Integer>() {
            @Override public void on(Integer i) {
                if (i == 0) { b.paused = !b.paused; Store.changed(); a.snack(b.name + (b.paused ? " paused · not counted in your totals" : " resumed")); render(false); }
                else if (i == 1) {
                    Bill c = copy(b);
                    c.id = Model.newId();
                    c.payments.clear();
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
        c.balance = b.balance; c.apr = b.apr; c.payInFull = b.payInFull; c.balanceMillis = b.balanceMillis;
        c.creditLimit = b.creditLimit; c.minPayment = b.minPayment;
        c.minRule = b.minRule; c.minFloor = b.minFloor; c.followMinimum = b.followMinimum; c.autoRoll = b.autoRoll; c.rolledMillis = b.rolledMillis;
        for (Model.CardPayment p : b.payments) {
            Model.CardPayment q = new Model.CardPayment();
            q.id = p.id; q.dateMillis = p.dateMillis; q.amount = p.amount; q.planned = p.planned; q.note = p.note;
            c.payments.add(q);
        }
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
                                @Override public void on(String v) {
                                    b.balance = Math.max(0, Ui.parse(v)); b.balanceMillis = System.currentTimeMillis(); b.rolledMillis = TaxEngine.toMillis(LocalDate.now());
                                    recalc(b);
                                    if (b.followMinimum || b.payInFull) amount.setText(b.amount > 0 ? Ui.num(b.amount) : "0");
                                    draw[0].run();
                                }
                            });
                        }
                    });
                    Ui.row(g, "percent", Theme.orange, "Interest rate (APR)", null, b.apr > 0 ? Ui.num(b.apr) + "%" : "Add", true, new Runnable() {
                        @Override public void run() {
                            Sheets.input(a, "Interest rate (APR %)", "Your purchase or balance APR. Use 0 for an interest-free period.", "e.g. 24.9", b.apr > 0 ? Ui.num(b.apr) : "", true, new Ui.Fn<String>() {
                                @Override public void on(String v) {
                                    b.apr = Math.max(0, Math.min(99, Ui.parse(v)));
                                    recalc(b);
                                    if (b.followMinimum || b.payInFull) amount.setText(b.amount > 0 ? Ui.num(b.amount) : "0");
                                    draw[0].run();
                                }
                            });
                        }
                    });
                    Ui.row(g, "account_balance_wallet", Theme.green, "Credit limit", b.creditLimit > 0 && b.balance > 0 ? Ui.money(available(b)) + " available" : null,
                            b.creditLimit > 0 ? Ui.money(b.creditLimit, 0) : "Add", true, new Runnable() {
                        @Override public void run() {
                            Sheets.input(a, "Credit limit", "Leave blank for loans.", "e.g. 3000", b.creditLimit > 0 ? Ui.num(b.creditLimit) : "", true, new Ui.Fn<String>() {
                                @Override public void on(String v) { b.creditLimit = Math.max(0, Ui.parse(v)); draw[0].run(); }
                            });
                        }
                    });
                    // Minimum payment: typed in, or worked out from the card's rule so it updates with the balance
                    final Runnable syncAmount = new Runnable() { @Override public void run() {
                        recalc(b);
                        if (b.followMinimum || b.payInFull) amount.setText(b.amount > 0 ? Ui.num(b.amount) : "0");
                    } };
                    Ui.row(g, "receipt_long", Theme.indigo, "Minimum payment", MIN_RULES[b.minRule],
                            b.minRule != 0 ? Ui.money(computeMin(b)) : b.minPayment > 0 ? Ui.money(b.minPayment) : "Add", true, new Runnable() {
                        @Override public void run() {
                            Sheets.choose(a, "How is the minimum worked out?", MIN_RULES, b.minRule, new Ui.Fn<Integer>() {
                                @Override public void on(Integer i) {
                                    b.minRule = i;
                                    if (i == 0) {
                                        Sheets.input(a, "Minimum payment", "The minimum on your latest statement.", "e.g. 25", b.minPayment > 0 ? Ui.num(b.minPayment) : "", true, new Ui.Fn<String>() {
                                            @Override public void on(String v) {
                                                b.minPayment = Math.max(0, Ui.parse(v));
                                                if (b.minPayment > 0 && Ui.parse(amount.getText().toString()) <= 0) amount.setText(Ui.num(b.minPayment));
                                                syncAmount.run(); draw[0].run();
                                            }
                                        });
                                    } else { syncAmount.run(); draw[0].run(); }
                                }
                            });
                        }
                    });
                    if (b.minRule != 0) {
                        Ui.row(g, "remove", Theme.text2, "Lowest minimum", "Cards charge at least this", Ui.money(b.minFloor, b.minFloor % 1 == 0 ? 0 : 2), true, new Runnable() {
                            @Override public void run() {
                                Sheets.input(a, "Lowest minimum", "Most UK cards use £5 or £25. Check your statement.", "e.g. 5", Ui.num(b.minFloor), true, new Ui.Fn<String>() {
                                    @Override public void on(String v) { b.minFloor = Math.max(0, Ui.parse(v)); syncAmount.run(); draw[0].run(); }
                                });
                            }
                        });
                    }
                    int mode = b.payInFull ? 2 : b.followMinimum ? 0 : 1;
                    Ui.row(g, "payments", Theme.green, "Each month I pay", null, mode == 0 ? "The minimum" : mode == 2 ? "In full" : "A fixed amount", true, new Runnable() {
                        @Override public void run() {
                            Sheets.choose(a, "Each month I pay", PAY_MODES, b.payInFull ? 2 : b.followMinimum ? 0 : 1, new Ui.Fn<Integer>() {
                                @Override public void on(Integer i) {
                                    b.followMinimum = i == 0; b.payInFull = i == 2;
                                    if (i == 0 && b.minRule == 0 && b.minPayment <= 0) b.minRule = 1;   // need a minimum to follow
                                    syncAmount.run(); draw[0].run();
                                }
                            });
                        }
                    });
                    Ui.switchRow(g, "autorenew", Theme.accent, "Smart monthly updates", "On each payment date, add interest, take off your payment and recalculate the minimum", b.autoRoll, new Ui.Fn<Boolean>() {
                        @Override public void on(Boolean v) { b.autoRoll = v; if (v) b.rolledMillis = TaxEngine.toMillis(LocalDate.now()); }
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
                if (isCredit(b) && (b.followMinimum || b.payInFull)) {
                    b.amount = amt; recalc(b); amt = b.amount;   // amount follows the balance
                    if (b.rolledMillis <= 0) b.rolledMillis = TaxEngine.toMillis(LocalDate.now());
                } else if (amt <= 0) { a.snack("Enter an amount"); return; }
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
            for (Object[] x : extrasIn(first, last)) {
                double v = ((Model.CardPayment) x[2]).amount;
                b.bills += v;
                if (!((LocalDate) x[0]).isBefore(t)) b.stillToPay += v;
            }
            b.safe = b.income - b.bills;
            return b;
        }
    }

    static String money0(double v) { return Ui.masked() ? Store.currency() + Ui.MASK : String.format(Locale.UK, "%s%,.0f", Store.currency(), v); }
}
