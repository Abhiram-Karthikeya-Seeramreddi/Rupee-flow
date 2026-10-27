# RupeeFlow 💸

**A personal finance app for Android, built around an Indian salary cycle (28th → 27th).**
Track every rupee you earn, spend, save or move — offline, private, and in about 4 taps per expense.

> Works completely offline. No login, no cloud, no internet permission. Your data stays on your phone.

---

## What's new in 1.1.0
- 🎤 **Voice entry fixed** — no more crash. The app now asks for microphone permission and listens inside the app (live text while you speak).
- 📎 **Receipts fixed** — attach from **Camera** or **Gallery** (system photo picker, no storage permission). Tap a receipt to view it.
- ⌨️ **Add screen redesigned** — full screen, the amount uses the phone's number keyboard, text fields use the normal keyboard (the old on-screen keypad is gone).
- ✅ **Clear confirmations** — e.g. “Expense added · ₹250 · Groceries”, “Saved ₹5,000 → Savings”.
- 🐷 **Goals**: “To” now lists only savings accounts, with a one-tap *Create Savings account*.
- 🏅 **Badminton → Sports**, with Badminton, Cricket, Football, Swimming and Sports gear as subcategories. Existing data moves automatically.
- 🧹 **Cleaner UI** — unused budgets collapsed, shorter text everywhere, challenge amount starts empty.

## Table of contents
1. [What the app does](#what-the-app-does)
2. [Technologies used](#technologies-used)
3. [Quick start for beginners (Windows)](#quick-start-for-beginners-windows)
4. [Quick start (macOS / Linux)](#quick-start-macos--linux)
5. [Run it on a phone or emulator](#run-it-on-a-phone-or-emulator)
6. [Make a change, commit and push](#make-a-change-commit-and-push)
7. [Project structure](#project-structure-where-things-live)
8. [Running the tests](#running-the-tests)
9. [How the money maths works](#how-the-money-maths-works-important-rules)
10. [Known limitations](#known-limitations)
11. [Troubleshooting](#troubleshooting)

---

## What the app does

| Screen | What you get |
|---|---|
| **Home** | "Safe to spend today" (with the maths shown), salary-day prompt, logging streak, today / week / cycle spending, accounts, budgets, upcoming bills, insights |
| **Add (+)** | Full-screen form: amount opens the phone's number keyboard, one-tap repeats of things you buy often, category grid (with subcategories like Sports › Badminton), Today/Yesterday, notes, receipt from **camera or gallery**. Type or **say** “250 biryani upi” (asks for microphone permission the first time) and it fills the form for you |
| **Activity** | Search, filters, sort, swipe to delete (with undo) |
| **Plan** | Budgets (daily → yearly, salary cycle, custom), alerts at 50/75/90/100 %; budgets with no spending yet are folded into one tidy row. Savings goals (money moves into a Savings account — one tap creates it), savings plan, small challenges |
| **Bills** | Subscriptions & recurring payments (Netflix, gym, EMI…), reminders 7/3/1 days before, “mark paid” without double counting |
| **Insights** | Charts by category, daily spend, 6-cycle trend, essential vs non-essential, top merchants, unusual spends, a transparent habits score |
| **Settings** | Salary day, accounts & reconcile, categories, auto-categorise rules, reminders, backup/restore (optional password), CSV export, bank-CSV & SMS import, app lock, dark mode |

Extras: home-screen widget, Quick Settings tile, long-press app-icon shortcuts.

---

## Technologies used

| What | Technology | Version |
|---|---|---|
| Language | Kotlin | 2.0.21 |
| UI | Jetpack Compose + Material 3 | Compose BOM 2024.12.01 |
| Architecture | MVVM, single source of truth (`SnapshotBuilder`), separate pure-Kotlin `:core` module | — |
| Database | Room (SQLite) with exported schema | 2.6.1 |
| Settings | Jetpack DataStore (Preferences) | 1.1.1 |
| Background work | WorkManager | 2.10.0 |
| Navigation | Navigation Compose | 2.8.5 |
| Security | AndroidX Biometric (app lock), AES-256-GCM + PBKDF2 (encrypted backups) | 1.1.0 |
| Async | Kotlin Coroutines + Flow | 1.9.0 |
| Charts | Hand-drawn with Compose Canvas (no chart library) | — |
| Dependency injection | Simple manual container (`AppContainer`) — no Hilt needed | — |
| Build | Gradle 8.11.1, Android Gradle Plugin 8.7.3, KSP | — |
| Tests | JUnit 4, kotlin.test, Room testing, Compose UI test | — |
| Android | minSdk 26 (Android 8.0), target/compile SDK 35 | — |

---

## Quick start for beginners (Windows)

You need two things installed: **Git** and **Java 17 or newer**. Android Studio is optional but recommended for editing code.

### Step 1 — Install the tools (one time)
1. **Git**: download from <https://git-scm.com/download/win> and install with the default options.
2. **Android Studio** (recommended): download from <https://developer.android.com/studio>, install, open it once and finish the setup wizard (it downloads the Android SDK and includes Java).
   *Don't want Android Studio?* Install **Java 17+** (e.g. <https://adoptium.net>) and later run `SETUP-SDK.bat` from this project to install just the Android SDK.

### Step 2 — Download (clone) the project
Open **Command Prompt** (press Start, type `cmd`, press Enter) and run:

```bat
cd %USERPROFILE%\Documents
git clone https://github.com/Abhiram-Karthikeya-Seeramreddi/Rupee-flow.git
cd Rupee-flow
```


### Step 3 — Build the app
**Option A — one double-click:** open the `Rupee-flow` folder in File Explorer and double-click **`BUILD-APK.bat`**.
- The first build downloads Gradle and libraries, so it can take 5–15 minutes.
- When it finishes you'll get **`RupeeFlow-debug.apk`** in the folder (or `RupeeFlow-1.1.0.apk` if you have the signing key, see below).
- Everything the build prints is saved in `build-log.txt`. If it fails, look for lines starting with `e:` or `FAILURE`.
- If it says *SDK location not found*, run **`SETUP-SDK.bat`** once, then `BUILD-APK.bat` again.

**Option B — Android Studio:** *File → Open →* select the `Rupee-flow` folder → wait for “Gradle sync” to finish (bottom bar) → press the green **▶ Run** button with your phone or an emulator selected.

---

## Quick start (macOS / Linux)

```bash
git clone https://github.com/Abhiram-Karthikeya-Seeramreddi/Rupee-flow.git
cd Rupee-flow
# Tell Gradle where the Android SDK is (Android Studio puts it here by default):
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # macOS
# echo "sdk.dir=$HOME/Android/Sdk" > local.properties         # Linux
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## Run it on a phone or emulator

**Real phone (easiest):**
1. Copy the `.apk` to your phone (USB, Google Drive, or WhatsApp → send as *Document*).
2. Tap it → allow *Install unknown apps* when asked → **Install**. If Play Protect warns, tap *More details → Install anyway*.

**From Android Studio:** turn on *Developer options → USB debugging* on your phone, connect it with a USB cable, pick it in the device dropdown and press **▶ Run**. Or create an emulator via *Device Manager → +*.

> ⚠️ A debug build and a release build are signed with different keys. Uninstall one before installing the other (export a backup in the app first if you have data).

---

## Make a change, commit and push

### First time only — tell Git who you are
```bash
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
```

### The everyday loop
```bash
git pull                                # 1. get the latest changes first
git checkout -b my-change               # 2. make a branch for your work
#    ... edit code in Android Studio, build, try it on a phone ...
./gradlew :core:test testDebugUnitTest  # 3. run the tests  (Windows: gradlew.bat ...)
git status                              # 4. see what changed
git add .                               # 5. stage your changes
git commit -m "Add dark mode to widget" # 6. save a snapshot with a clear message
git push -u origin my-change            # 7. upload your branch
```
Then open the repository on GitHub and click **Compare & pull request** so the other person can review and merge it.
Collaborators can also push straight to `main` for small fixes: `git checkout main`, `git pull`, edit, `git add .`, `git commit -m "..."`, `git push`.

**Never commit:** `keystore.properties`, `*.jks`, `local.properties`, `*.apk`, `build/` folders. They're already listed in `.gitignore`.

Every push also runs the tests and builds a debug APK automatically on GitHub (see the **Actions** tab; the APK is attached to each run).

More detail in [CONTRIBUTING.md](CONTRIBUTING.md).

---

## Project structure (where things live)

```
Rupee-flow/
├─ core/                         Pure Kotlin "brain" – no Android code, easy to test
│  └─ src/main/kotlin/com/rupeeflow/core/
│     ├─ Money.kt                ₹ formatting (1,25,000), parsing "1.5k", paise maths
│     ├─ Periods.kt              Salary cycle (28th→27th), weeks, months, custom ranges
│     ├─ Ledger.kt               Balances, spending, transfers, savings – the core rules
│     ├─ Budgets.kt              Budget status/alerts, "safe to spend"
│     ├─ Subscriptions.kt        Bill schedules, monthly/yearly cost, payment matching
│     ├─ QuickParse.kt           "250 biryani upi yesterday" → a filled form
│     ├─ Importers.kt            Bank CSV + bank SMS parsing
│     ├─ Insights.kt / Goals.kt  Analytics, health score, goal progress
│     └─ Categories.kt           Default categories, subcategories (Sports › Badminton…) + keywords
├─ app/                          The Android app
│  ├─ src/main/java/com/rupeeflow/app/
│  │  ├─ data/                   Room database, repository, settings, backup, CSV export
│  │  │  └─ Snapshot.kt          Builds the numbers every screen shows (one source of truth)
│  │  ├─ ui/screens/             One file per screen (Home, Activity, Plan, Bills, Insights…)
│  │  │  ├─ AddTransactionScreen.kt  The Add/Edit entry screen
│  │  │  ├─ VoiceInput.kt          Microphone permission + in-app speech recognition
│  │  │  └─ ReceiptInput.kt        Camera / gallery receipts
│  │  ├─ ui/components/          Reusable cards, charts, pickers
│  │  ├─ ui/theme/               Colours & fonts (light + dark)
│  │  ├─ notify/                 Reminders and alerts (WorkManager)
│  │  ├─ widget/                 Home-screen widget + Quick Settings tile
│  │  └─ MainActivity.kt         App entry point, app lock
│  ├─ src/main/res/              Icons, strings, widget layout
│  ├─ src/test/                  Fast tests that run on your computer
│  ├─ src/androidTest/           Tests that run on a phone/emulator
│  └─ schemas/                   Room database schema history (keep in git!)
├─ BUILD-APK.bat                 Windows: test + build an APK with one double-click
├─ SETUP-SDK.bat                 Windows: install the Android SDK without Android Studio
└─ gradle/libs.versions.toml     All library versions in one place
```

**Where to start reading:** `core/…/Ledger.kt` (the rules), then `app/…/data/Snapshot.kt` (how screens get their numbers), then `app/…/ui/screens/HomeScreen.kt`.

**Typical changes:**
- Change a colour → `ui/theme/Theme.kt`
- Add a default category → `core/…/Categories.kt`
- Change text on a screen → the matching file in `ui/screens/`
- Add a database column → see *Database changes* in CONTRIBUTING.md (needs a migration!)

---

## Running the tests

```bash
./gradlew :core:test             # core money/cycle/budget logic (fast, no phone needed)
./gradlew testDebugUnitTest      # app logic incl. the full acceptance scenario
./gradlew connectedAndroidTest   # database, backup & UI tests – needs a phone/emulator
```
On Windows use `gradlew.bat` instead of `./gradlew`.

The acceptance scenario from the original spec (₹45,000 salary, ₹300 food, ₹500 fuel, ₹10,000 to savings, ₹1,500 gym) is tested end-to-end and must give a bank balance of **₹32,700**.

---

## How the money maths works (important rules)

Please keep these true when changing code — the tests check them:

- All amounts are stored as **whole paise in a `Long`** (₹1 = 100). Never use `Double`/`Float` for money.
- **Transfers / savings / investments** move money between your own accounts: never counted as income or spending.
- **Credit card**: a purchase raises the card's balance owed; paying the card bill is a transfer bank → card, so the purchase is counted once.
- **Refunds** reduce spending in their category; **adjustments** fix a balance without counting as spending.
- **Future-dated** entries don't affect anything until their date.
- A **bill/subscription schedule is never spending** until you confirm the payment; each due date can be paid only once (enforced by a unique DB index).
- The **expected salary is a reminder**, not income, until you confirm it arrived.
- **Salary cycle**: salary day D → cycle runs D to D−1 next month; day 31 becomes 28/29 in February. If the real salary lands within ±7 days, the cycle follows the real date.

---

## Known limitations

- **No automatic bank sync.** Android doesn't allow apps to read bank/UPI apps. You can import a bank **CSV** statement or tap **Scan SMS** to read bank alerts — everything is reviewed before saving. SMS formats differ by bank, so some messages may be missed.
- **READ_SMS** permission is fine for sideloading but would need a special exemption to publish on the Play Store.
- **Voice input** uses the phone's own speech recogniser (Google or Samsung). Some phones need internet for it; typing the same sentence always works offline. Microphone permission is only asked when you tap the mic.
- **Reminders** may arrive a little late because Android saves battery; they never repeat.
- **Database is not encrypted at rest** (relies on Android's app sandbox + phone encryption). Backups can be password-encrypted.
- **Single device only** — no cloud sync between phones. Use Backup/Restore to move data.
- "Unused subscription" hints are based on skipped payments, since the app can't see how often you actually use a service.
- English and Indian Rupee (₹) only.
- Instrumented (on-phone) tests exist but are not run automatically in GitHub Actions.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `SDK location not found` | Run `SETUP-SDK.bat` (Windows) or create `local.properties` with `sdk.dir=…` pointing to your Android SDK |
| `Unsupported class file major version` / Java errors | Install Java 17 or newer, or build from Android Studio (it has its own Java) |
| Build is very slow the first time | Normal — it downloads ~500 MB once. Later builds take ~1 minute |
| `App not installed` on phone | Uninstall the old version first (debug and release use different keys) |
| Mic says "blocked" | Phone Settings → Apps → RupeeFlow → Permissions → Microphone → Allow |
| `Permission denied` running `./gradlew` on Mac/Linux | `chmod +x gradlew` |
| Gradle sync fails in Android Studio | *File → Invalidate Caches → Restart*, then *File → Sync Project with Gradle Files* |

---

## Signing & releases (project owner only)

Release APKs are signed with a private key kept **outside** this repository (`keystore/rupeeflow-release.jks` + `keystore.properties`, both git-ignored). If that file is present, `BUILD-APK.bat` makes a signed `RupeeFlow-1.1.0.apk`; otherwise it makes `RupeeFlow-debug.apk`. To publish an update that installs over the existing app, it must be signed with the same key and keep the application ID `com.rupeeflow.app`. Never upload the key to GitHub.
