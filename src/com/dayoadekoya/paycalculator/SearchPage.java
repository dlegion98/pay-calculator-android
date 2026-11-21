package com.dayoadekoya.paycalculator;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dayoadekoya.paycalculator.Model.PayEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Search across every pay period (job, notes, month, tax code). Only the results list re-renders while typing. */
public class SearchPage extends Page {
    private String query = "";
    private EditText field;
    private LinearLayout results;

    @Override public String title() { return "Search"; }
    @Override protected float expandedDp() { return 40; }

    @Override protected void build() {
        LinearLayout box = Ui.h();
        box.setBackground(Glass.panel(50));
        box.setPadding(Ui.dp(16), Ui.dp(4), Ui.dp(6), Ui.dp(4));
        box.addView(Ui.icon("search", Theme.text2, 22));
        field = new EditText(a);
        field.setBackground(null);
        field.setHint("Job, note, month, amount or tax code");
        field.setHintTextColor(Theme.text3);
        field.setTextColor(Theme.text);
        field.setSingleLine(true);
        field.setText(query);
        field.setTypeface(Ui.font(500));
        field.setPadding(Ui.dp(12), Ui.dp(12), Ui.dp(8), Ui.dp(12));
        box.addView(field, Ui.weight(1));
        box.addView(Ui.iconButton("close", Theme.text2, new Runnable() {
            @Override public void run() { field.setText(""); }
        }));
        box.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        add(box);
        results = Ui.v();
        add(results);
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence x, int b, int c, int d) {}
            @Override public void onTextChanged(CharSequence x, int b, int c, int d) {}
            @Override public void afterTextChanged(Editable x) {
                query = x.toString();
                refreshResults();
                final String snap = query.trim();
                field.removeCallbacks(saveRecent);
                if (snap.length() >= 2) field.postDelayed(saveRecent, 1500);
            }
        });
        refreshResults();
    }

    @Override public void onShown() {
        field.requestFocus();
        field.postDelayed(new Runnable() {
            @Override public void run() {
                InputMethodManager imm = (InputMethodManager) a.getSystemService(android.app.Activity.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 300);
    }

    /** Remember a search once the user pauses typing. */
    private final Runnable saveRecent = new Runnable() {
        @Override public void run() {
            String q = query.trim();
            if (q.length() < 2) return;
            List<String> r = recents();
            for (int i = r.size() - 1; i >= 0; i--) if (r.get(i).equalsIgnoreCase(q) || q.toLowerCase(Locale.UK).startsWith(r.get(i).toLowerCase(Locale.UK))) r.remove(i);
            r.add(0, q);
            while (r.size() > 6) r.remove(r.size() - 1);
            Store.put("recentSearches", android.text.TextUtils.join("\u001F", r));
        }
    };

    private static List<String> recents() {
        List<String> out = new ArrayList<String>();
        String raw = Store.str("recentSearches", "");
        if (!raw.isEmpty()) for (String x : raw.split("\u001F")) if (!x.trim().isEmpty()) out.add(x);
        return out;
    }

    private View chipRow(String title, List<String> items, final boolean clearable) {
        LinearLayout box = Ui.v();
        LinearLayout head = Ui.h();
        head.setPadding(Ui.dp(6), 0, Ui.dp(6), Ui.dp(8));
        head.addView(Ui.caption(title), Ui.weight(1));
        if (clearable) {
            TextView clr = Ui.text("Clear", 14, 500, Theme.accent);
            Ui.click(clr, new Runnable() { @Override public void run() { Store.put("recentSearches", ""); refreshResults(); } });
            head.addView(clr);
        }
        box.addView(head);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.h();
        for (final String it : items) {
            View ch = Ui.chip(clearable ? "history" : "work", it, clearable ? Theme.text2 : Ui.jobColor(it));
            Ui.tap(ch, 50, new Runnable() { @Override public void run() { field.setText(it); field.setSelection(it.length()); } });
            row.addView(ch, Ui.lpm(Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        }
        hs.addView(row);
        box.addView(hs);
        box.setLayoutParams(Ui.lpm(Ui.MATCH, Ui.WRAP, 0, 0, 0, 14));
        return box;
    }

    public void refreshResults() {
        if (results == null) return;
        results.removeAllViews();
        String q = query.trim().toLowerCase(Locale.UK);
        if (q.isEmpty() && !Store.entries.isEmpty()) {
            List<String> rec = recents();
            if (!rec.isEmpty()) results.addView(chipRow("Recent", rec, true));
            List<String> jobs = new ArrayList<String>();
            for (PayEntry e : Store.entries) {
                boolean dup = e.jobName.trim().isEmpty();
                for (String j : jobs) if (j.equalsIgnoreCase(e.jobName)) dup = true;
                if (!dup && jobs.size() < 10) jobs.add(e.jobName);
            }
            if (jobs.size() > 1) results.addView(chipRow("Jobs", jobs, false));
        }
        // Amount search: "432" or "£432.10" matches take-home or gross
        String digits = q.replace(Store.currency().toLowerCase(Locale.UK), "").replace(",", "").trim();
        final boolean amountQuery = digits.matches("\\d+(\\.\\d{0,2})?");
        List<PayEntry> hits = new ArrayList<PayEntry>();
        for (PayEntry e : Store.entries) {
            if (q.isEmpty()) { hits.add(e); continue; }
            String month = TaxEngine.toDate(e.dateMillis).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK)).toLowerCase(Locale.UK);
            boolean amountHit = amountQuery && (String.format(Locale.UK, "%.2f", TaxEngine.net(e)).startsWith(digits)
                    || String.format(Locale.UK, "%.2f", TaxEngine.effectiveGross(e)).startsWith(digits));
            if (amountHit || e.jobName.toLowerCase(Locale.UK).contains(q) || e.notes.toLowerCase(Locale.UK).contains(q)
                    || month.contains(q) || e.taxCode.toLowerCase(Locale.UK).contains(q)) hits.add(e);
        }
        if (hits.isEmpty()) {
            TextView t = Ui.text(Store.entries.isEmpty() ? "No pay records yet." : "No results for “" + query.trim() + "”.", 15, 500, Theme.text2);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(40), 0, Ui.dp(40));
            results.addView(t, Ui.lp(Ui.MATCH, Ui.WRAP));
            return;
        }
        double net = 0;
        for (PayEntry e : hits) net += TaxEngine.net(e);
        TextView sum = Ui.text(hits.size() + (hits.size() == 1 ? " pay period" : " pay periods") + " · " + Ui.money(net) + " take-home", 13.5f, 600, Theme.text2);
        sum.setPadding(Ui.dp(10), 0, Ui.dp(10), Ui.dp(10));
        results.addView(sum);
        LinearLayout card = Ui.group();
        card.setPadding(0, 0, 0, 0);
        int shown = 0;
        for (PayEntry e : hits) {
            if (shown++ >= 150) break;
            if (card.getChildCount() > 0) card.addView(Ui.divider(18));
            card.addView(DashboardPage.recordRow(a, e));
        }
        results.addView(card);
    }
}
