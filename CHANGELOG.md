# Changelog

## Unreleased

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