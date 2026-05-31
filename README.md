# Selavu — README

**Selavu** (Tamil: Money spent / Expense) is a lightweight, offline-first Android expense tracker built with Kotlin and Jetpack Compose.

## Features

- ⚡ **Fast expense entry** — Save an expense in under 3 seconds
- 💾 **Fully offline** — SQLite database, no cloud required
- 🏷️ **Saved items** — Quick-add chips for frequently used expense categories
- 📊 **Ledger with filters** — Sort, filter, group expenses by date or category
- 📋 **CSV backup** — Auto-sync + manual import/export
- 🎨 **Minimal UI** — Clean, frictionless design

## Quick Start

### Development Laptop
- All code is in this folder (3 main files: `MainActivity.kt`, `Database.kt`, `Repository.kt`)
- Code is ready for build

### Build Laptop
1. Copy the `expense tracker/` folder
2. Update `local.properties` with your Android SDK path
3. Run:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   .\gradlew.bat clean assembleDebug
   ```
4. Install the APK from `app/build/outputs/apk/debug/app-debug.apk`

## Screens

1. **Home** — Today's & monthly totals, quick-add chips, manual entry form
2. **Ledger** — Full expense history with filters, sort, inline edit/delete
3. **Item Names** — Manage saved expense categories
4. **Settings** — Export/import CSV backups

## Tech Stack

- Kotlin 2.0.0
- Jetpack Compose + Material 3
- Room Database (SQLite)
- Hilt Dependency Injection
- OpenCSV

## Database

Two tables:
- **items** — Saved expense categories
- **expenses** — Individual expense records with item links

CSV columns: `id, item_id, item_name, amount, date, created_at`

## Validation Rules

- Item names: letters, numbers, space, `.`, `,`, `'`, `-` (no special chars)
- "Others" reserved word (expense type for unsaved entries)
- Amount: positive numbers only
- Date: optional (defaults to today)

## Design Philosophy

Selavu is intentionally minimal:
- ✓ Fast entry
- ✓ Offline
- ✓ No bloat

We **exclude**:
- ✗ Cloud sync
- ✗ Login/auth
- ✗ Receipt scanning
- ✗ Recurring expenses
- ✗ Multi-user

---

**v1.0.0 — May 29, 2026**
