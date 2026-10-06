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

## Sprint 3

First launch asks for explicit disclaimer acceptance and lets you configure the
weekly schedule, work days/window, break interval and check time. Settings stay
available from Today. Denied notification or exact-alarm permissions do not
block session logging; warnings and system-settings links remain available.

Session, recall, office and pain notifications support quick actions and
snoozes. Exact alarms fall back to inexact when permission is unavailable.
Only the next office break is scheduled. Boot, time/timezone changes and saved
plan/settings/history changes trigger rescheduling. Weekly notifications use
WorkManager and open the plan; the Sprint 4 dashboard is not implemented yet.

Eligible strength/Pilates DONE or MINIMAL logs can be checked from the Home
card or notification. The check is due at the configured time on the next day
and expires 48 elapsed hours later. Red/yellow signals are never cleared just
because a check expires; absence of a PainCheck identifies an unverified log.

Long press the version in Settings to open Debug: alarm ledger, latest 50
events, last local crash stack trace and a test-notification button. No data
is sent off-device.

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

For Sprint 3, also verify on the physical device:
- Deny permissions during onboarding, accept the disclaimer and confirm the app remains usable with a notification warning.
- Enable notifications/exact alarms, save a near-future session time and confirm Debug shows the new trigger.
- With the screen off in Doze, check delivery timing, then reboot and confirm alarms are restored. Do not force-stop the app: Android suppresses alarms until it is launched again.
- Exercise Inizia, Minima, Rimanda 1h, recall Salta oggi, and office Fatto/Snooze/Salta with the app both running and fully closed.
- Toggle Oggi non lavoro and confirm office alarms/notifications stop for that date; change the system timezone and inspect recalculated triggers.
- For a next-day strength/Pilates log, complete the Home check and notification Tutto ok; verify duplicate actions do not overwrite the check and red/yellow colors cannot become green.
- Verify expired checks are unavailable, Debug test notifications appear, and a real crash stack trace is retained after reopening.
- Confirm changing the plan reschedules alarms within one second on the device; this timing acceptance criterion is not established by static checks.
