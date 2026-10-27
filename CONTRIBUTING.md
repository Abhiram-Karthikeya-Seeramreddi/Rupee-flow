# Contributing to RupeeFlow

Thanks for helping! This guide assumes you've never contributed to a project before.

## 1. Get set up
Follow **Quick start** in the [README](README.md) until `BUILD-APK.bat` (or `./gradlew assembleDebug`) works on your machine.

## 2. Pick something to do
Look at the **Issues** tab on GitHub, or open a new issue describing what you want to change. Small, focused changes are easiest to review.

## 3. Work on a branch
```bash
git checkout main
git pull                       # always start from the latest code
git checkout -b fix-budget-text
```
Branch names: short, lowercase, words joined by `-` (e.g. `add-upi-filter`, `fix-widget-crash`).

## 4. Make the change
- Open the project in Android Studio and edit.
- Run the app on a phone/emulator and check it works.
- Money logic goes in `core/` and needs a test in `core/src/test/` — see the rules in the README ("How the money maths works").
- Keep money in paise (`Long`). Use `Money.format()` to show amounts.

## 5. Test
```bash
./gradlew :core:test testDebugUnitTest      # Windows: gradlew.bat :core:test testDebugUnitTest
```
All tests must pass before you push.

## 6. Commit
```bash
git status            # what changed?
git diff              # look at the actual changes
git add .
git commit -m "Show remaining budget per day on Plan screen"
```
Good commit messages say **what** changed, in the present tense, in under ~70 characters.

## 7. Push and open a pull request
```bash
git push -u origin fix-budget-text
```
On GitHub click **Compare & pull request**, describe what you changed and how you tested it, then **Create pull request**. GitHub Actions will run the tests and build an APK you can download from the PR's checks.

## 8. Keep your branch up to date
If `main` moved on while you worked:
```bash
git checkout main && git pull
git checkout fix-budget-text
git merge main          # fix any conflicts, then:
git add . && git commit
git push
```

## Database changes (important)
If you add or change a field in `app/src/main/java/com/rupeeflow/app/data/Entities.kt`:
1. Increase `VERSION` in `AppDatabase.kt` (e.g. 1 → 2).
2. Add a `Migration(1, 2)` with the SQL (`ALTER TABLE …`) to `AppDatabase.MIGRATIONS`.
3. Build once — Room writes `app/schemas/.../2.json`. **Commit that file.**
4. Add a check to `MigrationTest` in `app/src/androidTest/`.
Never use `fallbackToDestructiveMigration()` — it would delete people's money data.

## Never commit
`keystore.properties`, `*.jks`, `local.properties`, `*.apk`, `build/`, `build-log.txt`. They're in `.gitignore` already; if `git status` shows one of them, don't `git add` it.

## Code style
- Kotlin official style (Android Studio: *Code → Reformat Code*, Ctrl+Alt+L).
- One screen per file in `ui/screens/`; reusable UI in `ui/components/`.
- Show friendly messages with `vm.toast("…")`; never crash on bad input — throw `ValidationException` in the repository.
