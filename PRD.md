# PRD — "Schiena" (nome provvisorio)

App Android personale per seguire con costanza un programma di esercizi per lombalgia (discopatia L5-S1). Utente unico: Paolo. Uso privato, dati solo in locale.

> Il programma è un **ponte** in attesa della valutazione fisiatrica. L'app non dà indicazioni mediche: esegue un programma configurabile e applica regole di sicurezza conservative. Il fisiatra potrà modificare esercizi e parametri, quindi tutto il contenuto del programma deve essere **dato**, non codice.

---

## 1. Problema e obiettivo

| | |
|---|---|
| **Problema** | L'utente fatica a capire cosa fare oggi e come eseguire gli esercizi; configurazione e scelte visibili ostacolano la costanza. Non si assume che conosca gli esercizi. |
| **Obiettivo** | Aderenza ≥80% alle sessioni pianificate per 12 settimane, con progressione di fase sicura, guidata dal dolore. |
| **Metrica north star** | % di settimane in cui l'obiettivo settimanale è raggiunto. |

## 2. Principi di design (vincolanti)

1. **Frizione minima**: loggare una sessione richiede ≤2 tap più uno slider.
2. **Mai tutto-o-niente**: esiste sempre una "versione minima" di 5' che conta come sessione fatta.
3. **Streak settimanale tollerante**: l'obiettivo è settimanale (default 4 sessioni). Un giorno saltato non azzera nulla.
4. **L'app propone, l'utente decide**: nessun aumento di carico automatico. I cambi di fase si confermano.
5. **Sicurezza > gamification**: i messaggi su red flag e dolore sono sempre neutri e chiari, mai ironici.
6. **Offline e privato**: nessuna rete, nessun analytics, nessun account.

## 3. Scope MVP

| # | Feature | Priorità |
|---|---|---|
| F1 | Onboarding: orari, giorni, fascia lavorativa, permessi, disclaimer | MVP |
| F2 | Piano settimanale da template | MVP |
| F3 | Home "Oggi" | MVP |
| F4 | Player della sessione con timer | MVP |
| F5 | Versione minima (5') | MVP |
| F6 | Log in un tap + semaforo del dolore | MVP |
| F7 | Check del dolore a 24h | MVP |
| F8 | Notifiche: sessioni, check 24h, pause ufficio | MVP |
| F9 | Dashboard: aderenza, streak settimanale, trend del dolore | MVP |
| F10 | Motore di progressione (gate di fase, regressione) | MVP |
| F11 | Modifica dei parametri degli esercizi e schermata red flag | MVP |

**Fuori scope MVP (v2):** widget sulla home, export CSV/PDF per il fisiatra, Health Connect/smartwatch, video degli esercizi, editor completo dei template, backup.

### Sprint di recupero UX - 2026-10-07

Le seguenti variazioni sostituiscono la presentazione prevista in F1, F3 e F4,
senza cambiare prescrizioni, semaforo, progressione o requisiti delle notifiche.

- **F1:** primo avvio separato dalle impostazioni complete. Consenso esplicito e accesso a Oggi usando piano e orari gia' inizializzati dal seed. Permessi in una sezione facoltativa; giorni, orari e fascia lavorativa modificabili successivamente in Impostazioni. Gli utenti gia' configurati non ripetono il primo avvio.
- **F3:** azione principale prima delle statistiche. Un controllo pendente ha priorita' visiva, ma non introduce un nuovo blocco clinico. La sessione registrata oggi per il tipo pianificato mostra risultato e modifica, non un nuovo avvio dominante. I tipi senza player mostrano "Registra attività". Versione minima sempre accessibile; registrazioni rapide, fase e pause in "Altre azioni"; storico recente nella destinazione Diario. Sicurezza, programma e impostazioni restano raggiungibili.
- **F4:** preparazione prima del primo esercizio e a ogni cambio esercizio, con avvio esplicito. Indicazioni complete senza ellissi; apertura di "Come si fa" mette in pausa senza azzerare il tempo. Serie, lati e recuperi interni restano automatici come nel motore esistente. Per gli stage senza timer il comando principale conferma la serie completata.
- **Guide visive:** immagini offline con descrizione accessibile e passaggi brevi, collegati per ID esercizio. Materiali autorizzati con fonte, licenza e revisione competente sono prerequisiti del rilascio delle dimostrazioni. Il catalogo iniziale e' vuoto: le cue esistenti sono disponibili, ma il requisito immagini resta aperto. Nessuna istruzione clinica nuova viene inventata per colmare questa lacuna.
- **Verifica:** prova sul telefono senza spiegazioni esterne: individuare la prossima azione entro 10 secondi e raggiungere la preparazione dalla home in massimo due tocchi, escluso il controllo pendente. Validare font grandi, TalkBack, rotazione, ritorno dal background e aggiornamento senza perdita di dati. Non riprendere nuove feature prima della review di questo sprint.

---

## 4. Requisiti funzionali e acceptance criteria

### F1 — Onboarding
- Mostra il disclaimer: *"Questa app segue un programma. Non sostituisce medico o fisiatra."* Serve una conferma esplicita.
- Configura:
  - giorni e orari del reminder per ogni tipo di sessione (default da seed);
  - orario del check a 24h (default 09:00);
  - fascia lavorativa per le pause (default lun-ven 09:00-18:30) e intervallo (default 75', range 60-90).
- Richiede i permessi `POST_NOTIFICATIONS` ed exact alarm.
- Guida l'utente a disattivare l'ottimizzazione della batteria (deep link alle impostazioni).
- **AC:** al termine esistono un `WeekPlan` e degli allarmi programmati. Se un permesso viene negato, l'app funziona comunque e mostra un banner persistente "Notifiche disattivate".

### F2 — Piano settimanale
- Template di default (modificabile in Impostazioni: giorno → tipo sessione → orario):

| Lun | Mar | Mer | Gio | Ven | Sab | Dom |
|---|---|---|---|---|---|---|
| A | Aerobico | Pilates | B | Aerobico | Pilates/Libero | Riposo attivo |

- Tipi di sessione: `STRENGTH_A`, `STRENGTH_B`, `AEROBIC`, `PILATES`, `FREE`, `ACTIVE_REST`.
- **AC:** cambiare il piano riprogramma gli allarmi entro 1 secondo.

### F3 — Home "Oggi"
Mostra:
- la sessione di oggi, con un bottone grande "Inizia" e uno secondario "Versione minima (5')";
- l'avanzamento settimanale (es. `3/4 ●●●○`);
- la fase corrente e la settimana nella fase (es. "Fase 1 · sett. 2/4");
- il contatore delle pause ufficio di oggi (es. `4/6`);
- se c'è un check a 24h pendente, una card in alto.

Si può loggare la sessione anche senza player: "Fatta" / "Minima" / "Saltata".

- **AC:** da un'apertura a freddo al log di una sessione servono ≤3 tap.

### F4 — Player della sessione
- Un esercizio per schermata: nome, obiettivo, cue (max 3 righe), serie × reps oppure tenuta, lato (se `perSide`).
- Timer automatico per gli esercizi a tenuta e timer di recupero tra le serie (default 45"). Vibrazione e suono a fine timer.
- Controlli: avanti, indietro, "Salta esercizio".
- Lo schermo resta acceso durante il player.
- Al termine si passa al log (F6).
- Per `AEROBIC` / `PILATES` / `FREE` non c'è player: solo un log con durata in minuti.
- **AC:** una sessione di Fase 1 si completa senza toccare il telefono durante le tenute.

### F5 — Versione minima
- Sessione fissa da ~5': bird dog, dead bug, glute bridge (vedi seed).
- Conta come sessione fatta ai fini dell'obiettivo settimanale.
- **Non** conta ai fini dell'aderenza per il gate di fase (vedi F10).
- **AC:** raggiungibile dalla home, dalla notifica della sessione e dalla schermata di log.

### F6 — Log e semaforo del dolore
Campi:

| Campo | Tipo | Obbligatorio |
|---|---|---|
| esito | `DONE` / `MINIMAL` / `SKIPPED` | sì |
| dolore durante | slider 0-10 | sì se non `SKIPPED` |
| dolore irradiato alla gamba | toggle | no (default off) |
| note | testo | no |

Stato provvisorio della sessione:
- 🔴 se dolore durante >5 **oppure** dolore irradiato = on;
- 🟡 se dolore durante 4-5;
- 🟢 altrimenti, in attesa del check a 24h.

Se il dolore irradiato è on, mostra subito la schermata red flag (F11).

- **AC:** il log si salva con un solo tap su "Salva". Il semaforo è visibile nello storico.

### F7 — Check a 24h
- Il giorno dopo ogni sessione `DONE` o `MINIMAL` di tipo `STRENGTH_*` o `PILATES`, all'orario configurato arriva una notifica.
- Il check chiede:
  1. dolore ora (0-10);
  2. "È tornato come prima dell'allenamento?" (Sì/No).
- Stato finale della sessione:
  - resta 🔴 se era 🔴;
  - 🟡 se era 🟡 **oppure** la risposta è "No";
  - 🟢 altrimenti.
- Se il check non viene fatto entro 48h, la sessione resta "non verificata" e non conta come 🟢 per il gate.
- **AC:** il check si completa direttamente dalla notifica (azioni rapide) o dalla card in home in 2 tap.

### F8 — Notifiche

| Tipo | Quando | Azioni nella notifica |
|---|---|---|
| Sessione | Orario pianificato | "Inizia", "Minima 5'", "Rimanda 1h" |
| Richiamo | +3h se la sessione non è ancora loggata (max 1 al giorno) | "Minima 5'", "Salta oggi" |
| Check 24h | Orario del check | "Tutto ok" (= 🟢 veloce: tornato come prima + dolore ≤3), "Apri" |
| Pausa ufficio | Ogni N minuti nella fascia lavorativa | "Fatto", "Snooze 15'", "Salta" |
| Weekly review | Domenica 19:00 | "Apri" |

Regole:
- Le pause ufficio non partono nei giorni festivi. In MVP: toggle manuale "Oggi non lavoro" in home.
- I testi ruotano da un pool e non si ripetono due volte di fila (§6).
- Dopo un reboot tutti gli allarmi vengono riprogrammati (`BOOT_COMPLETED`).
- **AC:** notifiche puntuali (±1') con il telefono in Doze, se l'exact alarm è concesso. Se non lo è, si passa a inexact con un warning in Impostazioni.

### F9 — Dashboard
- Settimana corrente: sessioni fatte / obiettivo, dettaglio giorno per giorno con i colori del semaforo.
- Streak settimanale: numero di settimane consecutive con obiettivo raggiunto.
- Trend del dolore: grafico a linee su 8 settimane (dolore durante e a 24h), una media per settimana.
- Aderenza delle ultime 2 settimane in % (definizione in F10).
- Weekly review (domenica): riepilogo in 3 righe più lo stato del gate di fase.
- **AC:** la dashboard si carica in <500 ms con 1 anno di dati.

### F10 — Motore di progressione

**Definizioni:**
- *Sessioni pianificate*: le sessioni di tipo `STRENGTH_A`, `STRENGTH_B`, `PILATES` nel piano.
- *Aderenza (14 giorni)*: `DONE` / pianificate negli ultimi 14 giorni. `MINIMAL` non conta.

**Gate di avanzamento di fase** (tutti veri):

| Condizione | Soglia |
|---|---|
| Giorni nella fase corrente | ≥ `phase.minWeeks × 7` |
| Aderenza negli ultimi 14 giorni | ≥ 80% |
| Sessioni 🟡 o 🔴 negli ultimi 14 giorni | 0 |
| Sessioni "non verificate" negli ultimi 14 giorni | ≤ 1 |

Se il gate è soddisfatto, l'app **propone** l'avanzamento: *"Sei pronto per la Fase 2. Consiglio: fallo validare dal fisiatra."* Le azioni sono "Avanza" / "Resto qui". La proposta si ripete al massimo una volta a settimana.

**Regressione:**
- con 2 sessioni 🔴 in 7 giorni, propone di tornare alla fase precedente o di ridurre il volume (−1 serie);
- con 🔴 per dolore irradiato, mostra la schermata red flag e non propone progressioni per 7 giorni.

Ogni cambio di fase viene salvato in `PhaseTransition` (data, da, a, motivo).

- **AC:** unit test su tutte le combinazioni di soglia (vedi CLAUDE.md).

### F11 — Parametri e red flag
- Impostazioni → Programma: per ogni esercizio si possono modificare serie, reps, tenuta e recupero. È anche possibile disattivare un esercizio.
- Si può fare "Ripristina default" dal seed.
- Schermata red flag (statica, tono neutro), sempre raggiungibile da Impostazioni e dallo storico. Contenuto:

> Contatta il medico se compaiono: dolore alla gamba nuovo o in peggioramento, formicolii o intorpidimento persistenti, perdita di forza (es. piede che "cade"), febbre associata al mal di schiena.
> **Vai subito in pronto soccorso** in caso di alterazioni del controllo di vescica o intestino, o di intorpidimento nella zona genitale/perineale.

---

## 5. Data model

| Entità | Campi |
|---|---|
| `Exercise` | id, name, goal, cues[], kind (`REPS`/`HOLD`/`DISTANCE`/`MOBILITY`), perSide |
| `PhaseExercise` | phaseId, sessionType, order, exerciseId, sets, reps?, holdSec?, distanceM?, restSec, enabled |
| `Phase` | id, name, minWeeks, description |
| `WeekPlanEntry` | dayOfWeek, sessionType, reminderTime |
| `SessionLog` | id, date, sessionType, phaseId, outcome, painDuring?, radiating, durationMin?, note?, status (`PENDING`/`GREEN`/`YELLOW`/`RED`/`UNVERIFIED`) |
| `PainCheck` | sessionLogId, timestamp, painNow, backToBaseline |
| `OfficeBreakEvent` | timestamp, action (`DONE`/`SNOOZED`/`SKIPPED`) |
| `ProgramState` | currentPhaseId, phaseStartDate, weeklyTarget, lastGateProposalDate |
| `PhaseTransition` | date, fromPhase, toPhase, reason |
| `Settings` (DataStore) | orari, fascia lavorativa, intervallo pause, checkTime, notificationsEnabled, workOffToday |

Il seed iniziale è in `seed/program_seed.json`.

---

## 6. Tono dei reminder (pool iniziale)

Il tono è colloquiale e ironico, **mai colpevolizzante**. Dopo una sessione saltata, niente guilt trip. L'utente deve poter aggiungere e modificare le frasi (v2; in MVP basta un file di risorse).

**Sessione**
- "La tua schiena ha chiesto un meeting. Niente slide, promesso."
- "Sessione A in agenda. Più breve di una riunione di allineamento, più utile pure."
- "Bird dog time. Il cane non c'è, l'uccello nemmeno. Tu sì."
- "40 minuti per la schiena. O 5, se oggi il mondo brucia."
- "Il disco L5-S1 ti manda i suoi saluti. E un reminder."

**Richiamo**
- "Ancora in tempo. Versione minima: 5 minuti, zero scuse credibili."
- "Non serve la sessione perfetta. Serve una sessione."

**Check 24h**
- "Com'è la schiena stamattina? Un tap, giuro."
- "Report giornaliero: niente KPI, solo come sta la lombare."

**Pausa ufficio**
- "Sei seduto da 75 minuti. Anche l'Excel può aspettare 2 minuti."
- "Alzati. Il modello sta ancora trainando, tu no."
- "Micro-pausa: 2 minuti, poi torni a salvare il mondo (o il Q4)."
- "La sedia non è un piano di carriera. In piedi."

**Weekly review**
- "Settimana chiusa. Vediamo i numeri, senza giudizi."

**Obiettivo raggiunto**
- "4 su 4. La schiena prende nota."
- "Streak di {n} settimane. Costanza sbloccata, chi l'avrebbe detto."

Qualsiasi contenuto legato a red flag o 🔴 usa testi neutri dedicati, **mai** dal pool ironico.

---

## 7. Requisiti non funzionali

| Area | Requisito |
|---|---|
| Piattaforma | Android, minSdk 26, targetSdk ultima stabile |
| Rete | Nessun permesso `INTERNET` |
| Dati | Solo in locale (Room + DataStore), esclusi dal backup cloud (`allowBackup=false`) |
| Affidabilità notifiche | Exact alarm, riprogrammazione al boot e al cambio fuso/ora, onboarding sull'ottimizzazione batteria (attenzione a MIUI/HyperOS e simili) |
| Accessibilità | Target touch ≥48dp, testi scalabili, dark mode |
| Lingua | Italiano |

---

## 8. Roadmap

| Sprint | Contenuto | Done quando |
|---|---|---|
| 1 | Setup progetto, schema Room, import del seed, `ProgramState`, piano settimanale (F2) | Seed caricato, piano visibile, test DAO verdi |
| 2 | Home (F3), player (F4), versione minima (F5), log (F6) | Sessione completa end-to-end su device |
| 3 | Notifiche (F8), check 24h (F7), onboarding (F1) | Notifiche puntuali in Doze, sopravvivono al reboot |
| 4 | Dashboard (F9), motore di progressione (F10), parametri e red flag (F11) | Test del motore verdi, weekly review funzionante |
| 5 | Polish, dark mode, rotazione frasi, bugfix dopo 1 settimana di uso reale | 7 giorni d'uso senza crash né notifiche perse |

---

## 9. Metriche personali di successo (da verificare dopo 4 settimane)

- ≥3 settimane su 4 con obiettivo settimanale raggiunto.
- ≥70% delle pause ufficio con esito `DONE` nei giorni lavorativi.
- Check 24h compilato su ≥80% delle sessioni.
