# back-pain-recovery

Android project scaffold bootstrapped from the official Google
[`android/architecture-templates`](https://github.com/android/architecture-templates)
`base` branch.

Included out of the box:
- Jetpack Compose
- Room
- Hilt
- Navigation
- Gradle wrapper

Notes:
- `customizer.sh` is intentionally not included or used.
- Application/package: Schiena / `it.finardi.schiena`. Program content is local, seeded once into Room.

## Sprint 2

The opening screen is Today, with the database-backed daily session, weekly goal,
phase, office-break count and recent logs. The weekly plan remains accessible.
Strength and minimal sessions use the exercise player; other session types open
the log with duration. Quick logs do not require using the player.

The player follows the enabled database prescriptions, including repetitions of
timed holds, both sides and rest between sets. Backgrounding or recreating the
player pauses it; resume explicitly. A saved log updates an existing entry for
the same date and session type rather than inflating weekly progress.

Notifications, completion of 24-hour checks, onboarding and progression remain
outside Sprint 2. Pending checks are shown but cannot yet be completed.

## Verification

Builds run only in GitHub Actions. CI runs unit tests and lint, builds the app,
and compiles the instrumented tests with `assembleDebugAndroidTest`. It does not
execute Compose instrumented tests because no emulator is configured.
The test APK is available as the `instrumented-test-apk` workflow artifact.

After installing the release APK on the physical device, verify:
- Today, weekly plan and existing program customizations survive the update.
- A strength session progresses through timed holds, sides and recovery, then opens the log.
- Timer completion signals sound/vibration, and the screen stays on only in the player.
- Pause, resume, previous/next, skip, app backgrounding and rotation behave correctly.
- Minimal sessions and DONE count toward the weekly goal; SKIPPED does not.
- Aerobic/Pilates/free logs validate duration; quick logs save in two taps plus the pain slider.
- Pain 4-5 is yellow; pain above 5 or radiating is red; radiating immediately opens safety information.
- Returning from safety information keeps the form. Re-logging updates the existing entry.
- Saving, then rotating on the weekly plan, does not reset navigation to Today.
- Large fonts and dark theme remain usable.
