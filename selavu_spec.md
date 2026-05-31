# Selavu — App Specification
**Version:** 1.0  
**Platform:** Native Android (Kotlin)  
**Purpose:** This document is the single source of truth for building the Selavu expense tracking app. Every screen, interaction, validation rule, data model, and edge case is defined here. No assumption should be made beyond what is written.

---

## Table of Contents
1. [App Overview](#1-app-overview)
2. [Tech Stack & Project Setup](#2-tech-stack--project-setup)
3. [Data Layer](#3-data-layer)
4. [Screen: Home](#4-screen-home)
5. [Screen: Sidebar Navigation](#5-screen-sidebar-navigation)
6. [Screen: Item Names](#6-screen-item-names)
7. [Screen: Item Popup](#7-screen-item-popup)
8. [Screen: Ledger](#8-screen-ledger)
9. [Screen: Settings](#9-screen-settings)
10. [First Launch: Storage Permission](#10-first-launch-storage-permission)
11. [CSV Backup System](#11-csv-backup-system)
12. [Validation Rules](#12-validation-rules)
13. [Edge Cases & Business Rules](#13-edge-cases--business-rules)
14. [UI Design System](#14-ui-design-system)

---

## 1. App Overview

Selavu (Tamil for "money spent") is a minimal Android app for daily expense tracking. The user can:
- Quickly log expenses via pre-saved item name chips or a manual entry form
- View all expenses in a filterable, sortable ledger
- Manage saved item names
- Backup and restore data via CSV

**Core philosophy:** Fast input, clean ledger. No clutter, no accounts, no cloud sync.

---

## 2. Tech Stack & Project Setup

| Concern | Choice |
|---|---|
| Language | Kotlin |
| Min SDK | API 26 (Android 8.0) |
| Target SDK | API 34 |
| UI | Jetpack Compose |
| Database | Room (SQLite wrapper) |
| Navigation | Jetpack Navigation Component (single Activity, multiple Composables) |
| State management | ViewModel + StateFlow |
| Dependency Injection | Hilt |
| Date handling | `java.time.LocalDate` |
| CSV | OpenCSV or manual comma-delimited writing |
| Storage Access | Storage Access Framework (SAF) for Android 10+; `WRITE_EXTERNAL_STORAGE` for Android 9 and below |

### Gradle dependencies (add to `build.gradle.kts` app module)
```kotlin
// Room
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// Hilt
implementation("com.google.dagger:hilt-android:2.50")
ksp("com.google.dagger:hilt-compiler:2.50")

// Compose
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
implementation("androidx.navigation:navigation-compose:2.7.6")
```

---

## 3. Data Layer

### 3.1 Database: `SelavuDatabase`

Single Room database named `selavu.db` with two tables.

---

### 3.2 Table: `items`

Stores user-defined item names (the "saved items" that appear as quick-add chips).

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | |
| `name` | TEXT | NOT NULL, UNIQUE | Case-insensitive unique. Store as user typed. |
| `created_at` | TEXT | NOT NULL | ISO 8601 datetime string. E.g. `"2025-05-28T10:30:00"` |

**Kotlin Entity:**
```kotlin
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: String
)
```

---

### 3.3 Table: `expenses`

Stores every expense entry.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | |
| `item_name` | TEXT | NOT NULL | Always stores the name as typed/selected. NEVER null. NEVER replaced with "Others". |
| `item_id` | INTEGER | NULLABLE, FK → items(id) | NULL means this entry belongs to the "Others" bucket. NOT NULL means it was logged via a saved item chip. |
| `amount` | REAL | NOT NULL | Positive decimal. Greater than 0. |
| `date` | TEXT | NOT NULL | ISO 8601 date string `"YYYY-MM-DD"`. Defaults to today if user does not pick one. |
| `created_at` | TEXT | NOT NULL | ISO 8601 datetime string. Used for tiebreaking sort. |

**Kotlin Entity:**
```kotlin
@Entity(
    tableName = "expenses",
    foreignKeys = [ForeignKey(
        entity = ItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["item_id"],
        onDelete = ForeignKey.SET_NULL  // CRITICAL: when item deleted, set item_id to null, preserve item_name
    )]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "item_name") val itemName: String,
    @ColumnInfo(name = "item_id", index = true) val itemId: Int? = null,
    val amount: Double,
    val date: String,
    @ColumnInfo(name = "created_at") val createdAt: String
)
```

**Critical rule on `onDelete`:** Use `ForeignKey.SET_NULL`. When a saved item is deleted, all its linked expenses have `item_id` set to NULL automatically, but `item_name` is preserved. This means those expenses silently move to the "Others" bucket while retaining their original names.

---

### 3.4 DAOs

**ItemDao:**
```kotlin
@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY name ASC")
    fun getAllItems(): Flow<List<ItemEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(item: ItemEntity)

    @Delete
    suspend fun deleteItem(item: ItemEntity)

    @Query("SELECT COUNT(*) FROM expenses WHERE item_id = :itemId")
    suspend fun getExpenseCountForItem(itemId: Int): Int

    @Query("SELECT SUM(amount) FROM expenses WHERE item_id = :itemId")
    suspend fun getTotalAmountForItem(itemId: Int): Double?
}
```

**ExpenseDao:**
```kotlin
@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC, created_at DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Insert
    suspend fun insertExpense(expense: ExpenseEntity)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("""
        SELECT SUM(amount) FROM expenses 
        WHERE date >= :startDate AND date <= :endDate
    """)
    suspend fun getTotalForDateRange(startDate: String, endDate: String): Double?

    @Query("SELECT SUM(amount) FROM expenses WHERE date = :date")
    suspend fun getTotalForDate(date: String): Double?

    @Query("""
        SELECT SUM(amount) FROM expenses 
        WHERE date >= :monthStart AND date <= :monthEnd
    """)
    suspend fun getMonthTotal(monthStart: String, monthEnd: String): Double?

    @Query("SELECT COUNT(*) FROM expenses WHERE date = :date")
    suspend fun getCountForDate(date: String): Int

    @Query("SELECT SUM(amount) FROM expenses WHERE date = :todayDate")
    suspend fun getTodayTotal(todayDate: String): Double?
}
```

---

### 3.5 Repository: `ExpenseRepository`

Create a single `ExpenseRepository` class injected via Hilt that wraps both DAOs. All ViewModels interact only with the repository, never directly with DAOs.

After every INSERT, UPDATE, or DELETE on `expenses`, the repository must call `writeCsvBackup()` (see Section 11).

---

## 4. Screen: Home

### 4.1 Layout (top to bottom)

```
[ Top bar ]
[ Monthly summary banner ]
[ Section label: "Quick add" ]  ← hidden if no saved items exist
[ Word cloud chips ]             ← hidden if no saved items exist
[ Horizontal divider ]
[ Section label: "Manual entry" ]
[ Item name input field ]
[ Inline suggestion banner ]     ← conditionally visible
[ Amount input field ]
[ Date picker field ]
[ Save button ]
```

### 4.2 Top Bar

- Left: hamburger icon (3 horizontal lines). Tapping opens the sidebar drawer.
- Center/Right: app name "Selavu" in medium weight.
- Right: settings gear icon. Tapping navigates to Settings screen.

### 4.3 Monthly Summary Banner

A card displayed below the top bar, always visible.

**Left side:**
- Label: "Spent this month" (small, muted text)
- Value: sum of all expenses where `date` falls within the current calendar month (e.g. June 1 to June 30). Format: `₹ X,XXX`. Show `₹ 0` if no entries.

**Right side:**
- Label: "Today" (small, muted text)
- Value: sum of all expenses where `date` equals today's date. Format: `₹ X,XXX`. Show `₹ 0` if no entries.

**Behaviour:**
- Recalculate every time the screen is foregrounded (`LaunchedEffect` or `OnResume`).
- Tapping the banner navigates to the Ledger screen with the current calendar month pre-applied as a date range filter.

**Queries:**
```kotlin
// Month total: first day of month to last day of month
// Today total: today's date only
val today = LocalDate.now()
val monthStart = today.withDayOfMonth(1).toString()        // "2025-06-01"
val monthEnd = today.withDayOfMonth(today.lengthOfMonth()).toString() // "2025-06-30"
val todayStr = today.toString()                             // "2025-06-29"
```

### 4.4 Word Cloud Chips

- Rendered as a horizontally wrapping row of rounded pill buttons.
- Source: all rows from the `items` table, ordered alphabetically.
- Each chip shows the item name.
- If no saved items exist, this entire section (including the "Quick add" label and divider) is hidden.
- Tapping a chip opens the **Item Popup** (Section 7).

### 4.5 Manual Entry Form

**Item name field:**
- Placeholder text: "Item name"
- Accepts: letters (A–Z, a–z), digits (0–9), spaces, and these special characters only: `. , ' - _`
- Reject all other characters silently on keystroke (do not allow them to appear).
- `imeAction = ImeAction.Next` (moves focus to amount field on keyboard "Next")

**Suggestion banner (conditional):**
- Trigger: fires `onChange` with a 300ms debounce after user types in the item name field.
- Match logic: check if any saved item name matches the typed text using case-insensitive comparison OR Levenshtein distance ≤ 2.
- If a match is found: show a banner between the item name field and amount field.
  - Banner text: `"Match found: [ItemName]"` with a tappable "Use it" link on the right.
  - "Use it" action: set the internal `selectedItemId` to the matched item's `id`, update the item name field to show the exact saved item name, hide the banner.
  - If user ignores the banner and taps Save: save with `item_id = null` (goes to Others bucket).
- If typed text is cleared: hide the banner.
- "Others" (case-insensitive) typed as item name: show inline error `"'Others' is a reserved name"` and disable the Save button.

**Amount field:**
- Placeholder text: "Amount"
- `keyboardType = KeyboardType.Decimal`
- `imeAction = ImeAction.Done`
- Validation fires `onFocusChanged` (when field loses focus) and when Save is tapped.
- See Section 12 for full validation rules.

**Date picker field:**
- Displays as a tappable row with calendar icon on the right.
- Placeholder text: "Date (optional — defaults to today)"
- When tapped: open Android `DatePickerDialog`.
- Once a date is selected: show it formatted as `"dd MMM yyyy"` (e.g. "28 May 2025").
- A small "✕" icon appears to the right to clear the selection.
- If no date selected at time of Save: use `LocalDate.now()`.

**Save button:**
- Enabled: when item name is non-empty, amount is valid (> 0, numeric), and item name is not "Others".
- Disabled (greyed out): if any of the above conditions fail.
- On tap: validate all fields → if valid, insert to DB → write CSV → clear all fields → show a brief Snackbar: `"Saved"`.

**What gets saved:**
- If user selected a chip suggestion ("Use it"): `item_id = matched item id`, `item_name = matched item name`
- Otherwise: `item_id = null`, `item_name = whatever was typed`

---

## 5. Screen: Sidebar Navigation

### 5.1 Behaviour
- Opened by tapping the hamburger icon on the Home top bar.
- Implemented as a `ModalNavigationDrawer` in Jetpack Compose.
- Tapping outside the drawer or pressing Back closes it.

### 5.2 Drawer Content (top to bottom)
- App name header: "Selavu" in medium weight, muted subtext "Menu"
- Divider
- Nav item 1: tag icon + "Item names" — navigates to Item Names screen
- Nav item 2: book icon + "Ledger" — navigates to Ledger screen
- Nav item 3: settings icon + "Settings" — navigates to Settings screen
- Active item is highlighted with a filled background tint.

---

## 6. Screen: Item Names

### 6.1 Layout
- Top bar with back arrow + title "Item names"
- "+ Add item" button (outlined, small, top of content area)
- List of existing item name pills, each with a "✕" delete button on the right

### 6.2 Adding an Item
- Tapping "+ Add item": shows an inline text input field + "Save" button (or Enter to confirm).
- Input validation: same character rules as the item name field in home (letters, digits, spaces, `. , ' - _` only).
- On Save:
  - Check if name already exists (case-insensitive). If duplicate: show inline error `"This item already exists"`, do not insert.
  - Check if name is "Others" (case-insensitive). If so: show inline error `"'Others' is a reserved name"`, do not insert.
  - If valid: insert into `items` table → new chip immediately appears in the list and on the Home word cloud.

### 6.3 Deleting an Item

**Step 1 — Query before showing dialog:**
Before showing any dialog, query:
```kotlin
val count = itemDao.getExpenseCountForItem(itemId)
val total = itemDao.getTotalAmountForItem(itemId) ?: 0.0
```

**Step 2a — Item has NO existing expenses:**
Show a simple confirmation dialog:
- Title: `Delete "[ItemName]"?`
- Body: "This item has no recorded expenses."
- Buttons: "Cancel" | "Delete"

**Step 2b — Item HAS existing expenses:**
Show a warning dialog:
- Title: `Delete "[ItemName]"?`
- Body line 1: `"[ItemName] has [count] entries totalling"`
- Body line 2: `"₹ [total]"` (larger, bold font)
- Body line 3: `"These entries will move to Others. Their names are preserved."`
- Buttons: "Cancel" (outlined) | "Delete" (filled red background)

**Step 3 — On "Delete" confirm:**
- Delete the `ItemEntity` from the `items` table.
- Room's `onDelete = ForeignKey.SET_NULL` automatically sets `item_id = null` on all linked expense rows.
- `item_name` on those expenses is UNCHANGED.
- Write CSV backup.
- Item pill disappears from the list. Corresponding chip disappears from Home word cloud.

---

## 7. Screen: Item Popup

### 7.1 Trigger
Opened when user taps any chip in the Home word cloud.

### 7.2 Appearance
Displayed as a `BottomSheetDialog` or a centered `AlertDialog`-style modal over a dimmed background.

### 7.3 Content (top to bottom)
- Title row: item name (e.g. "Add amount · Coffee") + "✕" close button on the right
- Amount input field (large text, e.g. 20sp bold)
  - Prefix: currency symbol "₹" as a static non-editable label inside the field on the left
  - `keyboardType = KeyboardType.Decimal`
  - **`autoFocus = true`** — keyboard MUST raise automatically when the popup opens. No extra tap required.
  - `imeAction = ImeAction.Done` — pressing Done on the keyboard triggers Save.
- Date picker field (same behaviour as home screen date picker — optional, defaults to today)
- "Save" button (full width, primary style)

### 7.4 Save Action
- Validate amount (see Section 12). If invalid: show inline error, do not save.
- On valid save: insert into `expenses` with:
  - `item_name` = the chip's item name
  - `item_id` = the chip's item id
  - `amount` = entered value
  - `date` = selected date or today
  - `created_at` = now
- Write CSV backup.
- Show brief Snackbar on home: `"Saved"`
- Close popup.
- Clear fields for next use.

---

## 8. Screen: Ledger

### 8.1 Layout (top to bottom)
```
[ Top bar: "Ledger" + back arrow ]
[ Item name filter chips (horizontal scroll) ]
[ Date mode toggle: "Single date" | "Date range" ]
[ Date input(s) ]
[ Sort controls ]
[ Group controls ]
[ Date total banner ]   ← only when a date filter is active
[ Expense rows, grouped ]
[ Divider ]
[ Total row ]
```

### 8.2 Item Name Filter Chips

- First chip: "All" (selected by default)
- Then one chip per saved item name (from `items` table), in alphabetical order
- Then one chip: "Others"
- Only one chip can be active at a time.
- "All": no filter on `item_id`.
- A saved item chip (e.g. "Coffee"): filter `WHERE item_id = [id]`.
- "Others": filter `WHERE item_id IS NULL`.

### 8.3 Date Mode Toggle

A segmented control with two options:
- "Single date" | "Date range"
- Default: "Date range"
- Switching mode clears any currently selected date(s).

**Single date mode:**
- Shows one date picker field.
- On date selected: query `WHERE date = ?`

**Date range mode:**
- Shows two date picker fields: "From" and "To".
- Either or both can be left empty.
- If only From is set: query `WHERE date >= ?`
- If only To is set: query `WHERE date <= ?`
- If both set: query `WHERE date BETWEEN ? AND ?`
- If neither set: no date filter (show all).

**Calendar UI:**
- Tapping a date field opens `DatePickerDialog`.
- In date range mode, both From and To open separate `DatePickerDialog` instances.
- In date range mode calendar display: visually highlight the selected date range (from = filled circle, to = filled circle, days in between = light tinted background).

### 8.4 Sort Controls

Two independent sort dimensions shown as small toggle buttons:

**Amount sort:**
- "↓ Amount" (descending) | "↑ Amount" (ascending)
- Default: none selected (sort by date)

**Date sort:**
- "↓ Date" (newest first) | "↑ Date" (oldest first)
- Default: newest first

Only one sort can be active at a time across both rows. Tapping a button activates it and deactivates any previously active sort.

### 8.5 Group Controls

Three options (radio-style, one active):
- "By name" — group all rows by `item_name`. Each group shows a group header.
- "By date" — group all rows by `date`. Each group shows a date header.
- "None" — flat list, no grouping.

Default: "By name"

**Group header (By name):**
- Left: the item name (e.g. "Coffee" or "Petrol" — the actual `item_name` value)
- Right: sum of amounts for that group + percentage of filtered total (e.g. `₹ 360 · 37%`)

**Group header (By date):**
- Left: date (e.g. "28 May 2025")
- Right: sum for that day + percentage

**Others group header:**
- Shows "Others" as the group label.
- Sub-groups within Others by `item_name` (e.g. Petrol rows together, Medicine rows together).
- Each sub-group shows item name + subtotal.

### 8.6 Date Total Banner

Appears ONLY when a date filter is active (single date selected, or at least one of From/To is set in range mode).

Layout:
- Left: date label (e.g. "28 May 2025" or "20 May – 28 May"), below it: entry count (e.g. "5 entries")
- Right: total amount for that date/range (respecting any active item name filter too)

This total respects BOTH the date filter AND the active item name filter chip simultaneously.

Hidden when no date filter is applied.

### 8.7 Expense Row — Normal State

Each row shows (left to right):
1. Tag badge: item name in a coloured pill
   - Saved item entry: blue background, dark blue text
   - Others entry: grey background, dark grey text
   - Text in badge = `item_name` value (the actual name, e.g. "Petrol", NOT the word "Others")
2. Date (if not grouped by date): small muted text
3. Spacer (flex)
4. Amount: `₹ XXX` bold
5. Percentage: `XX%` of filtered total, small muted text
6. Edit icon button
7. Delete icon button

### 8.8 Expense Row — Edit Mode

Triggered by tapping the edit icon. Only one row can be in edit mode at a time. Opening edit on a second row collapses the first.

The row expands to show:
1. Tag badge (static, not editable)
2. Amount input (pre-filled, `keyboardType = KeyboardType.Decimal`, auto-focused)
3. Date input (tappable, opens DatePickerDialog, pre-filled with current date)
4. Spacer
5. Green tick button → save changes
6. Grey X button → cancel, revert to normal state

**On green tick:**
- Validate amount (see Section 12). If invalid: show inline error.
- `UPDATE expenses SET amount = ?, date = ? WHERE id = ?`
- Write CSV.
- Collapse row back to normal state.
- Refresh total/banner values.

### 8.9 Expense Row — Delete Mode

Triggered by tapping the delete icon on a normal state row.

Row switches to danger state:
- Red-tinted background
- Text: `"Delete [ItemName] ₹[Amount]?"` (red text)
- Green tick → confirm delete
- Grey X → cancel, revert to normal state

**On green tick:**
- `DELETE FROM expenses WHERE id = ?`
- Write CSV.
- Remove row with a brief fade animation.
- Refresh totals.

### 8.10 Total Row (footer)

Always visible at the bottom, below all expense rows.
- Left: "Total"
- Right: sum of all currently filtered expenses

---

## 9. Screen: Settings

### 9.1 Navigation
Accessible from: hamburger sidebar, and from the gear icon on the Home top bar.

### 9.2 Layout

**Section: Backup**

| Row | Right side | Action |
|---|---|---|
| CSV backup | Toggle (on/off) | Enable/disable auto CSV backup on every DB change |
| Export CSV | Download icon | Opens SAF file picker to choose save location, writes CSV |
| Import CSV | Upload icon | Opens SAF file picker to select a CSV, triggers import flow |

**Section: Display**

| Row | Right side | Action |
|---|---|---|
| Currency symbol | Current symbol + chevron (e.g. "₹ ›") | Opens a small bottom sheet or dialog to pick symbol: ₹, $, €, £, or type custom |

**Section: Data**

| Row | Right side | Action |
|---|---|---|
| Clear all data | Red trash icon | Confirmation dialog → deletes all rows from both tables, rewrites empty CSV |

**Footer:**
- App version: "Selavu v1.0" in small muted centered text

### 9.3 CSV Backup Toggle
- If toggled OFF: set `backupEnabled = false` in `SharedPreferences`. No CSV is written on future DB changes.
- If toggled ON: set `backupEnabled = true`. Immediately trigger a CSV write.

### 9.4 Export CSV
- Uses SAF `Intent(Intent.ACTION_CREATE_DOCUMENT)` with MIME type `text/csv`.
- Suggested filename: `selavu_backup_YYYYMMDD.csv`
- Writes the full CSV content to the chosen location.

### 9.5 Import CSV
- Uses SAF `Intent(Intent.ACTION_OPEN_DOCUMENT)` with MIME type `text/csv`.
- On file selected: parse the CSV (see Section 11.3).
- Show a confirmation dialog before importing: `"This will add [N] expenses to your ledger. Continue?"` (import is additive, not a full replace unless the DB is empty).
- On confirm: run the import logic (see Section 11.4).

### 9.6 Clear All Data
- Confirmation dialog:
  - Title: "Clear all data?"
  - Body: "This will permanently delete all your expenses and saved item names. This cannot be undone."
  - Buttons: "Cancel" | "Delete everything" (red)
- On confirm: `DELETE FROM expenses`, `DELETE FROM items`, rewrite empty CSV.

---

## 10. First Launch: Storage Permission

### 10.1 Trigger
On the very first app launch only. Detected by checking `SharedPreferences` for a boolean key `"permissionDialogShown"`. If `false` (or absent): show the permission dialog after the Home screen has loaded. Set `"permissionDialogShown" = true` immediately so it never shows again.

### 10.2 Permission Dialog

Displayed as a non-dismissable `AlertDialog` (tapping outside does not close it — only buttons do).

Content:
- Icon: storage/floppy disk icon in a circular blue-tinted container
- Title: "Allow storage access?"
- Body: "Selavu would like to save an automatic backup of your expenses to your device storage.

This is used **only for backup**. No data is read from other folders, and nothing is uploaded anywhere."
- Buttons:
  - "Not now" (outlined, secondary style)
  - "Allow" (filled, primary style)

### 10.3 On "Allow"
- Request `WRITE_EXTERNAL_STORAGE` on Android ≤ 9, or use SAF `ACTION_OPEN_DOCUMENT_TREE` on Android 10+.
- If granted: set `backupEnabled = true` in `SharedPreferences`. Immediately write initial CSV.
- If denied by system: set `backupEnabled = false`. Show a small non-intrusive `Snackbar`: `"Backup disabled. You can enable it in Settings."`.

### 10.4 On "Not now"
- Set `backupEnabled = false` in `SharedPreferences`.
- App continues to function fully without backup.
- A small nudge row appears in Settings → Backup section: `"Backup is disabled. Tap to enable."` which, when tapped, requests the permission again.

### 10.5 Re-requesting Permission
- If user denied with "Not now" and later taps the nudge in Settings:
  - If `shouldShowRequestPermissionRationale` = true: show the same permission dialog again.
  - If permanently denied (user tapped "Don't ask again" in system dialog): show a dialog explaining they need to go to system Settings → Apps → Selavu → Permissions to enable Storage manually.

---

## 11. CSV Backup System

### 11.1 File Location
- Primary (internal, always): `context.filesDir/selavu_backup.csv`
  - This is the app's private storage. Always writable without permission.
- Secondary (external, requires permission): determined by user via SAF export.

### 11.2 CSV Format

**Header row (always present, even in empty file):**
```
id,item_name,item_id,amount,date,created_at
```

**Data rows:**
```
1,Coffee,5,120.0,2025-05-28,2025-05-28T10:30:00
2,Petrol,,800.0,2025-05-26,2025-05-26T09:15:00
```

- `item_id` is empty string (not the word "null") when null.
- `amount` always has one decimal place minimum.
- `date` in `YYYY-MM-DD` format.
- `created_at` in ISO 8601 datetime.
- Fields with commas: wrap in double quotes. E.g. `"item, with comma"`.

### 11.3 When to Write CSV

After EVERY successful:
- INSERT expense
- UPDATE expense
- DELETE expense
- DELETE item (which cascade-nulls expense item_ids)
- Clear all data

**Only write if `backupEnabled = true`** in SharedPreferences.

**Write strategy:** Always write the full file from scratch (SELECT * FROM expenses, write all rows). Do not append. This keeps the file consistent.

Write is done on a background coroutine (`Dispatchers.IO`). Do not block the UI.

### 11.4 Import Logic

When user imports a CSV:

1. Parse every data row (skip header).
2. For each row, check the `item_id` column:
   - If `item_id` is non-empty: look up whether an item with that `id` exists in the local `items` table.
     - If it exists: use it as-is.
     - If it does NOT exist: look up whether an item with the same `item_name` exists.
       - If name match found: use that item's id.
       - If no match: create a new item with that `item_name`, get the new id, use it.
   - If `item_id` is empty: `item_id = null` (Others bucket). `item_name` preserved as-is.
3. Insert each expense row into `expenses` table.
4. After all rows inserted: write CSV backup.
5. Show Snackbar: `"Import complete. [N] expenses added."`

---

## 12. Validation Rules

### 12.1 Amount Validation

Apply identically in all three places: Home manual entry, Item Popup, Ledger inline edit.

Extract as a shared utility function `validateAmount(input: String): AmountValidationResult`.

| Condition | Result | Save button |
|---|---|---|
| Empty string | Error: "Amount is required" | Disabled |
| Non-numeric characters present | Error: "Enter a valid number" | Disabled |
| Parsed value ≤ 0 | Error: "Amount must be greater than 0" | Disabled |
| Negative value | Error: "Amount cannot be negative" | Disabled |
| Valid positive number | No message shown | Enabled |

**Trigger timing:** Validation runs `onFocusChanged` (when amount field loses focus) AND when Save/tick is tapped. NOT on every keystroke (to avoid showing errors while user is mid-type).

**Do NOT show the "ok / looks good" message for valid input.** Show nothing when valid — only show messages for error states.

### 12.2 Item Name Validation

| Condition | Result |
|---|---|
| Empty | Save button disabled |
| Contains disallowed characters | Character is silently rejected on keypress (never appears in field) |
| Equals "Others" (case-insensitive) | Inline error: "'Others' is a reserved name". Save disabled. |
| Duplicate of existing saved item (in Item Names screen) | Inline error: "This item already exists" |

**Allowed characters:** A–Z, a–z, 0–9, space, `.`, `,`, `'`, `-`, `_`
**Character regex:** `^[A-Za-z0-9 .,'\-_]+$`

---

## 13. Edge Cases & Business Rules

### 13.1 "Others" is a bucket, not a name
- The word "Others" never appears in the `item_name` column of the database.
- "Others" is only a UI concept used to group and filter expenses where `item_id IS NULL`.
- When displaying a row in the Others bucket: show the actual `item_name` (e.g. "Petrol"), not "Others".
- The badge color on Others rows is grey to visually distinguish them from saved-item rows (which are blue).

### 13.2 Item deletion cascade
- When a saved item is deleted: `ForeignKey.SET_NULL` sets `item_id = null` on all linked expenses automatically.
- `item_name` on those expenses is NOT changed.
- Those expenses now appear under "Others" filter but still show their original name.

### 13.3 Duplicate item names in the Items screen
- Names are case-insensitively unique. "coffee" and "Coffee" are the same item.
- Store the name as the user typed it (preserve case).
- Compare using `.lowercase()` for uniqueness checks.

### 13.4 "Others" as item name
- User cannot create a saved item named "Others" (any case variant).
- User cannot manually enter "Others" as a free-text item name in the home form.
- Block at input validation level.

### 13.5 CSV import on new device
- The import is additive. It does not wipe existing data.
- Items are auto-recreated from the CSV if they don't exist on the new device (see Section 11.4).
- Duplicate expense rows (same id): skip them (use `INSERT OR IGNORE`).

### 13.6 Monthly summary banner: month boundaries
- Use the current calendar month, not a rolling 30-day window.
- Recalculate on every `onResume`.

### 13.7 One row in edit mode at a time
- If the user taps edit on a second ledger row while another is already in edit mode: silently discard unsaved changes on the first row, collapse it, open the new one.

### 13.8 Date default
- In all three entry points (home form, item popup, ledger edit): if no date is selected, use `LocalDate.now()` at the moment Save is tapped.

### 13.9 Ledger filters are AND logic
- Item name filter + date filter apply simultaneously.
- E.g. "Coffee" chip + date range "May 1–May 31" = show only Coffee entries in May.
- The date total banner respects both filters.

### 13.10 Currency symbol
- Default: ₹
- User can change in Settings (see Section 9.2).
- Store in `SharedPreferences` as `"currency_symbol"`.
- Apply everywhere amounts are displayed.

---

## 14. UI Design System

### 14.1 Typography

| Use | Size | Weight |
|---|---|---|
| Screen titles (top bar) | 18sp | Medium (500) |
| Section labels | 11sp | Medium, ALL CAPS, 0.06em letter spacing |
| Body / row text | 13–14sp | Normal (400) |
| Amount values (large) | 20–22sp | Medium |
| Amount values (row) | 13sp | Medium |
| Muted labels | 11sp | Normal |
| Small hints / notes | 10sp | Normal |

### 14.2 Colors (use Material3 color scheme)

Define in `Theme.kt`:

| Role | Light mode |
|---|---|
| Primary | `#1D9E75` (teal green) |
| Background | `#FFFFFF` |
| Surface | `#F5F5F3` |
| On-surface muted | `#888780` |
| Error | `#E24B4A` |
| Tag — saved item | Background `#E6F1FB`, Text `#0C447C` |
| Tag — Others | Background `#F1EFE8`, Text `#444441` |
| Delete danger | Background `#FCEBEB`, Border `#F7C1C1`, Text `#791F1F` |
| Edit active | Background `#F5F5F3`, Border `#B4B2A9` |

### 14.3 Corner Radius
- Cards, banners, dialogs: 12dp
- Input fields, buttons, chips: 8dp
- Small tags/badges: 20dp (full pill)

### 14.4 Spacing
- Screen horizontal padding: 16dp
- Between sections: 16dp
- Between rows in a list: 0dp (use dividers)
- Inside cards: 12dp padding

### 14.5 Borders
- Default border: 0.5dp, color `#E0E0E0` (light mode)
- Input focus border: 1dp, primary color
- Error border: 1dp, `#A32D2D`

### 14.6 Buttons
- Primary (Save, Allow, Delete confirm): filled background, white text, 8dp radius, full width in forms
- Secondary / outlined (Cancel, Not now): outlined border, no fill, 8dp radius
- Danger (Delete item, Clear all data): filled `#E24B4A` background, white text

### 14.7 Icons
Use Material Icons Extended or Tabler-equivalent:
- Hamburger menu: `Icons.Default.Menu`
- Settings: `Icons.Default.Settings`
- Add: `Icons.Default.Add`
- Edit: `Icons.Default.Edit`
- Delete/Trash: `Icons.Default.Delete`
- Calendar: `Icons.Default.DateRange`
- Tick/Check: `Icons.Default.Check`
- Close/X: `Icons.Default.Close`
- Download: `Icons.Default.Download`
- Upload: `Icons.Default.Upload`
- Storage: `Icons.Default.Storage`

### 14.8 Animations
- Row delete: fade out + slide up, 200ms
- Popup open: slide up from bottom, 250ms
- Sidebar open: slide in from left, 250ms
- Edit row expand: animate height change, 150ms

### 14.9 Snackbar
- Duration: `LENGTH_SHORT` (2 seconds)
- Position: bottom of screen
- No action button unless specified

---

## Appendix A: SharedPreferences Keys

| Key | Type | Default | Description |
|---|---|---|---|
| `permissionDialogShown` | Boolean | false | Whether first-launch permission dialog has been shown |
| `backupEnabled` | Boolean | false | Whether CSV backup is active |
| `currency_symbol` | String | "₹" | Currency symbol for display |

---

## Appendix B: Screen Navigation Map

```
Home
 ├── Hamburger → Sidebar Drawer
 │    ├── Item Names → ItemNamesScreen
 │    ├── Ledger → LedgerScreen
 │    └── Settings → SettingsScreen
 ├── Settings icon → SettingsScreen
 ├── Monthly banner tap → LedgerScreen (pre-filtered: current month)
 └── Chip tap → ItemPopup (modal, not a separate screen)

First launch overlay: StoragePermissionDialog (shown once over Home)
```

---

*End of Selavu App Specification v1.0*
