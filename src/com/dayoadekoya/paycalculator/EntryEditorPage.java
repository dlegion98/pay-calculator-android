package com.dayoadekoya.paycalculator;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dayoadekoya.paycalculator.Model.DailyPay;
import com.dayoadekoya.paycalculator.Model.PayEntry;
import com.dayoadekoya.paycalculator.Model.ShiftTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Create or edit a pay period — port of the iOS AddPayView form. */
public class EntryEditorPage extends Page {
    private final String editId;
    private String mode;              // "Shift Tracker" | "Payslip Totals"
    private final ShiftTemplate template;
    private PayEntry draft;
    private DailyPay[] grid;
    private boolean dirty;
    private final boolean[] expanded = new boolean[64];

    private TextView heroNet, heroLabel, heroRange;
    private int focusDay = -1;
    private EditText fHours, fGross, fTax, fNi, fPension, fExtra, fNet;
    private boolean suppress;
    /** Which payslip fields the user typed themselves (so auto-estimates never overwrite them). */
    private boolean taxEdited, niEdited, pensionEdited;

    private static final String[] FREQS = {"Weekly", "Bi-Weekly", "Monthly"};
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK);

    public EntryEditorPage(String editId, String mode) { this(editId, mode, null); }

    public EntryEditorPage(String editId, String mode, ShiftTemplate t) {
        this.editId = editId;
        this.mode = mode;
        this.template = t;
    }

    private LocalDate presetDay;

    /** New shift-tracker entry with the period containing `day`, and that day opened ready to type. */
    public static EntryEditorPage forDay(LocalDate day) {
        EntryEditorPage p = new EntryEditorPage(null, "Shift Tracker", null);
        p.presetDay = day;
        return p;
    }

    private boolean isNew() { return editId == null; }
    private boolean totals() { return "Payslip Totals".equals(mode); }

    @Override public String title() {
        if (!isNew()) return "Edit pay period";
        return totals() ? "Payslip totals" : "New shift";
    }

    @Override protected float expandedDp() { return 56; }

    @Override protected void actions() {
        LinearLayout save = Ui.button("Save", null, Theme.accent, Theme.onAccent, new Runnable() { @Override public void run() { save(); } });
        save.setPadding(Ui.dp(20), Ui.dp(9), Ui.dp(20), Ui.dp(9));
        LinearLayout.LayoutParams p = Ui.lpm(Ui.WRAP, Ui.WRAP, 0, 0, 8, 0);
        s.actions.addView(save, p);
    }

    private void init() {
        if (!isNew()) {
            PayEntry e = Store.find(editId);
            draft = e != null ? e.copy(false) : Store.newEntryWithDefaults();
            if (mode == null) mode = (draft.actualPayReceived != null || draft.actualGrossPay != null) ? "Payslip Totals" : "Shift Tracker";
        } else {
            draft = Store.newEntryWithDefaults();
            LocalDate today = LocalDate.now();
            LocalDate start = "Monthly".equals(draft.payFrequency) ? today.withDayOfMonth(1) : today.with(DayOfWeek.MONDAY);
            draft.dateMillis = TaxEngine.toMillis(start);
            if (mode == null) mode = "Shift Tracker";
            if (template != null) {
                draft.jobName = template.jobName.isEmpty() ? template.name : template.jobName;
                if (template.hourlyRate > 0) draft.hourlyRate = template.hourlyRate;
                draft.payFrequency = template.payFrequency;
                draft.dateMillis = TaxEngine.toMillis(today);
                DailyPay d = new DailyPay(0, template.hours);
                d.isOvertime = template.isOvertime; d.unpaidBreakMinutes = template.unpaidBreakMinutes;
                d.travelPay = template.travelPay; d.bonus = template.bonus; d.shiftTag = template.shiftTag;
                draft.days.add(d);
                expanded[0] = true;
            }
            if (presetDay != null) {
                LocalDate pStart;
                if ("Monthly".equals(draft.payFrequency)) pStart = presetDay.withDayOfMonth(1);
                else if ("Bi-Weekly".equals(draft.payFrequency)) pStart = presetDay;
                else pStart = presetDay.with(DayOfWeek.MONDAY);
                draft.dateMillis = TaxEngine.toMillis(pStart);
                int idx = (int) (presetDay.toEpochDay() - pStart.toEpochDay());
                if (idx >= 0 && idx < expanded.length) { expanded[idx] = true; focusDay = idx; }
            }
        }
        rebuildGrid();
    }

    private int gridSize() {
        int n;
        if ("Weekly".equals(draft.payFrequency)) n = 7;
        else if ("Bi-Weekly".equals(draft.payFrequency)) n = 14;
        else {
            LocalDate start = TaxEngine.toDate(draft.dateMillis);
            n = (int) java.time.temporal.ChronoUnit.DAYS.between(start, start.plusMonths(1));
        }
        // Never drop logged days that sit beyond the nominal period (e.g. imported from iOS).
        for (DailyPay d : draft.days) if (!d.isEmpty() && d.dayIndex + 1 > n && d.dayIndex < 62) n = d.dayIndex + 1;
        return n;
    }

    private void rebuildGrid() {
        int n = gridSize();
        DailyPay[] g = new DailyPay[n];
        if (grid != null) for (int i = 0; i < Math.min(n, grid.length); i++) g[i] = grid[i];
        else for (DailyPay d : draft.days) if (d.dayIndex >= 0 && d.dayIndex < n) g[d.dayIndex] = d.copy();
        for (int i = 0; i < n; i++) if (g[i] == null) g[i] = new DailyPay(i, 0);
        grid = g;
    }

    /** Snapshot of the form as a PayEntry, for live maths and saving. */
    private PayEntry snapshot() {
        PayEntry e = draft.copy(false);
        e.days.clear();
        if (totals()) {
            double h = fHours != null ? Ui.parseHours(fHours.getText().toString()) : TaxEngine.totalHours(draft);
            // Keep the day-by-day breakdown (calendar, weekly chart) when the total hours are unchanged
            if (draft.days.size() > 1 && Math.abs(h - TaxEngine.totalHours(draft)) < 0.01) {
                for (DailyPay d : draft.days) e.days.add(d.copy());
            } else if (h > 0) e.days.add(new DailyPay(0, h));
            if (fGross != null) {
                e.actualGrossPay = Ui.parseOpt(fGross.getText().toString());
                e.actualTaxAmount = Ui.parseOpt(fTax.getText().toString());
                e.actualNiAmount = Ui.parseOpt(fNi.getText().toString());
                e.actualPensionAmount = Ui.parseOpt(fPension.getText().toString());
                e.actualExtraDeductions = Ui.parseOpt(fExtra.getText().toString());
                e.actualPayReceived = Ui.parseOpt(fNet.getText().toString());
            }
        } else {
            for (int i = 0; i < grid.length; i++) {
                if (!grid[i].isEmpty()) { DailyPay d = grid[i].copy(); d.dayIndex = i; e.days.add(d); }
            }
            e.actualGrossPay = null; e.actualTaxAmount = null; e.actualNiAmount = null;
            e.actualPensionAmount = null; e.actualExtraDeductions = null; e.actualPayReceived = null;
        }
        return e;
    }

    private void updateHero() {
        if (heroNet == null) return;
        PayEntry e = snapshot();
        double net = totals() && e.actualPayReceived == null && (e.actualGrossPay == null || e.actualGrossPay <= 0) ? 0 : TaxEngine.net(e);
        heroNet.setText(Glass.bigMoney(net, Theme.onHero()));
        heroLabel.setText(totals() && e.actualPayReceived != null ? "PAYSLIP NET PAY" : "ESTIMATED NET PAY");
        heroRange.setText(DashboardPage.rangeString(e) + " · " + Ui.money(TaxEngine.effectiveGross(e)) + " gross");
    }

    private void touch() { dirty = true; updateHero(); }

    @Override protected void build() {
        if (draft == null) init();
        heroNet = null;
        add(hero());
        if (isNew() || true) {
            final String[] opts = {"Track shifts", "Enter totals"};
            View seg = Ui.segmented(opts, totals() ? 1 : 0, new Ui.Fn<Integer>() {
                @Override public void on(Integer i) {
                    String m = i == 1 ? "Payslip Totals" : "Shift Tracker";
                    if (!m.equals(mode)) {
                        if (totals()) captureTotals();
                        mode = m;
                        dirty = true;
                        s.setTitle(title());
                        render(false);
                    }
                }
            });
            seg.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 6));
            add(seg);
        }
        periodSection();
        setupSection();
        if (totals()) totalsSection(); else dailySection();
        notesSection();
        if (!isNew()) {
            add(Ui.space(8));
            add(Ui.button("Delete pay period", "delete", Theme.withAlpha(Theme.red, 0.12f), Theme.red, new Runnable() {
                @Override public void run() {
                    Sheets.confirm(a, "Delete this pay period?", "This can't be undone after the undo prompt disappears.", "Delete", true, new Runnable() {
                        @Override public void run() {
                            PayEntry e = Store.find(editId);
                            a.popAll();
                            if (e != null) DashboardPage.delete(a, e);
                        }
                    });
                }
            }));
        }
        updateHero();
    }

    @Override public void refresh() {
        try { syncFields(); } catch (Throwable ignored) {}
        super.refresh();
    }

    private void captureTotals() {
        if (fGross == null) return;
        PayEntry e = snapshot();
        draft.actualGrossPay = e.actualGrossPay; draft.actualTaxAmount = e.actualTaxAmount;
        draft.actualNiAmount = e.actualNiAmount; draft.actualPensionAmount = e.actualPensionAmount;
        draft.actualExtraDeductions = e.actualExtraDeductions; draft.actualPayReceived = e.actualPayReceived;
        double h = Ui.parse(fHours.getText().toString());
        draft.days.clear();
        if (h > 0) draft.days.add(new DailyPay(0, h));
    }

    private View hero() {
        int on = Theme.onHero();
        LinearLayout c = Ui.v();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setBackground(new Glass.Hero(34));
        c.setPadding(Ui.dp(20), Ui.dp(22), Ui.dp(20), Ui.dp(22));
        heroLabel = Ui.text("ESTIMATED NET PAY", 12, 700, Theme.withAlpha(on, 0.85f));
        heroLabel.setLetterSpacing(0.06f);
        c.addView(heroLabel);
        heroNet = Ui.single(Ui.money(0), 44, 800, on);
        heroNet.setPadding(0, Ui.dp(4), 0, Ui.dp(2));
        heroNet.setGravity(Gravity.CENTER);
        Ui.autosize(heroNet, 24, 44);
        c.addView(heroNet);
        heroRange = Ui.text("", 13, 600, Theme.withAlpha(on, 0.85f));
        c.addView(heroRange);
        c.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 14));
        return c;
    }

    // ------------------------------------------------------------------ sections

    private void periodSection() {
        add(Ui.sectionTitle("Pay period"));
        LinearLayout g = Ui.group();
        final LocalDate start = TaxEngine.toDate(draft.dateMillis);
        Ui.row(g, "calendar_month", Theme.accent, "Period start", DashboardPage.rangeString(draft), start.format(DAY), true, new Runnable() {
            @Override public void run() {
                Sheets.date(a, start, new Ui.Fn<LocalDate>() {
                    @Override public void on(LocalDate d) { syncFields(); draft.dateMillis = TaxEngine.toMillis(d); rebuildGrid(); dirty = true; applyMinWage(); render(false); }
                });
            }
        });
        Ui.row(g, "work", Theme.indigo, "Job", null, draft.jobName.isEmpty() ? "Add" : draft.jobName, true, new Runnable() {
            @Override public void run() { pickJob(); }
        });
        Ui.row(g, "autorenew", Theme.blue, "Pay frequency", null, draft.payFrequency, true, new Runnable() {
            @Override public void run() {
                Sheets.choose(a, "Pay frequency", FREQS, java.util.Arrays.asList(FREQS).indexOf(draft.payFrequency), new Ui.Fn<Integer>() {
                    @Override public void on(Integer i) { syncFields(); draft.payFrequency = FREQS[i]; rebuildGrid(); dirty = true; render(false); }
                });
            }
        });
        add(g);
    }

    /** Pick one of your existing jobs (filling in its usual rate, tax code and pension) or type a new one. */
    private void pickJob() {
        final java.util.List<String> jobs = new java.util.ArrayList<String>();
        for (PayEntry e : Store.entries) {
            String j = e.jobName.trim();
            boolean dup = j.isEmpty();
            for (String x : jobs) if (x.equalsIgnoreCase(j)) dup = true;
            if (!dup) jobs.add(j);
        }
        if (jobs.isEmpty()) { typeJob(); return; }
        final String[] opts = new String[jobs.size() + 1];
        for (int i = 0; i < jobs.size(); i++) opts[i] = jobs.get(i);
        opts[jobs.size()] = "New job…";
        int sel = -1;
        for (int i = 0; i < jobs.size(); i++) if (jobs.get(i).equalsIgnoreCase(draft.jobName)) sel = i;
        Sheets.choose(a, "Job", opts, sel, new Ui.Fn<Integer>() {
            @Override public void on(Integer i) {
                if (i == jobs.size()) { typeJob(); return; }
                syncFields();
                draft.jobName = jobs.get(i);
                if (isNew()) {
                    PayEntry b = Shift.base(draft.jobName);
                    if (b.hourlyRate > 0) draft.hourlyRate = b.hourlyRate;
                    draft.taxCode = b.taxCode; draft.pensionRate = b.pensionRate;
                    draft.isSalarySacrifice = b.isSalarySacrifice; draft.pensionProvider = b.pensionProvider;
                    if (!b.payFrequency.equals(draft.payFrequency)) { draft.payFrequency = b.payFrequency; rebuildGrid(); }
                }
                dirty = true;
                render(false);
            }
        });
    }

    private void typeJob() {
        Sheets.input(a, "Job name", null, "e.g. Warehouse", draft.jobName, false, new Ui.Fn<String>() {
            @Override public void on(String v) { syncFields(); draft.jobName = v.trim(); dirty = true; render(false); }
        });
    }

    /** Persist current text-field values into the draft before a re-render. */
    private void syncFields() {
        if (totals()) captureTotals();
    }

    private void applyMinWage() {
        if (isNew() && Store.bool("autoTrackMinimumWage", false)) {
            draft.hourlyRate = TaxEngine.minimumWage(draft.age, TaxEngine.toDate(draft.dateMillis));
        }
    }

    private void setupSection() {
        add(Ui.sectionTitle("Pay setup"));
        LinearLayout g = Ui.group();
        if (!totals()) {
            if (!draft.isSalariedMode) {
                Ui.fieldRow(g, "currency_pound", Theme.green, "Hourly rate", Store.currency(),
                        draft.hourlyRate > 0 ? Ui.num(draft.hourlyRate) : "", "0.00", new Ui.Fn<String>() {
                    @Override public void on(String v) { draft.hourlyRate = Ui.parse(v); touch(); }
                });
            }
            Ui.switchRow(g, "account_balance_wallet", Theme.teal, "Fixed salary", "Use a set gross amount per period", draft.isSalariedMode, new Ui.Fn<Boolean>() {
                @Override public void on(Boolean v) { draft.isSalariedMode = v; dirty = true; render(false); }
            });
            if (draft.isSalariedMode) {
                Ui.fieldRow(g, "payments", Theme.green, "Gross per period", Store.currency(),
                        draft.fixedMonthlyGross > 0 ? Ui.num(draft.fixedMonthlyGross) : "", "0.00", new Ui.Fn<String>() {
                    @Override public void on(String v) { draft.fixedMonthlyGross = Ui.parse(v); touch(); }
                });
            }
        }
        Ui.row(g, "person", Theme.orange, "Age", "Pension auto-enrolment applies from 22", String.valueOf(draft.age), true, new Runnable() {
            @Override public void run() {
                Sheets.input(a, "Age", null, "25", String.valueOf(draft.age), true, new Ui.Fn<String>() {
                    @Override public void on(String v) { syncFields(); draft.age = (int) Math.max(16, Math.min(80, Ui.parse(v))); dirty = true; applyMinWage(); render(false); }
                });
            }
        });
        Ui.row(g, "account_balance", Theme.red, "Tax code", null, draft.taxCode, true, new Runnable() {
            @Override public void run() {
                Sheets.input(a, "Tax code", "Find this on your payslip or P45, e.g. 1257L, BR, 0T.", "1257L", draft.taxCode, false, new Ui.Fn<String>() {
                    @Override public void on(String v) { syncFields(); draft.taxCode = v.toUpperCase(Locale.UK).replace(" ", ""); dirty = true; render(false); }
                });
            }
        });
        Ui.fieldRow(g, "savings", Theme.purple, "Pension", "%", draft.pensionRate > 0 ? Ui.num(draft.pensionRate) : "", "0", new Ui.Fn<String>() {
            @Override public void on(String v) { draft.pensionRate = Ui.parse(v); touch(); }
        });
        Ui.switchRow(g, "content_copy", Theme.text2, "Salary sacrifice", "Pension taken before NI", draft.isSalarySacrifice, new Ui.Fn<Boolean>() {
            @Override public void on(Boolean v) { draft.isSalarySacrifice = v; touch(); }
        });
        add(g);
    }

    private TextWatcher watcher(final Runnable r) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { if (!suppress) r.run(); }
        };
    }

    private void totalsSection() {
        add(Ui.sectionTitle(isNew() ? "Payslip totals" : "Official figures"));
        LinearLayout g = Ui.group();
        String sym = Store.currency();
        Ui.Fn<String> noop = new Ui.Fn<String>() { @Override public void on(String v) {} };
        fHours = Ui.fieldRow(g, "schedule", Theme.accent, "Total hours", null, fmtOpt(TaxEngine.totalHours(draft)), "0", noop);
        fGross = Ui.fieldRow(g, "payments", Theme.green, "Gross pay", sym, fmtMoney(draft.actualGrossPay), "0.00", noop);
        fTax = Ui.fieldRow(g, "account_balance", Theme.red, "Income tax", sym, fmtMoney(draft.actualTaxAmount), "0.00", noop);
        fNi = Ui.fieldRow(g, "percent", Theme.orange, "National Insurance", sym, fmtMoney(draft.actualNiAmount), "0.00", noop);
        fPension = Ui.fieldRow(g, "savings", Theme.purple, "Pension", sym, fmtMoney(draft.actualPensionAmount), "0.00", noop);
        fExtra = Ui.fieldRow(g, "remove", Theme.pink, "Other deductions", sym, fmtMoney(draft.actualExtraDeductions), "0.00", noop);
        fNet = Ui.fieldRow(g, "account_balance_wallet", Theme.accent, "Net pay received", sym, fmtMoney(draft.actualPayReceived), "0.00", noop);
        add(g);
        TextView hint = Ui.text("Type your gross pay and we'll estimate tax, NI and pension — then overwrite any figure with the exact amount from your payslip.", 13, 400, Theme.text2);
        hint.setPadding(Ui.dp(10), Ui.dp(2), Ui.dp(10), Ui.dp(4));
        add(hint);

        fGross.addTextChangedListener(watcher(new Runnable() {
            @Override public void run() {
                double gross = Ui.parse(fGross.getText().toString());
                double pension = TaxEngine.pensionFor(gross, draft.pensionRate, draft.age);
                double tax = TaxEngine.taxFor(gross, pension, draft.taxCode, draft.payFrequency);
                double ni = TaxEngine.niFor(gross, pension, draft.isSalarySacrifice, draft.payFrequency);
                suppress = true;
                if (!taxEdited) fTax.setText(gross > 0 ? Ui.num(round2(tax)) : "");
                if (!niEdited) fNi.setText(gross > 0 ? Ui.num(round2(ni)) : "");
                if (!pensionEdited) fPension.setText(gross > 0 && pension > 0 ? Ui.num(round2(pension)) : "");
                suppress = false;
                recomputeNet();
            }
        }));
        Runnable deps = new Runnable() { @Override public void run() { recomputeNet(); } };
        // Existing payslip figures count as typed-in, so they're never replaced by estimates.
        taxEdited = draft.actualTaxAmount != null; niEdited = draft.actualNiAmount != null; pensionEdited = draft.actualPensionAmount != null;
        fTax.addTextChangedListener(watcher(new Runnable() { @Override public void run() { taxEdited = !fTax.getText().toString().trim().isEmpty(); recomputeNet(); } }));
        fNi.addTextChangedListener(watcher(new Runnable() { @Override public void run() { niEdited = !fNi.getText().toString().trim().isEmpty(); recomputeNet(); } }));
        fPension.addTextChangedListener(watcher(new Runnable() { @Override public void run() { pensionEdited = !fPension.getText().toString().trim().isEmpty(); recomputeNet(); } }));
        fExtra.addTextChangedListener(watcher(deps));
        Runnable just = new Runnable() { @Override public void run() { touch(); } };
        fNet.addTextChangedListener(watcher(just));
        fHours.addTextChangedListener(watcher(just));
    }

    private void recomputeNet() {
        double gross = Ui.parse(fGross.getText().toString());
        double net = gross - Ui.parse(fTax.getText().toString()) - Ui.parse(fNi.getText().toString())
                - Ui.parse(fPension.getText().toString()) - Ui.parse(fExtra.getText().toString())
                - TaxEngine.studentLoanFor(gross, draft.payFrequency);
        suppress = true;
        fNet.setText(gross > 0 ? Ui.num(round2(net)) : "");
        suppress = false;
        touch();
    }

    private static double round2(double v) { return Math.round(v * 100) / 100.0; }
    private static String fmtMoney(Double d) { return d == null ? "" : Ui.num(round2(d)); }
    private static String fmtOpt(double d) { return d > 0 ? Ui.num(d) : ""; }

    private PayEntry previousPeriod() {
        PayEntry best = null;
        for (PayEntry e : Store.entries) {
            if (editId != null && editId.equals(e.id)) continue;
            if (e.days.isEmpty() || e.isSalariedMode) continue;
            if (!draft.jobName.isEmpty() && !e.jobName.equalsIgnoreCase(draft.jobName)) continue;
            if (!e.payFrequency.equals(draft.payFrequency)) continue;
            if (e.dateMillis >= draft.dateMillis && !isNew()) continue;
            if (best == null || e.dateMillis > best.dateMillis) best = e;
        }
        return best;
    }

    private void copyPrevious(final PayEntry p) {
        final DailyPay[] before = new DailyPay[grid.length];
        for (int i = 0; i < grid.length; i++) before[i] = grid[i].copy();
        for (int i = 0; i < grid.length; i++) grid[i] = new DailyPay(i, 0);
        for (DailyPay d : p.days) {
            if (d.dayIndex >= 0 && d.dayIndex < grid.length) { DailyPay c = d.copy(); c.dayIndex = d.dayIndex; grid[d.dayIndex] = c; }
        }
        dirty = true;
        Ui.haptic(s);
        render(false);
        a.snack("Copied " + Ui.hours(TaxEngine.totalHours(p)) + " from " + DashboardPage.rangeString(p), "Undo", new Runnable() {
            @Override public void run() { if (before.length == grid.length) { grid = before; render(false); } }
        });
    }

    private void dailySection() {
        final PayEntry prev = previousPeriod();
        if (prev != null) {
            add(Ui.header("Daily log", "Copy last period", new Runnable() { @Override public void run() { copyPrevious(prev); } }));
        } else {
            add(Ui.sectionTitle("Daily log"));
        }
        LocalDate start = TaxEngine.toDate(draft.dateMillis);
        for (int i = 0; i < grid.length; i++) add(dayCard(i, start.plusDays(i)));
    }

    private View dayCard(final int i, LocalDate date) {
        final DailyPay d = grid[i];
        final LinearLayout card = Ui.v();
        card.setBackground(Glass.panel(22));
        card.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(8), Ui.dp(10));
        card.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));

        LinearLayout top = Ui.h();
        LinearLayout lbl = Ui.v();
        boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        lbl.addView(Ui.text(date.format(DateTimeFormatter.ofPattern("EEEE", Locale.UK)), 16, 650, Theme.text));
        lbl.addView(Ui.text(date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.UK)) + (weekend ? " · weekend" : ""), 12.5f, 450, Theme.text2));
        top.addView(lbl, Ui.weight(1));

        final TextView dayPay = Ui.text("", 13, 650, Theme.green);
        dayPay.setPadding(0, 0, Ui.dp(10), 0);
        top.addView(dayPay);

        LinearLayout pill = Ui.h();
        pill.setBackground(Ui.rrStroke(Theme.accentSoft, Theme.withAlpha(Theme.accent, 0.35f), 1, 14));
        pill.setPadding(Ui.dp(10), 0, Ui.dp(12), 0);
        final EditText hrs = new EditText(a);
        hrs.setBackground(null);
        hrs.setText(d.hoursWorked > 0 ? Ui.num(d.hoursWorked) : "");
        hrs.setHint("0");
        hrs.setHintTextColor(Theme.withAlpha(Theme.accent, 0.5f));
        hrs.setTextColor(Theme.accent);
        hrs.setTypeface(Ui.font(750));
        hrs.setTextSize(19);
        hrs.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        hrs.setSingleLine(true);
        hrs.setMinWidth(Ui.dp(44));
        hrs.setSelectAllOnFocus(true);
        hrs.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        hrs.setPadding(Ui.dp(4), Ui.dp(8), Ui.dp(4), Ui.dp(8));
        pill.addView(hrs);
        pill.addView(Ui.text("hrs", 13, 650, Theme.text2));
        top.addView(pill);

        // Clock button: pick start and end times instead of working out the hours
        android.widget.ImageView clock = Ui.iconButton("schedule", Theme.accent, new Runnable() {
            @Override public void run() { pickTimes(hrs); }
        });
        clock.setContentDescription("Enter start and end time");
        top.addView(clock);
        final android.widget.ImageView more = Ui.iconButton(expanded[i] ? "expand_more" : "tune", Theme.text2, null);
        more.setContentDescription("More options for this day");
        top.addView(more);
        card.addView(top);

        final LinearLayout extra = Ui.v();
        extra.setVisibility(expanded[i] ? View.VISIBLE : View.GONE);
        card.addView(extra);

        final Runnable updatePay = new Runnable() {
            @Override public void run() {
                double gp = TaxEngine.dayGross(d, draft.hourlyRate);
                dayPay.setText(gp > 0 && !draft.isSalariedMode ? Ui.money(gp, 0) : "");
            }
        };
        updatePay.run();
        hrs.addTextChangedListener(watcher(new Runnable() {
            @Override public void run() { d.hoursWorked = Ui.parseHours(hrs.getText().toString()); updatePay.run(); touch(); }
        }));
        if (i == focusDay) {
            focusDay = -1;
            card.post(new Runnable() { @Override public void run() {
                try { s.scroll.smoothScrollTo(0, Math.max(0, card.getTop() + ((View) card.getParent()).getTop() - Ui.dp(140))); } catch (Throwable ignored) {}
                hrs.requestFocus();
            } });
        }

        Ui.click(more, new Runnable() {
            @Override public void run() {
                expanded[i] = !expanded[i];
                if (expanded[i] && extra.getChildCount() == 0) buildExtras(extra, d, updatePay);
                extra.setVisibility(expanded[i] ? View.VISIBLE : View.GONE);
                more.setImageResource(Ui.iconRes(expanded[i] ? "expand_more" : "tune"));
                if (expanded[i]) { extra.setAlpha(0); extra.animate().alpha(1).setDuration(250).start(); }
            }
        });
        if (expanded[i]) buildExtras(extra, d, updatePay);
        return card;
    }

    /** Start → end time pickers; handles overnight shifts. Writes the hours into the field. */
    private void pickTimes(final EditText hrs) {
        final int[] start = {Store.integer("lastShiftStartMin", 9 * 60)};
        try {
            android.app.TimePickerDialog sp = new android.app.TimePickerDialog(a, new android.app.TimePickerDialog.OnTimeSetListener() {
                @Override public void onTimeSet(android.widget.TimePicker v, int h, int m) {
                    start[0] = h * 60 + m;
                    int endGuess = (start[0] + 8 * 60) % (24 * 60);
                    android.app.TimePickerDialog ep = new android.app.TimePickerDialog(a, new android.app.TimePickerDialog.OnTimeSetListener() {
                        @Override public void onTimeSet(android.widget.TimePicker v2, int h2, int m2) {
                            int end = h2 * 60 + m2;
                            int mins = end - start[0];
                            if (mins <= 0) mins += 24 * 60;   // overnight
                            Store.put("lastShiftStartMin", start[0]);
                            hrs.setText(Ui.num(Math.round(mins / 60.0 * 100) / 100.0));
                            a.snack(String.format(Locale.UK, "%02d:%02d – %02d:%02d · %s", start[0] / 60, start[0] % 60, h2, m2, Ui.hours(mins / 60.0)));
                        }
                    }, endGuess / 60, endGuess % 60, true);
                    ep.setTitle("Finish time");
                    ep.show();
                }
            }, start[0] / 60, start[0] % 60, true);
            sp.setTitle("Start time");
            sp.show();
        } catch (Throwable t) { a.snack("Couldn't open the time picker"); }
    }

    private void buildExtras(LinearLayout extra, final DailyPay d, final Runnable updatePay) {
        extra.setPadding(0, Ui.dp(6), Ui.dp(8), Ui.dp(4));
        LinearLayout g = Ui.v();
        Ui.switchRow(g, "bolt", Theme.orange, "Overtime (1.5×)", null, d.isOvertime, new Ui.Fn<Boolean>() {
            @Override public void on(Boolean v) { d.isOvertime = v; updatePay.run(); touch(); }
        });
        // Unpaid break stepper
        g.addView(Ui.divider(72));
        LinearLayout br = Ui.h();
        br.setPadding(Ui.dp(18), Ui.dp(10), Ui.dp(6), Ui.dp(10));
        br.addView(Ui.badge("pause_circle", Theme.indigo, 38));
        br.addView(Ui.space(16));
        br.addView(Ui.text("Unpaid break", 16.5f, 500, Theme.text), Ui.weight(1));
        final TextView mins = Ui.text(Math.round(d.unpaidBreakMinutes) + " min", 15, 650, Theme.text2);
        br.addView(Ui.iconButton("remove", Theme.accent, new Runnable() {
            @Override public void run() { d.unpaidBreakMinutes = Math.max(0, d.unpaidBreakMinutes - 15); mins.setText(Math.round(d.unpaidBreakMinutes) + " min"); updatePay.run(); touch(); }
        }));
        mins.setMinWidth(Ui.dp(58));
        mins.setGravity(Gravity.CENTER);
        br.addView(mins);
        br.addView(Ui.iconButton("add", Theme.accent, new Runnable() {
            @Override public void run() { d.unpaidBreakMinutes = Math.min(240, d.unpaidBreakMinutes + 15); mins.setText(Math.round(d.unpaidBreakMinutes) + " min"); updatePay.run(); touch(); }
        }));
        g.addView(br);
        String sym = Store.currency();
        Ui.fieldRow(g, "directions_car", Theme.cyan, "Travel pay", sym, d.travelPay > 0 ? Ui.num(d.travelPay) : "", "0.00", new Ui.Fn<String>() {
            @Override public void on(String v) { d.travelPay = Ui.parse(v); updatePay.run(); touch(); }
        });
        Ui.fieldRow(g, "redeem", Theme.green, "Bonus", sym, d.bonus > 0 ? Ui.num(d.bonus) : "", "0.00", new Ui.Fn<String>() {
            @Override public void on(String v) { d.bonus = Ui.parse(v); updatePay.run(); touch(); }
        });
        Ui.fieldRow(g, "nights_stay", Theme.purple, "Premium hours (" + Ui.num(d.premiumMultiplier) + "×)", null,
                d.premiumHours > 0 ? Ui.num(d.premiumHours) : "", "0", new Ui.Fn<String>() {
            @Override public void on(String v) { d.premiumHours = Ui.parse(v); updatePay.run(); touch(); }
        });
        extra.addView(g);
    }

    private void notesSection() {
        add(Ui.sectionTitle("Notes"));
        final EditText n = Ui.field("Add a note (e.g. overtime reason, holiday pay)…", draft.notes, false);
        n.setSingleLine(false);
        n.setMinLines(2);
        n.setMaxLines(6);
        n.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        n.setBackground(Glass.panel(22));
        n.setPadding(Ui.dp(18), Ui.dp(16), Ui.dp(18), Ui.dp(16));
        n.setGravity(Gravity.TOP);
        n.addTextChangedListener(watcher(new Runnable() { @Override public void run() { draft.notes = n.getText().toString(); dirty = true; } }));
        add(n);
    }

    // ------------------------------------------------------------------ save

    private void save() {
        PayEntry e = snapshot();
        if (!totals()) {
            for (DailyPay d : e.days) {
                if (d.hoursWorked > 16) {
                    Sheets.confirm(a, "Over 16 hours on one day?", "One day has " + Ui.hours(d.hoursWorked) + ". Save anyway, or go back and check for a typo.", "Save anyway", false, new Runnable() {
                        @Override public void run() { doSave(); }
                    });
                    return;
                }
            }
        }
        doSave();
    }

    private void doSave() {
        PayEntry e = snapshot();
        if (totals()) {
            if (e.actualGrossPay == null && e.actualPayReceived == null) { a.snack("Enter at least your gross or net pay"); return; }
            if (e.actualPayReceived == null && e.actualGrossPay != null) e.actualPayReceived = TaxEngine.computedNet(e);
        } else if (e.days.isEmpty() && !(e.isSalariedMode && e.fixedMonthlyGross > 0)) {
            a.snack("Add some hours to at least one day");
            return;
        }
        if (e.jobName.trim().isEmpty()) e.jobName = "Main Job";
        if (isNew()) {
            Store.entries.add(e);
        } else {
            PayEntry old = Store.find(editId);
            int idx = old != null ? Store.entries.indexOf(old) : -1;
            if (idx >= 0) Store.entries.set(idx, e); else Store.entries.add(e);
        }
        a.hideKeyboard(s);
        Store.changed();
        Ui.confirmHaptic(s);
        a.popAll();
        String msg = (isNew() ? "Saved · " : "Updated · ") + Ui.money(TaxEngine.net(e)) + " take-home";
        double nmw = TaxEngine.minimumWage(e.age, TaxEngine.toDate(e.dateMillis));
        if (!totals() && !e.isSalariedMode && e.hourlyRate > 0 && nmw > 0 && e.hourlyRate < nmw - 0.004) {
            msg = "Saved. Your " + Ui.money(e.hourlyRate) + "/h is below the " + Ui.money(nmw) + " minimum wage for your age";
        }
        a.snack(msg);
    }

    @Override public boolean hasUnsavedChanges() { return dirty; }

    @Override public boolean onBack() {
        if (!dirty) return false;
        Sheets.confirm(a, "Discard changes?", "You have unsaved changes to this pay period.", "Discard", true, new Runnable() {
            @Override public void run() { dirty = false; a.pop(); }
        });
        return true;
    }
}
