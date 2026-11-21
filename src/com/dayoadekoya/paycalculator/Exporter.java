package com.dayoadekoya.paycalculator;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import com.dayoadekoya.paycalculator.Model.PayEntry;

import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** PDF statements (framework PdfDocument) and CSV export entry points. */
public final class Exporter {
    private Exporter() {}

    public static void chooser(final MainActivity a) {
        if (Store.entries.isEmpty()) { a.snack("Log some pay first"); return; }
        final Sheets.Handle h = Sheets.sheet(a, "Export earnings", null);
        h.body.addView(Sheets.optionCard("receipt_long", Theme.red, "PDF statement", "A tidy statement of every pay period", new Runnable() {
            @Override public void run() { h.dismiss(); a.startExport(MainActivity.REQ_EXPORT_PDF, "application/pdf", "Pay Statement " + LocalDate.now() + ".pdf"); }
        }));
        h.body.addView(Sheets.optionCard("table_view", Theme.green, "Save CSV spreadsheet", "Open in Sheets or Excel", new Runnable() {
            @Override public void run() { h.dismiss(); a.startExport(MainActivity.REQ_EXPORT_CSV, "text/csv", "My Earnings.csv"); }
        }));
        h.body.addView(Sheets.optionCard("ios_share", Theme.accent, "Share as text", "Send the CSV via any app", new Runnable() {
            @Override public void run() { h.dismiss(); a.shareText("My Earnings", Store.exportCsv()); }
        }));
        Sheets.show(h);
    }

    public static void writePdf(Context c, OutputStream out) throws Exception {
        Ui.unmasked = true;
        try { writePdfInner(c, out); } finally { Ui.unmasked = false; }
    }

    private static void writePdfInner(Context c, OutputStream out) throws Exception {
        PdfDocument doc = new PdfDocument();
        int W = 595, H = 842, M = 40;
        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        title.setTextSize(22);
        Paint h2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        h2.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        h2.setTextSize(10);
        Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
        body.setTextSize(9.5f);
        Paint muted = new Paint(body);
        muted.setColor(0xFF777777);
        Paint line = new Paint();
        line.setColor(0xFFDDDDDD);
        Paint accent = new Paint();
        accent.setColor(0xFF0BA572);

        int[] cols = {M, M + 70, M + 210, M + 260, M + 320, M + 380, M + 430, M + 475};
        String[] heads = {"Date", "Job", "Hours", "Gross", "Tax", "NI", "Pension", "Net"};
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.UK);
        int start = TaxEngine.taxYearStart(LocalDate.now());
        TaxEngine.Totals ty = TaxEngine.taxYearTotals(Store.entries, start);
        TaxEngine.Totals all = TaxEngine.totals(Store.entries);

        int pageNo = 1, i = 0;
        while (i < Store.entries.size() || pageNo == 1) {
            PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo).create());
            Canvas cv = page.getCanvas();
            int y = M;
            if (pageNo == 1) {
                cv.drawRect(0, 0, W, 6, accent);
                cv.drawText("Pay Statement", M, y + 20, title);
                cv.drawText("Generated " + LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK)) + " · Pay Calculator", M, y + 38, muted);
                y += 64;
                cv.drawText("Tax year " + TaxEngine.taxYearLabel(start), M, y, h2);
                y += 16;
                String[][] kv = {
                        {"Gross", Ui.money(ty.gross)}, {"Income tax", Ui.money(ty.tax)}, {"National Insurance", Ui.money(ty.ni)},
                        {"Pension", Ui.money(ty.pension)}, {"Student loan", Ui.money(ty.studentLoan)}, {"Take-home", Ui.money(ty.net)}};
                for (int k = 0; k < kv.length; k++) {
                    int cx = M + (k % 3) * 175, cy = y + (k / 3) * 30;
                    cv.drawText(kv[k][0], cx, cy, muted);
                    cv.drawText(kv[k][1], cx, cy + 13, h2);
                }
                y += 70;
                cv.drawText("All records: " + all.periods + " pay periods · " + Ui.money(all.net) + " take-home · " + Ui.hours(all.hours), M, y, muted);
                y += 24;
            }
            for (int k = 0; k < heads.length; k++) cv.drawText(heads[k], cols[k], y, h2);
            y += 6;
            cv.drawLine(M, y, W - M, y, line);
            y += 14;
            while (i < Store.entries.size() && y < H - M - 20) {
                PayEntry e = Store.entries.get(i++);
                String job = e.displayJob();
                if (job.length() > 24) job = job.substring(0, 23) + "…";
                String[] row = {TaxEngine.toDate(e.dateMillis).format(f), job, Ui.num(Math.round(TaxEngine.totalHours(e) * 10) / 10.0),
                        Ui.money(TaxEngine.effectiveGross(e)), Ui.money(TaxEngine.displayTax(e)), Ui.money(TaxEngine.displayNi(e)),
                        Ui.money(TaxEngine.displayPension(e)), Ui.money(TaxEngine.net(e))};
                for (int k = 0; k < row.length; k++) cv.drawText(row[k], cols[k], y, k == 7 ? h2 : body);
                y += 6;
                cv.drawLine(M, y, W - M, y, line);
                y += 13;
            }
            cv.drawText("Page " + pageNo + " · Figures are estimates unless entered from a payslip.", M, H - 24, muted);
            doc.finishPage(page);
            pageNo++;
            if (Store.entries.isEmpty()) break;
        }
        doc.writeTo(out);
        doc.close();
    }
}
