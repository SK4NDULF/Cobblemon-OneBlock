# Owner-Entscheidungen

> **Spec v4.1** · Quelle: Masterprompt v4 §24 plus Review-Punkte · Stand: 2026-10-19
> Diese Datei hat **Vorrang vor `docs/spec/`**. Jede neue Owner-Entscheidung wird hier mit Datum eingetragen.

## Regeln für Rückfragen

- In Phase 0A fragt der Agent **nur** Punkte mit Status `OFFEN` oder `VORSCHLAG` plus wirklich neue Fragen, die sich erst aus dem Repository ergeben. Insgesamt höchstens zehn, gesammelt in **einer** Nachricht.
- Punkte mit Status `ENTSCHIEDEN` werden nicht erneut gefragt. Hält der Agent eine Entscheidung technisch für problematisch, schreibt er ein ADR mit Alternativen und wartet.
- Frage nur, was die technische Umsetzung **materiell** beeinflusst. Formuliere Defaults als Empfehlung.

**Status:** `ENTSCHIEDEN` = gilt · `VORSCHLAG` = gilt als Arbeitsannahme, Owner bestätigt in Phase 0A · `OFFEN` = Owner muss entscheiden

## Entscheidungen

| Nr. | Thema | Entscheidung | Status | Datum |
|---|---|---|---|---|
| 1 | Projektname, Mod-ID, API-Package | Noch offen. Bis dahin `oneblock` als provisorischer Namespace. Muss vor der ersten persistenten Welt feststehen. | `OFFEN` | – |
| 2 | Betriebsmodell | Eigener OneBlock-Server **oder** OneBlock-Dimension auf einem sonst normalen Cobblemon-Server? v4-Default: kompletter OneBlock-Spielmodus mit Admin-Bypass, normale Dimensionen bleiben unverändert. Beeinflusst Join-Routing, Spawn-Logik und Rechte. | `OFFEN` | – |
| 3 | Speicher im ersten Release | Ausschließlich SQLite (pro Welt). MariaDB/MySQL erst nach stabilem SQLite-Release. | `ENTSCHIEDEN` | 2026-10-19 |
| 4 | Inselgröße und Kurve | Default Level 8 = 2049 × 2049; Radien verdoppeln sich: 8, 16, 32, 64, 128, 256, 512, 1024. | `ENTSCHIEDEN` | 2026-10-19 |
| 5 | Begrenzung seltener Belohnungen | Einmalige, accountgebundene Meilensteine plus konfigurierbare Event-Cooldowns. Reset und Restore erzeugen keine neue Einmalchance. | `ENTSCHIEDEN` | 2026-10-19 |
| 6 | Restore-Semantik | Restore legt die bisher aktive Insel als das eine neue Archiv ab (Tausch). | `ENTSCHIEDEN` | 2026-10-19 |
| 7 | Pflicht-Events in v1 | Resource Burst und Pokémon-Schwarm sind Pflicht. Trainer-Duell nur nach erfolgreichem Phase-0B-Spike. | `ENTSCHIEDEN` | 2026-10-19 |
| 8 | Hologramm | Stabilität geht vor: Actionbar und Vanilla-Text als Standard. Paketbasiertes Text-Display-Hologramm nur nach erfolgreichem Spike. | `ENTSCHIEDEN` | 2026-10-19 |
| 9 | Lizenz des Mods | Noch offen (z. B. All Rights Reserved, MIT, LGPL). Beeinflusst Veröffentlichung und die Lizenzprüfung gebündelter Bibliotheken. | `OFFEN` | – |
| 10 | Archiv bei Owner-Wechsel | Das Archiv gehört zur Insel-Linie und geht bei `/ob transfer` mit an den neuen Owner. Reset-Cooldown und Restore-Limits binden an Insel-Linie **und** Akteur. Details: `docs/spec/05-insel-lebenszyklus-und-coop.md`. | `VORSCHLAG` | 2026-10-19 |
| 11 | Unterstützte Server-Plattformen | Linux x64 offiziell (Pflichttests); Windows x64 mit Boot- und Join-Smoke-Test; Linux ARM64 nur, wenn die SQLite-Natives nachweislich funktionieren. | `VORSCHLAG` | 2026-10-19 |
| 12 | Weg für biomabhängige Spawns | Wird erst am Phase-0B-Gate entschieden, anhand der Spike-Ergebnisse und der Reihenfolge in `docs/spec/08-cobblemon.md` (Daten-Lösung → eigener Insel-Spawner → Eskalation). | `OFFEN` (nach Spike) | – |

## Änderungsprotokoll

| Datum | Nr. | Änderung |
|---|---|---|
| 2026-10-19 | 3–8 | Empfehlungen aus Masterprompt v4 §24 vom Owner übernommen |
| 2026-10-19 | 9–12 | Aus dem Review von v4 ergänzt |
