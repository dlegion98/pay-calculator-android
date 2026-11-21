package com.dayoadekoya.paycalculator;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dayoadekoya.paycalculator.Model.ShiftTemplate;

/** Reusable shift presets that appear as quick actions on Home. */
public class TemplatesPage extends Page {
    private static final String[] ICONS = {"bolt", "bedtime", "sunny", "work", "schedule", "nights_stay", "wb_twilight", "star"};

    @Override public String title() { return "Shift templates"; }
    @Override public String subtitle() { return "One-tap logging from Home"; }

    @Override protected void actions() {
        s.addAction("add", "New template", new Runnable() { @Override public void run() { edit(null); } });
    }

    @Override protected void build() {
        if (Store.templates.isEmpty()) {
            LinearLayout c = Ui.card();
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            c.setPadding(Ui.dp(24), Ui.dp(28), Ui.dp(24), Ui.dp(26));
            c.addView(Ui.badge("layers", Theme.indigo, 64));
            TextView t = Ui.text("No templates yet", 19, 700, Theme.text);
            t.setPadding(0, Ui.dp(14), 0, Ui.dp(6));
            c.addView(t);
            TextView d = Ui.text("Save shifts you work often — like “Saturday 10h” or “Night shift” — and log them from Home in one tap.", 14.5f, 400, Theme.text2);
            d.setGravity(Gravity.CENTER);
            c.addView(d);
            c.addView(Ui.space(18));
            c.addView(Ui.primary("Create template", "add", new Runnable() { @Override public void run() { edit(null); } }), Ui.lp(Ui.MATCH, Ui.WRAP));
            add(c);
            return;
        }
        LinearLayout g = Ui.group();
        for (final ShiftTemplate t : Store.templates) {
            Ui.row(g, iconOk(t.iconName), Theme.accent, t.name, summary(t), null, true, new Runnable() {
                @Override public void run() { edit(t); }
            });
        }
        add(g);
        TextView hint = Ui.text("Templates use your default tax code, age and pension settings when logged.", 13, 400, Theme.text2);
        hint.setPadding(Ui.dp(10), Ui.dp(4), Ui.dp(10), 0);
        add(hint);
    }

    private static String iconOk(String n) {
        for (String i : ICONS) if (i.equals(n)) return n.equals("star") ? "auto_awesome" : n;
        return "bolt";
    }

    private String summary(ShiftTemplate t) {
        StringBuilder sb = new StringBuilder(Ui.hours(t.hours));
        if (t.hourlyRate > 0) sb.append(" · ").append(Ui.money(t.hourlyRate)).append("/h");
        if (t.isOvertime) sb.append(" · overtime");
        if (t.unpaidBreakMinutes > 0) sb.append(" · ").append(Math.round(t.unpaidBreakMinutes)).append("m break");
        if (t.travelPay > 0) sb.append(" · travel ").append(Ui.money(t.travelPay, 0));
        if (t.bonus > 0) sb.append(" · bonus ").append(Ui.money(t.bonus, 0));
        return sb.toString();
    }

    private void edit(final ShiftTemplate existing) {
        final ShiftTemplate t = existing != null ? existing : new ShiftTemplate();
        final Sheets.Handle h = Sheets.sheet(a, existing == null ? "New template" : "Edit template", null);
        final android.widget.EditText name = Ui.field("Name (e.g. Saturday long shift)", t.name, false);
        h.body.addView(name, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
        final android.widget.EditText job = Ui.field("Job name (optional)", t.jobName, false);
        h.body.addView(job, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        LinearLayout g = Ui.group();
        g.setBackground(Ui.rr(Theme.dark ? Theme.field : Theme.surface, 12));
        final double[] vals = {t.hours, t.hourlyRate, t.unpaidBreakMinutes, t.travelPay, t.bonus};
        Ui.fieldRow(g, "schedule", Theme.accent, "Hours", null, t.hours > 0 ? Ui.num(t.hours) : "", "8", new Ui.Fn<String>() { @Override public void on(String v) { vals[0] = Ui.parse(v); } });
        Ui.fieldRow(g, "currency_pound", Theme.green, "Rate (blank = default)", Store.currency(), t.hourlyRate > 0 ? Ui.num(t.hourlyRate) : "", "0.00", new Ui.Fn<String>() { @Override public void on(String v) { vals[1] = Ui.parse(v); } });
        Ui.fieldRow(g, "pause_circle", Theme.indigo, "Unpaid break (min)", null, t.unpaidBreakMinutes > 0 ? Ui.num(t.unpaidBreakMinutes) : "", "0", new Ui.Fn<String>() { @Override public void on(String v) { vals[2] = Ui.parse(v); } });
        Ui.fieldRow(g, "directions_car", Theme.cyan, "Travel pay", Store.currency(), t.travelPay > 0 ? Ui.num(t.travelPay) : "", "0.00", new Ui.Fn<String>() { @Override public void on(String v) { vals[3] = Ui.parse(v); } });
        Ui.fieldRow(g, "redeem", Theme.green, "Bonus", Store.currency(), t.bonus > 0 ? Ui.num(t.bonus) : "", "0.00", new Ui.Fn<String>() { @Override public void on(String v) { vals[4] = Ui.parse(v); } });
        final boolean[] ot = {t.isOvertime};
        Ui.switchRow(g, "bolt", Theme.orange, "Overtime (1.5×)", null, t.isOvertime, new Ui.Fn<Boolean>() { @Override public void on(Boolean v) { ot[0] = v; } });
        h.body.addView(g);

        // icon picker
        final String[] chosen = {iconOk(t.iconName)};
        final LinearLayout icons = Ui.h();
        icons.setPadding(0, Ui.dp(4), 0, Ui.dp(4));
        final String[] opts = {"bolt", "bedtime", "sunny", "work", "schedule", "nights_stay"};
        for (final String ic : opts) {
            final android.widget.FrameLayout f = new android.widget.FrameLayout(a);
            f.setTag(ic);
            f.setBackground(Ui.rr(ic.equals(chosen[0]) ? Theme.accent : Theme.field, 16));
            f.addView(Ui.icon(ic, ic.equals(chosen[0]) ? Theme.onAccent : Theme.text2, 22),
                    new android.widget.FrameLayout.LayoutParams(Ui.dp(22), Ui.dp(22), Gravity.CENTER));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.dp(48), 1f);
            p.setMargins(Ui.dp(3), 0, Ui.dp(3), 0);
            icons.addView(f, p);
            Ui.click(f, new Runnable() {
                @Override public void run() {
                    chosen[0] = ic;
                    for (int i = 0; i < icons.getChildCount(); i++) {
                        android.widget.FrameLayout x = (android.widget.FrameLayout) icons.getChildAt(i);
                        boolean sel = ic.equals(x.getTag());
                        x.setBackground(Ui.rr(sel ? Theme.accent : Theme.field, 16));
                        ((android.widget.ImageView) x.getChildAt(0)).setImageTintList(android.content.res.ColorStateList.valueOf(sel ? Theme.onAccent : Theme.text2));
                    }
                }
            });
        }
        h.body.addView(Ui.sectionTitle("Icon"));
        h.body.addView(icons);

        Sheets.buttons(h, "Save", new Runnable() {
            @Override public void run() {
                String n = name.getText().toString().trim();
                if (n.isEmpty()) n = Ui.hours(vals[0] > 0 ? vals[0] : 8) + " shift";
                t.name = n;
                t.jobName = job.getText().toString().trim();
                t.hours = vals[0] > 0 ? vals[0] : 8;
                t.hourlyRate = vals[1];
                t.unpaidBreakMinutes = vals[2];
                t.travelPay = vals[3];
                t.bonus = vals[4];
                t.isOvertime = ot[0];
                t.iconName = chosen[0];
                if (existing == null) Store.templates.add(t);
                Store.changed();
                h.dismiss();
                render(false);
            }
        });
        if (existing != null) {
            h.body.addView(Ui.space(8));
            h.body.addView(Ui.button("Delete template", "delete", Theme.withAlpha(Theme.red, 0.12f), Theme.red, new Runnable() {
                @Override public void run() { Store.templates.remove(existing); Store.changed(); h.dismiss(); render(false); }
            }));
        }
        Sheets.show(h);
    }
}
