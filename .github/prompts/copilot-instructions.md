# Copilot instructions — Schiena app

Leggi [PRD.md](../PRD.md) e il seed [program_seed.json](../seed/program_seed.json) prima di qualsiasi modifica. Lavora **uno sprint alla volta** (PRD §8) e fermati a fine sprint per la review.

## Stack
- Kotlin (ultima stabile), Jetpack Compose con Material 3 e Compose BOM, Navigation Compose
- Architettura: single-activity, MVVM + UDF (`UiState` immutabile, `StateFlow`)
- DI: Hilt
- Persistenza: Room (dati), DataStore Preferences (settings)
- Scheduling: `AlarmManager.setExactAndAllowWhileIdle` per sessioni e pause; WorkManager solo per job non puntuali (weekly review, pulizia)
- Grafici: Vico (Compose)
- Serializzazione del seed: kotlinx.serialization
- Test: JUnit5 o JUnit4, Turbine per i Flow, Room in-memory, Compose UI test per i flussi critici

Usa sempre versioni stabili correnti. Niente librerie alpha salvo necessità, da motivare.

## Struttura dei package
```
app/
  data/        room entities, dao, db, seed importer, repositories
  domain/      modelli puri, use case, TrafficLightEngine, ProgressionEngine
  notif/       AlarmScheduler, receivers (Alarm, Boot, TimeChange), NotificationFactory, ReminderCopy
  ui/          home, player, log, paincheck, dashboard, settings, onboarding, redflags, theme
assets/seed/program_seed.json
```

## Regole non negoziabili
1. **Nessun permesso INTERNET**, nessun analytics o SDK di terze parti con rete. `allowBackup=false`.
2. Il contenuto del programma (esercizi, parametri, fasi, piano) arriva **solo** dal seed o dal DB. Mai hardcoded nella UI.
3. La logica di semaforo e progressione vive in `domain/` come **funzioni pure** (input: log + stato + `today: LocalDate`), senza dipendenze Android. Coverage ≥90% su questi due engine.
4. Nessun cambio di fase senza conferma esplicita dell'utente.
5. I testi red flag e 🔴 sono stringhe dedicate e neutre. `ReminderCopy` (pool ironico) non deve mai essere usato per quei casi.
6. Tempo: usa `java.time` e inietta `Clock` per la testabilità. Settimana ISO (lun-dom), fuso di sistema.

## Notifiche: dettagli Android
- Richiedi `POST_NOTIFICATIONS` (API 33+) e gestisci `SCHEDULE_EXACT_ALARM` / `canScheduleExactAlarms()`. Se negato, usa un fallback inexact e mostra un warning.
- Riprogramma tutto su `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, e a ogni modifica di piano o settings.
- Usa canali separati: `sessions`, `pain_check`, `office_breaks`, `weekly`.
- Gestisci le azioni nella notifica con un `BroadcastReceiver` che scrive su Room senza aprire l'app ("Fatto", "Snooze 15'", "Tutto ok").
- Pause ufficio: programma solo il prossimo allarme (catena). Non programmare tutti gli slot del giorno.
- Onboarding: deep link a `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, con una nota per le ROM aggressive (Xiaomi/HyperOS: autostart).

## Test obbligatori (minimo)
- `TrafficLightEngine`: tutte le combinazioni di dolore durante (0-10), dolore irradiato, check 24h (sì/no/assente, scaduto a 48h).
- `ProgressionEngine`: soglie al limite (27 vs 28 giorni, 79% vs 80%, 0 vs 1 🟡, 1 vs 2 non verificate), regressione con 2 🔴 in 7 giorni, blocco di 7 giorni dopo dolore irradiato, proposta massimo una volta a settimana.
- Obiettivo settimanale: `MINIMAL` conta per la streak ma non per l'aderenza.
- Seed importer: import idempotente e "Ripristina default" che non tocca i log.
- `AlarmScheduler`: il calcolo del prossimo trigger rispetta fascia lavorativa, giorni e "Oggi non lavoro".

## Ambiente di build (niente Android Studio)
- Non esiste Android Studio né un emulatore. La build ufficiale gira **solo su GitHub Actions** (`.github/workflows/build.yml`). L'APK viene pubblicato come release e installato sul telefono fisico.
- Il progetto parte dal template `android/architecture-templates` (branch `base`). Non rigenerare il Gradle wrapper e non rimuovere `gradle/wrapper/gradle-wrapper.jar`.
- `versionCode` si legge dalla property Gradle `versionCode` (default 1) e deve crescere a ogni build CI.
- `signingConfigs.debug` punta a `keystore/debug.keystore` (committato, password `android`), così gli aggiornamenti si installano sopra la versione precedente senza perdere i dati.
- Dato che non c'è Logcat, implementa in Sprint 3 una **schermata Debug** (Impostazioni → tocco lungo sulla versione) con:
  - elenco degli allarmi programmati (tipo, prossimo trigger);
  - ultimi 50 eventi di notifica e receiver;
  - ultimo crash (stack trace), salvato in locale da un `UncaughtExceptionHandler`;
  - bottone "Invia notifica di test".
- Prima di ogni push, verifica la compilazione ragionando sul codice. Se la CI fallisce, l'utente ti incollerà il log: correggi la causa, non il sintomo.

## Comandi (eseguiti in CI)
```
./gradlew testDebugUnitTest lintDebug assembleDebug -PversionCode=<n>
```

## Convenzioni
- UI in italiano, codice e identificatori in inglese.
- Stringhe in `strings.xml`. Il pool dei reminder va in `res/values/reminder_copy.xml` come string-array per categoria.
- Commit piccoli, uno per feature. Aggiorna `CHANGELOG.md` a fine sprint.
- Se una scelta del PRD è ambigua o tecnicamente discutibile: segnalala e proponi un'alternativa. Non improvvisare.
