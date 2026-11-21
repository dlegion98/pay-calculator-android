package com.dayoadekoya.paycalculator;

import android.view.View;

/** A screen: either a tab or a pushed full-screen page. */
public abstract class Page {
    protected MainActivity a;
    protected Scaffold s;
    protected boolean isTab;
    private int animIndex;

    public abstract String title();
    public String subtitle() { return null; }
    protected float expandedDp() { return isTab ? 118 : 84; }
    protected abstract void build();
    protected void actions() {}

    public View create(MainActivity act, boolean tab) {
        a = act;
        isTab = tab;
        s = new Scaffold(act, title(), subtitle(), tab ? null : new Runnable() {
            @Override public void run() { a.onBackPressed(); }
        }, expandedDp());
        if (tab) s.extraBottomDp = 120;
        actions();
        render(true);
        return s;
    }

    public void render(boolean animate) {
        if (s == null) return;
        s.clear();
        animIndex = 0;
        try {
            build();
        } catch (Throwable t) {
            MainActivity.reportError(t);
        }
        try { adapt(); } catch (Throwable t) { t.printStackTrace(); }
        if (animate) {
            for (int i = 0; i < s.body.getChildCount(); i++) Ui.enter(s.body.getChildAt(i), i);
        }
    }

    // ------------------------------------------------------------------ adaptive width

    /** Centres content on wide windows and flows tab content into two columns when there's room. */
    private void adapt() {
        int avail = a.availableWidthDp(this);
        int cols = isTab && avail >= 680 ? 2 : 1;
        int maxW = cols == 2 ? 1180 : 720;
        int content = Math.min(avail, maxW);
        int padDp = Math.max(16, (avail - content) / 2 + 16);
        s.setContentPadding(Ui.dp(padDp));
        if (cols == 2) reflow(Ui.dp(avail) - 2 * Ui.dp(padDp));
    }

    private static final class Unit {
        java.util.List<View> views = new java.util.ArrayList<View>();
        boolean span;
    }

    private void reflow(int innerPx) {
        android.widget.LinearLayout body = s.body;
        java.util.List<View> kids = new java.util.ArrayList<View>();
        for (int i = 0; i < body.getChildCount(); i++) kids.add(body.getChildAt(i));
        body.removeAllViews();
        java.util.List<Unit> units = new java.util.ArrayList<Unit>();
        View pendingSection = null;
        for (View v : kids) {
            Object tag = v.getTag();
            if (pendingSection != null) {
                Unit u = new Unit();
                u.views.add(pendingSection);
                u.views.add(v);
                u.span = "span".equals(tag);
                units.add(u);
                pendingSection = null;
            } else if ("section".equals(tag)) {
                pendingSection = v;
            } else if ("follow".equals(tag) && !units.isEmpty()) {
                units.get(units.size() - 1).views.add(v);
            } else {
                Unit u = new Unit();
                u.views.add(v);
                u.span = "span".equals(tag);
                units.add(u);
            }
        }
        if (pendingSection != null) { Unit u = new Unit(); u.views.add(pendingSection); units.add(u); }

        int gap = Ui.dp(14);
        int colW = (innerPx - gap) / 2;
        android.widget.LinearLayout c1 = null, c2 = null;
        int h1 = 0, h2 = 0;
        for (Unit u : units) {
            if (u.span) {
                c1 = null; c2 = null;
                for (View v : u.views) body.addView(v);
                continue;
            }
            if (c1 == null) {
                android.widget.LinearLayout pair = Ui.h();
                pair.setGravity(android.view.Gravity.TOP);
                c1 = Ui.v(); c2 = Ui.v();
                android.widget.LinearLayout.LayoutParams l1 = new android.widget.LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
                l1.rightMargin = gap / 2;
                android.widget.LinearLayout.LayoutParams l2 = new android.widget.LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
                l2.leftMargin = gap / 2;
                pair.addView(c1, l1);
                pair.addView(c2, l2);
                body.addView(pair, new android.widget.LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
                h1 = 0; h2 = 0;
            }
            int h = 0;
            for (View v : u.views) h += measure(v, colW);
            android.widget.LinearLayout target = h1 <= h2 ? c1 : c2;
            if (target == c1) h1 += h; else h2 += h;
            for (View v : u.views) target.addView(v);
        }
    }

    private static int measure(View v, int width) {
        try {
            int ml = 0, mr = 0, mt = 0, mb = 0;
            if (v.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                android.view.ViewGroup.MarginLayoutParams m = (android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams();
                ml = m.leftMargin; mr = m.rightMargin; mt = m.topMargin; mb = m.bottomMargin;
            }
            v.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1, width - ml - mr), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            return v.getMeasuredHeight() + mt + mb;
        } catch (Throwable t) {
            return Ui.dp(200);
        }
    }

    /** Layout hints for the two-column flow. */
    protected static View span(View v) { v.setTag("span"); return v; }
    protected static View follow(View v) { v.setTag("follow"); return v; }

    public void refresh() { render(false); }

    /** Pages with editable drafts override this so navigation can warn before discarding. */
    public boolean hasUnsavedChanges() { return false; }

    /** @return true if the page consumed the back press. */
    public boolean onBack() { return false; }

    public void onShown() {}

    protected void add(View v) { s.body.addView(v); }
}
