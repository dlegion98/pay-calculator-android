package com.dayoadekoya.paycalculator;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;

import com.dayoadekoya.paycalculator.Model.DailyPay;
import com.dayoadekoya.paycalculator.Model.PayEntry;

import java.time.LocalDate;
import java.util.Locale;

/** Live shift timer: clock in, watch earnings tick up, clock out to log the shift. */
public final class Shift {
    private Shift() {}

    public static final String ACTION_STOP = "com.dayoadekoya.paycalculator.STOP_SHIFT";
    private static final String CHANNEL = "shift";
    private static final int NOTIF_ID = 42;

    public static boolean running() { return Store.prefs().getLong("shiftStart", 0) > 0; }
    public static long startMillis() { return Store.prefs().getLong("shiftStart", 0); }
    public static String job() { return Store.str("shiftJob", "Main Job"); }

    /** The job you last timed, or your most recent job. */
    public static String lastJob() {
        String j = Store.str("shiftJob", "");
        if (!j.isEmpty()) return j;
        PayEntry latest = null;
        for (PayEntry e : Store.entries) if (latest == null || e.dateMillis > latest.dateMillis) latest = e;
        return latest != null && !latest.jobName.isEmpty() ? latest.jobName : "Main Job";
    }

    public static long elapsedMs() { return running() ? Math.max(0, System.currentTimeMillis() - startMillis()) : 0; }

    /** Pay settings for a job, copied from its most recent pay period so rate, tax code and pension match. */
    static PayEntry base(String job) {
        PayEntry last = null;
        for (PayEntry e : Store.entries) {
            if (job != null && job.equalsIgnoreCase(e.jobName) && !e.isSalariedMode && (last == null || e.dateMillis > last.dateMillis)) last = e;
        }
        PayEntry e;
        if (last != null) {
            e = last.copy(true);
            e.days.clear();
            e.actualGrossPay = null; e.actualPayReceived = null;
            e.actualTaxAmount = null; e.actualNiAmount = null; e.actualPensionAmount = null; e.actualExtraDeductions = null;
            e.notes = "";
        } else {
            e = Store.newEntryWithDefaults();
        }
        e.jobName = job == null || job.trim().isEmpty() ? "Main Job" : job;
        return e;
    }

    public static double rate() { return base(job()).hourlyRate; }

    /** What a shift of this length would pay, without saving it. */
    public static PayEntry preview(double hours) {
        PayEntry e = base(job());
        e.days.add(new DailyPay(0, Math.round(hours * 100) / 100.0));
        return e;
    }

    public static String clock(long ms) {
        long s = ms / 1000;
        return String.format(Locale.UK, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    public static void start(Context c, String job) { start(c, job, System.currentTimeMillis()); }

    /** Start the timer, optionally back-dated ("I started 15 minutes ago"). */
    public static void start(Context c, String job, long startMillis) {
        Store.prefs(c).edit().putLong("shiftStart", Math.min(System.currentTimeMillis(), startMillis))
                .putString("shiftJob", job == null || job.trim().isEmpty() ? "Main Job" : job.trim()).apply();
        showNotification(c);
    }

    public static void cancel(Context c) {
        Store.prefs(c).edit().putLong("shiftStart", 0).apply();
        hideNotification(c);
    }

    /** Stops the timer and logs the shift (rounded to the nearest minute). Returns the new entry, or null if under a minute. */
    public static PayEntry stopAndLog(Context c, double unpaidBreakMinutes) {
        long start = Store.prefs(c).getLong("shiftStart", 0);
        if (start <= 0) return null;
        double hours = Math.round((System.currentTimeMillis() - start) / 60000.0) / 60.0;
        Store.prefs(c).edit().putLong("shiftStart", 0).apply();
        hideNotification(c);
        if (hours < 1.0 / 60) return null;
        PayEntry e = base(Store.prefs(c).getString("shiftJob", "Main Job"));
        e.dateMillis = TaxEngine.toMillis(TaxEngine.toDate(start));
        DailyPay d = new DailyPay(0, Math.round(hours * 100) / 100.0);
        d.unpaidBreakMinutes = unpaidBreakMinutes;
        e.days.add(d);
        e.notes = "Logged with the shift timer";
        Store.entries.add(e);
        Store.changed();
        return e;
    }

    // ------------------------------------------------------------------ ongoing notification

    public static void showNotification(Context ctx) {
        Context c = ctx.getApplicationContext();
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Shift timer", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Shows your running shift");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
            Intent open = new Intent(c, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent content = PendingIntent.getActivity(c, 40, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Intent stop = new Intent(c, Reminders.class);
            stop.setAction(ACTION_STOP);
            PendingIntent stopPi = PendingIntent.getBroadcast(c, 41, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Action act = new Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_check), "Stop & log", stopPi).build();
            Notification n = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_timer)
                    .setContentTitle("Shift in progress · " + job())
                    .setContentText("At " + Ui.money(rate()) + "/h · tap to open")
                    .setUsesChronometer(true)
                    .setWhen(startMillis())
                    .setShowWhen(true)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setColor(0xFF0BA572)
                    .setContentIntent(content)
                    .addAction(act)
                    .setCategory(Notification.CATEGORY_STOPWATCH)
                    .build();
            nm.notify(NOTIF_ID, n);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void hideNotification(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(NOTIF_ID);
        } catch (Throwable ignored) {}
    }

    public static LocalDate startDate() { return TaxEngine.toDate(startMillis()); }
}
