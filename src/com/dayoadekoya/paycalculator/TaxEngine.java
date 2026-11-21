package com.dayoadekoya.paycalculator;

import com.dayoadekoya.paycalculator.Model.DailyPay;
import com.dayoadekoya.paycalculator.Model.PayEntry;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * UK PAYE engine — a direct port of the iOS PayEntry maths (HMRC income tax bands, Class 1
 * employee NI, workplace pension, student/postgraduate loans), plus the "Flat %" mode.
 * Pure Java so it can be unit-tested off-device.
 */
public final class TaxEngine {
    private TaxEngine() {}

    /** User-level settings that affect the maths. */
    public static final class Config {
        public String mode = "HMRC Bands";   // or "Flat %"
        public double flatTaxRate = 20.0;
        public double flatNiRate = 8.0;
        public String studentLoanPlan = "None";
        public boolean isFlat() { return "Flat %".equals(mode); }
    }

    public static Config config = new Config();

    // ---------------------------------------------------------------- Hours & gross

    public static double paidHours(DailyPay d) {
        return Math.max(0, d.hoursWorked - d.unpaidBreakMinutes / 60.0);
    }

    public static double dayHours(DailyPay d) { return paidHours(d) + d.premiumHours; }

    public static double dayGross(DailyPay d, double rate) {
        double std = paidHours(d) * rate * (d.isOvertime ? 1.5 : 1.0);
        double prem = d.premiumHours * rate * d.premiumMultiplier;
        return std + prem + d.travelPay + d.bonus;
    }

    public static double totalHours(PayEntry e) {
        double t = 0;
        for (DailyPay d : e.days) t += dayHours(d);
        return t;
    }

    public static double grossCalculated(PayEntry e) {
        if (e.isSalariedMode) return e.fixedMonthlyGross;
        double t = 0;
        for (DailyPay d : e.days) t += dayGross(d, e.hourlyRate);
        return t;
    }

    public static double divisor(String freq) {
        if ("Weekly".equals(freq)) return 52.0;
        if ("Bi-Weekly".equals(freq)) return 26.0;
        return 12.0;
    }

    public static double effectiveGross(PayEntry e) {
        if (e.actualGrossPay != null && e.actualGrossPay > 0) return e.actualGrossPay;
        return grossCalculated(e);
    }

    // ---------------------------------------------------------------- Deductions

    public static double pension(PayEntry e) {
        return pensionFor(effectiveGross(e), e.pensionRate, e.age);
    }

    public static double pensionFor(double gross, double rate, int age) {
        if (age < 22 || age > 66 || rate <= 0) return 0;
        return gross * (rate / 100.0);
    }

    public static double tax(PayEntry e) {
        return taxFor(effectiveGross(e), pension(e), e.taxCode, e.payFrequency);
    }

    public static double taxFor(double gross, double pension, String taxCode, String freq) {
        if (config.isFlat()) return Math.max(0, (gross - pension) * (config.flatTaxRate / 100.0));
        double div = divisor(freq);
        double grossYearly = (gross - pension) * div;
        String code = taxCode == null ? "" : taxCode.toUpperCase(Locale.UK).trim();
        double taxYearly;
        if (code.contains("BR")) {
            taxYearly = grossYearly * 0.20;
        } else if (code.contains("D0")) {
            taxYearly = grossYearly * 0.40;
        } else if (code.contains("D1")) {
            taxYearly = grossYearly * 0.45;
        } else if (code.contains("NT")) {
            taxYearly = 0;
        } else {
            double allowance = codeAllowance(code);
            boolean kCode = code.startsWith("K");
            if (kCode) {
                // K codes add the figure to taxable pay instead of giving an allowance.
                double taxable = Math.max(0, grossYearly + allowance);
                return bands(taxable, 0) / div;
            }
            if (grossYearly > 100000) {
                allowance = Math.max(0, allowance - (grossYearly - 100000) / 2.0);
            }
            double taxable = Math.max(0, grossYearly - allowance);
            taxYearly = bands(taxable, allowance);
        }
        return Math.max(0, taxYearly / div);
    }

    private static double bands(double taxable, double allowance) {
        double basic = 37700.0;
        double higher = Math.max(basic, 125140.0 - allowance);
        if (taxable <= basic) return taxable * 0.20;
        if (taxable <= higher) return basic * 0.20 + (taxable - basic) * 0.40;
        return basic * 0.20 + (higher - basic) * 0.40 + (taxable - higher) * 0.45;
    }

    /** Allowance from the first run of digits only, so emergency suffixes like "1257L M1" / "W1" / "X" are ignored. */
    public static double codeAllowance(String code) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(code == null ? "" : code);
        if (!m.find()) return 0;
        try { return Double.parseDouble(m.group()) * 10.0; } catch (Exception ex) { return 0; }
    }

    public static double ni(PayEntry e) {
        return niFor(effectiveGross(e), pension(e), e.isSalarySacrifice, e.payFrequency);
    }

    public static double niFor(double gross, double pension, boolean salarySacrifice, String freq) {
        double liable = salarySacrifice ? gross - pension : gross;
        if (config.isFlat()) return Math.max(0, liable * (config.flatNiRate / 100.0));
        double div = divisor(freq);
        double pt = 242.0 * (52.0 / div);
        double uel = 967.0 * (52.0 / div);
        if (liable <= pt) return 0;
        if (liable <= uel) return (liable - pt) * 0.08;
        return (uel - pt) * 0.08 + (liable - uel) * 0.02;
    }

    public static double studentLoan(PayEntry e) {
        return studentLoanFor(effectiveGross(e), e.payFrequency);
    }

    public static double studentLoanFor(double gross, String freq) {
        String plan = config.studentLoanPlan == null ? "None" : config.studentLoanPlan;
        double threshold, rate;
        switch (plan) {
            // 2026/27 thresholds
            case "Plan 1": threshold = 26900; rate = 0.09; break;
            case "Plan 2": threshold = 29385; rate = 0.09; break;
            case "Plan 4": threshold = 33795; rate = 0.09; break;
            case "Plan 5": threshold = 25000; rate = 0.09; break;
            case "Postgraduate": threshold = 21000; rate = 0.06; break;
            default: return 0;
        }
        return Math.max(0, gross - threshold / divisor(freq)) * rate;
    }

    // ---------------------------------------------------------------- Display values (honour payslip overrides)

    public static double displayTax(PayEntry e) {
        return e.actualTaxAmount != null && e.actualTaxAmount > 0 ? e.actualTaxAmount : tax(e);
    }

    public static double displayNi(PayEntry e) {
        return e.actualNiAmount != null && e.actualNiAmount > 0 ? e.actualNiAmount : ni(e);
    }

    public static double displayPension(PayEntry e) {
        return e.actualPensionAmount != null && e.actualPensionAmount > 0 ? e.actualPensionAmount : pension(e);
    }

    public static double displayExtra(PayEntry e) {
        return e.actualExtraDeductions != null && e.actualExtraDeductions > 0 ? e.actualExtraDeductions : 0;
    }

    public static double net(PayEntry e) {
        if (e.actualPayReceived != null && e.actualPayReceived > 0) return e.actualPayReceived;
        return effectiveGross(e) - displayTax(e) - displayNi(e) - displayPension(e) - displayExtra(e) - studentLoan(e);
    }

    /** Net implied by the component figures, ignoring a manual net override. */
    public static double computedNet(PayEntry e) {
        return effectiveGross(e) - displayTax(e) - displayNi(e) - displayPension(e) - displayExtra(e) - studentLoan(e);
    }

    // ---------------------------------------------------------------- Dates & tax years

    public static LocalDate toDate(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static long toMillis(LocalDate d) {
        return d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 12L * 3600_000L;
    }

    public static int taxYearStart(LocalDate d) {
        int m = d.getMonthValue(), day = d.getDayOfMonth();
        return (m < 4 || (m == 4 && day < 6)) ? d.getYear() - 1 : d.getYear();
    }

    public static String taxYearLabel(int startYear) {
        return startYear + "/" + String.format(Locale.UK, "%02d", (startYear + 1) % 100);
    }

    public static int startYearFor(String label, LocalDate reference) {
        if (label == null || label.isEmpty() || "Current".equals(label)) return taxYearStart(reference);
        StringBuilder sb = new StringBuilder();
        for (char c : label.toCharArray()) if (Character.isDigit(c)) sb.append(c);
        if (sb.length() >= 4) {
            try { return Integer.parseInt(sb.substring(0, 4)); } catch (Exception ignored) {}
        }
        return taxYearStart(reference);
    }

    public static boolean inTaxYear(PayEntry e, int startYear) {
        LocalDate d = toDate(e.dateMillis);
        LocalDate s = LocalDate.of(startYear, 4, 6), end = LocalDate.of(startYear + 1, 4, 6);
        return !d.isBefore(s) && d.isBefore(end);
    }

    /** Fraction of the given tax year that has elapsed (clamped to 0.02 … 1). */
    public static double taxYearElapsed(int startYear, LocalDate today) {
        int cur = taxYearStart(today);
        if (startYear < cur) return 1.0;
        if (startYear > cur) return 0.02;
        LocalDate s = LocalDate.of(startYear, 4, 6), end = LocalDate.of(startYear + 1, 4, 6);
        double span = end.toEpochDay() - s.toEpochDay();
        double el = today.toEpochDay() - s.toEpochDay();
        return Math.min(1, Math.max(0.02, el / span));
    }

    // ---------------------------------------------------------------- Totals

    public static final class Totals {
        public double gross, tax, ni, pension, studentLoan, extra, net, hours;
        public int periods;
        public double deductions() { return tax + ni + pension + studentLoan + extra; }

        public void add(PayEntry e) {
            gross += effectiveGross(e);
            tax += displayTax(e);
            ni += displayNi(e);
            pension += displayPension(e);
            studentLoan += studentLoan(e);
            extra += displayExtra(e);
            net += net(e);
            hours += totalHours(e);
            periods++;
        }
    }

    public static Totals totals(List<PayEntry> entries) {
        Totals t = new Totals();
        for (PayEntry e : entries) t.add(e);
        return t;
    }

    public static Totals taxYearTotals(List<PayEntry> entries, int startYear) {
        Totals t = new Totals();
        for (PayEntry e : entries) if (inTaxYear(e, startYear)) t.add(e);
        return t;
    }

    public static List<Integer> availableTaxYears(List<PayEntry> entries, LocalDate today) {
        List<Integer> years = new ArrayList<Integer>();
        years.add(taxYearStart(today));
        for (PayEntry e : entries) {
            int y = taxYearStart(toDate(e.dateMillis));
            if (!years.contains(y)) years.add(y);
        }
        java.util.Collections.sort(years, java.util.Collections.<Integer>reverseOrder());
        return years;
    }

    // ---------------------------------------------------------------- Minimum wage (UK)

    public static double minimumWage(int age, LocalDate date) {
        if (!date.isBefore(LocalDate.of(2026, 4, 1))) {
            if (age >= 21) return 12.71; if (age >= 18) return 10.85; return 8.00;
        }
        if (!date.isBefore(LocalDate.of(2025, 4, 1))) {
            if (age >= 21) return 12.21; if (age >= 18) return 10.00; return 7.55;
        }
        if (!date.isBefore(LocalDate.of(2024, 4, 1))) {
            if (age >= 21) return 11.44; if (age >= 18) return 8.60; return 6.40;
        }
        if (age >= 23) return 10.42; if (age >= 21) return 10.18; if (age >= 18) return 7.49; return 5.28;
    }

    // ---------------------------------------------------------------- What-if tools

    public static final class Scenario {
        public double gross, tax, ni, pension, loan, net, hours, rate;
    }

    public static Scenario weeklyScenario(double rate, double hours, double pensionRate, int age, String taxCode) {
        Scenario s = new Scenario();
        s.rate = Math.max(0, rate);
        s.hours = Math.max(0, hours);
        s.gross = s.rate * s.hours;
        s.pension = pensionFor(s.gross, pensionRate, age);
        s.tax = taxFor(s.gross, s.pension, taxCode, "Weekly");
        s.ni = niFor(s.gross, s.pension, false, "Weekly");
        s.loan = studentLoanFor(s.gross, "Weekly");
        s.net = s.gross - s.tax - s.ni - s.pension - s.loan;
        return s;
    }

    public static double requiredRate(double targetWeeklyNet, double hours, double pensionRate, int age, String taxCode) {
        if (hours <= 0) return 0;
        double lo = 0, hi = 500;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (weeklyScenario(mid, hours, pensionRate, age, taxCode).net < targetWeeklyNet) lo = mid; else hi = mid;
        }
        return (lo + hi) / 2;
    }

    public static String bandName(double annualGross) {
        if (annualGross < 12570) return "Personal Allowance (0%)";
        if (annualGross < 50270) return "Basic Rate (20%)";
        if (annualGross < 125140) return "Higher Rate (40%)";
        return "Additional Rate (45%)";
    }

    public static double marginalRate(double annualGross) {
        if (annualGross < 12570) return 0.0;
        if (annualGross < 50270) return 0.20;
        if (annualGross < 125140) return 0.40;
        return 0.45;
    }

    /** Plain-English explanation of a tax code: [headline, point, point...]. */
    public static List<String> explainTaxCode(String rawCode, String sym) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.UK);
        List<String> out = new ArrayList<String>();
        double allowance = codeAllowance(code);
        if (code.contains("BR")) {
            out.add("No personal allowance on this income");
            out.add("All of this income is taxed at the 20% basic rate.");
            out.add("Usually used for a second job or pension.");
        } else if (code.contains("D0")) {
            out.add("No personal allowance on this income");
            out.add("All of this income is taxed at the 40% higher rate.");
            out.add("Common for a well-paid second job.");
        } else if (code.contains("D1")) {
            out.add("No personal allowance on this income");
            out.add("All of this income is taxed at the 45% additional rate.");
        } else if (code.contains("NT")) {
            out.add("No tax");
            out.add("No tax is deducted from this income at all.");
        } else if (code.startsWith("K")) {
            out.add("Reduces your allowance");
            out.add("A K code adds " + sym + String.format(Locale.UK, "%,.0f", allowance) + " to your taxable pay (e.g. for benefits or owed tax).");
            out.add("You have no tax-free allowance on this income.");
        } else if (code.startsWith("0T") || allowance == 0) {
            out.add("No tax-free allowance");
            out.add("You have no tax-free allowance on this income; tax applies from the first pound.");
        } else {
            out.add(sym + String.format(Locale.UK, "%,.0f", allowance) + " tax-free / year");
            out.add("You can earn " + sym + String.format(Locale.UK, "%,.0f", allowance) + " tax-free this year before 20% tax starts.");
            if (code.endsWith("L")) out.add("‘L’ means you get the standard tax-free Personal Allowance.");
            else if (code.endsWith("M")) out.add("‘M’ means you receive 10% of your partner's allowance (Marriage Allowance).");
            else if (code.endsWith("N")) out.add("‘N’ means you've transferred 10% of your allowance to your partner.");
            else if (code.endsWith("T")) out.add("‘T’ means your code includes other calculations HMRC reviews.");
        }
        if (code.startsWith("S")) out.add("‘S’ prefix: Scottish taxpayer — Scottish bands differ; this estimate uses UK rates.");
        if (code.startsWith("C")) out.add("‘C’ prefix: Welsh taxpayer — currently the same rates as England.");
        return out;
    }

    // ---------------------------------------------------------------- Expenses

    public static final double MILEAGE_RATE = 0.45;
    public static final double MILEAGE_RATE_AFTER_10K = 0.25;

    public static double mileageClaim(double miles) {
        if (miles <= 10000) return miles * MILEAGE_RATE;
        return 10000 * MILEAGE_RATE + (miles - 10000) * MILEAGE_RATE_AFTER_10K;
    }

    // ---------------------------------------------------------------- Daily activity

    /** Hours [0] and net [1] attributed to each calendar day in [from, to] (net split by each day's share of gross). */
    public static java.util.Map<LocalDate, double[]> dailyActivity(List<PayEntry> entries, LocalDate from, LocalDate to) {
        java.util.Map<LocalDate, double[]> map = new java.util.HashMap<LocalDate, double[]>();
        for (PayEntry e : entries) {
            LocalDate start = toDate(e.dateMillis);
            if (start.isAfter(to) || start.plusDays(62).isBefore(from)) continue;
            double totalGross = grossCalculated(e), net = net(e);
            List<DailyPay> active = new ArrayList<DailyPay>();
            for (DailyPay d : e.days) if (dayGross(d, e.hourlyRate) > 0 || dayHours(d) > 0) active.add(d);
            if (e.isSalariedMode || totalGross <= 0 || active.isEmpty()) {
                addDay(map, start, from, to, totalHours(e), net);
            } else {
                for (DailyPay d : active) addDay(map, start.plusDays(d.dayIndex), from, to, dayHours(d), net * dayGross(d, e.hourlyRate) / totalGross);
            }
        }
        return map;
    }

    private static void addDay(java.util.Map<LocalDate, double[]> m, LocalDate d, LocalDate from, LocalDate to, double h, double n) {
        if (d.isBefore(from) || d.isAfter(to)) return;
        double[] v = m.get(d);
        if (v == null) { v = new double[2]; m.put(d, v); }
        v[0] += h; v[1] += n;
    }

    // ---------------------------------------------------------------- Tax check (refund / underpayment estimate)

    public static final class TaxCheck {
        public double paid, expected, difference; // difference > 0 => likely refund
        public boolean meaningful;
    }

    /**
     * Compares income tax actually deducted this tax year with what HMRC's cumulative basis
     * would expect on the same pay, using the most recent tax code. Rough guide only.
     */
    public static TaxCheck taxCheck(List<PayEntry> entries, int startYear, LocalDate today, String fallbackCode) {
        TaxCheck t = new TaxCheck();
        double taxable = 0;
        String code = fallbackCode;
        long latest = Long.MIN_VALUE;
        int n = 0;
        for (PayEntry e : entries) {
            if (!inTaxYear(e, startYear)) continue;
            n++;
            taxable += effectiveGross(e) - displayPension(e);
            t.paid += displayTax(e);
            if (e.dateMillis > latest) { latest = e.dateMillis; code = e.taxCode; }
        }
        if (n == 0 || config.isFlat()) return t;
        double frac = taxYearElapsed(startYear, today);
        double annualised = taxable / frac;
        // Annual tax on the annualised figure (monthly calc × 12 keeps the same band logic)
        double annualTax = taxFor(annualised / 12.0, 0, normaliseCode(code), "Monthly") * 12.0;
        t.expected = annualTax * frac;
        t.difference = t.paid - t.expected;
        t.meaningful = true;
        return t;
    }

    /** Emergency (week 1 / month 1) markers don't apply to a cumulative check. */
    public static String normaliseCode(String code) {
        if (code == null) return "1257L";
        return code.toUpperCase(Locale.UK).replace("W1", "").replace("M1", "").replace("X", "").replace("/", "").trim();
    }
}
