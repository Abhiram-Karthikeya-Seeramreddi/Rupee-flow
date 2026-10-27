# Changelog

## 1.1.0 — 2026-10-27
Based on the app review (bugs, improvements, UI/UX).

### Fixed
- **Root cause of both crashes:** `MainActivity` (a `FragmentActivity`, needed for the fingerprint lock) used `androidx.fragment` 1.2.5, which throws `IllegalArgumentException: Can only use lower 16 bits for requestCode` whenever the Activity Result API opens another screen (voice input, photo picker). Reproduced on an Android 14 emulator and fixed by depending on `androidx.fragment` 1.8.5.
- **Microphone crash.** Voice entry now runs inside the app with Android's `SpeechRecognizer` and a proper `RECORD_AUDIO` permission flow (ask → listening with live text → form filled). Denied permission shows a message; permanently denied offers a Settings shortcut. Phones without a speech service fall back to the system voice screen; every failure is a message, never a crash.
- **Attach receipt crash.** Receipts come from the system photo picker (no storage permission) or the camera (via `FileProvider`, no camera permission). Images are decoded, rotated upright and stored on a background thread. Tap to view. Orphaned receipt files are cleaned up.
- **Emergency fund From/To.** “To” lists only savings/investment accounts; if none exist, a one-tap *Create Savings account* appears. Balances are shown next to each account.
- The Add flow no longer runs inside a bottom sheet; it is a full-screen page, which also removes the window/lifecycle issues behind the crashes.

### Changed
- **Keyboard:** amount uses the numeric keyboard and is focused automatically; the custom on-screen keypad was removed. Note/shop/tags use the text keyboard with sensible capitalisation; numeric-only fields use the number pad.
- **Confirmations** are specific: “Expense added · ₹250 · Groceries”, “Income added · ₹45,000”, “Saved ₹5,000 → Savings”, “Expense deleted” (with Undo).
- **Plan:** budgets with no spending yet are grouped in one collapsible row; budget cards show fewer lines.
- **Challenges:** the “Under” amount starts empty; Start is enabled only with a valid amount.
- **Categories:** “Badminton & sports” is now **Sports** with subcategories Badminton, Cricket, Football, Swimming and Sports gear. Existing transactions, bills, budgets, rules and templates are migrated automatically (one transaction, safe to repeat).
- **Less text** across Home, Add, Plan, Bills, Settings, Accounts, Import/Backup and onboarding.

### Tests
- New: Sports migration and savings-account creation (instrumented), amount input validation and confirmation messages (JVM), Sports category structure and keyword routing (core).

## 1.0.0 — 2026-10-24
First release.
