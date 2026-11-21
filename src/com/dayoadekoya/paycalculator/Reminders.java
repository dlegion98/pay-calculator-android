package com.dayoadekoya.paycalculator;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** Friday shift reminder + payday reminder, via AlarmManager (no Play Services needed). */
public class Reminders extends BroadcastReceiver {
    private static final String CHANNEL = "reminders";
    private static final String ACTION_FRIDAY = "com.dayoadekoya.paycalculator.FRIDAY";
    private static final String ACTION_PAYDAY = "com.dayoadekoya.paycalculator.PAYDAY";

    public static void ensureChannel(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Shift logging and payday reminders");
            nm.createNotificationChannel(ch);
        } catch (Throwable ignored) {}
    }

    private static PendingIntent pi(Context c, String action, int code) {
        Intent i = new Intent(c, Reminders.class);
        i.setAction(action);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static long millis(LocalDateTime t) { return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }

    public static void reschedule(Context ctx) {
        Context c = ctx.getApplicationContext();
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent fri = pi(c, ACTION_FRIDAY, 1), pay = pi(c, ACTION_PAYDAY, 2);
            am.cancel(fri);
            am.cancel(pay);
            android.content.SharedPreferences p = Store.prefs(c);
            if (p.getBoolean("enableFridayReminder", false)) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)).atTime(17, 0);
                if (!next.isAfter(now)) next = next.plusWeeks(1);
                am.setWindow(AlarmManager.RTC_WAKEUP, millis(next), 15 * 60 * 1000L, fri);
            }
            if (p.getBoolean("enablePaydayReminder", false)) {
                LocalDate d = Payday.next();
                if (d != null) {
                    LocalDateTime when = d.atTime(9, 0);
                    if (when.isAfter(LocalDateTime.now())) am.setWindow(AlarmManager.RTC_WAKEUP, millis(when), 30 * 60 * 1000L, pay);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || "android.intent.action.MY_PACKAGE_REPLACED".equals(action)) {
            try { if (!Store.ready()) Store.init(c); } catch (Throwable ignored) {}
            reschedule(c);
            try { if (Shift.running()) Shift.showNotification(c); } catch (Throwable ignored) {}
            return;
        }
        if (Shift.ACTION_STOP.equals(action)) {
            try {
                if (!Store.ready()) Store.init(c);
                Model.PayEntry e = Shift.stopAndLog(c, 0);
                android.widget.Toast.makeText(c, e == null ? "Shift too short to log" :
                        "Logged " + Ui.hours(TaxEngine.totalHours(e)) + " · " + Ui.money(TaxEngine.net(e)) + " take-home", android.widget.Toast.LENGTH_LONG).show();
            } catch (Throwable t) { t.printStackTrace(); }
            return;
        }
        ensureChannel(c);
        String title, text;
        if (ACTION_PAYDAY.equals(action)) {
            title = "It's payday 💸";
            text = "Check your payslip and log the actual figures to keep your tax year accurate.";
        } else {
            title = "Time to log your shift ⏰";
            text = "Don't forget to track your hours for this week to keep your tax estimates accurate.";
        }
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(c, 3, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_currency_pound)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setColor(0xFF0BA572)
                .setContentIntent(content)
                .setAutoCancel(true)
                .build();
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(ACTION_PAYDAY.equals(action) ? 2 : 1, n);
        } catch (Throwable ignored) {}
        try { if (!Store.ready()) Store.init(c); } catch (Throwable ignored) {}
        reschedule(c);
    }
}
