# ADR 006: Statusanzeige – Actionbar als Standard, Paket-Hologramm optional

- **Status:** Akzeptiert für den Standard (`DECISIONS` Nr. 8); Paket-Hologramm wartet auf den Owner-Test
- **Datum:** 2026-10-19

## Entscheidung

- v1 zeigt Phase, Fortschritt und Eventstatus über die **Actionbar** und Chat-Nachrichten.
- Das paketbasierte Text-Display (`docs/POC_RESULTS.md` §9) wird erst eingebaut, wenn der Owner mit echtem Cobblemon-Client bestätigt: sichtbar, keine Duplikate nach Chunk-Reload, Relog und Dimensionswechsel.
- Wenn es eingebaut wird: nur Pakete, nie eine gespeicherte Entity. Neu senden, sobald ein Spieler den Chunk zu sehen beginnt, und beim Dimensionswechsel. Den passenden Hook für 1.21.1 muss der Hologramm-Spike noch belegen.
