package com.dayoadekoya.paycalculator;

import android.animation.ValueAnimator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dayoadekoya.paycalculator.Model.PayEntry;
import com.dayoadekoya.paycalculator.Model.ShiftTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardPage extends Page {
    private final String query = "";
    private String filterJob = null, filterFreq = null, filterType = "All";
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("d MMM", Locale.UK);

    @Override public String title() { return greeting(); }

    @Override public String subtitle() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK));
    }

    private static String greeting() {
        int h = LocalTime.now().getHour();
        String g = h < 5 ? "Good evening" : h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
        String name = Store.str("userName", "").trim();
        if (!name.isEmpty()) g += ", " + name.split("\\s+")[0];
        return g;
    }

    private android.widget.ImageView eye;
    private static final java.util.Set<String> collapsed = new java.util.HashSet<String>();
    private int ledgerLimit = 40;

    @Override protected void actions() {
        s.smallTitle.setText("Pay Calculator");
        eye = s.addAction(Ui.masked() ? "visibility_off" : "visibility", "Hide or show amounts", new Runnable() {
            @Override public void run() { togglePrivacy(); }
        });
        s.addAction("search", "Search", new Runnable() {
            @Override public void run() { a.push(new SearchPage()); }
        });
        s.addAction("more_vert", "More", new Runnable() { @Override public void run() { moreMenu(); } });
        s.onScroll = new Ui.Fn<Integer>() {
            @Override public void on(Integer dy) { if (Math.abs(dy) > 6) a.setFabExpanded(dy < 0 || s.scroll.getScrollY() < Ui.dp(40)); }
        };
    }

    @Override public void onShown() {
        s.setTitle(greeting());
        s.smallTitle.setText("Pay Calculator");
        if (eye != null) eye.setImageResource(Ui.iconRes(Ui.masked() ? "visibility_off" : "visibility"));
    }

    /** Privacy mode: hide every amount (app and widgets) for checking pay in public. */
    private void togglePrivacy() {
        boolean v = !Store.bool("privacyMode", false);
        Store.put("privacyMode", v);
        Ui.haptic(eye);
        eye.setImageResource(Ui.iconRes(v ? "visibility_off" : "visibility"));
        a.refreshAll();
        render(false);
        try { Widgets.updateAll(a); } catch (Throwable ignored) {}
        a.snack(v ? "Amounts hidden" : "Amounts visible");
    }

    private String timeframe() {
        String t = Store.str("dashboardTimeframe", "Tax Year");
        if ("Lifetime".equals(t)) t = "All Time";
        return t;
    }

    private List<PayEntry> filtered() {
        List<PayEntry> out = new ArrayList<PayEntry>();
        LocalDate now = LocalDate.now();
        String tf = timeframe();
        int startYear = TaxEngine.startYearFor(Store.str("selectedTaxYear", "Current"), now);
        String q = query.toLowerCase(Locale.UK);
        for (PayEntry e : Store.entries) {
            LocalDate d = TaxEngine.toDate(e.dateMillis);
            if ("This Month".equals(tf) && (d.getMonthValue() != now.getMonthValue() || d.getYear() != now.getYear())) continue;
            if ("Tax Year".equals(tf) && !TaxEngine.inTaxYear(e, startYear)) continue;
            if (!q.isEmpty() && !e.jobName.toLowerCase(Locale.UK).contains(q) && !e.notes.toLowerCase(Locale.UK).contains(q)) continue;
            if (filterJob != null && !filterJob.equals(e.jobName)) continue;
            if (filterFreq != null && !filterFreq.equals(e.payFrequency)) continue;
            if ("Actual".equals(filterType) && !e.isActual()) continue;
            if ("Estimated".equals(filterType) && e.isActual()) continue;
            out.add(e);
        }
        return out;
    }

    private boolean filterActive() { return filterJob != null || filterFreq != null || !"All".equals(filterType); }

    @Override protected void build() {
        if (filterActive()) add(span(filterBanner()));
        List<PayEntry> items = filtered();
        View ty = taxYearBanner();
        if (ty != null) add(span(ty));
        add(span(hero(items)));
        View bk = backupNudge();
        if (bk != null) add(span(bk));
        if (Shift.running()) add(span(liveShiftCard()));
        add(span(quickActions()));
        View pd = paydayCard();
        if (pd != null) add(pd);
        if (!Store.entries.isEmpty()) add(weekCard());
        if (!Store.bills.isEmpty()) add(budgetCard());
        if (Store.entries.isEmpty()) {
            add(span(emptyState()));
        } else {
            add(span(recordsHeader()));
            if (items.isEmpty()) {
                LinearLayout box = Ui.v();
                box.setGravity(Gravity.CENTER_HORIZONTAL);
                box.setPadding(0, Ui.dp(26), 0, Ui.dp(26));
                TextView t = Ui.text(filterActive() ? "No pay records match these filters." : "No pay records in this range.", 15, 500, Theme.text2);
                t.setGravity(Gravity.CENTER);
                box.addView(t);
                box.addView(Ui.space(12));
                box.addView(Ui.tonal(filterActive() ? "Clear filters" : "Show all time", filterActive() ? "close" : "calendar_month", new Runnable() {
                    @Override public void run() {
                        if (filterActive()) { filterJob = null; filterFreq = null; filterType = "All"; }
                        else Store.put("dashboardTimeframe", "All Time");
                        render(true);
                    }
                }));
                add(span(box));
            } else {
                buildLedger(items);
            }
        }
    }

    // ------------------------------------------------------------------ search & filter

    private View filterBanner() {
        LinearLayout r = Ui.h();
        r.setPadding(Ui.dp(8), 0, Ui.dp(4), Ui.dp(8));
        List<String> parts = new ArrayList<String>();
        if (filterJob != null) parts.add(filterJob);
        if (filterFreq != null) parts.add(filterFreq);
        if (!"All".equals(filterType)) parts.add(filterType);
        r.addView(Ui.chip("filter_list", android.text.TextUtils.join(" · ", parts), Theme.accent));
        r.addView(Ui.flex());
        TextView reset = Ui.text("Reset", 14, 700, Theme.text2);
        reset.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(8));
        Ui.click(reset, new Runnable() { @Override public void run() { filterJob = null; filterFreq = null; filterType = "All"; render(false); } });
        r.addView(reset);
        return r;
    }

    private void filterSheet() {
        final Sheets.Handle h = Sheets.sheet(a, "Filter history", null);
        final List<String> jobs = new ArrayList<String>();
        jobs.add("All jobs");
        for (PayEntry e : Store.entries) if (!e.jobName.isEmpty() && !jobs.contains(e.jobName)) jobs.add(e.jobName);
        LinearLayout g = Ui.group();
        g.setBackground(Ui.rr(Theme.dark ? Theme.field : Theme.surface, 12));
        Ui.row(g, "work", Theme.accent, "Job", null, filterJob == null ? "All" : filterJob, true, new Runnable() {
            @Override public void run() {
                Sheets.choose(a, "Job", jobs.toArray(new String[0]), filterJob == null ? 0 : jobs.indexOf(filterJob), new Ui.Fn<Integer>() {
                    @Override public void on(Integer i) { filterJob = i == 0 ? null : jobs.get(i); h.dismiss(); render(false); filterSheet(); }
                });
            }
        });
        final String[] freqs = {"All", "Weekly", "Bi-Weekly", "Monthly"};
        Ui.row(g, "autorenew", Theme.blue, "Frequency", null, filterFreq == null ? "All" : filterFreq, true, new Runnable() {
            @Override public void run() {
                Sheets.choose(a, "Frequency", freqs, filterFreq == null ? 0 : java.util.Arrays.asList(freqs).indexOf(filterFreq), new Ui.Fn<Integer>() {
                    @Override public void on(Integer i) { filterFreq = i == 0 ? null : freqs[i]; h.dismiss(); render(false); filterSheet(); }
                });
            }
        });
        h.body.addView(g);
        h.body.addView(Ui.space(6));
        final String[] types = {"All", "Estimated", "Actual"};
        h.body.addView(Ui.segmented(types, java.util.Arrays.asList(types).indexOf(filterType), new Ui.Fn<Integer>() {
            @Override public void on(Integer i) { filterType = types[i]; render(false); }
        }));
        h.body.addView(Ui.space(16));
        h.body.addView(Ui.primary("Done", null, new Runnable() { @Override public void run() { h.dismiss(); } }));
        Sheets.show(h);
    }

    private void moreMenu() {
        final Sheets.Handle h = Sheets.sheet(a, null, null);
        h.body.addView(Sheets.optionCard("donut_large", Theme.accent, "Tax year summary", "Totals, deductions and effective rate", new Runnable() {
            @Override public void run() { h.dismiss(); a.push(new TaxSummaryPage()); }
        }));
        h.body.addView(Sheets.optionCard("ios_share", Theme.green, "Export statement", "PDF statement or CSV spreadsheet", new Runnable() {
            @Override public void run() { h.dismiss(); Exporter.chooser(a); }
        }));
        h.body.addView(Sheets.optionCard("event_repeat", Theme.teal, "Subscriptions & bills", "Recurring payments, trials and safe-to-spend", new Runnable() {
            @Override public void run() { h.dismiss(); a.showTab(MainActivity.TAB_SUBS, true); }
        }));
        h.body.addView(Sheets.optionCard("filter_list", Theme.purple, "Filter history", "By job, frequency or estimated vs actual", new Runnable() {
            @Override public void run() { h.dismiss(); filterSheet(); }
        }));
        h.body.addView(Sheets.optionCard("calendar_month", Theme.orange, "Timeframe · " + timeframe(), "This month, tax year or all time", new Runnable() {
            @Override public void run() { h.dismiss(); chooseTimeframe(); }
        }));
        Sheets.show(h);
    }

    private void chooseTimeframe() {
        final String[] opts = {"This Month", "Tax Year", "All Time"};
        Sheets.choose(a, "Show pay records for", opts, java.util.Arrays.asList(opts).indexOf(timeframe()), new Ui.Fn<Integer>() {
            @Override public void on(Integer i) { Store.put("dashboardTimeframe", opts[i]); render(true); }
        });
    }

    // ------------------------------------------------------------------ hero

    private View hero(List<PayEntry> items) {
        TaxEngine.Totals k = TaxEngine.totals(items);
        LocalDate now = LocalDate.now();
        int startYear = TaxEngine.startYearFor(Store.str("selectedTaxYear", "Current"), now);
        TaxEngine.Totals ty = TaxEngine.taxYearTotals(Store.entries, startYear);
        double projected = ty.net / TaxEngine.taxYearElapsed(startYear, now);

        final int on = Theme.onHero();
        final LinearLayout card = Ui.v();
        card.setBackground(new Glass.Hero(34));
        card.setPadding(Ui.dp(22), Ui.dp(22), Ui.dp(22), Ui.dp(20));
        card.setElevation(Ui.dp(2));
        card.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 14));

        LinearLayout top = Ui.h();
        top.setGravity(Gravity.TOP);
        LinearLayout left = Ui.v();
        String tf = timeframe();
        String label = "Tax Year".equals(tf) ? "Tax year net · " + TaxEngine.taxYearLabel(startYear) : ("This Month".equals(tf) ? "This month net" : "All-time net");
        TextView cap = Ui.text(label.toUpperCase(Locale.UK), 12, 700, Theme.withAlpha(on, 0.85f));
        cap.setLetterSpacing(0.06f);
        left.addView(cap);
        final TextView big = Ui.single(Ui.money(k.net), 38, 800, on);
        big.setPadding(0, Ui.dp(6), 0, 0);
        big.setLetterSpacing(-0.02f);
        Ui.autosize(big, 22, 38);
        left.addView(big);
        countUp(big, k.net);
        top.addView(left, Ui.weight(1));
        final double heroNet = k.net;

        // Goal ring
        double goal = Store.dbl("monthlyEarningsGoal", 2000);
        double monthNet = 0;
        for (PayEntry e : Store.entries) {
            LocalDate d = TaxEngine.toDate(e.dateMillis);
            if (d.getMonthValue() == now.getMonthValue() && d.getYear() == now.getYear()) monthNet += TaxEngine.net(e);
        }
        float prog = goal > 0 ? (float) Math.min(1, monthNet / goal) : 0;
        String goalLine = goal <= 0 ? null : monthNet >= goal ? "Monthly goal reached 🎉"
                : Ui.money(goal - monthNet, 0) + " to go for your " + Ui.money(goal, 0) + " monthly goal";
        if (goalLine != null) {
            TextView gt = Ui.text(goalLine, 12.5f, 600, Theme.withAlpha(on, 0.85f));
            gt.setPadding(0, Ui.dp(2), Ui.dp(8), 0);
            left.addView(gt);
        }
        FrameLayout ringBox = new FrameLayout(a);
        Charts.Ring ring = new Charts.Ring(a);
        ring.track = Theme.withAlpha(on, 0.22f);
        ring.fill = on;
        ringBox.addView(ring, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        LinearLayout rt = Ui.v();
        rt.setGravity(Gravity.CENTER);
        TextView pctT = Ui.text(Math.round(prog * 100) + "%", 17, 800, on);
        pctT.setGravity(Gravity.CENTER);
        pctT.setIncludeFontPadding(false);
        rt.addView(pctT, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        TextView gl = Ui.text("GOAL", 9, 700, Theme.withAlpha(on, 0.8f));
        gl.setLetterSpacing(0.08f);
        gl.setGravity(Gravity.CENTER);
        gl.setIncludeFontPadding(false);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
        glp.topMargin = Ui.dp(2);
        rt.addView(gl, glp);
        ringBox.addView(rt, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH, Gravity.CENTER));
        top.addView(ringBox, new LinearLayout.LayoutParams(Ui.dp(80), Ui.dp(80)));
        ring.set(prog);
        ringBox.setContentDescription("Monthly goal " + Math.round(prog * 100) + " percent");
        Ui.click(ringBox, new Runnable() { @Override public void run() { editGoal(); } });
        card.addView(top);
        maybeCelebrate(prog);

        // Stats row
        LinearLayout stats = Ui.h();
        stats.setPadding(0, Ui.dp(18), 0, 0);
        stats.addView(heroStat(Ui.money(k.gross, 0), "Gross", on), Ui.weight(1));
        stats.addView(heroStat(Ui.money(k.tax + k.ni, 0), "Tax + NI", on), Ui.weight(1));
        stats.addView(heroStat(Ui.hours(k.hours), "Hours", on), Ui.weight(1));
        stats.addView(heroStat(Ui.compact(projected), "Per year", on), Ui.weight(1));
        card.addView(stats);

        // Trend sparkline
        final List<PayEntry> pts = new ArrayList<PayEntry>();
        for (int i = Math.min(Store.entries.size(), 10) - 1; i >= 0; i--) pts.add(Store.entries.get(i));
        if (pts.size() >= 2) {
            View div = new View(a);
            div.setBackgroundColor(Theme.withAlpha(on, 0.2f));
            card.addView(div, Ui.lpm(Ui.MATCH, Math.max(1, Ui.dp(0.7f)), 0, 18, 0, 14));
            final double first = TaxEngine.net(pts.get(0)), last = TaxEngine.net(pts.get(pts.size() - 1));
            final double delta = last - first;
            LinearLayout th = Ui.h();
            final TextView tl = Ui.text("NET TREND", 11, 700, Theme.withAlpha(on, 0.85f));
            tl.setLetterSpacing(0.06f);
            th.addView(tl, Ui.weight(1));
            final TextView pill = Ui.text((delta >= 0 ? "▲ " : "▼ ") + Ui.money(Math.abs(delta), 0), 11.5f, 700, on);
            pill.setBackground(Ui.rr(Theme.withAlpha(on, 0.2f), 50));
            pill.setPadding(Ui.dp(10), Ui.dp(3), Ui.dp(10), Ui.dp(3));
            th.addView(pill);
            card.addView(th);
            Charts.Spark sp = new Charts.Spark(a);
            sp.color = on;
            double[] vals = new double[pts.size()];
            for (int i = 0; i < pts.size(); i++) vals[i] = TaxEngine.net(pts.get(i));
            sp.set(vals, new Charts.Scrub() {
                @Override public void on(int i) {
                    if (i < 0) {
                        tl.setText("NET TREND");
                        pill.setText((delta >= 0 ? "▲ " : "▼ ") + Ui.money(Math.abs(delta), 0));
                    } else {
                        PayEntry e = pts.get(i);
                        tl.setText(TaxEngine.toDate(e.dateMillis).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)).toUpperCase(Locale.UK));
                        pill.setText(Ui.money(TaxEngine.net(e), 0));
                    }
                }
            });
            card.addView(sp, Ui.lpm(Ui.MATCH, Ui.dp(56), 0, 8, 0, 0));
        }
        card.setContentDescription(label + " " + Ui.money(k.net) + ". Tap for the tax year summary, long-press to copy.");
        Ui.click(card, new Runnable() { @Override public void run() { a.push(new TaxSummaryPage()); } });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { copyAmount(a, heroNet); Ui.haptic(v); return true; }
        });
        return card;
    }

    /** Copies an amount (always the real figure) to the clipboard. */
    static void copyAmount(MainActivity a, double v) {
        String txt = Store.currency() + String.format(Locale.UK, "%,.2f", v);
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Amount", txt));
            a.snack("Copied " + (Ui.masked() ? "amount" : txt));
        } catch (Throwable t) { a.snack("Couldn't copy"); }
    }

    // ------------------------------------------------------------------ banners

    /** 6–30 April: remind people their tax code and thresholds have rolled over. */
    private View taxYearBanner() {
        LocalDate now = LocalDate.now();
        if (now.getMonthValue() != 4 || now.getDayOfMonth() < 6) return null;
        final int startYear = TaxEngine.taxYearStart(now);
        if (Store.integer("taxYearBannerDismissed", 0) == startYear) return null;
        return banner("celebration", Theme.purple, "New tax year " + TaxEngine.taxYearLabel(startYear),
                "Allowances reset on 6 April. Check your tax code on your first payslip.", "Check tax code", new Runnable() {
                    @Override public void run() { a.showTab(MainActivity.TAB_SETTINGS, true); }
                }, new Runnable() {
                    @Override public void run() { Store.put("taxYearBannerDismissed", startYear); render(true); }
                });
    }

    /** A gentle nudge to export a backup when there's data worth protecting. */
    private View backupNudge() {
        if (Store.entries.size() < 5) return null;
        long now = System.currentTimeMillis();
        long last = (long) Store.dbl("lastBackupMillis", 0);
        long snooze = (long) Store.dbl("backupSnoozeUntil", 0);
        if (now < snooze || now - last < 30L * 86400000L) return null;
        String when = last <= 0 ? "You haven't backed up yet." : "Last backup " + ((now - last) / 86400000L) + " days ago.";
        return banner("backup", Theme.blue, "Back up your pay records", when + " A backup also moves your data to a new phone or iPhone.", "Back up now", new Runnable() {
            @Override public void run() { a.startExport(MainActivity.REQ_EXPORT_JSON, "application/json", "PayCalculator_SyncData.json"); }
        }, new Runnable() {
            @Override public void run() { Store.put("backupSnoozeUntil", (double) (System.currentTimeMillis() + 14L * 86400000L)); render(true); }
        });
    }

    private View banner(String icon, int color, String title, String body, String action, final Runnable onAction, final Runnable onDismiss) {
        LinearLayout c = Ui.card();
        c.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 14));
        LinearLayout r = Ui.h();
        r.setGravity(Gravity.TOP);
        r.addView(Ui.badge(icon, color, 34));
        r.addView(Ui.space(12));
        LinearLayout t = Ui.v();
        t.addView(Ui.text(title, 16, 650, Theme.text));
        TextView b = Ui.text(body, 13.5f, 400, Theme.text2);
        b.setPadding(0, Ui.dp(2), 0, Ui.dp(8));
        t.addView(b);
        TextView act = Ui.text(action, 15, 600, Theme.accent);
        act.setPadding(0, Ui.dp(4), Ui.dp(12), Ui.dp(4));
        Ui.click(act, onAction);
        t.addView(act);
        r.addView(t, Ui.weight(1));
        android.widget.ImageView x = Ui.iconButton("close", Theme.text3, onDismiss);
        x.setContentDescription("Dismiss");
        r.addView(x, new LinearLayout.LayoutParams(Ui.dp(36), Ui.dp(36)));
        c.addView(r);
        return c;
    }

    private View heroStat(String value, String label, int on) {
        LinearLayout l = Ui.v();
        TextView v = Ui.single(value, 16, 750, on);
        Ui.autosize(v, 10, 16);
        l.addView(v);
        TextView t = Ui.text(label.toUpperCase(Locale.UK), 10, 650, Theme.withAlpha(on, 0.75f));
        t.setLetterSpacing(0.05f);
        l.addView(t);
        return l;
    }

    private void countUp(final TextView t, final double target) {
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(900);
        va.setInterpolator(Ui.EMPHASIZED);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator an) {
                t.setText(Glass.bigMoney(target * (Float) an.getAnimatedValue(), t.getCurrentTextColor()));
            }
        });
        va.start();
    }

    private void editGoal() {
        Sheets.input(a, "Monthly goal", "Your target take-home pay for each calendar month.", "2000",
                Ui.num(Store.dbl("monthlyEarningsGoal", 2000)), true, new Ui.Fn<String>() {
            @Override public void on(String v) { Store.put("monthlyEarningsGoal", Ui.parse(v)); render(false); }
        });
    }

    private void maybeCelebrate(float prog) {
        if (prog < 1f) return;
        LocalDate n = LocalDate.now();
        String key = n.getYear() + "-" + n.getMonthValue();
        if (key.equals(Store.str("goalCelebratedPeriod", ""))) return;
        Store.put("goalCelebratedPeriod", key);
        s.postDelayed(new Runnable() { @Override public void run() { a.snack("🎉 Monthly goal reached!"); } }, 900);
    }

    // ------------------------------------------------------------------ quick actions

    private View quickActions() {
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setPadding(Ui.dp(2), 0, Ui.dp(2), 0);
        LinearLayout row = Ui.h();
        if (!Shift.running()) {
            row.addView(quick("timer", "Start shift", Theme.green, new Runnable() { @Override public void run() { startShift(); } }));
        }
        final PayEntry last = lastSingleShift();
        if (last != null) {
            final double lh = TaxEngine.totalHours(last);
            row.addView(quick("history", "Repeat " + Ui.hours(lh) + " " + last.displayJob(), Theme.teal, new Runnable() {
                @Override public void run() { repeatShift(last); }
            }));
        }
        row.addView(quick("bolt", "Log 8h shift", Theme.orange, new Runnable() { @Override public void run() { quickLog(8, null); } }));
        row.addView(quick("bedtime", "Log 12h night", Theme.indigo, new Runnable() { @Override public void run() { quickLog(12, "Night Shift"); } }));
        row.addView(quick("sunny", "Log 4h half", Theme.yellow, new Runnable() { @Override public void run() { quickLog(4, null); } }));
        for (final ShiftTemplate t : Store.templates) {
            row.addView(quick(t.iconName, t.name, Theme.accent, new Runnable() { @Override public void run() { logTemplate(t); } }));
        }
        row.addView(quick("layers", Store.templates.isEmpty() ? "Create template" : "Templates", Theme.text2, new Runnable() {
            @Override public void run() { a.push(new TemplatesPage()); }
        }));
        hs.addView(row);
        hs.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 14));
        return hs;
    }

    private View quick(String icon, String label, int color, Runnable r) {
        LinearLayout p = Ui.h();
        p.setBackground(Glass.panel(50));
        p.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(16), Ui.dp(10));
        FrameLayout dot = new FrameLayout(a);
        dot.setBackground(Ui.oval(Theme.withAlpha(color, Theme.dark ? 0.25f : 0.15f)));
        dot.addView(Ui.icon(icon, color, 16), new FrameLayout.LayoutParams(Ui.dp(16), Ui.dp(16), Gravity.CENTER));
        p.addView(dot, new LinearLayout.LayoutParams(Ui.dp(28), Ui.dp(28)));
        p.addView(Ui.space(8));
        p.addView(Ui.text(label, 14, 650, Theme.text));
        Ui.tap(p, 50, r);
        LinearLayout.LayoutParams lp = Ui.lpm(Ui.WRAP, Ui.WRAP, 0, 0, 8, 0);
        p.setLayoutParams(lp);
        return p;
    }

    /** Most recent one-day pay period (a typical logged shift). */
    private static PayEntry lastSingleShift() {
        PayEntry best = null;
        for (PayEntry e : Store.entries) {
            if (e.isActual() || e.isSalariedMode) continue;
            int days = 0;
            for (Model.DailyPay d : e.days) if (!d.isEmpty()) days++;
            if (days != 1) continue;
            if (best == null || e.dateMillis > best.dateMillis) best = e;
        }
        return best;
    }

    private void repeatShift(PayEntry src) {
        PayEntry e = src.copy(true);
        e.dateMillis = TaxEngine.toMillis(LocalDate.now());
        for (Model.DailyPay d : e.days) d.dayIndex = 0;
        e.notes = "";
        Store.entries.add(e);
        Store.changed();
        Ui.confirmHaptic(s);
        final String id = e.id;
        a.snack("Logged " + Ui.hours(TaxEngine.totalHours(e)) + " " + e.displayJob() + " · " + Ui.money(TaxEngine.net(e)), "Undo", new Runnable() {
            @Override public void run() { PayEntry x = Store.find(id); if (x != null) { Store.entries.remove(x); Store.changed(); } }
        });
    }

    private void quickLog(double hours, String job) {
        PayEntry e = Store.quickShift(hours, job);
        Store.entries.add(e);
        Store.changed();
        Ui.confirmHaptic(s);
        final String id = e.id;
        a.snack("Logged " + Ui.num(hours) + "h shift · " + Ui.money(TaxEngine.net(e)), "Undo", new Runnable() {
            @Override public void run() { PayEntry x = Store.find(id); if (x != null) { Store.entries.remove(x); Store.changed(); } }
        });
    }

    private void logTemplate(ShiftTemplate t) {
        PayEntry e = Store.newEntryWithDefaults();
        e.jobName = t.jobName.isEmpty() ? t.name : t.jobName;
        if (t.hourlyRate > 0) e.hourlyRate = t.hourlyRate;
        e.payFrequency = t.payFrequency;
        e.dateMillis = TaxEngine.toMillis(LocalDate.now());
        Model.DailyPay d = new Model.DailyPay(0, t.hours);
        d.isOvertime = t.isOvertime; d.unpaidBreakMinutes = t.unpaidBreakMinutes;
        d.travelPay = t.travelPay; d.bonus = t.bonus; d.shiftTag = t.shiftTag;
        e.days.add(d);
        Store.entries.add(e);
        Store.changed();
        final String id = e.id;
        a.snack("Logged " + t.name, "Undo", new Runnable() {
            @Override public void run() { PayEntry x = Store.find(id); if (x != null) { Store.entries.remove(x); Store.changed(); } }
        });
    }

    // ------------------------------------------------------------------ live shift timer

    private void startShift() {
        final java.util.List<String> jobs = new ArrayList<String>();
        for (PayEntry e : Store.entries) if (!e.jobName.isEmpty() && !jobs.contains(e.jobName)) jobs.add(e.jobName);
        if (jobs.isEmpty()) jobs.add("Main Job");
        if (jobs.size() == 1) { beginShift(jobs.get(0)); return; }
        Sheets.choose(a, "Which job?", jobs.toArray(new String[0]), 0, new Ui.Fn<Integer>() {
            @Override public void on(Integer i) { beginShift(jobs.get(i)); }
        });
    }

    private void beginShift(final String job) {
        final String[] opts = {"Just now", "15 minutes ago", "30 minutes ago", "1 hour ago", "2 hours ago"};
        final int[] mins = {0, 15, 30, 60, 120};
        Sheets.choose(a, "When did you start?", opts, 0, new Ui.Fn<Integer>() {
            @Override public void on(Integer i) { beginShiftAt(job, System.currentTimeMillis() - mins[i] * 60000L); }
        });
    }

    private void beginShiftAt(String job, long start) {
        Shift.start(a, job, start);
        Ui.confirmHaptic(s);
        a.requestNotificationPermission();
        a.snack("Shift started · " + job);
        render(true);
    }

    private View liveShiftCard() {
        final int on = Theme.onHero();
        LinearLayout c = Ui.v();
        c.setBackground(new Glass.Hero(30));
        c.setPadding(Ui.dp(20), Ui.dp(18), Ui.dp(20), Ui.dp(18));
        c.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 14));
        LinearLayout top = Ui.h();
        View dot = new View(a);
        dot.setBackground(Ui.oval(0xFFFF5A5F));
        top.addView(dot, new LinearLayout.LayoutParams(Ui.dp(10), Ui.dp(10)));
        android.animation.ObjectAnimator blink = android.animation.ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.25f);
        blink.setDuration(900); blink.setRepeatMode(android.animation.ValueAnimator.REVERSE); blink.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        blink.start();
        final android.animation.ObjectAnimator fBlink = blink;
        dot.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { if (!fBlink.isStarted()) fBlink.start(); }
            @Override public void onViewDetachedFromWindow(View v) { fBlink.cancel(); }
        });
        top.addView(Ui.space(8));
        TextView lbl = Ui.text(("SHIFT IN PROGRESS · " + Shift.job()).toUpperCase(Locale.UK), 12, 700, Theme.withAlpha(on, 0.9f));
        lbl.setLetterSpacing(0.05f);
        top.addView(lbl, Ui.weight(1));
        c.addView(top);
        final TextView clock = Ui.text(Shift.clock(Shift.elapsedMs()), 40, 800, on);
        clock.setFontFeatureSettings("tnum");
        clock.setPadding(0, Ui.dp(6), 0, 0);
        c.addView(clock);
        final TextView earn = Ui.text("", 14, 600, Theme.withAlpha(on, 0.9f));
        c.addView(earn);
        final double rate = Shift.rate();
        final Runnable[] tick = new Runnable[1];
        tick[0] = new Runnable() {
            @Override public void run() {
                if (!Shift.running()) return;
                long ms = Shift.elapsedMs();
                clock.setText(Shift.clock(ms));
                double hrs = ms / 3600000.0;
                double net = hrs >= 0.02 ? TaxEngine.net(Shift.preview(hrs)) : 0;
                earn.setText("≈ " + Ui.money(net) + " take-home · " + Ui.money(hrs * rate) + " gross · since "
                        + java.time.Instant.ofEpochMilli(Shift.startMillis()).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm", Locale.UK)));
                if (clock.isAttachedToWindow()) clock.postDelayed(tick[0], 1000);
            }
        };
        tick[0].run();
        // Tick only while on screen (re-renders create a fresh card; the old one stops).
        clock.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { v.removeCallbacks(tick[0]); tick[0].run(); }
            @Override public void onViewDetachedFromWindow(View v) { v.removeCallbacks(tick[0]); }
        });
        LinearLayout btns = Ui.h();
        btns.setPadding(0, Ui.dp(14), 0, 0);
        LinearLayout stop = Ui.button("Stop & log", "check", 0xFFFFFFFF, Theme.heroBase, new Runnable() {
            @Override public void run() { stopShift(); }
        });
        btns.addView(stop, Ui.weight(1));
        btns.addView(Ui.space(10));
        LinearLayout cancel = Ui.button("Discard", null, Theme.withAlpha(on, 0.18f), on, new Runnable() {
            @Override public void run() {
                Sheets.confirm(a, "Discard this shift?", "The running timer will be cleared without logging anything.", "Discard", true, new Runnable() {
                    @Override public void run() { Shift.cancel(a); render(true); }
                });
            }
        });
        btns.addView(cancel, Ui.weight(0.7f));
        c.addView(btns);
        return c;
    }

    private void stopShift() {
        final String[] opts = {"No break", "15 min break", "30 min break", "45 min break", "60 min break"};
        final double[] mins = {0, 15, 30, 45, 60};
        // UK rest-break rule: 20+ minutes for shifts over 6 hours, so suggest 30 min on long shifts
        int suggest = Shift.elapsedMs() >= 6 * 3600000L ? 2 : 0;
        Sheets.choose(a, "Unpaid break?", opts, suggest, new Ui.Fn<Integer>() {
            @Override public void on(Integer i) {
                PayEntry e = Shift.stopAndLog(a, mins[i]);
                if (e == null) { a.snack("Shift was under a minute, so nothing was logged"); render(true); return; }
                Ui.confirmHaptic(s);
                final String id = e.id;
                a.snack("Logged " + Ui.hours(TaxEngine.totalHours(e)) + " · " + Ui.money(TaxEngine.net(e)), "Edit", new Runnable() {
                    @Override public void run() { if (Store.find(id) != null) a.push(new EntryEditorPage(id, null)); }
                });
            }
        });
    }

    // ------------------------------------------------------------------ this week

    private View weekCard() {
        LocalDate today = LocalDate.now();
        LocalDate mon = today.with(java.time.DayOfWeek.MONDAY);
        java.util.Map<LocalDate, double[]> act = TaxEngine.dailyActivity(Store.entries, mon, mon.plusDays(6));
        final double[] hrs = new double[7];
        final double[] net = new double[7];
        double th = 0, tn = 0;
        for (int i = 0; i < 7; i++) {
            double[] v = act.get(mon.plusDays(i));
            if (v != null) { hrs[i] = v[0]; net[i] = v[1]; th += v[0]; tn += v[1]; }
        }
        double lastWeekHours = 0;
        for (double[] v : TaxEngine.dailyActivity(Store.entries, mon.minusDays(7), mon.minusDays(1)).values()) lastWeekHours += v[0];
        LinearLayout c = Ui.card();
        LinearLayout h = Ui.h();
        LinearLayout ht = Ui.v();
        ht.addView(Ui.caption("This week"));
        final TextView val = Ui.text(Ui.money(tn), 22, 800, Theme.text);
        val.setPadding(0, Ui.dp(4), 0, 0);
        ht.addView(val);
        final TextView sub = Ui.text(Ui.hours(th) + " worked · " + mon.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)) + " – " + mon.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)), 13, 500, Theme.text2);
        ht.addView(sub);
        h.addView(ht, Ui.weight(1));
        if (lastWeekHours > 0 || th > 0) {
            double diff = th - lastWeekHours;
            String txt = Math.abs(diff) < 0.05 ? "Same as last week" : (diff > 0 ? "▲ " : "▼ ") + Ui.hours(Math.abs(diff)) + " vs last week";
            TextView cmp = Ui.text(txt, 12, 600, diff >= 0 ? Theme.green : Theme.text2);
            cmp.setBackground(Ui.rr(Theme.withAlpha(diff >= 0 ? Theme.green : Theme.text2, 0.12f), 50));
            cmp.setPadding(Ui.dp(10), Ui.dp(4), Ui.dp(10), Ui.dp(4));
            h.addView(cmp);
        }
        c.addView(h);
        if (th > 48) {
            LinearLayout w = Ui.h();
            w.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(8));
            w.setBackground(Ui.rr(Theme.withAlpha(Theme.orange, 0.12f), 10));
            w.addView(Ui.icon("warning", Theme.orange, 18));
            w.addView(Ui.space(8));
            w.addView(Ui.text("Over 48 hours this week. That's the UK weekly limit unless you've opted out in writing.", 12.5f, 500, Theme.text), Ui.weight(1));
            c.addView(w, Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        }
        Charts.Bars bars = new Charts.Bars(a);
        bars.color = Theme.accent;
        final String[] labels = {"M", "T", "W", "T", "F", "S", "S"};
        final String[] full = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        final double fth = th, ftn = tn;
        final LocalDate fmon = mon;
        bars.set(hrs, labels, new Charts.Scrub() {
            @Override public void on(int i) {
                if (i < 0) return;
                val.setText(Ui.money(net[i]));
                sub.setText(Ui.hours(hrs[i]) + " on " + full[i] + " " + fmon.plusDays(i).format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)));
            }
        });
        c.addView(bars, Ui.lpm(Ui.MATCH, Ui.dp(110), 0, 12, 0, 0));
        return c;
    }

    // ------------------------------------------------------------------ budget

    private View budgetCard() {
        BillsPage.Budget b = BillsPage.Budget.now();
        LinearLayout c = Ui.card();
        LinearLayout h = Ui.h();
        h.addView(Ui.badge("account_balance_wallet", Theme.accent, 40));
        h.addView(Ui.space(12));
        LinearLayout t = Ui.v();
        t.addView(Ui.caption("Safe to spend · after bills & subscriptions"));
        TextView v = Ui.text(Ui.money(b.safe), 22, 800, b.safe >= 0 ? Theme.text : Theme.red);
        t.addView(v);
        h.addView(t, Ui.weight(1));
        h.addView(Ui.icon("chevron_right", Theme.text3, 22));
        c.addView(h);
        Charts.Progress p = new Charts.Progress(a);
        p.color = b.safe >= 0 ? Theme.accent : Theme.red;
        p.track = Theme.field;
        p.set(b.bills > 0 ? (float) Math.min(1, b.income / b.bills) : 1f);
        c.addView(p, Ui.lpm(Ui.MATCH, Ui.dp(8), 0, 14, 0, 8));
        c.addView(Ui.text(Ui.money(b.income, 0) + " earned · " + Ui.money(b.bills, 0) + " due this month · " + Ui.money(b.stillToPay, 0) + " still to pay", 13, 500, Theme.text2));
        if (b.nextBill != null) {
            LinearLayout nx = Ui.h();
            nx.setPadding(0, Ui.dp(10), 0, 0);
            nx.addView(Ui.badge(BillsPage.icon(b.nextBill.category), BillsPage.color(b.nextBill.category), 28));
            nx.addView(Ui.space(10));
            nx.addView(Ui.single("Next: " + b.nextBill.name + " · " + BillsPage.when(b.nextDate), 14, 600, Theme.text), Ui.weight(1));
            nx.addView(Ui.text(Ui.money(b.nextBill.amount), 14, 650, Theme.text));
            c.addView(nx);
        }
        Ui.tap(c, 28, new Runnable() { @Override public void run() { a.showTab(MainActivity.TAB_SUBS, true); } });
        return c;
    }

    // ------------------------------------------------------------------ payday

    private View paydayCard() {
        LocalDate next = Payday.next();
        if (next == null) return null;
        long days = next.toEpochDay() - LocalDate.now().toEpochDay();
        LinearLayout c = Ui.h();
        c.setBackground(Glass.panel(26));
        c.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(18), Ui.dp(14));
        c.addView(Ui.badge("payments", Theme.green, 44));
        c.addView(Ui.space(14));
        LinearLayout t = Ui.v();
        String when = days <= 0 ? "Payday is today 💸" : (days == 1 ? "Payday is tomorrow" : "Payday in " + days + " days");
        t.addView(Ui.text(when, 16.5f, 650, Theme.text));
        t.addView(Ui.text(next.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)) + (Payday.isManual() ? "" : " · predicted"), 13.5f, 400, Theme.text2));
        double expect = expectedPay();
        if (expect > 0) t.addView(Ui.text("Usually about " + Ui.money(expect, 0) + " take-home", 13.5f, 600, Theme.green));
        c.addView(t, Ui.weight(1));
        if (days > 0 && days <= 31) {
            TextView n = Ui.text(String.valueOf(days), 26, 800, Theme.green);
            c.addView(n);
            TextView dl = Ui.text(days == 1 ? " day" : " days", 13, 600, Theme.text2);
            c.addView(dl);
        }
        c.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 6));
        Ui.tap(c, 26, new Runnable() { @Override public void run() { a.showTab(1, true); } });
        return c;
    }

    /** Average take-home of the last three pay periods for your most recent job. */
    private static double expectedPay() {
        if (Store.entries.isEmpty()) return 0;
        PayEntry latest = null;
        for (PayEntry e : Store.entries) if (latest == null || e.dateMillis > latest.dateMillis) latest = e;
        List<PayEntry> same = new ArrayList<PayEntry>();
        for (PayEntry e : Store.entries) if (e.displayJob().equalsIgnoreCase(latest.displayJob())) same.add(e);
        java.util.Collections.sort(same, new java.util.Comparator<PayEntry>() {
            @Override public int compare(PayEntry x, PayEntry y) { return Long.compare(y.dateMillis, x.dateMillis); }
        });
        double sum = 0; int n = 0;
        for (PayEntry e : same) { if (n >= 3) break; sum += TaxEngine.net(e); n++; }
        return n == 0 ? 0 : sum / n;
    }

    // ------------------------------------------------------------------ ledger

    private View recordsHeader() {
        LinearLayout r = Ui.h();
        r.setPadding(Ui.dp(8), Ui.dp(18), Ui.dp(2), Ui.dp(10));
        r.addView(Ui.text("Pay records", 20, 700, Theme.text), Ui.weight(1));
        r.setContentDescription("Pay records, showing " + timeframe());
        LinearLayout chip = Ui.h();
        chip.setBackground(Ui.rr(Theme.accentSoft, 50));
        chip.setPadding(Ui.dp(14), Ui.dp(7), Ui.dp(10), Ui.dp(7));
        chip.addView(Ui.text(timeframe(), 14, 700, Theme.accent));
        chip.addView(Ui.icon("expand_more", Theme.accent, 18));
        Ui.tap(chip, 50, new Runnable() { @Override public void run() { chooseTimeframe(); } });
        r.addView(chip);
        return r;
    }

    private void buildLedger(List<PayEntry> items) {
        Map<String, List<PayEntry>> groups = new LinkedHashMap<String, List<PayEntry>>();
        for (PayEntry e : items) {
            LocalDate d = TaxEngine.toDate(e.dateMillis);
            String key = d.getMonth().getDisplayName(TextStyle.FULL, Locale.UK) + " " + d.getYear();
            List<PayEntry> l = groups.get(key);
            if (l == null) { l = new ArrayList<PayEntry>(); groups.put(key, l); }
            l.add(e);
        }
        int shown = 0;
        boolean truncated = false;
        for (Map.Entry<String, List<PayEntry>> g : groups.entrySet()) {
            if (shown >= ledgerLimit) { truncated = true; break; }
            double net = 0;
            for (PayEntry e : g.getValue()) net += TaxEngine.net(e);
            final String key = g.getKey();
            final boolean isCollapsed = collapsed.contains(key);
            LinearLayout head = Ui.h();
            head.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(6), Ui.dp(8));
            head.addView(Ui.text(key, 15, 700, Theme.text));
            int n = g.getValue().size();
            head.addView(Ui.text("  ·  " + n + (n == 1 ? " shift" : " shifts"), 13, 600, Theme.text2), Ui.weight(1));
            head.addView(Ui.text(Ui.money(net), 15, 750, Theme.accent));
            android.widget.ImageView chev = Ui.icon("expand_more", Theme.text3, 20);
            chev.setRotation(isCollapsed ? -90 : 0);
            head.addView(chev);
            head.setContentDescription(key + ", " + n + " pay periods, " + Ui.money(net) + (isCollapsed ? ". Collapsed, tap to expand" : ". Tap to collapse"));
            // Tap a month to fold it away
            Ui.tap(head, 10, new Runnable() {
                @Override public void run() {
                    if (isCollapsed) collapsed.remove(key); else collapsed.add(key);
                    Ui.haptic(s);
                    render(false);
                }
            });
            if (isCollapsed) { add(head); continue; }
            head.setTag("section");
            add(head);
            LinearLayout card = Ui.group();
            card.setPadding(0, 0, 0, 0);
            for (int i = 0; i < g.getValue().size(); i++) {
                if (shown >= ledgerLimit) { truncated = true; break; }
                if (i > 0) card.addView(Ui.divider(18));
                card.addView(recordRow(g.getValue().get(i)));
                shown++;
            }
            add(card);
        }
        if (truncated) {
            final int remaining = items.size() - shown;
            LinearLayout more = Ui.tonal("Show " + Math.min(40, remaining) + " more of " + remaining, "expand_more", new Runnable() {
                @Override public void run() { ledgerLimit += 40; render(false); }
            });
            more.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
            add(span(more));
        }
    }

    public static String rangeString(PayEntry e) {
        LocalDate d = TaxEngine.toDate(e.dateMillis);
        LocalDate end = "Weekly".equals(e.payFrequency) ? d.plusDays(6)
                : ("Bi-Weekly".equals(e.payFrequency) ? d.plusDays(13) : d.plusMonths(1).minusDays(1));
        return d.format(MD) + " – " + end.format(MD);
    }

    private View recordRow(final PayEntry e) { return recordRow(a, e); }

    /** A pay-record row (shared with search). Long-press for edit / duplicate / delete. */
    public static View recordRow(final MainActivity a, final PayEntry e) {
        LinearLayout r = Ui.v();
        r.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        LinearLayout top = Ui.h();
        top.addView(Ui.avatar(e.displayJob(), 46));
        top.addView(Ui.space(14));
        LinearLayout t = Ui.v();
        t.addView(Ui.single(e.displayJob(), 16.5f, 650, Theme.text));
        t.addView(Ui.text(rangeString(e), 13, 450, Theme.text2));
        top.addView(t, Ui.weight(1));
        LinearLayout rv = Ui.v();
        rv.setGravity(Gravity.END);
        rv.addView(Ui.text(Ui.money(TaxEngine.net(e)), 19, 750, Theme.text));
        boolean actual = e.isActual();
        TextView badge = Ui.text(actual ? "Actual" : "Estimated", 11, 700, actual ? Theme.green : Theme.orange);
        badge.setBackground(Ui.rr(Theme.withAlpha(actual ? Theme.green : Theme.orange, 0.14f), 50));
        badge.setPadding(Ui.dp(8), Ui.dp(2), Ui.dp(8), Ui.dp(2));
        LinearLayout.LayoutParams bp = Ui.lpm(Ui.WRAP, Ui.WRAP, 0, 3, 0, 0);
        bp.gravity = Gravity.END;
        rv.addView(badge, bp);
        top.addView(rv);
        r.addView(top);
        LinearLayout chips = Ui.h();
        chips.setPadding(0, Ui.dp(12), 0, 0);
        chips.addView(Ui.chip("schedule", Ui.hours(TaxEngine.totalHours(e)), Theme.accent));
        chips.addView(Ui.space(6));
        chips.addView(Ui.chip("payments", Ui.money(TaxEngine.effectiveGross(e), 0), Theme.green));
        chips.addView(Ui.space(6));
        chips.addView(Ui.chip("autorenew", e.payFrequency, Theme.text2));
        if (!e.notes.trim().isEmpty() && !e.notes.startsWith("Logged with the shift timer")) {
            chips.addView(Ui.space(6));
            chips.addView(Ui.icon("edit", Theme.text3, 16));
        }
        chips.addView(Ui.flex());
        chips.addView(Ui.icon("chevron_right", Theme.text3, 20));
        r.addView(chips);
        r.setBackground(Ui.ripple(Ui.rr(Theme.surface, 0), 0));
        Ui.click(r, new Runnable() { @Override public void run() { a.push(new EntryDetailPage(e.id)); } });
        r.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { Ui.haptic(v); rowMenu(a, e); return true; }
        });
        r.setContentDescription(e.displayJob() + ", " + rangeString(e) + ", " + Ui.money(TaxEngine.net(e)) + " take-home, "
                + (actual ? "from payslip" : "estimated") + ". Swipe left to delete, long-press for more.");
        return swipeToDelete(a, r, e);
    }

    /** Wraps a row so swiping it left deletes the pay period (with undo), like iOS Mail. */
    static View swipeToDelete(final MainActivity a, final View row, final PayEntry e) {
        final FrameLayout wrap = new FrameLayout(a);
        wrap.setBackgroundColor(Theme.red);
        LinearLayout under = Ui.h();
        under.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        under.setPadding(0, 0, Ui.dp(22), 0);
        under.addView(Ui.icon("delete", 0xFFFFFFFF, 22));
        under.addView(Ui.space(6));
        under.addView(Ui.text("Delete", 15, 600, 0xFFFFFFFF));
        wrap.addView(under, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        wrap.addView(row, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        final int slop = android.view.ViewConfiguration.get(a).getScaledTouchSlop();
        row.setOnTouchListener(new View.OnTouchListener() {
            float x0, y0; boolean swiping, decided;
            @Override public boolean onTouch(View v, android.view.MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        x0 = ev.getRawX(); y0 = ev.getRawY(); swiping = false; decided = false;
                        return false;
                    case android.view.MotionEvent.ACTION_MOVE: {
                        float dx = ev.getRawX() - x0, dy = ev.getRawY() - y0;
                        if (!decided && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                            decided = true;
                            swiping = dx < 0 && Math.abs(dx) > Math.abs(dy) * 1.3f;
                            if (swiping) {
                                v.getParent().requestDisallowInterceptTouchEvent(true);
                                v.cancelLongPress();
                                v.setPressed(false);
                            }
                        }
                        if (swiping) { v.setTranslationX(Math.min(0, dx)); return true; }
                        return false;
                    }
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL: {
                        if (!swiping) return false;
                        swiping = false;
                        float dx = v.getTranslationX();
                        if (-dx > v.getWidth() * 0.35f && ev.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
                            Ui.confirmHaptic(v);
                            v.animate().translationX(-v.getWidth()).setDuration(180).withEndAction(new Runnable() {
                                @Override public void run() { delete(a, e); }
                            }).start();
                        } else {
                            v.animate().translationX(0).setDuration(260).setInterpolator(Ui.SPRING).start();
                        }
                        return true;
                    }
                }
                return false;
            }
        });
        return wrap;
    }

    static void rowMenu(final MainActivity a, final PayEntry e) {
        final Sheets.Handle h = Sheets.sheet(a, e.displayJob(), rangeString(e) + " · " + Ui.money(TaxEngine.net(e)));
        h.body.addView(Sheets.optionCard("edit", Theme.accent, "Edit", "Change hours, rates or payslip figures", new Runnable() {
            @Override public void run() { h.dismiss(); a.push(new EntryEditorPage(e.id, null)); }
        }));
        h.body.addView(Sheets.optionCard("content_copy", Theme.purple, "Duplicate", "Copy into the next pay period", new Runnable() {
            @Override public void run() { h.dismiss(); duplicate(a, e); }
        }));
        h.body.addView(Sheets.optionCard("delete", Theme.red, "Delete", "Remove this pay period", new Runnable() {
            @Override public void run() { h.dismiss(); delete(a, e); }
        }));
        Sheets.show(h);
    }

    /** Copies a pay period into the following period (next week / fortnight / month). */
    public static void duplicate(final MainActivity a, PayEntry e) {
        PayEntry c = e.copy(true);
        LocalDate d = TaxEngine.toDate(e.dateMillis);
        LocalDate next = "Monthly".equals(e.payFrequency) ? d.plusMonths(1) : d.plusDays("Bi-Weekly".equals(e.payFrequency) ? 14 : 7);
        c.dateMillis = TaxEngine.toMillis(next);
        boolean hasDays = false;
        for (Model.DailyPay x : c.days) if (!x.isEmpty()) hasDays = true;
        if (hasDays) {   // shift-based: next period's payslip isn't known yet
            c.actualGrossPay = null; c.actualPayReceived = null; c.actualTaxAmount = null;
            c.actualNiAmount = null; c.actualPensionAmount = null; c.actualExtraDeductions = null;
        }
        Store.entries.add(c);
        Store.changed();
        final String id = c.id;
        a.snack("Copied to " + next.format(MD), "Edit", new Runnable() {
            @Override public void run() { if (Store.find(id) != null) a.push(new EntryEditorPage(id, null)); }
        });
    }

    public static void delete(final MainActivity a, final PayEntry e) {
        final int idx = Store.entries.indexOf(e);
        Store.entries.remove(e);
        Store.changed();
        a.snack("Pay period deleted", "Undo", new Runnable() {
            @Override public void run() {
                if (Store.find(e.id) == null) { Store.entries.add(Math.max(0, Math.min(idx, Store.entries.size())), e); Store.changed(); }
            }
        });
    }

    // ------------------------------------------------------------------ empty

    private View emptyState() {
        LinearLayout c = Ui.card();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(Ui.dp(24), Ui.dp(32), Ui.dp(24), Ui.dp(28));
        c.addView(Ui.badge("receipt_long", Theme.accent, 72));
        TextView t = Ui.text("No shifts logged yet", 20, 700, Theme.text);
        t.setPadding(0, Ui.dp(18), 0, Ui.dp(6));
        c.addView(t);
        TextView s2 = Ui.text("Add your first shift to see your HMRC take-home pay, or bring your data over from your iPhone.", 15, 400, Theme.text2);
        s2.setGravity(Gravity.CENTER);
        s2.setLineSpacing(0, 1.15f);
        c.addView(s2);
        c.addView(Ui.space(20));
        c.addView(Ui.primary("Add shift", "add", new Runnable() { @Override public void run() { AddHub.show(a); } }), Ui.lp(Ui.MATCH, Ui.WRAP));
        c.addView(Ui.space(10));
        c.addView(Ui.tonal("Import iPhone backup", "download", new Runnable() { @Override public void run() { a.startImport(); } }), Ui.lp(Ui.MATCH, Ui.WRAP));
        return c;
    }
}
