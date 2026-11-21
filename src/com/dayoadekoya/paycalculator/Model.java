package com.dayoadekoya.paycalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Plain data model, mirroring the iOS SwiftData models (PayEntry, DailyPay, ShiftTemplate, WorkExpense). */
public final class Model {
    private Model() {}

    public static String newId() { return UUID.randomUUID().toString().toUpperCase(); }

    public static final class DailyPay {
        public int dayIndex;
        public double hoursWorked;
        public double premiumHours;
        public double premiumMultiplier = 2.0;
        public boolean isOvertime;
        public double unpaidBreakMinutes;
        public double travelPay;
        public double bonus;
        public String shiftTag = "None";

        public DailyPay() {}
        public DailyPay(int dayIndex, double hours) { this.dayIndex = dayIndex; this.hoursWorked = hours; }

        public DailyPay copy() {
            DailyPay d = new DailyPay();
            d.dayIndex = dayIndex; d.hoursWorked = hoursWorked; d.premiumHours = premiumHours;
            d.premiumMultiplier = premiumMultiplier; d.isOvertime = isOvertime;
            d.unpaidBreakMinutes = unpaidBreakMinutes; d.travelPay = travelPay; d.bonus = bonus;
            d.shiftTag = shiftTag;
            return d;
        }

        public boolean isEmpty() {
            return hoursWorked <= 0 && premiumHours <= 0 && travelPay <= 0 && bonus <= 0;
        }
    }

    public static final class PayEntry {
        public String id = newId();
        /** Start of the pay period (matches iOS `date`), epoch millis. */
        public long dateMillis = System.currentTimeMillis();
        public String jobName = "";
        public String payFrequency = "Weekly";
        public int sortIndex;
        public List<DailyPay> days = new ArrayList<DailyPay>();
        public double hourlyRate;
        public String taxCode = "1257L";
        public String pensionProvider = "";
        public double pensionRate;
        public boolean isSalarySacrifice;
        public int age = 25;
        public String notes = "";
        public boolean isSalariedMode;
        public double fixedMonthlyGross;
        public Double actualGrossPay;
        public Double actualTaxAmount;
        public Double actualNiAmount;
        public Double actualPensionAmount;
        public Double actualExtraDeductions;
        public Double actualPayReceived;

        public PayEntry copy(boolean newIdentity) {
            PayEntry e = new PayEntry();
            e.id = newIdentity ? newId() : id;
            e.dateMillis = dateMillis; e.jobName = jobName; e.payFrequency = payFrequency;
            e.sortIndex = sortIndex; e.hourlyRate = hourlyRate; e.taxCode = taxCode;
            e.pensionProvider = pensionProvider; e.pensionRate = pensionRate;
            e.isSalarySacrifice = isSalarySacrifice; e.age = age; e.notes = notes;
            e.isSalariedMode = isSalariedMode; e.fixedMonthlyGross = fixedMonthlyGross;
            e.actualGrossPay = actualGrossPay; e.actualTaxAmount = actualTaxAmount;
            e.actualNiAmount = actualNiAmount; e.actualPensionAmount = actualPensionAmount;
            e.actualExtraDeductions = actualExtraDeductions; e.actualPayReceived = actualPayReceived;
            for (DailyPay d : days) e.days.add(d.copy());
            return e;
        }

        public String displayJob() { return jobName == null || jobName.trim().isEmpty() ? "Shift" : jobName; }
        public boolean isActual() { return actualPayReceived != null && actualPayReceived > 0; }
    }

    public static final class ShiftTemplate {
        public String id = newId();
        public String name = "";
        public String jobName = "";
        public double hourlyRate;
        public String payFrequency = "Weekly";
        public double hours = 8;
        public double unpaidBreakMinutes;
        public boolean isOvertime;
        public double travelPay;
        public double bonus;
        public String shiftTag = "None";
        public String iconName = "bolt";
        public long createdAt = System.currentTimeMillis();
    }

    public static final class WorkExpense {
        public String id = newId();
        public String category = "Other";
        public long dateMillis = System.currentTimeMillis();
        public double amount;
        public double miles;
        public boolean isMileage;
        public String note = "";
    }

    /** A recurring payment: subscription, bill, rent, loan or savings transfer. */
    public static final class Bill {
        public String id = newId();
        public String name = "";
        public double amount;
        public int dayOfMonth = 1;              // legacy (v3.3–6.2): monthly bills only stored this
        public String category = "Bills";
        public String frequency = "Monthly";   // Weekly, Fortnightly, Monthly, Quarterly, Yearly
        public long startMillis;               // a payment date the schedule repeats from (0 = use dayOfMonth)
        public boolean subscription;           // subscription vs. bill
        public int remindDays = -1;            // -1 off, 0 on the day, n days before
        public boolean paused;
        public boolean trial;                  // free trial: first charge on startMillis
        public String notes = "";
    }
}
