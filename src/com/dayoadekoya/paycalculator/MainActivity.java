package com.dayoadekoya.paycalculator;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.biometrics.BiometricPrompt;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    public static MainActivity current;

    public static final int REQ_EXPORT_JSON = 101, REQ_IMPORT_JSON = 102, REQ_EXPORT_CSV = 103, REQ_EXPORT_PDF = 104;

    private FrameLayout root, tabHost, pageLayer, snackLayer, sidePane;
    private LinearLayout nav, rail, row, dock;
    private Glass.Frost navBar;
    private View navLens;
    private boolean dockCompact;
    private View paneDivider;
    private int insetLeft, insetRight;
    /** Adaptive layout state (Material window size classes). */
    private boolean useRail, twoPane;
    private LinearLayout fab;
    private View lockOverlay;
    public static final int TAB_HOME = 0, TAB_CALENDAR = 1, TAB_SUBS = 2, TAB_INSIGHTS = 3, TAB_SETTINGS = 4, TAB_COUNT = 5;
    private final Page[] tabs = new Page[TAB_COUNT];
    private final View[] tabViews = new View[TAB_COUNT];
    private final List<Page> stack = new ArrayList<Page>();
    private final List<View> stackViews = new ArrayList<View>();
    private int currentTab = 0;
    private boolean unlocked, authenticating, internalNav;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int imeBottom, navInset;

    private static final Runnable DATA_LISTENER = new Runnable() {
        @Override public void run() { if (current != null && !current.isFinishing()) current.refreshAll(); }
    };

    private static final String[] TAB_NAMES = {"Home", "Calendar", "Subs", "Insights", "Settings"};
    private static final String[] TAB_A11Y = {"Home", "Calendar", "Subscriptions and bills", "Insights", "Settings"};
    private static final String[] TAB_ICONS = {"home", "calendar_month", "event_repeat", "bar_chart", "settings"};

    // ------------------------------------------------------------------ lifecycle

    private boolean superCreated;

    @Override protected void onCreate(Bundle state) {
        installCrashHandler();
        current = this;
        // If the previous run crashed, show the report first (plain views only) instead of looping.
        File crash = new File(getFilesDir(), "crash.txt");
        if (crash.exists()) {
            String txt = "";
            try { txt = Store.readAll(new java.io.FileInputStream(crash)); } catch (Throwable ignored) {}
            crash.delete();
            super.onCreate(state);
            superCreated = true;
            showDiagnostic(txt);
            return;
        }
        try {
            startUp(state);
            installLoopGuard();
        } catch (Throwable t) {
            if (!superCreated) { super.onCreate(state); superCreated = true; }
            showDiagnostic(stack(t));
        }
    }

    private void startUp(Bundle state) {
        Ui.init(this);
        Store.init(this);
        Theme.apply(this);
        setTheme(Theme.dark ? android.R.style.Theme_DeviceDefault_NoActionBar : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        super.onCreate(state);
        superCreated = true;
        Ui.init(this);

        root = new FrameLayout(this);
        root.setBackground(new Glass.Aurora());
        row = Ui.h();
        row.setGravity(Gravity.TOP);
        tabHost = new FrameLayout(this);
        row.addView(tabHost, new LinearLayout.LayoutParams(0, Ui.MATCH, 1f));
        paneDivider = new View(this);
        paneDivider.setBackgroundColor(Theme.divider);
        paneDivider.setVisibility(View.GONE);
        row.addView(paneDivider, new LinearLayout.LayoutParams(Math.max(1, Ui.dp(1)), Ui.MATCH));
        sidePane = new FrameLayout(this);
        sidePane.setVisibility(View.GONE);
        row.addView(sidePane, new LinearLayout.LayoutParams(0, Ui.MATCH));
        root.addView(row, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        buildFab();
        buildNav();
        buildRail();
        pageLayer = new FrameLayout(this);
        root.addView(pageLayer, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        snackLayer = new FrameLayout(this);
        snackLayer.setClickable(false);
        root.addView(snackLayer, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        setContentView(root);
        try { setupWindow(); } catch (Throwable t) { t.printStackTrace(); }

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                int top, bottom, ime = 0;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets sb = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    top = sb.top; bottom = sb.bottom;
                    insetLeft = sb.left; insetRight = sb.right;
                    ime = in.getInsets(WindowInsets.Type.ime()).bottom;
                } else {
                    top = in.getSystemWindowInsetTop(); bottom = in.getSystemWindowInsetBottom();
                    insetLeft = in.getSystemWindowInsetLeft(); insetRight = in.getSystemWindowInsetRight();
                    if (bottom > Ui.dp(100)) { ime = bottom; bottom = Ui.dp(24); }
                }
                navInset = bottom;
                imeBottom = ime;
                Ui.insetTop = top;
                // Resize the whole UI above the keyboard so focused fields scroll into view.
                root.setPadding(0, 0, 0, ime);
                Ui.insetBottom = ime > 0 ? 0 : bottom;
                applyInsets();
                return in;
            }
        });

        Store.addListener(DATA_LISTENER);
        showTab(state != null ? state.getInt("tab", 0) : 0, false);
        Reminders.ensureChannel(this);
        maybeShowWelcome();
        handleIntent(getIntent());
        try { Widgets.updateAll(this); } catch (Throwable ignored) {}
        publishShortcuts();
        handler.postDelayed(new Runnable() { @Override public void run() { if (!isFinishing()) Updater.checkAuto(MainActivity.this); } }, 4000);
    }

    // ------------------------------------------------------------------ keyboard (foldables, tablets, Chromebooks)

    @Override public boolean onKeyShortcut(int keyCode, android.view.KeyEvent event) {
        if (root == null || !event.isCtrlPressed()) return super.onKeyShortcut(keyCode, event);
        // Never throw away an open editor's changes from a shortcut
        for (Page pg : stack) if (pg.hasUnsavedChanges() && keyCode != android.view.KeyEvent.KEYCODE_N) { snack("Save or discard your changes first"); return true; }
        switch (keyCode) {
            case android.view.KeyEvent.KEYCODE_N: AddHub.show(this); return true;
            case android.view.KeyEvent.KEYCODE_F: popAll(); showTab(0, false); push(new SearchPage()); return true;
            case android.view.KeyEvent.KEYCODE_1: popAll(); showTab(0, true); return true;
            case android.view.KeyEvent.KEYCODE_2: popAll(); showTab(1, true); return true;
            case android.view.KeyEvent.KEYCODE_3: popAll(); showTab(2, true); return true;
            case android.view.KeyEvent.KEYCODE_4: popAll(); showTab(3, true); return true;
            case android.view.KeyEvent.KEYCODE_5: popAll(); showTab(4, true); return true;
        }
        return super.onKeyShortcut(keyCode, event);
    }

    @Override public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_ESCAPE && root != null) { onBackPressed(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (root != null) handleIntent(intent);
    }

    public static final String EXTRA_SHORTCUT = "shortcut";

    /** Long-press launcher shortcuts: Log pay, Start/stop shift, Search, Insights. */
    private void publishShortcuts() {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            android.content.pm.ShortcutManager sm = getSystemService(android.content.pm.ShortcutManager.class);
            if (sm == null) return;
            java.util.List<android.content.pm.ShortcutInfo> list = new java.util.ArrayList<android.content.pm.ShortcutInfo>();
            list.add(shortcut("add", "Log pay", "Add a shift or payslip", "add", Theme.accent));
            list.add(shortcut("timer", "Shift timer", "Start or check the shift timer", "timer", Theme.green));
            list.add(shortcut("search", "Search", "Search pay records", "search", Theme.indigo));
            list.add(shortcut("insights", "Insights", "Tax and earnings insights", "insights", Theme.orange));
            sm.setDynamicShortcuts(list);
        } catch (Throwable t) { t.printStackTrace(); }
    }

    private android.content.pm.ShortcutInfo shortcut(String id, String label, String longLabel, String icon, int color) {
        Intent i = new Intent(this, MainActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        i.putExtra(EXTRA_SHORTCUT, id);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return new android.content.pm.ShortcutInfo.Builder(this, id).setShortLabel(label).setLongLabel(longLabel)
                .setIcon(shortcutIcon(icon, color)).setIntent(i).build();
    }

    /** Adaptive icon: coloured background with a white glyph in the safe zone. */
    private android.graphics.drawable.Icon shortcutIcon(String name, int color) {
        int size = Ui.dp(108);
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        c.drawColor(color);
        android.graphics.drawable.Drawable d = getDrawable(Ui.iconRes(name));
        if (d != null) {
            d = d.mutate();
            d.setTint(0xFFFFFFFF);
            int g = Ui.dp(44), o = (size - g) / 2;
            d.setBounds(o, o, o + g, o + g);
            d.draw(c);
        }
        return android.graphics.drawable.Icon.createWithAdaptiveBitmap(b);
    }

    private void handleIntent(Intent intent) {
        final String sc = intent != null ? intent.getStringExtra(EXTRA_SHORTCUT) : null;
        if (sc != null) {
            intent.removeExtra(EXTRA_SHORTCUT);
            handler.postDelayed(new Runnable() { @Override public void run() {
                try {
                    popAll();
                    if ("add".equals(sc)) { showTab(0, false); AddHub.show(MainActivity.this); }
                    else if ("search".equals(sc)) { showTab(0, false); push(new SearchPage()); }
                    else if ("insights".equals(sc)) showTab(TAB_INSIGHTS, false);
                    else if ("bills".equals(sc)) showTab(TAB_SUBS, false);
                    else if ("timer".equals(sc)) {
                        showTab(0, false);
                        if (Shift.running()) snack("Shift running · " + Shift.clock(Shift.elapsedMs()) + ". Tap Stop & log on Home when you finish");
                        else { Shift.start(MainActivity.this, Shift.lastJob()); requestNotificationPermission(); Store.changed(); snack("Shift started · " + Shift.job()); }
                    }
                } catch (Throwable t) { reportError(t); }
            } }, 350);
            return;
        }
        if (intent != null && intent.getBooleanExtra(Widgets.EXTRA_OPEN_ADD, false)) {
            intent.removeExtra(Widgets.EXTRA_OPEN_ADD);
            handler.postDelayed(new Runnable() { @Override public void run() {
                try { popAll(); if (currentTab != 0) showTab(0, false); AddHub.show(MainActivity.this); } catch (Throwable t) { reportError(t); }
            } }, 350);
        }
    }

    // ------------------------------------------------------------------ diagnostics

    private static String stack(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static int guardFailures;
    private static boolean guardInstalled, crashHandlerInstalled;

    /** Catches exceptions on the main thread so we can show them instead of closing. Installed once per process. */
    private void installLoopGuard() {
        if (guardInstalled) return;
        guardInstalled = true;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                while (true) {
                    try {
                        Looper.loop();
                    } catch (Throwable e) {
                        guardFailures++;
                        MainActivity act = current;
                        if (guardFailures > 3 || act == null || act.isFinishing()) throw new RuntimeException(e);
                        try { act.showDiagnostic(stack(e)); } catch (Throwable e2) { throw new RuntimeException(e); }
                    }
                }
            }
        });
    }

    private void showDiagnostic(final String trace) {
        final String report = "Pay Calculator " + SettingsPage.VERSION + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL
                + "\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n\n" + trace;
        try { getWindow().getDecorView().setSystemUiVisibility(0); } catch (Throwable ignored) {}
        android.widget.LinearLayout l = new android.widget.LinearLayout(this);
        l.setOrientation(android.widget.LinearLayout.VERTICAL);
        l.setBackgroundColor(0xFFF3F3F6);
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * d);
        l.setPadding(pad, (int) (56 * d), pad, (int) (40 * d));
        TextView title = new TextView(this);
        title.setText("Pay Calculator hit a problem");
        title.setTextSize(22);
        title.setTextColor(0xFF111111);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        l.addView(title);
        TextView info = new TextView(this);
        info.setText("Your data is safe. Please tap Share report (or Copy) and send it so this can be fixed.");
        info.setTextSize(15);
        info.setTextColor(0xFF555555);
        info.setPadding(0, (int) (8 * d), 0, (int) (12 * d));
        l.addView(info);
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        android.widget.Button share = new android.widget.Button(this);
        share.setText("Share report");
        share.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            try { shareText("Pay Calculator crash report", report); } catch (Throwable ignored) {}
        }});
        android.widget.Button copy = new android.widget.Button(this);
        copy.setText("Copy");
        copy.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", report));
                android.widget.Toast.makeText(MainActivity.this, "Copied", android.widget.Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {}
        }});
        android.widget.Button retry = new android.widget.Button(this);
        retry.setText("Try again");
        retry.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            try { new File(getFilesDir(), "crash.txt").delete(); } catch (Throwable ignored) {}
            Intent i = new Intent(MainActivity.this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            Runtime.getRuntime().exit(0);
        }});
        row.addView(share);
        row.addView(copy);
        row.addView(retry);
        l.addView(row);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(report);
        tv.setTextSize(12);
        tv.setTextColor(0xFF222222);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        tv.setPadding(0, (int) (12 * d), 0, 0);
        sv.addView(tv);
        l.addView(sv, new android.widget.LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(l);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", currentTab);
    }

    @Override protected void onResume() {
        super.onResume();
        current = this;
        if (root == null) return; // diagnostic screen
        // Smart credit updates: roll cards forward to today (interest in, regular payment out, new minimum)
        try { if (BillsPage.rollAll()) Store.changed(); } catch (Throwable t) { t.printStackTrace(); }
        MeshSync.onChange = new Runnable() { @Override public void run() { if (tabs[TAB_SETTINGS] != null && currentTab == TAB_SETTINGS && stack.isEmpty()) tabs[TAB_SETTINGS].refresh(); } };
        try { MeshSync.start(this); } catch (Throwable t) { t.printStackTrace(); }
        internalNav = false;
        boolean lockOn = Store.bool("requireBiometrics", false);
        // Grace period: only re-lock if the app was away longer than the chosen delay
        if (lockOn && unlocked && awayAt > 0 && System.currentTimeMillis() - awayAt > Store.integer("lockGraceSeconds", 0) * 1000L) unlocked = false;
        awayAt = 0;
        // Keep pay figures out of the recent-apps preview while the app lock is on
        if (Build.VERSION.SDK_INT >= 33) { try { setRecentsScreenshotEnabled(!lockOn); } catch (Throwable ignored) {} }
        if (lockOn && !unlocked && !authenticating) showLock();
    }

    private long awayAt;

    @Override protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) { try { MeshSync.stop(); } catch (Throwable ignored) {} }
        // Re-lock when the user leaves the app — but not while we're showing our own
        // file picker / share sheet, or the system PIN screen during authentication.
        if (Store.bool("requireBiometrics", false) && !isChangingConfigurations() && !internalNav && !authenticating) {
            if (Store.integer("lockGraceSeconds", 0) <= 0) unlocked = false; else awayAt = System.currentTimeMillis();
        }
    }

    private void setupWindow() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getDecorView();
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(Theme.dark ? 0 : mask, mask);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (!Theme.dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void applyInsets() {
        applyLayoutMode();
        for (View v : tabViews) if (v instanceof Scaffold) ((Scaffold) v).applyInsets();
        for (View v : stackViews) if (v instanceof Scaffold) ((Scaffold) v).applyInsets();
    }

    // ------------------------------------------------------------------ adaptive layout (foldables, tablets, landscape)

    private static final int RAIL_DP = 88;

    public int screenWidthDp() { return getResources().getConfiguration().screenWidthDp; }

    private int sidePaneWidthDp() {
        int avail = screenWidthDp() - (useRail ? RAIL_DP : 0) - Math.round((insetLeft + insetRight) / Ui.density);
        return Math.max(360, Math.min(560, Math.round(avail * 0.52f)));
    }

    /** Width (dp) the given page currently has to lay itself out in. */
    public int availableWidthDp(Page p) {
        int w = screenWidthDp() - Math.round((insetLeft + insetRight) / Ui.density);
        boolean isTab = false;
        for (Page t : tabs) if (t == p) isTab = true;
        if (isTab) {
            if (useRail) w -= RAIL_DP;
            if (twoPane && !stack.isEmpty()) w -= sidePaneWidthDp() + 1;
            return w;
        }
        return twoPane ? sidePaneWidthDp() : w;
    }

    public boolean usesRail() { return useRail; }

    private void applyLayoutMode() {
        int w = screenWidthDp();
        boolean newRail = w >= 600;
        boolean newTwo = w >= 720;
        boolean changed = newRail != useRail || newTwo != twoPane;
        useRail = newRail;
        twoPane = newTwo;

        // Move open pages into the right container (overlay vs. side pane)
        FrameLayout target = twoPane ? sidePane : pageLayer;
        for (View v : stackViews) {
            if (v.getParent() != target) {
                if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
                target.addView(v, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
                v.setTranslationX(0); v.setAlpha(1f);
            }
        }
        boolean paneOpen = twoPane && !stack.isEmpty();
        sidePane.setVisibility(paneOpen ? View.VISIBLE : View.GONE);
        paneDivider.setVisibility(paneOpen ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams sp = (LinearLayout.LayoutParams) sidePane.getLayoutParams();
        sp.width = Ui.dp(sidePaneWidthDp());
        sidePane.setLayoutParams(sp);

        row.setPadding((useRail ? Ui.dp(RAIL_DP) : 0) + insetLeft, 0, insetRight, 0);

        // Bottom bar vs. navigation rail
        // Floating navigation bar (One UI 9 / Material You): a capsule above the gesture area
        FrameLayout.LayoutParams np = (FrameLayout.LayoutParams) dock.getLayoutParams();
        np.bottomMargin = navInset + Ui.dp(12);
        int side = Ui.dp(w < 360 ? 10 : 16);
        np.leftMargin = side + insetLeft;
        np.rightMargin = side + insetRight;
        LinearLayout.LayoutParams bp = (LinearLayout.LayoutParams) navBar.getLayoutParams();
        if (w >= 600) { np.width = Ui.WRAP; np.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL; bp.width = Ui.dp(460); bp.weight = 0; }
        else { np.width = Ui.MATCH; np.gravity = Gravity.BOTTOM; bp.width = 0; bp.weight = 1; }
        navBar.setLayoutParams(bp);
        dock.setLayoutParams(np);
        // The add FAB floats just above the bar, at the trailing edge
        FrameLayout.LayoutParams fp = (FrameLayout.LayoutParams) fab.getLayoutParams();
        fp.bottomMargin = navInset + Ui.dp(12 + 70 + 14);
        fp.rightMargin = side + insetRight + Ui.dp(4);
        fab.setLayoutParams(fp);
        boolean fabWanted = !useRail && stack.isEmpty() && imeBottom == 0 && currentTab != TAB_SETTINGS;
        if (!fabWanted) fab.setVisibility(View.GONE);
        else if (fab.getVisibility() != View.VISIBLE) { fab.setVisibility(View.VISIBLE); fab.setScaleX(1f); fab.setScaleY(1f); }
        placeLens(false);
        dock.setVisibility(!useRail && stack.isEmpty() && imeBottom == 0 ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams rp = (FrameLayout.LayoutParams) rail.getLayoutParams();
        rp.leftMargin = insetLeft + Ui.dp(10);
        rail.setLayoutParams(rp);
        rail.setVisibility(useRail && (twoPane || stack.isEmpty()) ? View.VISIBLE : View.GONE);
        for (int i = 0; i < nav.getChildCount(); i++) {
            if (!(nav.getChildAt(i) instanceof LinearLayout)) continue;
            View lbl = ((LinearLayout) nav.getChildAt(i)).getChildAt(1);
            if (lbl instanceof TextView) ((TextView) lbl).setTextSize(w < 360 ? 10.5f : 12f);
        }


        for (int i = 0; i < TAB_COUNT; i++) {
            if (tabViews[i] instanceof Scaffold) ((Scaffold) tabViews[i]).extraBottomDp = useRail ? 28 : (i == TAB_SETTINGS ? 104 : 176);
        }
        if (changed) {
            for (Page t : tabs) if (t != null) t.render(false);
        }
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        if (root == null) return;
        Ui.init(this);
        applyLayoutMode();
        for (int i = 0; i < TAB_COUNT; i++) {
            if (tabs[i] == null) continue;
            if (tabViews[i] instanceof Scaffold) ((Scaffold) tabViews[i]).applyInsets();
            tabs[i].render(false);
        }
        for (int i = 0; i < stack.size(); i++) {
            if (stackViews.get(i) instanceof Scaffold) ((Scaffold) stackViews.get(i)).applyInsets();
            stack.get(i).refresh();
        }
        root.requestApplyInsets();
    }

    // ------------------------------------------------------------------ tabs & nav

    private Page makeTab(int i) {
        switch (i) {
            case 0: return new DashboardPage();
            case 1: return new CalendarPage();
            case 2: return new BillsPage();
            case 3: return new AnalyticsPage();
            default: return new SettingsPage();
        }
    }

    public void showTab(int i, boolean animate) {
        if (tabs[i] == null) {
            tabs[i] = makeTab(i);
            tabViews[i] = tabs[i].create(this, true);
            tabHost.addView(tabViews[i], new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            if (tabViews[i] instanceof Scaffold) {
                ((Scaffold) tabViews[i]).extraBottomDp = useRail ? 28 : (i == TAB_SETTINGS ? 104 : 176);
                ((Scaffold) tabViews[i]).applyInsets();
            }
        } else if (i != currentTab) {
            // Refresh quietly: the page slide is the transition, so cards don't re-animate on top of it
            tabs[i].render(false);
        }
        // Shared-axis transition (Material You) with One UI's direction-aware slide.
        // Both pages move together on hardware layers, so it stays smooth even on busy screens.
        boolean motion = animate && i != currentTab && currentTab >= 0 && !Ui.reducedMotion();
        final int dir = i > currentTab ? 1 : -1;
        final int travel = Math.max(Ui.dp(48), (int) (root.getWidth() * 0.14f));
        for (int j = 0; j < TAB_COUNT; j++) {
            final View tv = tabViews[j];
            if (tv == null) continue;
            tv.animate().cancel();
            if (j == i) {
                tv.setVisibility(View.VISIBLE);
                tv.bringToFront();
                if (motion) {
                    tv.setAlpha(0f);
                    tv.setTranslationX(travel * dir);
                    tv.setScaleX(1f); tv.setScaleY(1f);
                    tv.animate().alpha(1f).translationX(0).setStartDelay(30).setDuration(400)
                            .setInterpolator(Ui.EMPHASIZED).withLayer().withEndAction(null).start();
                } else {
                    tv.setAlpha(1f); tv.setScaleX(1f); tv.setScaleY(1f); tv.setTranslationX(0);
                }
            } else if (motion && j == currentTab && tv.getVisibility() == View.VISIBLE) {
                tv.animate().alpha(0f).translationX(-travel * 0.45f * dir).setStartDelay(0).setDuration(190)
                        .setInterpolator(new android.view.animation.AccelerateInterpolator(1.6f)).withLayer()
                        .withEndAction(new Runnable() {
                            @Override public void run() {
                                tv.setVisibility(View.GONE);
                                tv.setAlpha(1f); tv.setScaleX(1f); tv.setScaleY(1f); tv.setTranslationX(0);
                            }
                        }).start();
            } else {
                tv.setVisibility(View.GONE);
                tv.setAlpha(1f); tv.setScaleX(1f); tv.setScaleY(1f); tv.setTranslationX(0);
            }
        }
        currentTab = i;
        updateNav(animate);
        setFabVisible(true, animate);
        tabs[i].onShown();

    }

    private void buildRail() {
        rail = Ui.v();
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setBackground(Glass.panel(36));
        if (!Theme.dark) { rail.setElevation(Ui.dp(8)); rail.setOutlineSpotShadowColor(0x26000000); rail.setOutlineAmbientShadowColor(0x14000000); }
        rail.setPadding(Ui.dp(6), Ui.dp(12), Ui.dp(6), Ui.dp(14));
        // M3 rail: the primary action sits at the top
        LinearLayout add = Ui.h();
        add.setGravity(Gravity.CENTER);
        add.setBackground(Glass.gloss(Theme.accent, 20));
        add.addView(Ui.icon("add", Theme.onAccent, 26));
        Ui.tap(add, 18, new Runnable() { @Override public void run() { AddHub.show(MainActivity.this); } });
        add.setContentDescription("Add pay");
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(Ui.dp(56), Ui.dp(56));
        ap.bottomMargin = Ui.dp(18);
        rail.addView(add, ap);
        for (int i = 0; i < TAB_COUNT; i++) {
            LinearLayout item = navItem(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(68), Ui.WRAP);
            lp.bottomMargin = Ui.dp(10);
            rail.addView(item, lp);
        }
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(Ui.dp(RAIL_DP - 12), Ui.WRAP, Gravity.START | Gravity.CENTER_VERTICAL);
        p.leftMargin = Ui.dp(10);
        rail.setVisibility(View.GONE);
        root.addView(rail, p);
    }

    private LinearLayout navItem(final int idx) {
        LinearLayout item = Ui.v();
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setGravity(Gravity.CENTER);
        FrameLayout pill = new FrameLayout(this);
        ImageView iv = Ui.icon(TAB_ICONS[idx], Theme.text2, 24);
        pill.addView(iv, new FrameLayout.LayoutParams(Ui.dp(24), Ui.dp(24), Gravity.CENTER));
        item.addView(pill, new LinearLayout.LayoutParams(Ui.dp(56), Ui.dp(32)));
        TextView label = Ui.text(TAB_NAMES[idx], 12, 500, Theme.text2);
        label.setPadding(0, Ui.dp(3), 0, 0);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        item.addView(label, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        item.setContentDescription(TAB_A11Y[idx] + " tab");
        Ui.click(item, new Runnable() {
            @Override public void run() {
                Ui.haptic(nav != null ? nav : root);
                if (idx == currentTab) {
                    View v = tabViews[idx];
                    if (v instanceof Scaffold) ((Scaffold) v).scroll.smoothScrollTo(0, 0);
                } else if (twoPane && !stack.isEmpty()) {
                    boolean unsaved = false;
                    for (Page pg : stack) if (pg.hasUnsavedChanges()) unsaved = true;
                    if (unsaved) {
                        Sheets.confirm(MainActivity.this, "Discard changes?", "You have unsaved changes to this pay period.", "Discard", true, new Runnable() {
                            @Override public void run() { closePaneQuietly(); showTab(idx, true); }
                        });
                    } else { closePaneQuietly(); showTab(idx, true); }
                } else {
                    showTab(idx, true);
                }
            }
        });
        item.setContentDescription(TAB_A11Y[idx]);
        item.setTag("nav" + idx);
        return item;
    }

    /** In two-pane mode, switching tabs closes the detail pane. */
    private void closePaneQuietly() {
        for (View v : stackViews) sidePane.removeView(v);
        stack.clear();
        stackViews.clear();
        applyLayoutMode();
    }

    /** iOS tab bar: translucent material with a hairline top, four tabs and a central add button. */
    /** Floating navigation bar: a frosted capsule whose indicator pill slides between tabs. */
    private void buildNav() {
        dock = Ui.h();
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        navBar = new Glass.Frost(this, 35, true);
        navBar.setTarget(root);
        shadow(navBar);
        // One indicator for the whole bar (Material You pill that moves like One UI 9's)
        navLens = new View(this);
        navLens.setBackground(Glass.tinted(Theme.accent, 16));
        navLens.setVisibility(View.INVISIBLE);
        navBar.addView(navLens, new FrameLayout.LayoutParams(Ui.dp(56), Ui.dp(32), Gravity.TOP | Gravity.START));
        nav = Ui.h();
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(Ui.dp(4), 0, Ui.dp(4), 0);
        for (int i = 0; i < TAB_COUNT; i++) nav.addView(navItem(i), new LinearLayout.LayoutParams(0, Ui.MATCH, 1f));
        navBar.addView(nav, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        nav.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (r - l != or - ol || b - t != ob - ot) placeLens(false);
            }
        });
        dock.addView(navBar, new LinearLayout.LayoutParams(0, Ui.dp(70), 1f));
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        p.setMargins(Ui.dp(16), 0, Ui.dp(16), Ui.dp(12));
        root.addView(dock, p);
        root.addView(fab, new FrameLayout.LayoutParams(Ui.dp(60), Ui.dp(60), Gravity.BOTTOM | Gravity.END));
    }

    private View navItemView(int idx) {
        if (nav == null) return null;
        for (int c = 0; c < nav.getChildCount(); c++) if (("nav" + idx).equals(nav.getChildAt(c).getTag())) return nav.getChildAt(c);
        return null;
    }

    /** Moves the indicator under the selected tab, stretching as it travels. */
    private void placeLens(boolean animate) {
        if (navLens == null || nav == null) return;
        final View item = navItemView(currentTab);
        if (item == null) return;
        if (item.getWidth() == 0) {
            nav.post(new Runnable() { @Override public void run() { View it = navItemView(currentTab); if (it != null && it.getWidth() > 0) placeLens(false); } });
            return;
        }
        View pill = ((LinearLayout) item).getChildAt(0);
        float x = nav.getLeft() + item.getLeft() + pill.getLeft();
        float y = nav.getTop() + item.getTop() + pill.getTop();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) navLens.getLayoutParams();
        if (lp.width != pill.getWidth() || lp.height != pill.getHeight()) { lp.width = pill.getWidth(); lp.height = pill.getHeight(); navLens.setLayoutParams(lp); }
        boolean placed = navLens.getVisibility() == View.VISIBLE;
        navLens.setVisibility(View.VISIBLE);
        if (lensAnim != null) lensAnim.cancel();
        if (animate && placed && !Ui.reducedMotion()) {
            float dist = Math.abs(x - navLens.getTranslationX());
            float stretch = 1f + Math.min(0.9f, dist / Ui.dp(160));
            navLens.setPivotX(pill.getWidth() / 2f);
            lensAnim = android.animation.ObjectAnimator.ofPropertyValuesHolder(navLens,
                    android.animation.PropertyValuesHolder.ofFloat(View.TRANSLATION_X, x),
                    android.animation.PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, y),
                    android.animation.PropertyValuesHolder.ofKeyframe(View.SCALE_X,
                            android.animation.Keyframe.ofFloat(0f, 1f),
                            android.animation.Keyframe.ofFloat(0.4f, stretch),
                            android.animation.Keyframe.ofFloat(1f, 1f)),
                    android.animation.PropertyValuesHolder.ofKeyframe(View.SCALE_Y,
                            android.animation.Keyframe.ofFloat(0f, 1f),
                            android.animation.Keyframe.ofFloat(0.4f, 0.82f),
                            android.animation.Keyframe.ofFloat(1f, 1f)));
            lensAnim.setDuration(480);
            lensAnim.setInterpolator(Ui.EMPHASIZED);
            lensAnim.start();
        } else {
            navLens.setTranslationX(x);
            navLens.setTranslationY(y);
            navLens.setScaleX(1f);
            navLens.setScaleY(1f);
        }
    }

    private android.animation.ObjectAnimator lensAnim;

    private static void shadow(View v) {
        if (Theme.dark) { v.setElevation(0); return; }
        v.setElevation(Ui.dp(10));
        v.setOutlineSpotShadowColor(0x2E000000);
        v.setOutlineAmbientShadowColor(0x1A000000);
    }

    /** Slides the glass lens under the selected tab. */

    private void updateNav(boolean animate) {
        updateNavIn(nav, animate);
        placeLens(animate);
        if (rail != null) updateNavIn(rail, animate);
    }

    private void updateNavIn(LinearLayout container, boolean animate) {
        for (int c = 0; c < container.getChildCount(); c++) {
            Object tag = container.getChildAt(c).getTag();
            if (!(tag instanceof String) || !((String) tag).startsWith("nav")) continue;
            int i = Integer.parseInt(((String) tag).substring(3));
            LinearLayout item = (LinearLayout) container.getChildAt(c);
            FrameLayout pill = (FrameLayout) item.getChildAt(0);
            ImageView iv = (ImageView) pill.getChildAt(0);
            TextView label = (TextView) item.getChildAt(1);
            boolean sel = i == currentTab;
            item.setSelected(sel);
            iv.setImageResource(Ui.iconRes(TAB_ICONS[i] + (sel ? "_fill" : "")));
            boolean inDock = container == nav;
            int on = inDock ? (Theme.dark ? Theme.blend(Theme.accent, 0xFFFFFFFF, 0.25f) : Theme.blend(Theme.accent, 0xFF000000, 0.15f)) : Theme.onAccent;
            int off = Theme.text2;
            iv.setImageTintList(android.content.res.ColorStateList.valueOf(sel ? on : off));
            label.setTextColor(sel ? (inDock ? on : Theme.accent) : off);
            label.setTypeface(Ui.font(sel ? 700 : 500));
            label.setVisibility(inDock && dockCompact ? View.GONE : View.VISIBLE);
            // Material You active indicator: a tonal pill that grows out from the icon
            pill.setBackground(sel && !inDock ? Glass.gloss(Theme.accent, 16) : null);
            if (sel && animate && !Ui.reducedMotion()) {
                if (!inDock) {
                    pill.setScaleX(0.35f); pill.setScaleY(0.9f); pill.setAlpha(0.4f);
                    pill.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(520).setInterpolator(Ui.SPRING).start();
                }
                // One UI-style icon bounce
                iv.setScaleX(0.75f); iv.setScaleY(0.75f); iv.setTranslationY(Ui.dp(3));
                iv.animate().scaleX(1f).scaleY(1f).translationY(0).setStartDelay(40).setDuration(560).setInterpolator(Ui.SPRING).start();
                label.setAlpha(0.3f);
                label.animate().alpha(1f).setDuration(260).start();
            } else if (!sel) {
                pill.animate().cancel(); pill.setScaleX(1f); pill.setScaleY(1f); pill.setAlpha(1f);
                iv.animate().cancel(); iv.setScaleX(1f); iv.setScaleY(1f); iv.setTranslationY(0);
            }
        }
    }

    private void buildFab() {
        fab = Ui.h();
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(Glass.gloss(Theme.accent, 22));
        shadow(fab);
        if (!Theme.dark) fab.setOutlineSpotShadowColor(Theme.withAlpha(Theme.accent, 0.55f));
        fab.addView(Ui.icon("add", Theme.onAccent, 28));
        Ui.tap(fab, 22, new Runnable() {
            @Override public void run() {
                if (!Ui.reducedMotion()) fab.animate().rotation(fab.getRotation() + 90).setDuration(420).setInterpolator(Ui.SPRING).start();
                if (currentTab == TAB_SUBS && tabs[TAB_SUBS] instanceof BillsPage) ((BillsPage) tabs[TAB_SUBS]).addNew();
                else AddHub.show(MainActivity.this);
            }
        });
        fab.setContentDescription("Add pay");
    }

    /** iOS tab bars don't minimise; kept for callers. */
    public void setFabExpanded(boolean expanded) {}

    /** The + button shows on every tab except Settings, and scales in/out as tabs change. */
    private void setFabVisible(boolean visible, boolean animate) {
        visible = visible && !useRail && stack.isEmpty() && currentTab != TAB_SETTINGS;
        fab.animate().cancel();
        if (!visible) {
            if (fab.getVisibility() != View.VISIBLE) return;
            if (animate && !Ui.reducedMotion()) {
                fab.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(160).setInterpolator(Ui.EMPHASIZED)
                        .withEndAction(new Runnable() { @Override public void run() { fab.setVisibility(View.GONE); fab.setAlpha(1f); } }).start();
            } else fab.setVisibility(View.GONE);
            return;
        }
        boolean wasHidden = fab.getVisibility() != View.VISIBLE;
        fab.setVisibility(View.VISIBLE);
        fab.setContentDescription(currentTab == TAB_SUBS ? "Add subscription or bill" : "Add pay");
        if (animate && wasHidden && !Ui.reducedMotion()) {
            fab.setScaleX(0.4f); fab.setScaleY(0.4f); fab.setAlpha(0f);
            fab.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(420).setInterpolator(Ui.SPRING).start();
        } else { fab.setScaleX(1f); fab.setScaleY(1f); fab.setAlpha(1f); }
    }

    /** Opens the subscriptions tab with the add sheet. */
    public void addSubscription() {
        popAll();
        showTab(TAB_SUBS, true);
        if (tabs[TAB_SUBS] instanceof BillsPage) ((BillsPage) tabs[TAB_SUBS]).addNew();
    }

    // ------------------------------------------------------------------ page stack

    public void push(Page p) {
        boolean wasEmpty = stack.isEmpty();
        stack.add(p);
        View v = p.create(this, false);
        stackViews.add(v);
        (twoPane ? sidePane : pageLayer).addView(v, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        if (v instanceof Scaffold) ((Scaffold) v).applyInsets();
        v.setElevation(Ui.dp(4));
        v.setTranslationX(Ui.dp(twoPane ? 60 : 0) + (twoPane ? 0 : getResources().getDisplayMetrics().widthPixels * 0.35f));
        v.setAlpha(0f);
        v.animate().translationX(0).alpha(1f).setDuration(420).setInterpolator(Ui.EMPHASIZED).start();
        dock.setVisibility(View.GONE);
        fab.setVisibility(View.GONE);
        applyLayoutMode();
        if (twoPane && wasEmpty && tabs[currentTab] != null) tabs[currentTab].render(false);
        p.onShown();
    }

    public void pop() {
        if (stack.isEmpty()) return;
        stack.remove(stack.size() - 1);
        final View v = stackViews.remove(stackViews.size() - 1);
        hideKeyboard(v);
        if (twoPane && stack.isEmpty()) {
            if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
            applyLayoutMode();
            if (tabs[currentTab] != null) tabs[currentTab].refresh();
            return;
        }
        v.animate().translationX(getResources().getDisplayMetrics().widthPixels * 0.3f).alpha(0f)
                .setDuration(260).setInterpolator(Ui.EMPHASIZED)
                .withEndAction(new Runnable() { @Override public void run() {
                    if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
                    applyLayoutMode();
                } }).start();
        hideKeyboard(v);
        if (stack.isEmpty()) {
            if (!twoPane) applyLayoutMode();
            setFabVisible(true, true);
            if (tabs[currentTab] != null) tabs[currentTab].refresh();
        } else {
            stack.get(stack.size() - 1).refresh();
        }
    }

    public void popAll() { while (!stack.isEmpty()) pop(); }

    @Override public void onBackPressed() {
        if (!stack.isEmpty()) {
            Page top = stack.get(stack.size() - 1);
            if (top.onBack()) return;
            pop();
            return;
        }
        if (currentTab != 0) { showTab(0, true); return; }
        super.onBackPressed();
    }

    public void hideKeyboard(View v) {
        try {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Throwable ignored) {}
    }

    public void refreshAll() {
        for (int i = 0; i < TAB_COUNT; i++) if (tabs[i] != null) tabs[i].refresh();
        if (!stack.isEmpty()) {
            Page top = stack.get(stack.size() - 1);
            if (!top.hasUnsavedChanges() && !(top instanceof EntryEditorPage) && !(top instanceof SearchPage)) top.refresh();
            else if (top instanceof SearchPage) ((SearchPage) top).refreshResults();
        }
    }

    public void applyThemeChange() {
        Theme.apply(this);
        try { Widgets.updateAll(this); } catch (Throwable ignored) {}
        recreate();
    }

    // ------------------------------------------------------------------ snackbar

    public void snack(String message) { snack(message, null, null); }

    public void snack(String message, String action, final Runnable onAction) {
        snackLayer.removeAllViews();
        final LinearLayout bar = Ui.h();
        bar.setBackground(Ui.rr(Theme.dark ? 0xFFEDEDF0 : 0xFF26262B, 50));
        bar.setElevation(Ui.dp(8));
        bar.setPadding(Ui.dp(20), Ui.dp(14), Ui.dp(action != null ? 10 : 22), Ui.dp(14));
        TextView t = Ui.text(message, 15, 550, Theme.dark ? 0xFF111111 : 0xFFFFFFFF);
        bar.addView(t, Ui.weight(1));
        if (action != null) {
            TextView a = Ui.text(action, 15, 700, Theme.dark ? Theme.blend(Theme.accent, 0xFF000000, 0.3f) : Theme.blend(Theme.accent, 0xFFFFFFFF, 0.35f));
            a.setPadding(Ui.dp(14), Ui.dp(4), Ui.dp(14), Ui.dp(4));
            Ui.click(a, new Runnable() { @Override public void run() { snackLayer.removeAllViews(); onAction.run(); } });
            bar.addView(a);
        }
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        int bottom = navInset + Ui.dp(stack.isEmpty() && !useRail ? (currentTab == TAB_SETTINGS ? 92 : 166) : 20);
        p.setMargins(Ui.dp(20), 0, Ui.dp(20), bottom);
        snackLayer.addView(bar, p);
        bar.setTranslationY(Ui.dp(40));
        bar.setAlpha(0f);
        bar.animate().translationY(0).alpha(1f).setDuration(480).setInterpolator(Ui.SPRING).start();
        bar.setContentDescription(message + (action != null ? ". " + action + " available" : ""));
        bar.announceForAccessibility(message);
        // Swipe the snackbar sideways to dismiss it
        bar.setOnTouchListener(new View.OnTouchListener() {
            float x0;
            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN: x0 = e.getRawX(); return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - x0;
                        v.setTranslationX(dx);
                        v.setAlpha(Math.max(0.2f, 1f - Math.abs(dx) / v.getWidth()));
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        if (Math.abs(v.getTranslationX()) > v.getWidth() * 0.3f) {
                            final View fv = v;
                            v.animate().translationX(v.getTranslationX() > 0 ? v.getWidth() : -v.getWidth()).alpha(0f).setDuration(160)
                                    .withEndAction(new Runnable() { @Override public void run() { snackLayer.removeView(fv); } }).start();
                        } else {
                            v.animate().translationX(0).alpha(1f).setDuration(220).start();
                        }
                        return true;
                }
                return false;
            }
        });
        handler.removeCallbacksAndMessages("snack");
        handler.postAtTime(new Runnable() {
            @Override public void run() {
                bar.animate().alpha(0f).translationY(Ui.dp(30)).setDuration(220).withEndAction(new Runnable() {
                    @Override public void run() { snackLayer.removeView(bar); }
                }).start();
            }
        }, "snack", android.os.SystemClock.uptimeMillis() + (action != null ? 6000 : 3800));
    }

    // ------------------------------------------------------------------ lock

    private void showLock() {
        if (lockOverlay == null) {
            LinearLayout l = Ui.v();
            l.setGravity(Gravity.CENTER);
            l.setBackground(new Glass.Aurora());
            l.setClickable(true);
            l.setPadding(Ui.dp(32), Ui.insetTop + Ui.dp(24), Ui.dp(32), Ui.insetBottom + Ui.dp(24));
            // App icon tile with a small lock, centred like the iOS / One UI lock screens
            FrameLayout iconBox = new FrameLayout(this);
            iconBox.addView(Ui.badge("currency_pound", Theme.accent, 84), new FrameLayout.LayoutParams(Ui.dp(84), Ui.dp(84), Gravity.CENTER));
            View lockDot = Ui.badge("lock", Theme.dark ? 0xFF3A3A3C : 0xFF1C1C1E, 34);
            FrameLayout.LayoutParams ld = new FrameLayout.LayoutParams(Ui.dp(34), Ui.dp(34), Gravity.BOTTOM | Gravity.END);
            iconBox.addView(lockDot, ld);
            l.addView(iconBox, new LinearLayout.LayoutParams(Ui.dp(100), Ui.dp(100)));
            TextView t = Ui.text("Pay Calculator is locked", 24, 700, Theme.text);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(20), 0, Ui.dp(8));
            l.addView(t, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
            TextView s = Ui.text("Use your fingerprint, face or screen lock to see your pay.", 15, 400, Theme.text2);
            s.setGravity(Gravity.CENTER);
            s.setPadding(0, 0, 0, Ui.dp(28));
            l.addView(s, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
            final LinearLayout btn = Ui.primary("Unlock", "fingerprint", new Runnable() { @Override public void run() { authenticate(); } });
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
            l.addView(btn, bl);
            btn.post(new Runnable() { @Override public void run() {
                // keep the button a comfortable width on tablets and unfolded foldables
                int max = Ui.dp(360);
                if (btn.getWidth() > max) { LinearLayout.LayoutParams p2 = (LinearLayout.LayoutParams) btn.getLayoutParams(); p2.width = max; btn.setLayoutParams(p2); }
            } });
            lockOverlay = l;
            root.addView(l, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        }
        lockOverlay.setVisibility(View.VISIBLE);
        lockOverlay.setElevation(Ui.dp(30));
        authenticate();
    }

    private void unlock() {
        unlocked = true;
        authenticating = false;
        if (lockOverlay != null) {
            lockOverlay.animate().alpha(0f).setDuration(260).withEndAction(new Runnable() {
                @Override public void run() { lockOverlay.setVisibility(View.GONE); lockOverlay.setAlpha(1f); }
            }).start();
        }
    }

    @SuppressWarnings("deprecation")
    private void authenticate() {
        if (authenticating) return;
        authenticating = true;
        try {
            BiometricPrompt.Builder b = new BiometricPrompt.Builder(this)
                    .setTitle("Unlock Pay Calculator")
                    .setSubtitle("Securely view your earnings");
            if (Build.VERSION.SDK_INT >= 30) {
                b.setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
                        | android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            } else if (Build.VERSION.SDK_INT >= 29) {
                b.setDeviceCredentialAllowed(true);
            } else {
                b.setNegativeButton("Cancel", getMainExecutor(), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {}
                });
            }
            b.build().authenticate(new CancellationSignal(), getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) { unlock(); }
                @Override public void onAuthenticationError(int code, CharSequence msg) {
                    authenticating = false;
                    // Hardware unavailable / no lock screen set: don't trap the user out of their data.
                    if (code == BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT || code == BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE
                            || code == BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS || code == 14 /* NO_DEVICE_CREDENTIAL */) {
                        unlock();
                    }
                }
            });
        } catch (Throwable t) {
            unlock();
        }
    }

    // ------------------------------------------------------------------ files (SAF)

    public void startExport(int req, String mime, String name) {
        try {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType(mime);
            i.putExtra(Intent.EXTRA_TITLE, name);
            internalNav = true;
            startActivityForResult(i, req);
        } catch (Throwable t) { snack("No file app available"); }
    }

    public void startImport() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream", "text/json"});
            internalNav = true;
            startActivityForResult(i, REQ_IMPORT_JSON);
        } catch (Throwable t) { snack("No file app available"); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (req == REQ_IMPORT_JSON) {
                InputStream in = getContentResolver().openInputStream(uri);
                int n = Store.importSyncJson(Store.readAll(in));
                snack(n == 1 ? "Imported 1 new pay period" : "Imported " + n + " new pay periods");
                return;
            }
            OutputStream out = getContentResolver().openOutputStream(uri, "wt");
            if (out == null) throw new Exception("Could not open file");
            if (req == REQ_EXPORT_JSON) out.write(Store.exportSyncJson().getBytes(StandardCharsets.UTF_8));
            else if (req == REQ_EXPORT_CSV) out.write(Store.exportCsv().getBytes(StandardCharsets.UTF_8));
            else if (req == REQ_EXPORT_PDF) Exporter.writePdf(this, out);
            out.close();
            if (req == REQ_EXPORT_JSON) { Store.put("lastBackupMillis", (double) System.currentTimeMillis()); refreshAll(); }
            snack(req == REQ_EXPORT_JSON ? "Backup saved" : "Saved");
        } catch (Throwable t) {
            snack(req == REQ_IMPORT_JSON ? "That file couldn't be read" : "Couldn't save the file");
        }
    }

    public void shareText(String subject, String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, subject);
        i.putExtra(Intent.EXTRA_TEXT, text);
        internalNav = true;
        startActivity(Intent.createChooser(i, subject));
    }

    public void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
        }
    }

    // ------------------------------------------------------------------ errors

    private void installCrashHandler() {
        if (crashHandlerInstalled) return;
        crashHandlerInstalled = true;
        final java.io.File dir = getFilesDir();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    FileOutputStream f = new FileOutputStream(new File(dir, "crash.txt"));
                    f.write(sw.toString().getBytes(StandardCharsets.UTF_8));
                    f.close();
                } catch (Throwable ignored) {}
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    public static void reportError(Throwable t) {
        t.printStackTrace();
        if (current != null) {
            try { current.snack("Something went wrong: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {}
        }
    }

    private void maybeShowWelcome() {
        if (Store.bool("hasSeenOnboarding", false)) return;
        handler.postDelayed(new Runnable() { @Override public void run() { try { Welcome.show(MainActivity.this); } catch (Throwable t) { reportError(t); } } }, 450);
    }
}
