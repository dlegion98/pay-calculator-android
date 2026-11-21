# Pay Calculator — Android

UK take-home pay tracker. It's the Android version of the iOS Pay Calculator app, written in plain Java on the
Android framework with no third-party libraries and no Google Play Services. Needs Android 9 or later.

The design follows iOS: grouped inset lists, large titles, a translucent tab bar, iOS system colours and the
Inter typeface. Dark mode uses AMOLED black. The layout adapts to foldables and tablets with a navigation rail
and two panes.

## Features

**Home**
- Take-home hero card with a monthly-goal ring and a trend line you can scrub. Tap it for the tax-year
  summary, or long-press to copy the amount.
- Privacy mode hides every amount in the app and the widgets.
- Shift timer:
  - It runs as an ongoing notification and shows your take-home live.
  - You can back-date the start and pick an unpaid break when you stop.
  - It uses each job's own rate, tax code and pension.
- Quick actions: repeat your last shift, log 8h, log a template, start a shift.
- This-week card: hours by day, a comparison with last week and a warning over 48 hours.
- Bills and a "safe to spend" figure.
- Payday countdown with the take-home you usually get.
- A reminder each April when the tax year rolls over, and a backup reminder.
- Pay records grouped by month:
  - Swipe left to delete, with undo; long-press for more.
  - Tap a month to collapse it.
  - Long lists load in batches.

**Subscriptions & bills** (its own tab, Subs)
- Track any recurring payment: weekly, fortnightly, monthly, quarterly or yearly.
- Quick-add for popular services, and a split between subscriptions and bills.
- Name suggestions as you type (about 100 UK services and bills, plus your own payments), with typical UK plan
  prices (Nov 2026) you can tap to fill in.
- Upcoming payments for the next 30 days, cost per month and per year, a breakdown by category, and the share
  of your take-home they use.
- Free-trial alerts before the first charge and the option to pause a payment.
- Payment reminders: one switch for all payments, with lead time (on the day up to a week before) and time of day,
  plus per-payment overrides.
- Payments show on the calendar as coloured dots, with the day's payments and monthly totals.

**Adding pay**
- Day-by-day shifts (overtime, unpaid breaks, travel, bonus) or payslip totals.
- Pick an existing job to fill in its usual rate, tax code and pension.
- "Copy last period" fills in the previous period's hours.
- Start and end time pickers, including overnight shifts.
- Hours can be typed as `7.5`, `7,5`, `7:30` or `7h30`.
- Warns about days over 16 hours and rates below the minimum wage.

**Calendar**: earnings heatmap and payday markers. Swipe to change month, long-press a day to log a shift.
Monthly totals include averages and take-home per hour.

**Insights**
- KPIs, trends and a forecast.
- A tax check that estimates any refund or underpayment.
- Personal-allowance usage.
- Holiday accrual (12.07%) and your best and average month.

**Search**: by job, note, month, tax code or amount, with recent searches and job filters.

**Widgets**: 11 home-screen widgets (2x2, 4x2 and 4x1). They respect privacy mode.

**System**
- Launcher shortcuts and a Quick Settings tile for the shift timer.
- Keyboard shortcuts:
  - Ctrl+N: log pay.
  - Ctrl+F: search.
  - Ctrl+1 to Ctrl+4: switch tabs.
  - Esc: back.
- App lock (biometric or screen lock) with an optional grace period. While the lock is on, the app is hidden in
  recent apps.
- Haptics, reduced motion and TalkBack labels.

**Sync and backup**
- Nearby Device Sync with the iOS app over the local network (Bonjour `_paycalc-mesh._tcp`).
- JSON backup in the same format as the iOS app.
- PDF statement and CSV export.

## Download and updates
- **Download:** [`releases/PayCalculator.apk`](releases/PayCalculator.apk). Open it on your phone to install.
- **Automatic updates:** from 6.1 the app checks `releases/latest.json` when it opens (at most every 12 hours),
  or when you tap Settings › Updates › Check for updates. When there's a newer version it downloads the APK,
  checks it's a newer Pay Calculator build, and installs it once you confirm.
- **Publishing a new version:**
  1. Raise `versionCode` and `versionName` in `AndroidManifest.xml`.
  2. Run `./release.sh "What's new"`. It builds the APK, updates `releases/`, commits and pushes.

  Updates install only if the APK is signed with the same `release.jks`.

## Tax engine
`TaxEngine.java` ports the iOS maths:
- Income-tax bands, with the allowance taper above £100k.
- BR, D0, D1, NT and K tax codes, plus emergency codes.
- Class 1 NI at 8% and 2%.
- Pension auto-enrolment, with optional salary sacrifice.
- Student loan plans 1, 2, 4, 5 and postgraduate, at 2026/27 thresholds.
- Minimum wage at April 2026 rates.

## Building
`./build.sh` builds and signs the APK with the plain SDK tools: aapt, javac, dx, zipalign and apksigner.

1. Edit the jar paths at the top of the script:
   - `A23` is an API 23 `android.jar`, used to link resources.
   - `A36` is a recent `android.jar`, used to compile.
2. Give the signing password as `PAYCALC_KEY_PASS`, or put a `keystore.properties` file (`password=...`) next to
   the script.

The signing key (`release.jks`) and `keystore.properties` are deliberately left out of the repository. Keep your
copy safe: updates must be signed with the same key to install over an existing version.

`build.gradle` and `settings.gradle` let you open the project in Android Studio as a convenience.
