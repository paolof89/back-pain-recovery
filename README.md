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

First launch asks for explicit disclaimer acceptance and opens Today with the
existing seed defaults. Reminder permissions are optional; the full weekly
schedule, work days/window, break interval and check time remain in Settings.
Denied notification or exact-alarm permissions do not
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

## Guided UX Recovery

Today now puts the next action before statistics. A pending pain check takes
visual priority without blocking the session. Today's recorded planned session
offers editing instead of another primary start. Non-player activities use an
honest recording label. Minimal sessions remain available; quick logs, phase
details and office-break controls are under Other actions. Recent logs live in
Diary, with the existing textual safety statuses and red-flag access.

The player starts in preparation and requires explicit confirmation before each
new exercise, including changes via skip or navigation. Instructions are no
longer truncated. Reopening instructions pauses playback without resetting it;
internal sides, holds and recovery stages retain their existing timing.

Offline image support reads `app/src/main/assets/guides/exercise_guides.json`.
The catalog is intentionally empty pending authorized, clinically reviewed
materials. Until then the player uses the existing database cues; visual
demonstrations are **not complete**. See the guide asset README for the content
contract. No database migration or seed replacement is required.

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
- Aerobic/Pilates/free logs validate duration; quick logs from Today use three taps including Other actions, plus the pain slider.
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

For guided recovery, also verify on the physical device:
- On a separate test installation, consent and enter Today without editing seven days of settings. Do not clear or uninstall the real installation to test first launch.
- Denied permissions do not block entry; full configuration remains reachable from the settings icon. Existing users do not repeat onboarding.
- Identify the next action within 10 seconds without coaching. Start preparation in at most two taps from Today when no check is pending.
- Check pending, already-recorded, non-player activity, loading and failure states. Minimal, direct log, skip, diary, work-off and safety remain reachable.
- Leave preparation open: no timer starts. Confirm, complete or skip an exercise, and verify the next exercise waits for confirmation and scrolls to the top.
- Reopen instructions, background, rotate and restore: time is not reset, playback does not silently resume and completion/log callbacks are not duplicated.
- Verify all existing cue text is readable with large fonts and TalkBack. Verified images must be checked for exercise/laterality accuracy, usable framing and accessible descriptions before publication.
- Run GuidedFlowTest and the existing instrumented suites on the device; CI compilation alone does not establish behavioral success.
