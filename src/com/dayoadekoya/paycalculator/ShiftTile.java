package com.dayoadekoya.paycalculator;

import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import com.dayoadekoya.paycalculator.Model.PayEntry;

/** Quick Settings tile: tap to clock in, tap again to stop and log the shift. */
public class ShiftTile extends TileService {

    @Override public void onStartListening() {
        super.onStartListening();
        update();
    }

    @Override public void onClick() {
        super.onClick();
        try {
            if (!Store.ready()) Store.init(getApplicationContext());
            if (Shift.running()) {
                // Suggest the statutory break on long shifts, same as the in-app stop sheet
                double brk = Shift.elapsedMs() >= 6 * 3600000L ? 30 : 0;
                PayEntry e = Shift.stopAndLog(this, brk);
                String msg = e == null ? "Shift was under a minute, so nothing was logged"
                        : "Logged " + Ui.hours(TaxEngine.totalHours(e)) + (brk > 0 ? " (30 min break)" : "") + " · " + Ui.money(TaxEngine.net(e)) + " take-home";
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            } else {
                Shift.start(this, Shift.lastJob());
                Store.changed();
                Toast.makeText(this, "Shift started · " + Shift.job(), Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        update();
    }

    private void update() {
        Tile t = getQsTile();
        if (t == null) return;
        try {
            if (!Store.ready()) Store.init(getApplicationContext());
            boolean on = Shift.running();
            t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            t.setLabel(on ? "On shift" : "Start shift");
            if (Build.VERSION.SDK_INT >= 29) t.setSubtitle(on ? "Since " + java.time.Instant.ofEpochMilli(Shift.startMillis())
                    .atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : Shift.lastJob());
            t.setIcon(Icon.createWithResource(this, R.drawable.ic_timer));
            t.setContentDescription(on ? "Shift timer running. Tap to stop and log" : "Start the shift timer");
            t.updateTile();
        } catch (Throwable ignored) {}
    }
}
