package com.dayoadekoya.paycalculator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Port of iOS PaydayPredictor: manual anchor + frequency, or a prediction from the latest entry. */
public final class Payday {
    private Payday() {}

    public static boolean isManual() {
        return Store.bool("useManualPayday", false) && Store.integer("nextPaydayEpochDay", 0) > 0;
    }

    public static LocalDate anchor() {
        int d = Store.integer("nextPaydayEpochDay", 0);
        return d > 0 ? LocalDate.ofEpochDay(d) : null;
    }

    public static String frequency() { return Store.str("payFrequency", "Weekly"); }

    private static LocalDate step(LocalDate d, String f, int dir) {
        if ("Monthly".equals(f)) return d.plusMonths(dir);
        if ("Bi-Weekly".equals(f)) return d.plusDays(14L * dir);
        return d.plusDays(7L * dir);
    }

    public static LocalDate upcoming(LocalDate anchor, String f, LocalDate ref) {
        LocalDate d = anchor;
        int safety = 0;
        while (d.isBefore(ref) && safety++ < 2000) d = step(d, f, 1);
        return d;
    }

    public static LocalDate next() {
        LocalDate today = LocalDate.now();
        if (isManual()) return upcoming(anchor(), frequency(), today);
        if (Store.entries.isEmpty()) return null;
        Model.PayEntry latest = Store.entries.get(0);
        LocalDate p = step(TaxEngine.toDate(latest.dateMillis), latest.payFrequency, 1);
        // Only show a prediction if it's in the future (otherwise it's stale).
        return p.isBefore(today) ? null : p;
    }

    public static List<LocalDate> schedule(int count) {
        List<LocalDate> out = new ArrayList<LocalDate>();
        if (!isManual()) return out;
        LocalDate d = upcoming(anchor(), frequency(), LocalDate.now());
        for (int i = 0; i < count; i++) { out.add(d); d = step(d, frequency(), 1); }
        return out;
    }

    public static Set<LocalDate> inMonth(int year, int month) {
        Set<LocalDate> out = new HashSet<LocalDate>();
        LocalDate start = LocalDate.of(year, month, 1), end = start.plusMonths(1);
        if (isManual()) {
            String f = frequency();
            LocalDate d = anchor();
            int safety = 0;
            if (d.isBefore(start)) { while (d.isBefore(start) && safety++ < 5000) d = step(d, f, 1); }
            else { while (!d.isBefore(start) && safety++ < 5000) d = step(d, f, -1); d = step(d, f, 1); }
            safety = 0;
            while (d.isBefore(end) && safety++ < 100) { out.add(d); d = step(d, f, 1); }
        } else {
            LocalDate n = next();
            if (n != null && !n.isBefore(start) && n.isBefore(end)) out.add(n);
        }
        return out;
    }
}
