package com.dayoadekoya.paycalculator;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** One UI style grouped settings — port of iOS SettingsView. */
public class SettingsPage extends Page {
    public static final String VERSION = "7.0 (Android)";

    @Override public String title() { return "Settings"; }

    private static int idx(String[] arr, String v) {
        int i = Arrays.asList(arr).indexOf(v);
        return Math.max(0, i);
    }

    private void choose(String title, final String key, final String[] opts, String def, final Runnable after) {
        Sheets.choose(a, title, opts, Arrays.asList(opts).indexOf(Store.str(key, def)), new Ui.Fn<Integer>() {
            @Override public void on(Integer i) { Store.put(key, opts[i]); Store.syncConfig(); if (after != null) after.run(); else changed(); }
        });
    }

    private void number(String title, String msg, final String key, double def, final Runnable after) {
        Sheets.input(a, title, msg, Ui.num(def), Ui.num(Store.dbl(key, def)), true, new Ui.Fn<String>() {
            @Override public void on(String v) { Store.put(key, Ui.parse(v)); Store.syncConfig(); if (after != null) after.run(); else changed(); }
        });
    }

    private void changed() { Store.changed(); render(false); }

    @Override protected void build() {
        add(span(profileCard()));
        meshSection();

        // You
        add(Ui.sectionTitle("You"));
        LinearLayout you = Ui.group();
        String nm = Store.str("userName", "");
        Ui.row(you, "person", Theme.blue, "Your name", "Used in the Home greeting", nm.isEmpty() ? "Add" : nm, true, new Runnable() {
            @Override public void run() {
                Sheets.input(a, "Your name", "Shown as “Good morning, Name” on Home.", "e.g. Dayo", Store.str("userName", ""), false, new Ui.Fn<String>() {
                    @Override public void on(String v) { Store.put("userName", v.trim()); a.refreshAll(); render(false); }
                });
            }
        });
        Ui.switchRow(you, "visibility_off", Theme.indigo, "Privacy mode", "Hide amounts in the app and widgets", Store.bool("privacyMode", false), new Ui.Fn<Boolean>() {
            @Override public void on(Boolean v) {
                Store.put("privacyMode", v);
                a.refreshAll();
                try { Widgets.updateAll(a); } catch (Throwable ignored) {}
                render(false);
            }
        });
        Ui.switchRow(you, "mobile_vibrate", Theme.purple, "Haptic feedback", "Subtle vibration on taps and confirmations", Store.bool("haptics", true), new Ui.Fn<Boolean>() {
            @Override public void on(Boolean v) { Store.put("haptics", v); }
        });
        add(you);

        // Appearance
        add(Ui.sectionTitle("Appearance"));
        LinearLayout g = Ui.group();
        final String themeName = Store.str("appThemeColor", "Blue");
        Ui.row(g, "palette", Theme.accent, "Theme colour", "Dynamic follows your wallpaper (Material You)", themeName.equals("Dynamic") ? "Dynamic" : themeName, true, new Runnable() {
            @Override public void run() { themePicker(); }
        });
        final String[] modes = {"System", "Light", "Dark"};
        Ui.row(g, "contrast", Theme.indigo, "Appearance", "Dark uses true AMOLED black", Store.str("appearanceMode", "System"), true, new Runnable() {
            @Override public void run() {
                Sheets.choose(a, "Appearance", modes, idx(modes, Store.str("appearanceMode", "System")), new Ui.Fn<Integer>() {
                    @Override public void on(Integer i) { Store.put("appearanceMode", modes[i]); a.applyThemeChange(); }
                });
            }
        });
        final String[] cur = {"£", "$", "€", "¥", "₹", "Fr"};
        Ui.row(g, "currency_pound", Theme.green, "Currency symbol", null, Store.currency(), true, new Runnable() {
            @Override public void run() { choose("Currency symbol", "currencySymbol", cur, "£", null); }
        });
        final String[] tfs = {"Tax Year", "This Month", "All Time"};
        Ui.row(g, "pie_chart", Theme.cyan, "Dashboard range", null, Store.str("dashboardTimeframe", "Tax Year"), true, new Runnable() {
            @Override public void run() { choose("Dashboard range", "dashboardTimeframe", tfs, "Tax Year", null); }
        });
        Ui.row(g, "flag", Theme.pink, "Monthly target", "Shown as the goal ring on Home", Ui.money(Store.dbl("monthlyEarningsGoal", 2000), 0), true, new Runnable() {
            @Override public void run() { number("Monthly target", "Your take-home goal for each month.", "monthlyEarningsGoal", 2000, null); }
        });
        add(g);

        // Tax engine
        add(Ui.sectionTitle("Tax configuration"));
        LinearLayout t = Ui.group();
        final List<Integer> years = TaxEngine.availableTaxYears(com.dayoadekoya.paycalculator.Store.entries, LocalDate.now());
        final String[] yearOpts = new String[years.size() + 1];
        yearOpts[0] = "Current (" + TaxEngine.taxYearLabel(TaxEngine.taxYearStart(LocalDate.now())) + ")";
        for (int i = 0; i < years.size(); i++) yearOpts[i + 1] = "Tax year " + TaxEngine.taxYearLabel(years.get(i));
        String sel = Store.str("selectedTaxYear", "Current");
        Ui.row(t, "event", Theme.purple, "Active tax year", "Filters Home and Insights", "Current".equals(sel) ? "Current" : sel, true, new Runnable() {
            @Override public void run() {
                String cur = Store.str("selectedTaxYear", "Current");
                int si = 0;
                for (int i = 0; i < years.size(); i++) if (TaxEngine.taxYearLabel(years.get(i)).equals(cur)) si = i + 1;
                Sheets.choose(a, "Active tax year", yearOpts, si, new Ui.Fn<Integer>() {
                    @Override public void on(Integer i) { Store.put("selectedTaxYear", i == 0 ? "Current" : TaxEngine.taxYearLabel(years.get(i - 1))); changed(); }
                });
            }
        });
        final String[] calc = {"HMRC Bands", "Flat %"};
        Ui.row(t, "calculate", Theme.purple, "Calculation method", null, "Flat %".equals(Store.str("taxCalculationMode", "HMRC Bands")) ? "Flat percentage" : "HMRC tax bands", true, new Runnable() {
            @Override public void run() {
                Sheets.choose(a, "Calculation method", new String[]{"HMRC tax bands", "Flat percentage (%)"}, idx(calc, Store.str("taxCalculationMode", "HMRC Bands")), new Ui.Fn<Integer>() {
                    @Override public void on(Integer i) { Store.put("taxCalculationMode", calc[i]); Store.syncConfig(); changed(); }
                });
            }
        });
        if ("Flat %".equals(Store.str("taxCalculationMode", "HMRC Bands"))) {
            Ui.row(t, "percent", Theme.red, "Tax rate", null, Ui.num(Store.dbl("flatTaxRate", 20)) + "%", true, new Runnable() {
                @Override public void run() { number("Flat tax rate (%)", null, "flatTaxRate", 20, null); }
            });
            Ui.row(t, "percent", Theme.orange, "NI rate", null, Ui.num(Store.dbl("flatNIRate", 8)) + "%", true, new Runnable() {
                @Override public void run() { number("Flat NI rate (%)", null, "flatNIRate", 8, null); }
            });
        }
        final String[] plans = {"None", "Plan 1", "Plan 2", "Plan 4", "Plan 5", "Postgraduate"};
        Ui.row(t, "school", Theme.blue, "Student loan", null, Store.str("studentLoanPlan", "None"), true, new Runnable() {
            @Override public void run() { choose("Student loan", "studentLoanPlan", plans, "None", null); }
        });
        add(t);

        // Defaults
        add(Ui.sectionTitle("Default shift settings"));
        LinearLayout d = Ui.group();
        Ui.switchRow(d, "trending_up", Theme.green, "Auto-track minimum wage", "Uses the UK rate for your age", Store.bool("autoTrackMinimumWage", false), new Ui.Fn<Boolean>() {
            @Override public void on(Boolean v) { Store.put("autoTrackMinimumWage", v); render(false); }
        });
        if (!Store.bool("autoTrackMinimumWage", false)) {
            double r = Store.dbl("defaultHourlyRate", 0);
            Ui.row(d, "currency_pound", Theme.accent, "Hourly rate", null, r > 0 ? Ui.money(r) : "Not set", true, new Runnable() {
                @Override public void run() { number("Default hourly rate", null, "defaultHourlyRate", 0, new Runnable() { @Override public void run() { render(false); } }); }
            });
        }
        final String[] freqs = {"Weekly", "Bi-Weekly", "Monthly"};
        Ui.row(d, "autorenew", Theme.blue, "Pay frequency", null, Store.str("payFrequency", "Weekly"), true, new Runnable() {
            @Override public void run() { choose("Pay frequency", "payFrequency", freqs, "Weekly", null); }
        });
        Ui.row(d, "person", Theme.orange, "Age", null, String.valueOf(Store.integer("defaultAge", 25)), true, new Runnable() {
            @Override public void run() {
                Sheets.input(a, "Age", "Used for pension auto-enrolment and minimum wage.", "25", String.valueOf(Store.integer("defaultAge", 25)), true, new Ui.Fn<String>() {
                    @Override public void on(String v) { Store.put("defaultAge", (int) Math.max(16, Math.min(80, Ui.parse(v)))); render(false); }
                });
            }
        });
        Ui.switchRow(d, "event", Theme.pink, "Set my next payday", "Otherwise it's predicted from your records", Store.bool("useManualPayday", false), new Ui.Fn<Boolean>() {
            @Override public void on(Boolean v) {
                Store.put("useManualPayday", v);
                if (v && Store.integer("nextPaydayEpochDay", 0) == 0) Store.put("nextPaydayEpochDay", (int) LocalDate.now().toEpochDay());
                changed();
            }
        });
        if (Store.bool("useManualPayday", false)) {
            final LocalDate anchor = Payday.anchor() != null ? Payday.anchor() : LocalDate.now();
            Ui.row(d, "payments", Theme.green, "Next payday", null, anchor.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)), true, new Runnable() {
                @Override public void run() {
                    Sheets.date(a, anchor, new Ui.Fn<LocalDate>() {
                        @Override public void on(LocalDate v) { Store.put("nextPaydayEpochDay", (int) v.toEpochDay()); changed(); }
                    });
                }
            });
            List<LocalDate> sched = Payday.schedule(3);
            StringBuilder sb = new StringBuilder();
            for (LocalDate x : sched) { if (sb.length() > 0) sb.append("  ·  "); sb.append(x.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK))); }
            Ui.row(d, "calendar_month", Theme.text2, "Upcoming", sb.toString(), null, false, null);
        }
        add(d);

        add(Ui.sectionTitle("Tax & pension defaults"));
        LinearLayout p = Ui.group();
        Ui.row(p, "account_balance", Theme.red, "Tax code", null, Store.str("defaultTaxCode", "1257L"), true, new Runnable() {
            @Override public void run() {
                Sheets.input(a, "Tax code", "Find this on your payslip or P45.", "1257L", Store.str("defaultTaxCode", "1257L"), false, new Ui.Fn<String>() {
                    @Override public void on(String v) { Store.put("defaultTaxCode", v.toUpperCase(Locale.UK).replace(" ", "")); render(false); }
                });
            }
        });
        String prov = Store.str("defaultPensionProvider", "");
        Ui.row(p, "work", Theme.text2, "Pension provider", null, prov.isEmpty() ? "e.g. NEST" : prov, true, new Runnable() {
            @Override public void run() {
                Sheets.input(a, "Pension provider", null, "e.g. NEST", Store.str("defaultPensionProvider", ""), false, new Ui.Fn<String>() {
                    @Override public void on(String v) { Store.put("defaultPensionProvider", v); render(false); }
                });
            }
        });
        Ui.row(p, "savings", Theme.green, "Pension rate", null, Ui.num(Store.dbl("defaultPensionRate", 5)) + "%", true, new Runnable() {
            @Override public void run() { number("Pension rate (%)", "Employee contribution. Auto-enrolment minimum is 5%.", "defaultPensionRate", 5, new Runnable() { @Override public void run() { render(false); } }); }
        });
        Ui.switchRow(p, "content_copy", Theme.text2, "Salary sacrifice", null, Store.bool("defaultIsSalarySacrifice", false), new Ui.Fn<Boolean>() {
            @Override public void on(Boolean v) { Store.put("defaultIsSalarySacrifice", v); }
        });
        add(p);

        // Tools
        add(Ui.sectionTitle("Tools"));
        LinearLayout tl = Ui.group();
        Ui.row(tl, "layers", Theme.indigo, "Shift templates", Store.templates.size() + " saved", null, true, new Runnable() {
            @Override public void run() { a.push(new TemplatesPage()); }
        });
        Ui.row(tl, "event_repeat", Theme.teal, "Subscriptions & bills", Store.bills.isEmpty() ? "Track recurring payments and trials" : Store.bills.size() + " payments · " + Ui.money(BillsPage.total(), 0) + "/month", null, true, new Runnable() {
            @Override public void run() { a.showTab(MainActivity.TAB_SUBS, true); }
        });
        Ui.row(tl, "timer", Theme.green, Shift.running() ? "Shift timer · running" : "Shift timer", Shift.running() ? "Started " + Shift.clock(Shift.elapsedMs()) + " ago" : "Clock in and out from Home", null, true, new Runnable() {
            @Override public void run() { a.showTab(0, true); }
        });
        Ui.row(tl, "receipt_long", Theme.pink, "Work expenses & mileage", "Estimate tax relief you can claim", null, true, new Runnable() {
            @Override public void run() { a.push(new ExpensesPage()); }
        });
        Ui.row(tl, "donut_large", Theme.accent, "Tax year summary", null, null, true, new Runnable() {
            @Override public void run() { a.push(new TaxSummaryPage()); }
        });
        add(tl);

        // Security
        add(Ui.sectionTitle("Security & reminders"));
        LinearLayout sr = Ui.group();
        Ui.switchRow(sr, "fingerprint", Theme.accent, "App lock", "Fingerprint, face or screen lock", Store.bool("requireBiometrics", false), new Ui.Fn<Boolean>() {
            @Override public void on(Boolean v) { Store.put("requireBiometrics", v); render(false); }
        });
        if (Store.bool("requireBiometrics", false)) {
            final String[] graceOpts = {"Immediately", "After 1 minute", "After 5 minutes", "After 15 minutes"};
            final int[] graceSecs = {0, 60, 300, 900};
            int gs = Store.integer("lockGraceSeconds", 0);
            int gi = 0;
            for (int i = 0; i < graceSecs.length; i++) if (graceSecs[i] == gs) gi = i;
            final int fgi = gi;
            Ui.row(sr, "lock_clock", Theme.text2, "Lock when away", null, graceOpts[gi], true, new Runnable() {
                @Override public void run() {
                    Sheets.choose(a, "Lock the app", graceOpts, fgi, new Ui.Fn<Integer>() {
                        @Override public void on(Integer i) { Store.put("lockGraceSeconds", graceSecs[i]); render(false); }
                    });
                }
            });
        }
        Ui.switchRow(sr, "notifications", Theme.orange, "Friday shift reminder", "Fridays at 5pm", Store.bool("enableFridayReminder", false), new Ui.Fn<Boolean>() {
            @Override public void on(Boolean v) { Store.put("enableFridayReminder", v); if (v) a.requestNotificationPermission(); Reminders.reschedule(a); }
        });
        Ui.switchRow(sr, "payments", Theme.green, "Payday reminder", "Morning of your next payday", Store.bool("enablePaydayReminder", false), new Ui.Fn<Boolean>() {
            @Override public void on(Boolean v) { Store.put("enablePaydayReminder", v); if (v) a.requestNotificationPermission(); Reminders.reschedule(a); }
        });
        BillsPage.reminderRows(a, sr, new Runnable() { @Override public void run() { render(false); } });
        add(sr);

        // Backup
        add(Ui.sectionTitle("Backup & transfer"));
        LinearLayout b = Ui.group();
        long lastBk = (long) Store.dbl("lastBackupMillis", 0);
        String bkSub = lastBk <= 0 ? "Never backed up · same format as the iPhone app"
                : "Last backup " + java.time.Instant.ofEpochMilli(lastBk).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)) + " · works on iPhone too";
        Ui.row(b, "upload", Theme.accent, "Export backup", bkSub, null, true, new Runnable() {
            @Override public void run() { a.startExport(MainActivity.REQ_EXPORT_JSON, "application/json", "PayCalculator_SyncData.json"); }
        });
        Ui.row(b, "download", Theme.purple, "Import backup", "Merge shifts from an iPhone or Android export", null, true, new Runnable() {
            @Override public void run() { a.startImport(); }
        });
        Ui.row(b, "ios_share", Theme.green, "Export statement", "PDF statement or CSV spreadsheet", null, true, new Runnable() {
            @Override public void run() { Exporter.chooser(a); }
        });
        add(b);
        TextView foot = Ui.text("To move from iPhone: in the iOS app open Settings › Export Database, send the file to this phone, then tap Import backup.", 13, 400, Theme.text2);
        foot.setPadding(Ui.dp(10), 0, Ui.dp(10), Ui.dp(4));
        add(follow(foot));

        // Updates (from the GitHub releases page)
        add(Ui.sectionTitle("Updates"));
        LinearLayout up = Ui.group();
        Ui.row(up, "autorenew", Theme.green, "Check for updates", "You have version " + Updater.currentVersion(a), null, true, new Runnable() {
            @Override public void run() { Updater.check(a, true); }
        });
        Ui.switchRow(up, "history", Theme.blue, "Check automatically", "Looks for a new version when you open the app", Store.bool("autoUpdateCheck", true), new Ui.Fn<Boolean>() {
            @Override public void on(Boolean v) { Store.put("autoUpdateCheck", v); }
        });
        add(up);

        // Help
        add(Ui.sectionTitle("Help"));
        LinearLayout h = Ui.group();
        Ui.row(h, "auto_awesome", Theme.purple, "Welcome tour", null, null, true, new Runnable() { @Override public void run() { Welcome.show(a); } });
        Ui.row(h, "info", Theme.accent, "About", null, "v" + VERSION, true, new Runnable() {
            @Override public void run() {
                Sheets.info(a, "Pay Calculator", "UK take-home pay for shift workers.\n\nVersion " + VERSION + "\nTax year " + TaxEngine.taxYearLabel(TaxEngine.taxYearStart(LocalDate.now()))
                        + " · HMRC bands, Class 1 NI, pensions and student loans.\n\nYour pay data stays on this device. The app only goes online to sync with your iPhone on Wi-Fi and to check GitHub for updates. Figures are estimates — always check your payslip.");
            }
        });
        Ui.row(h, "delete", Theme.red, "Erase all data", null, null, false, new Runnable() {
            @Override public void run() {
                Sheets.confirm(a, "Erase all data?", "This permanently deletes every pay period, template, expense and bill on this phone. Export a backup first if you might need it.", "Erase", true, new Runnable() {
                    @Override public void run() {
                        Store.entries.clear(); Store.templates.clear(); Store.expenses.clear(); Store.bills.clear();
                        if (Shift.running()) Shift.cancel(a);
                        Store.put("recentSearches", "");
                        changed();
                        a.snack("All data erased");
                    }
                });
            }
        });
        add(h);

        TextView ver = Ui.text("Pay Calculator · v" + VERSION, 12.5f, 500, Theme.text3);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, Ui.dp(16), 0, 0);
        add(span(ver));
    }

    private void meshSection() {
        add(Ui.sectionTitle("Nearby device sync"));
        LinearLayout g = Ui.group();
        Ui.switchRow(g, "sync", Theme.accent, "Sync with nearby devices", null, MeshSync.enabled(), new Ui.Fn<Boolean>() {
            @Override public void on(Boolean v) { MeshSync.setEnabled(a, v); render(false); }
        });
        if (MeshSync.enabled()) {
            final String code = MeshSync.code();
            Ui.row(g, "lock", Theme.text2, "Pairing code", null, code.isEmpty() ? "Choose a code" : code, true, new Runnable() {
                @Override public void run() {
                    Sheets.input(a, "Pairing code", "Type exactly the same code as on your iPhone or iPad (it's case-sensitive).",
                            "e.g. DAYO2026", MeshSync.code(), false, new Ui.Fn<String>() {
                        @Override public void on(String v) { MeshSync.setCode(a, v); render(false); }
                    });
                }
            });
            if (code.isEmpty()) {
                Ui.row(g, "info", Theme.orange, "Pick any code (e.g. a word or PIN) and type the exact same one on each of your devices. They'll pair automatically.", null, null, false, null);
            } else {
                Ui.row(g, "info", Theme.text2, "To pair another device, open Nearby Device Sync on it and type the same code: " + code + ". Devices with a different code won't sync.", null, null, false, null);
            }
            int n = MeshSync.connectedCount();
            TextView cv = Ui.row(g, "sync", n > 0 ? Theme.green : Theme.text2, "Connected devices",
                    MeshSync.status.isEmpty() ? null : MeshSync.status, String.valueOf(n), false, null);
            if (cv != null) { cv.setTextColor(n > 0 ? Theme.green : Theme.text2); cv.setTypeface(Ui.font(800)); }
            Ui.row(g, "autorenew", n > 0 ? Theme.accent : Theme.text3, MeshSync.syncing ? "Syncing…" : "Sync now",
                    MeshSync.lastReceived > 0 ? "Received " + MeshSync.lastReceived + " new or updated pay period" + (MeshSync.lastReceived == 1 ? "" : "s") : null,
                    null, false, new Runnable() {
                @Override public void run() {
                    if (MeshSync.connectedCount() == 0) a.snack("No devices connected yet");
                    else { MeshSync.broadcast(); a.snack("Sent to " + MeshSync.connectedCount() + " device" + (MeshSync.connectedCount() == 1 ? "" : "s")); }
                }
            });
        }
        add(g);
        TextView foot = Ui.text("Sync directly with Pay Calculator on your iPhone or iPad over the same Wi-Fi network, with no account or internet needed. Only devices using the same pairing code accept each other's data. Keep the app open on both devices while they sync. Deleted pay periods aren't removed from the other device.", 13, 400, Theme.text2);
        foot.setPadding(Ui.dp(10), 0, Ui.dp(10), Ui.dp(4));
        add(follow(foot));
    }

    private View profileCard() {
        LinearLayout c = Ui.card();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(Ui.dp(20), Ui.dp(24), Ui.dp(20), Ui.dp(22));
        ImageView icon = new ImageView(a);
        icon.setImageResource(R.mipmap.ic_launcher);
        c.addView(icon, new LinearLayout.LayoutParams(Ui.dp(72), Ui.dp(72)));
        TextView n = Ui.text("Pay Calculator", 21, 700, Theme.text);
        n.setPadding(0, Ui.dp(12), 0, Ui.dp(2));
        c.addView(n);
        c.addView(Ui.text("Tax year " + TaxEngine.taxYearLabel(TaxEngine.taxYearStart(LocalDate.now())) + " · UK edition", 13.5f, 500, Theme.text2));
        LinearLayout chips = Ui.h();
        chips.setPadding(0, Ui.dp(14), 0, 0);
        int n2 = Store.entries.size();
        chips.addView(Ui.chip("work", n2 + (n2 == 1 ? " pay period" : " pay periods"), Theme.accent));
        chips.addView(Ui.space(8));
        chips.addView(Ui.chip("lock", "On-device vault", Theme.green));
        c.addView(chips);
        return c;
    }

    private void themePicker() {
        final Sheets.Handle h = Sheets.sheet(a, "Theme colour", "Dynamic picks up the colours of your wallpaper, just like the rest of Android.");
        LinearLayout grid = Ui.v();
        LinearLayout row = null;
        String current = Store.str("appThemeColor", "Blue");
        for (int i = 0; i < Theme.THEMES.length; i++) {
            if (i % 4 == 0) { row = Ui.h(); grid.addView(row, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 14)); }
            final String name = Theme.THEMES[i];
            LinearLayout item = Ui.v();
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout sw = new FrameLayout(a);
            int col = Theme.namedColor(a, name);
            boolean sel = name.equals(current);
            if (name.startsWith("Night")) sw.setBackground(Ui.rrStroke(0xFF000000, 0xFF555555, 1.5f, 20));
            else sw.setBackground(Ui.rr(col, 20));
            if (sel) {
                FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(Ui.dp(24), Ui.dp(24), Gravity.CENTER);
                sw.addView(Ui.icon("check", name.startsWith("Night") ? 0xFFFFFFFF : (Theme.isLight(col) ? 0xFF111111 : 0xFFFFFFFF), 24), ip);
            }
            if (name.equals("Dynamic")) {
                FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(Ui.dp(22), Ui.dp(22), Gravity.CENTER);
                if (!sel) sw.addView(Ui.icon("auto_awesome", Theme.isLight(col) ? 0xFF111111 : 0xFFFFFFFF, 22), ip);
            }
            item.addView(sw, new LinearLayout.LayoutParams(Ui.dp(56), Ui.dp(56)));
            TextView t = Ui.text(name.startsWith("Night") ? "OLED" : name, 12.5f, sel ? 750 : 500, sel ? Theme.text : Theme.text2);
            t.setPadding(0, Ui.dp(6), 0, 0);
            item.addView(t);
            row.addView(item, Ui.weight(1));
            Ui.press(item);
            Ui.click(item, new Runnable() {
                @Override public void run() { h.dismiss(); Store.put("appThemeColor", name); a.applyThemeChange(); }
            });
        }
        h.body.addView(grid);
        Sheets.show(h);
    }
}
