# Taper — Android v1

Native Kotlin + Compose. Builds an installable debug APK in GitHub Actions, no local
Android Studio required (though Studio makes debugging far easier).

## What v1 actually does

- Reads real per-app foreground minutes via `UsageStatsManager` (`UsageRepo.kt`).
- Takes a baseline from your last 7 days, then lowers the daily target by your chosen
  pace each week, with a floor (`TaperPlan.kt`).
- Escalates intervention with the weeks: reminders (w1–2) → 15s pause (w3–5) →
  soft windows (w6–7) → windows only (w8+), with an optional hard block.
- Shows the pause screen over a tracked app via an `AccessibilityService`
  (`BlockerService.kt` → `PauseActivity.kt`). Package names only; window content is
  never read (`canRetrieveWindowContent="false"`).
- Hourly check that notifies once a day when you cross 75% of target (`Reminders.kt`).
- Four screens: Today, Insights, Ladder, Settings (`MainActivity.kt`).

Not in v1: buddy/accountability (needs a server and accounts), billing, editable
window times, onboarding flow, Caprasimo font (headings use bold system sans — drop
`Caprasimo.ttf` into `app/src/main/res/font` and set `fontFamily` in `Theme.kt`).

## Getting an APK

1. Create a new empty GitHub repo (don't reuse `frisbee` — that's a different project).
2. Push this project's `android/` folder and `.github/workflows/android.yml`, keeping
   the same layout:

   ```
   android/…
   .github/workflows/android.yml
   ```
3. The workflow runs on push. Open **Actions → Build APK → the run → Artifacts** and
   download `taper-debug-apk`.
4. Transfer the APK to your phone and install it. You'll have to allow installs from
   unknown sources.

If the build fails, copy the failing step's log back to me — the first build of a
project this size usually needs a version or import fix or two.

## Turning it on, on the phone

Both permissions are granted in system settings by hand; that's Android's design and
can't be automated. The Today tab links straight to each one.

1. **Screen time access** — Settings → Apps → Special app access → Usage access → Taper.
2. **The pause screen** — Settings → Accessibility → Installed apps → Taper → on.
3. Back in Taper, tap **Open** on the baseline row to accept your 7-day average.

Then open Instagram. Expect the pause screen within a second or so. There's a 45-second
cooldown per app, so it won't loop on you.

## For Play, later

- Debug APKs can't go to Play. You'll need a release keystore, `signingConfigs` in
  `app/build.gradle.kts` with the keystore fed from GitHub secrets, and
  `gradle bundleRelease` to produce an AAB.
- An accessibility service triggers a **Permissions declaration** review. Your use is
  legitimate (it's the core function) but you must describe it plainly and show a
  prominent in-app disclosure before enabling it.
- A privacy policy is mandatory. Usage data stays on-device in v1 — say exactly that.
- Bump `versionCode` on every upload.
