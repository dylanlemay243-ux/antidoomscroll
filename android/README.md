# Taper — Android v1

Native Kotlin + Compose. Builds an installable debug APK in GitHub Actions, no local
Android Studio required (though Studio makes debugging far easier).

## What v1 actually does

- First-run onboarding: what it does, screen-time permission, baseline (use your last 7
  days or measure a fresh week with nothing blocked), pace, then the pause screen —
  including the prominent disclosure Play requires before an accessibility service
  (`OnboardingActivity.kt`).
- Reads real per-app foreground minutes via `UsageStatsManager` (`UsageRepo.kt`).
- Keeps its own on-device daily history so streaks and "hours saved" survive past
  Android's ~28-day usage-stats window (`History.kt`).
- Lowers the daily target by your pace each week, down to a floor you pick
  (`TaperPlan.kt`).
- Escalates intervention with the weeks: reminders (w1–2) → 15s pause (w3–5) →
  soft windows (w6–7) → windows only (w8+), with an optional hard block.
- Shows the pause screen over a tracked app via an `AccessibilityService`
  (`BlockerService.kt` → `PauseActivity.kt`). Package names only; window content is
  never read (`canRetrieveWindowContent="false"`).
- Hourly check that notifies once a day when you cross 75% of target, and snapshots the
  day into history (`Reminders.kt`).
- Four screens: Today (ring, streak, per-app), Insights (7-day bars, all-time beads),
  Ladder (streak, hours saved, 12-week plan), Settings (pace, floor, editable window
  times, intervention toggles, tracked apps, re-measure) — `MainActivity.kt`.

Not yet: ads or any monetization, Caprasimo headings (drop `Caprasimo.ttf` into
`app/src/main/res/font` and set `fontFamily` in `Theme.kt`), a real app icon.

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

Onboarding walks you through both permissions; they're granted in system settings by
hand, which is Android's design and can't be automated.

1. **Screen time access** — Settings → Apps → Special app access → Usage access → Taper.
2. **The pause screen** — Settings → Accessibility → Installed apps → Taper → on.
3. Pick a baseline: your last 7 days, or measure a fresh week (nothing is blocked while
   measuring).

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
