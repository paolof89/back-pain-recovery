---
description: Esegue uno sprint della roadmap del PRD
agent: agent
---
Esegui lo **Sprint ${input:sprint:numero dello sprint}** della roadmap in [PRD.md](../../PRD.md) §8, rispettando [copilot-instructions.md](../copilot-instructions.md).

1. Riassumi in 5 righe cosa implementerai e quali file toccherai. Aspetta il mio ok.
2. Implementa, con i test previsti per lo sprint.
3. Rileggi il diff per errori di compilazione (la build gira solo in CI). Poi dimmi di fare commit e push.
4. Aggiorna `CHANGELOG.md`.
5. Chiudi con: cosa è fatto, cosa resta aperto, cosa devo verificare io su device.
Fermati alla fine dello sprint.
