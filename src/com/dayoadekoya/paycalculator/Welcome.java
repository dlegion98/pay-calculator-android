package com.dayoadekoya.paycalculator;

import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** First-run welcome sheet (iOS OnboardingView equivalent). */
public final class Welcome {
    private Welcome() {}

    public static void show(final MainActivity a) {
        final Sheets.Handle h = Sheets.sheet(a, null, null);
        h.dialog.setCancelable(true);
        LinearLayout top = Ui.v();
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView icon = new ImageView(a);
        icon.setImageResource(R.mipmap.ic_launcher);
        top.addView(icon, new LinearLayout.LayoutParams(Ui.dp(76), Ui.dp(76)));
        TextView t = Ui.text("Welcome to Pay Calculator", 23, 750, Theme.text);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(14), 0, Ui.dp(4));
        top.addView(t);
        TextView s = Ui.text("Know exactly what lands in your account.", 15, 450, Theme.text2);
        s.setGravity(Gravity.CENTER);
        top.addView(s);
        h.body.addView(top, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 18));
        h.body.addView(point("schedule", Theme.accent, "Log shifts in seconds", "Quick actions, templates and a day-by-day log with overtime, breaks and bonuses."));
        h.body.addView(point("account_balance", Theme.red, "Real HMRC maths", "Tax bands, NI, pensions and student loans for the current UK tax year."));
        h.body.addView(point("insights", Theme.purple, "Insights that matter", "Trends, a run-rate forecast, holiday pay and what-if tools."));
        h.body.addView(point("download", Theme.green, "Bring your iPhone data", "Export from the iOS app and import here — the backup format is shared."));
        h.body.addView(Ui.space(10));
        h.body.addView(Ui.primary("Get started", null, new Runnable() {
            @Override public void run() { Store.put("hasSeenOnboarding", true); h.dismiss(); }
        }));
        h.dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface d) { Store.put("hasSeenOnboarding", true); }
        });
        Sheets.show(h);
    }

    private static LinearLayout point(String icon, int color, String title, String sub) {
        LinearLayout r = Ui.h();
        r.setGravity(Gravity.TOP);
        r.setPadding(0, 0, 0, Ui.dp(16));
        r.addView(Ui.badge(icon, color, 44));
        r.addView(Ui.space(14));
        LinearLayout t = Ui.v();
        t.addView(Ui.text(title, 16, 650, Theme.text));
        TextView s = Ui.text(sub, 14, 400, Theme.text2);
        s.setLineSpacing(0, 1.1f);
        t.addView(s);
        r.addView(t, Ui.weight(1));
        return r;
    }
}
