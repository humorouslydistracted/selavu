# Expense Tracker — Requirements

This file will track the requirements and development notes for the `Expense Tracker` Android app. We will update this file as we discuss features and decisions.

## Project Goal

Build a lightweight Android Kotlin app to track personal and small-business expenses: add/edit/delete expenses, categorize them, view period summaries (day/week/month), and export/import CSV backups. The code will be developed on this laptop; you will copy files to your build laptop to run Gradle commands and produce the APK.

## Notes / Decisions Pending

- Package name (e.g., `com.example.expensetracker`): TBD
- Minimum SDK / target SDK: TBD (I can default to Min SDK 31 / Target 35 if you want)
- Persistence: Room (SQLite) recommended
- DI: Hilt (optional)
- UI: Jetpack Compose (recommended)
- CSV backup location & format: TBD

## Initial Checklist

- [ ] Confirm package name
- [ ] Confirm min/target SDK
- [ ] Confirm main screens and features
- [ ] Scaffold project structure
- [ ] Implement core features (expenses DB, add/edit, lists, filters)
- [ ] CSV export/import
- [ ] Build & test on build laptop


## First step

I'll wait for your confirmation to proceed. Tell me any immediate constraints or preferences (package name, SDK levels, required screens, auth, categories, recurring expenses, currency, multi-user, etc.).

## Proposed Decisions (draft — I'll proceed with these unless you ask changes)

- **Package name:** `com.expensetracker.app`
- **Min SDK / Target SDK:** Min SDK 31, Target SDK 35 (matches your build laptop environment)
- **Language & UI:** Kotlin with Jetpack Compose (modern, aligns with existing project)
- **Persistence:** Room (SQLite) for local storage
- **DI:** Hilt (recommended for clean architecture)
- **Auth:** Optional PIN/biometric unlock (configurable in settings)
- **Currency:** Default INR (`₹`) but configurable in settings (support for other currencies)
- **CSV backup:** Monthly CSVs + latest file; exported to app external files and `Documents/ExpenseTracker` (dual backup). Import/Sync supported.
- **Core screens:**
	- Home / Dashboard: quick add, recent expenses, balance for selected period
	- Expenses List: filter by Day / Week / Month / Custom range, search, sort
	- Add / Edit Expense: amount, category, date/time, note, payment method, tags
	- Categories: add/edit/delete categories, reorder
	- Reports: summary charts & tables (period totals, category breakdown)
	- Budgets (optional): set monthly budgets per category, show warnings
	- Settings: backup, import, currency, PIN/biometric, export CSV

- **Data model (initial):**
	- `Expense` — `id`, `amount`, `currency`, `categoryId`, `dateTime`, `note`, `paymentMethod`, `tags`, `createdAt`, `updatedAt`
	- `Category` — `id`, `name`, `color`, `sortOrder`
	- `Budget` — `id`, `categoryId`, `month`, `amount`
	- `Recurring` (future) — `id`, `expenseTemplateId`, `frequency`, `nextRun`

- **CSV columns (orders analogy → expenses):** `Date,Category,Amount,Currency,PaymentMethod,Note,Tags,CreatedAt`

- **Build & workflow notes:**
	- I will scaffold all source files and Gradle config here on this laptop.
	- You will copy the project folder to your build laptop and run exactly:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat clean assembleDebug
```

	- The scaffold will include `local.properties` instructions (you must set `sdk.dir` on the build laptop).

## Next step (recommended)

- If you agree with the proposed decisions, I will scaffold a minimal Android project skeleton under the `expense tracker/` folder (Gradle wrapper, `app/` module, basic `MainActivity`, Room entities, and a working `requirements.md`).
- If you want changes to any defaults above, tell me which items to change and I'll update this file and proceed accordingly.

---

_Edited on May 29, 2026 — draft spec appended by developer assistant._
