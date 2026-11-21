package com.dayoadekoya.paycalculator;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Self-updater: checks the latest GitHub release of this app and installs its APK.
 * Releases live at github.com/dlegion98/pay-calculator-android/releases. The APK must be
 * signed with the same key as the installed app, or Android refuses the update.
 */
public final class Updater {
    static final String REPO = "dlegion98/pay-calculator-android";
    static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    static final String PAGE = "https://github.com/" + REPO + "/releases/latest";
    /** Manifest committed next to the APK in the repo's releases/ folder (written by release.sh). */
    static final String MANIFEST = "https://raw.githubusercontent.com/" + REPO + "/main/releases/latest.json";
    private static final long AUTO_INTERVAL = 12L * 3600000L;
    private static volatile boolean busy;

    public static final class Release {
        String version = "", notes = "", apkUrl, pageUrl = PAGE;
        long size;
    }

    private Updater() {}

    public static String currentVersion(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Exception e) { return "0"; }
    }

    /** Compares dotted versions numerically: 6.10 > 6.9. */
    static int compare(String a, String b) {
        String[] x = a.replaceAll("[^0-9.]", "").split("\\.");
        String[] y = b.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length && !x[i].isEmpty() ? Integer.parseInt(x[i]) : 0;
            int q = i < y.length && !y[i].isEmpty() ? Integer.parseInt(y[i]) : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    /** Prefers the repo's releases/latest.json; falls back to GitHub Releases. Returns the newer of the two. */
    static Release fetch() throws Exception {
        Release a = null, b = null;
        Exception err = null;
        try { a = fetchManifest(); } catch (Exception e) { err = e; }
        try { b = fetchRelease(); } catch (Exception e) { if (a == null && err != null) throw e; }
        if (a == null) return b;
        if (b == null || b.apkUrl == null) return a;
        return compare(b.version, a.version) > 0 ? b : a;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url + (url.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis() / 60000).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", "PayCalculator-Android");
        try {
            int code = c.getResponseCode();
            if (code == 404) return null;
            if (code != 200) throw new Exception("HTTP " + code);
            InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        } finally { c.disconnect(); }
    }

    static Release fetchManifest() throws Exception {
        String body = get(MANIFEST);
        if (body == null) return null;
        JSONObject j = new JSONObject(body);
        Release r = new Release();
        r.version = j.optString("version", "");
        r.notes = j.optString("notes", "");
        r.apkUrl = j.optString("apk", null);
        r.size = j.optLong("size", 0);
        r.pageUrl = "https://github.com/" + REPO;
        return r;
    }

    static Release fetchRelease() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "PayCalculator-Android");
        try {
            int code = c.getResponseCode();
            if (code == 404) return null;            // no releases yet
            if (code != 200) throw new Exception("HTTP " + code);
            InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            JSONObject j = new JSONObject(bo.toString("UTF-8"));
            Release r = new Release();
            r.version = j.optString("tag_name", "").replaceFirst("^[vV]", "");
            r.notes = j.optString("body", "").trim();
            r.pageUrl = j.optString("html_url", PAGE);
            JSONArray assets = j.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject as = assets.getJSONObject(i);
                    if (as.optString("name", "").toLowerCase(java.util.Locale.UK).endsWith(".apk")) {
                        r.apkUrl = as.optString("browser_download_url", null);
                        r.size = as.optLong("size", 0);
                        break;
                    }
                }
            }
            return r;
        } finally {
            c.disconnect();
        }
    }

    /** Quiet check on launch, at most every 12 hours. */
    public static void checkAuto(MainActivity a) {
        if (!Store.bool("autoUpdateCheck", true)) return;
        if (System.currentTimeMillis() - (long) Store.dbl("updateCheckedAt", 0) < AUTO_INTERVAL) return;
        check(a, false);
    }

    public static void check(final MainActivity a, final boolean manual) {
        if (busy) { if (manual) a.snack("Already checking for updates…"); return; }
        busy = true;
        if (manual) a.snack("Checking for updates…");
        new Thread(new Runnable() {
            @Override public void run() {
                Release r = null;
                boolean failed = false;
                try { r = fetch(); } catch (Throwable t) { failed = true; }
                final Release fr = r;
                final boolean ff = failed;
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        busy = false;
                        if (a.isFinishing()) return;
                        String cur = currentVersion(a);
                        if (ff) { if (manual) a.snack("Couldn't check for updates. Check you're online"); return; }
                        Store.put("updateCheckedAt", (double) System.currentTimeMillis());
                        if (fr == null || fr.apkUrl == null || compare(fr.version, cur) <= 0) {
                            if (manual) a.snack("You're up to date · version " + cur);
                            return;
                        }
                        if (!manual && fr.version.equals(Store.str("skippedUpdate", ""))) return;
                        offer(a, fr);
                    }
                });
            }
        }).start();
    }

    static void offer(final MainActivity a, final Release r) {
        String notes = r.notes.isEmpty() ? "Bug fixes and improvements." : r.notes;
        if (notes.length() > 700) notes = notes.substring(0, 700).trim() + "…";
        notes = notes.replace("**", "").replace("### ", "").replace("## ", "");
        String size = r.size > 0 ? String.format(java.util.Locale.UK, " · %.1f MB", r.size / 1048576.0) : "";
        Sheets.confirm(a, "Update to " + r.version + size,
                "You have " + currentVersion(a) + ". Your data stays as it is.\n\n" + notes,
                "Update now", false, new Runnable() {
                    @Override public void run() { download(a, r); }
                });
        Store.put("skippedUpdate", r.version);   // auto-check won't nag about this version again
    }

    static void download(final MainActivity a, final Release r) {
        // Android 8+: the user must allow this app to install updates (once)
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            a.snack("Allow Pay Calculator to install updates, then tap Update again");
            try {
                a.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
            } catch (Throwable ignored) {}
            Store.put("skippedUpdate", "");
            return;
        }
        if (busy) return;
        busy = true;
        a.snack("Downloading " + r.version + "…");
        new Thread(new Runnable() {
            @Override public void run() {
                String err = null;
                File f = new File(a.getCacheDir(), "update.apk");
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(r.apkUrl).openConnection();
                    c.setInstanceFollowRedirects(true);
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(30000);
                    c.setRequestProperty("User-Agent", "PayCalculator-Android");
                    c.setRequestProperty("Accept", "application/octet-stream");
                    if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
                    InputStream in = c.getInputStream();
                    OutputStream out = new FileOutputStream(f);
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    out.close();
                    in.close();
                    c.disconnect();
                    // Safety: must be this app, and newer than what's installed
                    PackageInfo pi = a.getPackageManager().getPackageArchiveInfo(f.getAbsolutePath(), 0);
                    PackageInfo mine = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
                    if (pi == null || !a.getPackageName().equals(pi.packageName)) throw new Exception("That download isn't Pay Calculator");
                    if (pi.getLongVersionCode() <= mine.getLongVersionCode()) throw new Exception("That download isn't newer");
                    install(a, f);
                } catch (Throwable t) {
                    err = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                }
                final String fe = err;
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        busy = false;
                        if (fe != null) a.snack("Update failed: " + fe);
                    }
                });
            }
        }).start();
    }

    private static void install(Context c, File apk) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        p.setAppPackageName(c.getPackageName());
        if (Build.VERSION.SDK_INT >= 31) p.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int id = pi.createSession(p);
        PackageInstaller.Session s = pi.openSession(id);
        try {
            InputStream in = new FileInputStream(apk);
            OutputStream out = s.openWrite("base.apk", 0, apk.length());
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            s.fsync(out);
            out.close();
            in.close();
            Intent i = new Intent(c, Status.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent pend = PendingIntent.getBroadcast(c, id, i, flags);
            s.commit(pend.getIntentSender());
        } finally {
            s.close();
        }
    }

    /** Receives the installer's result and shows Android's confirmation screen when needed. */
    public static class Status extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { c.startActivity(confirm); } catch (Throwable t) { Toast.makeText(c, "Open Pay Calculator to finish the update", Toast.LENGTH_LONG).show(); }
                }
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Toast.makeText(c, "Update not installed" + (msg != null ? ": " + msg : ""), Toast.LENGTH_LONG).show();
            }
        }
    }
}
