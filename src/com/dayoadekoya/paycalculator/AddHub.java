package com.dayoadekoya.paycalculator;

import com.dayoadekoya.paycalculator.Model.ShiftTemplate;

/** "Add pay" chooser sheet — Android counterpart of the iOS AddMethodHub. */
public final class AddHub {
    private AddHub() {}

    public static void show(final MainActivity a) {
        final Sheets.Handle h = Sheets.sheet(a, "Add pay", "Log a shift by hand, enter your payslip figures, or bring records over from your iPhone.");
        h.body.addView(Sheets.optionCard("schedule", Theme.accent, "Track shifts", "Log hours day by day — overtime, breaks, travel and bonuses", new Runnable() {
            @Override public void run() { h.dismiss(); a.push(new EntryEditorPage(null, "Shift Tracker")); }
        }));
        h.body.addView(Sheets.optionCard("receipt_long", Theme.purple, "Enter payslip totals", "Type the gross, tax, NI and net from your payslip", new Runnable() {
            @Override public void run() { h.dismiss(); a.push(new EntryEditorPage(null, "Payslip Totals")); }
        }));
        if (!Store.templates.isEmpty()) {
            h.body.addView(Sheets.optionCard("layers", Theme.indigo, "From a template", Store.templates.size() + " saved shift" + (Store.templates.size() == 1 ? "" : "s"), new Runnable() {
                @Override public void run() {
                    h.dismiss();
                    String[] names = new String[Store.templates.size()];
                    for (int i = 0; i < names.length; i++) names[i] = Store.templates.get(i).name;
                    Sheets.choose(a, "Log a template", names, -1, new Ui.Fn<Integer>() {
                        @Override public void on(Integer i) {
                            ShiftTemplate t = Store.templates.get(i);
                            a.push(new EntryEditorPage(null, "Shift Tracker", t));
                        }
                    });
                }
            }));
        }
        h.body.addView(Sheets.optionCard("event_repeat", Theme.teal, "Subscription or bill", "Track a recurring payment, with reminders and trial alerts", new Runnable() {
            @Override public void run() { h.dismiss(); a.addSubscription(); }
        }));
        h.body.addView(Sheets.optionCard("download", Theme.green, "Import from iPhone", "Open the backup file exported from Pay Calculator on iOS", new Runnable() {
            @Override public void run() { h.dismiss(); a.startImport(); }
        }));
        Sheets.show(h);
    }
}
