# Changelog

## Unreleased

### Sprint 3 - 2026-10-06

- Add explicit disclaimer onboarding, database-backed weekly schedule editing, work window/days, check time and notification preferences.
- Request notification permission and link exact-alarm and battery settings; keep the app usable with denied permissions and show persistent warnings.
- Add exact allow-idle session/check/office alarms with inexact fallback, one daily recall, session/office snoozes and quick receiver actions.
- Chain only the next office break; respect work days, work window and dated work-off preference. Reschedule on boot, time/timezone changes, app update, permission changes and persisted data changes.
- Add separate channels and persisted rotating reminder pools; use neutral copy for red/radiating checks. Weekly notification and maintenance use nonpunctual WorkManager jobs.
- Complete eligible 24-hour checks from Home or notifications; atomically save pain and final status, reject duplicate/early/expired actions and preserve red/yellow safety signals.
- Define the check window as 48 elapsed hours from the next-day configured check time because logs store a date, not session completion time. Unchecked low-pain logs become UNVERIFIED; unchecked red/yellow logs retain their safety color and have no PainCheck.
- Add cold/warm notification navigation, transactional seven-day plan replacement, a system-zone-aware injected Clock and protection for due alarm tokens during cold-process startup.
- Add hidden Debug via long press on the settings version: scheduled alarm ledger, last 50 receiver/notification events, locally persisted last crash and test notification.
- Add unit/Room/Compose coverage for scheduling boundaries, stale actions, copy rotation, onboarding persistence, notification requests, check windows, traffic-light confirmation and daylight-saving expiry.

### Sprint 3 Verification

- Editor diagnostics reported no errors. XML parsing, duplicate string names, Kotlin string references, seed integrity, offline/backup manifest assertions and git diff whitespace checks passed locally.
- No Gradle, Kotlin compilation, Android lint or Kotlin tests were run locally, following the CI-only build policy. CI must run testDebugUnitTest, lintDebug, assembleDebug and assembleDebugAndroidTest.
- Instrumented tests are compiled but not executed by the current CI. Physical-device checks remain required for permission denial/grant, cold/warm actions, Doze timing, reboot, timezone change, battery restrictions and crash capture.
- Exact permission does not bypass force-stop or manufacturer restrictions; inexact fallback cannot promise delivery within one minute. Weekly review notification opens the weekly plan; dashboard/review content and progression remain Sprint 4.
- Room plan replacement is atomic; plan and DataStore settings are separate stores, so a failed settings write can leave the new plan saved until retry. The UI retains the draft and reports failure.
- Stop at Sprint 3 for review. No commit or push performed; wrapper, signing, CI versionCode and seed remain intact.

### Sprint 2 - 2026-10-06

- Open on Today with the daily database-backed session, ISO-week progress, phase week, office-break totals, dated work-off toggle and pending-check count.
- Keep the weekly plan accessible and add Navigation 3 routes with saveable navigation keys and an acknowledged save result.
- Add enabled normal/minimal prescription loading, preserving seed and database parameters without changing the Room schema.
- Add an exercise player with automatic repeated holds, side/set progression, recovery timers, manual repetition/distance steps, previous/next/skip and pause/resume.
- Keep the screen awake only during the foreground player; signal timer completion with sound/vibration and pause on background or recreation.
- Provide minimal-session entry points from Today and log; non-strength DONE logs require duration.
- Add quick DONE/MINIMAL/SKIPPED logs, pain slider, radiating toggle, notes, validation, retry and duplicate-save protection.
- Freeze session date/type/phase for playback and saving; restore context through SavedStateHandle.
- Save same-date/type logs transactionally as updates, removing obsolete pain checks; count distinct completed days toward the weekly goal, including MINIMAL but not SKIPPED.
- Add pure provisional/verified traffic-light functions and recent history with textual, color-coded status. Low-pain sessions show a provisional green pending verification.
- Immediately show dedicated neutral safety information on radiating pain; preserve the log draft when returning. Safety information is also reachable from Today/history.
- Add unit tests for traffic-light combinations, weekly counting, Room session persistence, player stages/timers and Home/session state; add Compose form and real navigation-key restoration tests.
- Compile the instrumented test APK in CI and upload it as a workflow artifact, without introducing an emulator.

### Sprint 2 Verification

- Editor diagnostics reported no errors in reviewed changes; XML/resource-reference validation and git diff whitespace checks passed without invoking Gradle locally.
- No Kotlin compilation, lint, unit tests or instrumented tests have been run locally. CI must confirm unit tests, lint, app build and instrumented-test compilation.
- Compose instrumented tests are not executed by the current CI workflow. Physical-device playback, timer signals, rotation, form restoration and accessibility remain to be verified.
- Pending 24-hour checks are display-only. Notification actions/scheduling, check completion and onboarding remain Sprint 3; dashboard, progression and parameter editing remain Sprint 4.
- The static red-flag view is included now for F6 safety; no phase progression or medical recommendations are implemented.
- Weekly progress counts at most one completed day, avoiding inflation from duplicate logs. MINIMAL does not alter phase/adherence state.
- Stop at Sprint 2 for review. Existing wrapper, debug signing, CI versionCode, offline manifest and seed remain intact.

### Sprint 1 - 2026-10-06

- Rename the application and all source/test packages to `it.finardi.schiena`; display name: Schiena.
- Remove the MyModel sample, sample navigation, repository, database and tests.
- Require Android 8 (API 26), disable backup and keep the app without INTERNET permission.
- Add the complete PRD Room schema, ISO-week ordering, java.time converters, indexed history and injectable Clock.
- Copy `seed/program_seed.json` unchanged to `app/src/main/assets/seed/program_seed.json`.
- Import the seed transactionally once using a persistent marker; preserve customizations on subsequent launches.
- Materialize Phase 3 inheritance and retain minimal-session prescriptions, sport protocols and office-break instructions.
- Initialize Phase 1 with the local date, the seed weekly target and the seven-day seed plan.
- Persist validated Settings with Preferences DataStore; initialize defaults once and scope work-off toggles to their date.
- Restore program content and weekly plan atomically without changing session logs, pain checks, office events, phase transitions, current state or settings.
- Display the database-backed weekly plan and current phase, with loading, retry and confirmed restore states.
- Add plan-update repository methods. Plan editing UI and alarm scheduling are not part of this minimal Sprint 1 screen.
- Add JUnit/Robolectric in-memory DAO, seed import/reset, rollback, Settings, converter and UI-state tests, including Flow assertions with Turbine.

### Verification

- Reviewed the diff and integration contracts; checked resources, manifest and seed asset integrity without invoking Gradle locally.
- Android build, lint and Kotlin tests must be confirmed by GitHub Actions (`testDebugUnitTest lintDebug assembleDebug`). Test success is not claimed before CI.
- The Room schema JSON is generated into `app/schemas` by KSP during CI; no generated schema was fabricated locally.
- Keep the existing Gradle wrapper, debug signing key and CI-controlled versionCode unchanged.
- Stop at Sprint 1 for review; no notification, player, progression-engine or onboarding implementation included.