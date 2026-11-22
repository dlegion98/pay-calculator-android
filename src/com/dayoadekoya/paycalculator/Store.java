package com.dayoadekoya.paycalculator;

import android.content.Context;
import android.content.SharedPreferences;

import com.dayoadekoya.paycalculator.Model.DailyPay;
import com.dayoadekoya.paycalculator.Model.PayEntry;
import com.dayoadekoya.paycalculator.Model.ShiftTemplate;
import com.dayoadekoya.paycalculator.Model.WorkExpense;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** On-device vault: a single JSON file plus SharedPreferences for settings. */
public final class Store {
    private Store() {}

    /** Seconds between 1970-01-01 and 2001-01-01 (Apple's reference date). */
    public static final double APPLE_EPOCH_OFFSET = 978307200.0;

    public static final List<PayEntry> entries = new ArrayList<PayEntry>();
    public static final List<ShiftTemplate> templates = new ArrayList<ShiftTemplate>();
    public static final List<WorkExpense> expenses = new ArrayList<WorkExpense>();
    public static final List<Model.Bill> bills = new ArrayList<Model.Bill>();

    private static Context app;
    private static final List<Runnable> listeners = new ArrayList<Runnable>();

    public static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public static SharedPreferences prefs() { return prefs(app); }

    // ------------------------------------------------------------- prefs helpers

    public static String str(String k, String def) { return prefs().getString(k, def); }
    public static boolean bool(String k, boolean def) { return prefs().getBoolean(k, def); }
    public static int integer(String k, int def) { return prefs().getInt(k, def); }
    public static double dbl(String k, double def) {
        try { return Double.longBitsToDouble(prefs().getLong(k, Double.doubleToLongBits(def))); }
        catch (ClassCastException ex) { return def; }
    }
    public static void put(String k, String v) { prefs().edit().putString(k, v).apply(); }
    public static void put(String k, boolean v) { prefs().edit().putBoolean(k, v).apply(); }
    public static void put(String k, int v) { prefs().edit().putInt(k, v).apply(); }
    public static void put(String k, double v) { prefs().edit().putLong(k, Double.doubleToLongBits(v)).apply(); }

    public static String currency() { return str("currencySymbol", "£"); }

    public static void syncConfig() {
        TaxEngine.Config c = TaxEngine.config;
        c.mode = str("taxCalculationMode", "HMRC Bands");
        c.flatTaxRate = dbl("flatTaxRate", 20.0);
        c.flatNiRate = dbl("flatNIRate", 8.0);
        c.studentLoanPlan = str("studentLoanPlan", "None");
    }

    // ------------------------------------------------------------- lifecycle

    public static void init(Context c) {
        app = c.getApplicationContext();
        syncConfig();
        load();
    }

    public static boolean ready() { return app != null; }

    public static void addListener(Runnable r) { if (!listeners.contains(r)) listeners.add(r); }

    public static void changed() {
        sort();
        save();
        syncConfig();
        for (Runnable r : new ArrayList<Runnable>(listeners)) {
            try { r.run(); } catch (Throwable t) { t.printStackTrace(); }
        }
        try { Reminders.reschedule(app); } catch (Throwable ignored) {}
        try { Widgets.updateAll(app); } catch (Throwable ignored) {}
    }

    private static File file() { return new File(app.getFilesDir(), "vault.json"); }

    private static void sort() {
        Collections.sort(entries, new Comparator<PayEntry>() {
            @Override public int compare(PayEntry a, PayEntry b) { return Long.compare(b.dateMillis, a.dateMillis); }
        });
        Collections.sort(templates, new Comparator<ShiftTemplate>() {
            @Override public int compare(ShiftTemplate a, ShiftTemplate b) { return Long.compare(a.createdAt, b.createdAt); }
        });
        Collections.sort(bills, new Comparator<Model.Bill>() {
            @Override public int compare(Model.Bill a, Model.Bill b) { return BillsPage.nextDue(a, java.time.LocalDate.now()).compareTo(BillsPage.nextDue(b, java.time.LocalDate.now())); }
        });
        Collections.sort(expenses, new Comparator<WorkExpense>() {
            @Override public int compare(WorkExpense a, WorkExpense b) { return Long.compare(b.dateMillis, a.dateMillis); }
        });
    }

    public static void load() {
        entries.clear(); templates.clear(); expenses.clear(); bills.clear();
        File f = file();
        if (!f.exists()) return;
        try {
            JSONObject root = new JSONObject(readAll(new FileInputStream(f)));
            JSONArray es = root.optJSONArray("entries");
            if (es != null) for (int i = 0; i < es.length(); i++) entries.add(entryFrom(es.getJSONObject(i), false));
            JSONArray ts = root.optJSONArray("templates");
            if (ts != null) for (int i = 0; i < ts.length(); i++) templates.add(templateFrom(ts.getJSONObject(i)));
            JSONArray xs = root.optJSONArray("expenses");
            if (xs != null) for (int i = 0; i < xs.length(); i++) expenses.add(expenseFrom(xs.getJSONObject(i)));
            JSONArray bs = root.optJSONArray("bills");
            if (bs != null) for (int i = 0; i < bs.length(); i++) bills.add(billFrom(bs.getJSONObject(i)));
            sort();
        } catch (Throwable t) {
            t.printStackTrace();
            // Keep a copy of an unreadable vault rather than silently overwriting it.
            f.renameTo(new File(app.getFilesDir(), "vault-corrupt-" + System.currentTimeMillis() + ".json"));
        }
    }

    public static void save() {
        try {
            JSONObject root = new JSONObject();
            JSONArray es = new JSONArray();
            for (PayEntry e : entries) es.put(entryJson(e, false));
            root.put("entries", es);
            JSONArray ts = new JSONArray();
            for (ShiftTemplate t : templates) ts.put(templateJson(t));
            root.put("templates", ts);
            JSONArray xs = new JSONArray();
            for (WorkExpense x : expenses) xs.put(expenseJson(x));
            root.put("expenses", xs);
            JSONArray bs = new JSONArray();
            for (Model.Bill b : bills) bs.put(billJson(b));
            root.put("bills", bs);
            File tmp = new File(app.getFilesDir(), "vault.json.tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
            out.close();
            tmp.renameTo(file());
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static String readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------- JSON (iOS SyncData compatible)

    private static Object opt(Double d) { return d == null ? JSONObject.NULL : d; }
    private static Double optD(JSONObject o, String k) {
        if (!o.has(k) || o.isNull(k)) return null;
        double v = o.optDouble(k, Double.NaN);
        return Double.isNaN(v) ? null : v;
    }

    /** @param apple true to write dates as Apple reference-date seconds (iOS JSONEncoder default). */
    public static JSONObject entryJson(PayEntry e, boolean apple) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", e.id);
        if (apple) o.put("date", e.dateMillis / 1000.0 - APPLE_EPOCH_OFFSET);
        else o.put("dateMillis", e.dateMillis);
        o.put("jobName", e.jobName);
        o.put("payFrequency", e.payFrequency);
        o.put("hourlyRate", e.hourlyRate);
        o.put("taxCode", e.taxCode);
        o.put("pensionProvider", e.pensionProvider);
        o.put("pensionRate", e.pensionRate);
        o.put("isSalarySacrifice", e.isSalarySacrifice);
        o.put("age", e.age);
        o.put("notes", e.notes);
        o.put("isSalariedMode", e.isSalariedMode);
        o.put("fixedMonthlyGross", e.fixedMonthlyGross);
        o.put("actualGrossPay", opt(e.actualGrossPay));
        o.put("actualTaxAmount", opt(e.actualTaxAmount));
        o.put("actualNiAmount", opt(e.actualNiAmount));
        o.put("actualPensionAmount", opt(e.actualPensionAmount));
        o.put("actualExtraDeductions", opt(e.actualExtraDeductions));
        o.put("actualPayReceived", opt(e.actualPayReceived));
        if (!apple) o.put("sortIndex", e.sortIndex);
        JSONArray days = new JSONArray();
        for (DailyPay d : e.days) {
            JSONObject j = new JSONObject();
            j.put("dayIndex", d.dayIndex);
            j.put("hoursWorked", d.hoursWorked);
            j.put("premiumHours", d.premiumHours);
            j.put("premiumMultiplier", d.premiumMultiplier);
            j.put("isOvertime", d.isOvertime);
            j.put("unpaidBreakMinutes", d.unpaidBreakMinutes);
            j.put("travelPay", d.travelPay);
            j.put("bonus", d.bonus);
            j.put("shiftTag", d.shiftTag);
            days.put(j);
        }
        o.put("dailyPays", days);
        return o;
    }

    public static PayEntry entryFrom(JSONObject o, boolean apple) throws Exception {
        PayEntry e = new PayEntry();
        e.id = o.optString("id", Model.newId());
        if (o.has("dateMillis")) e.dateMillis = o.getLong("dateMillis");
        else if (o.has("date")) {
            Object raw = o.get("date");
            if (raw instanceof String) e.dateMillis = parseIso((String) raw);
            else e.dateMillis = Math.round((o.getDouble("date") + APPLE_EPOCH_OFFSET) * 1000.0);
        }
        e.jobName = o.optString("jobName", "");
        e.payFrequency = o.optString("payFrequency", "Weekly");
        e.hourlyRate = o.optDouble("hourlyRate", 0);
        e.taxCode = o.optString("taxCode", "1257L");
        e.pensionProvider = o.optString("pensionProvider", "");
        e.pensionRate = o.optDouble("pensionRate", 0);
        e.isSalarySacrifice = o.optBoolean("isSalarySacrifice", false);
        e.age = o.optInt("age", 25);
        e.notes = o.optString("notes", "");
        e.isSalariedMode = o.optBoolean("isSalariedMode", false);
        e.fixedMonthlyGross = o.optDouble("fixedMonthlyGross", 0);
        e.actualGrossPay = optD(o, "actualGrossPay");
        e.actualTaxAmount = optD(o, "actualTaxAmount");
        e.actualNiAmount = optD(o, "actualNiAmount");
        e.actualPensionAmount = optD(o, "actualPensionAmount");
        e.actualExtraDeductions = optD(o, "actualExtraDeductions");
        e.actualPayReceived = optD(o, "actualPayReceived");
        e.sortIndex = o.optInt("sortIndex", 0);
        JSONArray days = o.optJSONArray("dailyPays");
        if (days != null) {
            for (int i = 0; i < days.length(); i++) {
                JSONObject j = days.getJSONObject(i);
                DailyPay d = new DailyPay();
                d.dayIndex = j.optInt("dayIndex", i);
                d.hoursWorked = j.optDouble("hoursWorked", 0);
                d.premiumHours = j.optDouble("premiumHours", 0);
                d.premiumMultiplier = j.optDouble("premiumMultiplier", 2.0);
                d.isOvertime = j.optBoolean("isOvertime", false);
                d.unpaidBreakMinutes = j.optDouble("unpaidBreakMinutes", 0);
                d.travelPay = j.optDouble("travelPay", 0);
                d.bonus = j.optDouble("bonus", 0);
                d.shiftTag = j.optString("shiftTag", "None");
                e.days.add(d);
            }
            Collections.sort(e.days, new Comparator<DailyPay>() {
                @Override public int compare(DailyPay a, DailyPay b) { return Integer.compare(a.dayIndex, b.dayIndex); }
            });
        }
        return e;
    }

    private static long parseIso(String s) {
        try { return java.time.Instant.parse(s).toEpochMilli(); } catch (Exception ex) { return System.currentTimeMillis(); }
    }

    private static JSONObject templateJson(ShiftTemplate t) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", t.id); o.put("name", t.name); o.put("jobName", t.jobName);
        o.put("hourlyRate", t.hourlyRate); o.put("payFrequency", t.payFrequency); o.put("hours", t.hours);
        o.put("unpaidBreakMinutes", t.unpaidBreakMinutes); o.put("isOvertime", t.isOvertime);
        o.put("travelPay", t.travelPay); o.put("bonus", t.bonus); o.put("shiftTag", t.shiftTag);
        o.put("iconName", t.iconName); o.put("createdAt", t.createdAt);
        return o;
    }

    private static ShiftTemplate templateFrom(JSONObject o) {
        ShiftTemplate t = new ShiftTemplate();
        t.id = o.optString("id", Model.newId()); t.name = o.optString("name", "Shift");
        t.jobName = o.optString("jobName", ""); t.hourlyRate = o.optDouble("hourlyRate", 0);
        t.payFrequency = o.optString("payFrequency", "Weekly"); t.hours = o.optDouble("hours", 8);
        t.unpaidBreakMinutes = o.optDouble("unpaidBreakMinutes", 0); t.isOvertime = o.optBoolean("isOvertime", false);
        t.travelPay = o.optDouble("travelPay", 0); t.bonus = o.optDouble("bonus", 0);
        t.shiftTag = o.optString("shiftTag", "None"); t.iconName = o.optString("iconName", "bolt");
        t.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        return t;
    }

    private static JSONObject expenseJson(WorkExpense x) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", x.id); o.put("category", x.category); o.put("dateMillis", x.dateMillis);
        o.put("amount", x.amount); o.put("miles", x.miles); o.put("isMileage", x.isMileage); o.put("note", x.note);
        return o;
    }

    private static WorkExpense expenseFrom(JSONObject o) {
        WorkExpense x = new WorkExpense();
        x.id = o.optString("id", Model.newId()); x.category = o.optString("category", "Other");
        x.dateMillis = o.optLong("dateMillis", System.currentTimeMillis());
        x.amount = o.optDouble("amount", 0); x.miles = o.optDouble("miles", 0);
        x.isMileage = o.optBoolean("isMileage", false); x.note = o.optString("note", "");
        return x;
    }

    // ------------------------------------------------------------- Backup / transfer

    /** Same shape as the iOS "Export Database" file, so it can be imported on iPhone too. */
    static JSONObject billJson(Model.Bill b) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", b.id); o.put("name", b.name); o.put("amount", b.amount);
        o.put("dayOfMonth", b.dayOfMonth); o.put("category", b.category);
        o.put("frequency", b.frequency); o.put("startMillis", b.startMillis);
        o.put("subscription", b.subscription); o.put("remindDays", b.remindDays);
        o.put("paused", b.paused); o.put("trial", b.trial); o.put("notes", b.notes);
        o.put("balance", b.balance); o.put("apr", b.apr); o.put("payInFull", b.payInFull); o.put("balanceMillis", b.balanceMillis);
        o.put("creditLimit", b.creditLimit); o.put("minPayment", b.minPayment);
        JSONArray cp = new JSONArray();
        for (Model.CardPayment p : b.payments) {
            JSONObject x = new JSONObject();
            x.put("id", p.id); x.put("dateMillis", p.dateMillis); x.put("amount", p.amount); x.put("planned", p.planned); x.put("note", p.note);
            cp.put(x);
        }
        o.put("cardPayments", cp);
        return o;
    }

    static Model.Bill billFrom(JSONObject o) {
        Model.Bill b = new Model.Bill();
        b.id = o.optString("id", Model.newId()); b.name = o.optString("name", "Bill");
        b.amount = o.optDouble("amount", 0); b.dayOfMonth = o.optInt("dayOfMonth", 1);
        b.category = o.optString("category", "Bills");
        if ("Phone & Internet".equals(b.category)) b.category = "Phone & internet";   // pre-6.3 names
        if ("Debt".equals(b.category)) b.category = "Loans & credit";
        b.frequency = o.optString("frequency", "Monthly");
        b.startMillis = o.optLong("startMillis", 0);
        b.subscription = o.optBoolean("subscription", "Subscriptions".equals(b.category));
        b.remindDays = o.optInt("remindDays", -1);
        b.paused = o.optBoolean("paused", false);
        b.trial = o.optBoolean("trial", false);
        b.notes = o.optString("notes", "");
        b.balance = o.optDouble("balance", 0);
        b.apr = o.optDouble("apr", 0);
        b.payInFull = o.optBoolean("payInFull", false);
        b.balanceMillis = o.optLong("balanceMillis", 0);
        b.creditLimit = o.optDouble("creditLimit", 0);
        b.minPayment = o.optDouble("minPayment", 0);
        JSONArray cp = o.optJSONArray("cardPayments");
        if (cp != null) for (int i = 0; i < cp.length(); i++) {
            JSONObject x = cp.optJSONObject(i);
            if (x == null) continue;
            Model.CardPayment p = new Model.CardPayment();
            p.id = x.optString("id", Model.newId()); p.dateMillis = x.optLong("dateMillis", 0);
            p.amount = x.optDouble("amount", 0); p.planned = x.optBoolean("planned", false); p.note = x.optString("note", "");
            b.payments.add(p);
        }
        return b;
    }

    public static String exportSyncJson() throws Exception {
        JSONObject root = new JSONObject();
        JSONArray es = new JSONArray();
        for (PayEntry e : entries) es.put(entryJson(e, true));
        root.put("entries", es);
        // Android extra: the iOS app ignores keys it doesn't know
        JSONArray rp = new JSONArray();
        for (Model.Bill b : bills) rp.put(billJson(b));
        root.put("recurringPayments", rp);
        root.put("syncCode", "android");
        return root.toString(2);
    }

    /** Merges entries from an iOS or Android backup, skipping IDs that already exist. Returns count added. */
    public static int importSyncJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray es = root.getJSONArray("entries");
        Set<String> ids = new HashSet<String>();
        for (PayEntry e : entries) ids.add(e.id.toUpperCase(Locale.ROOT));
        int added = 0;
        for (int i = 0; i < es.length(); i++) {
            JSONObject o = es.getJSONObject(i);
            PayEntry e = entryFrom(o, !o.has("dateMillis"));
            if (ids.contains(e.id.toUpperCase(Locale.ROOT))) continue;
            entries.add(e);
            ids.add(e.id.toUpperCase(Locale.ROOT));
            added++;
        }
        Set<String> tIds = new HashSet<String>();
        for (ShiftTemplate t : templates) tIds.add(t.id);
        JSONArray ts = root.optJSONArray("templates");
        if (ts != null) for (int i = 0; i < ts.length(); i++) {
            ShiftTemplate t = templateFrom(ts.getJSONObject(i));
            if (tIds.add(t.id)) templates.add(t);
        }
        Set<String> xIds = new HashSet<String>();
        for (WorkExpense x : expenses) xIds.add(x.id);
        JSONArray xs = root.optJSONArray("expenses");
        if (xs != null) for (int i = 0; i < xs.length(); i++) {
            WorkExpense x = expenseFrom(xs.getJSONObject(i));
            if (xIds.add(x.id)) expenses.add(x);
        }
        changed();
        JSONArray rps = root.optJSONArray("recurringPayments");
        if (rps != null) {
            Set<String> bIds = new HashSet<String>();
            for (Model.Bill b : bills) bIds.add(b.id);
            for (int i = 0; i < rps.length(); i++) {
                Model.Bill b = billFrom(rps.getJSONObject(i));
                if (bIds.add(b.id)) bills.add(b);
            }
        }
        return added;
    }

    public static String exportCsv() {
        String sym = currency();
        StringBuilder sb = new StringBuilder("Date,Job,Frequency,Total Hours,Gross Pay,Tax Paid,NI Paid,Pension,Student Loan,Net Pay,Notes\n");
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        for (PayEntry e : entries) {
            sb.append(TaxEngine.toDate(e.dateMillis).format(f)).append(',')
              .append(csv(e.jobName)).append(',')
              .append(e.payFrequency).append(',')
              .append(String.format(Locale.UK, "%.1f", TaxEngine.totalHours(e))).append(',')
              .append(sym).append(String.format(Locale.UK, "%.2f", TaxEngine.effectiveGross(e))).append(',')
              .append(sym).append(String.format(Locale.UK, "%.2f", TaxEngine.displayTax(e))).append(',')
              .append(sym).append(String.format(Locale.UK, "%.2f", TaxEngine.displayNi(e))).append(',')
              .append(sym).append(String.format(Locale.UK, "%.2f", TaxEngine.displayPension(e))).append(',')
              .append(sym).append(String.format(Locale.UK, "%.2f", TaxEngine.studentLoan(e))).append(',')
              .append(sym).append(String.format(Locale.UK, "%.2f", TaxEngine.net(e))).append(',')
              .append(csv(e.notes)).append('\n');
        }
        return sb.toString();
    }

    private static String csv(String s) {
        if (s == null) return "";
        String t = s.replace("\"", "\"\"").replace("\n", " ");
        return t.contains(",") ? "\"" + t + "\"" : t;
    }

    public static PayEntry find(String id) {
        for (PayEntry e : entries) if (e.id.equals(id)) return e;
        return null;
    }

    /** Creates a single quick-logged shift using the saved defaults (dashboard quick actions). */
    public static PayEntry quickShift(double hours, String job) {
        PayEntry e = newEntryWithDefaults();
        if (job != null) e.jobName = job;
        java.time.LocalDate today = java.time.LocalDate.now();
        e.dateMillis = TaxEngine.toMillis(today);
        e.payFrequency = "Weekly";
        e.days.add(new DailyPay(0, hours));
        return e;
    }

    public static PayEntry newEntryWithDefaults() {
        PayEntry e = new PayEntry();
        e.jobName = "Main Job";
        e.age = integer("defaultAge", 25);
        double rate = dbl("defaultHourlyRate", 0);
        if (bool("autoTrackMinimumWage", false) || rate <= 0) {
            rate = rate > 0 && !bool("autoTrackMinimumWage", false) ? rate : TaxEngine.minimumWage(e.age, java.time.LocalDate.now());
        }
        e.hourlyRate = rate;
        e.taxCode = str("defaultTaxCode", "1257L");
        e.pensionProvider = str("defaultPensionProvider", "");
        e.pensionRate = dbl("defaultPensionRate", 5.0);
        e.isSalarySacrifice = bool("defaultIsSalarySacrifice", false);
        e.payFrequency = str("payFrequency", "Weekly");
        return e;
    }
}
