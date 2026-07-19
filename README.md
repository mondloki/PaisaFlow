# PaisaFlow

PaisaFlow is a fast, private Android ledger for everyday expenses and investment cash flow. Investment purchases reduce Available Cash without being mixed into ordinary expenses.

## MVP features

- Expense, investment-debit, and income/credit transaction types
- One-time, skippable Opening Balance setup and a lifetime Available Cash figure
- Default investment, deposit, money-transfer, credit-card, and individual utility categories
- User-created categories with a compact native vector icon and color
- Week, month, calendar year, financial year, start-to-now, and custom date filters
- Switchable Summary and category Donut views
- Compact navigation drawer with Dashboard, Categories, and Settings
- Separate expense, investment, and credit category breakdowns
- Exact rupee/paise arithmetic using 64-bit integer minor units
- Offline SQLite storage with indexed date and category queries
- CSV export for the selected period through Android's document picker
- Validated full JSON backup and restore through Android's document picker
- No login, internet permission, advertising, analytics, or third-party runtime library

## Performance design

- Dashboard totals and category breakdowns use indexed SQL aggregate queries.
- Transactions are ordered through the date index and the visible list is capped at 250 rows.
- Database writes use write-ahead logging and run on one background executor.
- List rows are recycled rather than recreated while scrolling.
- Money is stored as integer paise, avoiding slow and inaccurate floating-point calculations.
- The interface, category icons, and donut chart use native Canvas rendering rather than image or chart libraries.
- Disposable Gradle output is kept in the local temp directory to avoid OneDrive file locks.

## Run on a phone

1. Enable Developer options and USB debugging on the phone.
2. Connect it to the laptop with a USB data cable and approve the debugging prompt.
3. Open this repository in Android Studio.
4. Select the physical phone in the target-device menu.
5. Select the `app` run configuration and click **Run**.

Run `connectedDebugAndroidTest` only on an emulator or isolated test device. Android's connected-test cleanup can uninstall the app under test and remove its app-owned data.

The ready-to-install debug package is at `dist/PaisaFlow-debug.apk`.

## Build and test

The project targets Android API 35 and supports Android 6.0 (API 23) or newer.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --max-workers=1
```

## Project structure

- `MainActivity.java` builds the dashboard, onboarding, dialogs, filters, recycled lists, and export flow.
- `LedgerDatabase.java` owns the SQLite schema, settings, seeds, writes, indexes, and aggregate queries.
- `CategoryIconView.java` renders lightweight category icons with native Canvas primitives.
- `DonutChartView.java` renders the category donut without a charting dependency.
- `DateRanges.java` calculates all supported date presets using the device's local calendar.
- `Money.java` parses and formats exact INR amounts.

## Privacy

All ledger data stays in the app's private SQLite database. Cloud backup is disabled. Encrypted device-to-device transfer is allowed by the Android backup rules.
