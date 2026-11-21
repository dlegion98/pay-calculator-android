package com.dayoadekoya.paycalculator;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * One UI style page frame: a tall "viewing area" with a large left-aligned title that
 * collapses into a compact toolbar as you scroll into the "interaction area".
 */
public class Scaffold extends FrameLayout {
    public final ScrollView scroll;
    public final LinearLayout content;
    public final LinearLayout body;
    public final LinearLayout toolbar;
    public final LinearLayout actions;
    public final TextView smallTitle, largeTitle, subtitle;
    private final FrameLayout header;
    private final LinearLayout headerText;
    private final Glass.Frost tbWrap;
    public int extraBottomDp = 24;
    private int contentPad = -1;
    private final boolean hasBack;
    private final float expandedDp;
    public Ui.Fn<Integer> onScroll;

    public Scaffold(Context c, String title, String sub, final Runnable onBack, float expandedDp) {
        super(c);
        this.hasBack = onBack != null;
        this.expandedDp = expandedDp;
        setBackground(new Glass.Aurora());

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.setClipToPadding(false);
        content = Ui.v();
        scroll.addView(content, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        addView(scroll, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        header = new FrameLayout(c);
        headerText = Ui.v();
        headerText.setPadding(Ui.dp(20), 0, Ui.dp(20), Ui.dp(8));
        // iOS large title
        largeTitle = Ui.single(title, 34, 700, Theme.text);
        largeTitle.setLetterSpacing(-0.015f);
        headerText.addView(largeTitle);
        subtitle = Ui.text(sub == null ? "" : sub, 15, 400, Theme.text2);
        subtitle.setPadding(0, Ui.dp(1), 0, 0);
        subtitle.setVisibility(sub == null || sub.isEmpty() ? View.GONE : View.VISIBLE);
        headerText.addView(subtitle);
        header.addView(headerText, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM));
        content.addView(header, new LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(200)));

        body = Ui.v();
        body.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(24));
        body.setClipChildren(false);
        content.addView(body, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        // Collapsing toolbar overlay
        tbWrap = new Glass.Frost(c, 0, false).plain();
        tbWrap.setTarget(this);
        tbWrap.setBackdropAlpha(0f);
        // iOS navigation bar: "‹ Back" on the left, centred inline title, tinted actions on the right
        smallTitle = Ui.single(title, 17, 600, Theme.text);
        smallTitle.setAlpha(0f);
        smallTitle.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams stp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.dp(44), Gravity.BOTTOM);
        stp.leftMargin = stp.rightMargin = Ui.dp(96);
        tbWrap.addView(smallTitle, stp);
        toolbar = Ui.h();
        toolbar.setPadding(Ui.dp(hasBack ? 4 : 16), 0, Ui.dp(8), 0);
        if (hasBack) {
            LinearLayout back = Ui.h();
            back.addView(Ui.icon("chevron_left", Theme.accent, 30));
            TextView bt = Ui.text("Back", 17, 400, Theme.accent);
            bt.setPadding(0, 0, Ui.dp(8), 0);
            back.addView(bt);
            back.setPadding(0, Ui.dp(6), 0, Ui.dp(6));
            back.setContentDescription("Back");
            Ui.tap(back, 10, onBack);
            toolbar.addView(back);
        }
        toolbar.addView(Ui.flex());
        actions = Ui.h();
        toolbar.addView(actions, new LinearLayout.LayoutParams(Ui.WRAP, Ui.dp(44)));
        tbWrap.addView(toolbar, new FrameLayout.LayoutParams(Ui.MATCH, Ui.dp(44), Gravity.BOTTOM));
        addView(tbWrap, new FrameLayout.LayoutParams(Ui.MATCH, Ui.dp(44)));

        scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override public void onScrollChange(View v, int x, int y, int ox, int oy) { onScrolled(y); if (onScroll != null) onScroll.on(y - oy); }
        });
        applyInsets();
    }

    public void setTitle(String t) { largeTitle.setText(t); smallTitle.setText(t); }

    public void setSubtitle(String s) {
        boolean was = subtitle.getVisibility() == View.VISIBLE;
        subtitle.setText(s);
        subtitle.setVisibility(s == null || s.isEmpty() ? View.GONE : View.VISIBLE);
        if (was != (subtitle.getVisibility() == View.VISIBLE)) applyInsets();
    }

    public ImageView addAction(String icon, String desc, Runnable r) {
        // iOS bar buttons: plain tinted glyphs
        ImageView b = Ui.iconButton(icon, Theme.accent, r);
        b.setContentDescription(desc);
        b.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
        actions.addView(b, new LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(44)));
        return b;
    }

    public void applyInsets() {
        int tb = Ui.dp(44);
        int heightDp = getResources().getConfiguration().screenHeightDp;
        boolean shortWin = heightDp > 0 && heightDp < 560;
        // iOS: the large title sits directly under the bar (no tall One UI viewing area)
        int expanded = Ui.dp((shortWin ? 44 : 52) + (subtitle.getVisibility() == View.VISIBLE ? 24 : 0));
        largeTitle.setTextSize(shortWin ? 28 : 34);
        tbWrap.getLayoutParams().height = Ui.insetTop + tb;
        tbWrap.requestLayout();
        header.getLayoutParams().height = Ui.insetTop + tb + expanded;
        header.requestLayout();
        int pad = contentPad >= 0 ? contentPad : Ui.dp(16);
        body.setPadding(pad, Ui.dp(8), pad, Ui.insetBottom + Ui.dp(extraBottomDp));
        headerText.setPadding(pad + Ui.dp(4), 0, pad + Ui.dp(4), Ui.dp(8));
        onScrolled(scroll.getScrollY());
    }

    private void onScrolled(int y) {
        int range = Math.max(1, header.getLayoutParams().height - (Ui.insetTop + Ui.dp(44)));
        float p = Math.max(0, Math.min(1, y / (float) range));
        // iOS: large title scrolls under the bar; inline title + bar material fade in at the edge
        headerText.setAlpha(Math.max(0, 1 - p * 1.3f));
        headerText.setTranslationY(0);
        if (y < 0) { float sc = 1f + Math.min(0.08f, -y / (float) Ui.dp(900)); largeTitle.setPivotX(0); largeTitle.setScaleX(sc); largeTitle.setScaleY(sc); }
        smallTitle.setAlpha(Math.max(0, (p - 0.75f) / 0.25f));
        tbWrap.setBackdropAlpha(p >= 0.9f ? 1f : 0f);
    }

    public void clear() { body.removeAllViews(); }

    public void setContentPadding(int px) {
        contentPad = px;
        body.setPadding(px, Ui.dp(8), px, body.getPaddingBottom());
        headerText.setPadding(px + Ui.dp(4), 0, px + Ui.dp(4), Ui.dp(8));
    }

    public void add(View v) { body.addView(v); }
}
