# ADR 006: Statusanzeige – Actionbar als Standard, Paket-Hologramm optional

- **Status:** Akzeptiert für den Standard (`DECISIONS` Nr. 8); Paket-Hologramm wartet auf den Owner-Test
- **Datum:** 2026-10-19

## Entscheidung

- v1 zeigt Phase, Fortschritt und Eventstatus über die **Actionbar** und Chat-Nachrichten.
- Das paketbasierte Text-Display (`docs/POC_RESULTS.md` §9) ist im Owner-Test vom 2026-10-19 sichtbar und lässt sich entfernen. Eingebaut wird es erst, wenn zusätzlich bestätigt ist: keine Duplikate nach Chunk-Reload, Relog und Dimensionswechsel. Das ist ein Folge-Spike in Phase 2 oder 3.
- Wenn es eingebaut wird: nur Pakete, nie eine gespeicherte Entity. Neu senden, sobald ein Spieler den Chunk zu sehen beginnt, und beim Dimensionswechsel. Den passenden Hook für 1.21.1 muss der Hologramm-Spike noch belegen.
