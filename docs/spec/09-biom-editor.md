# Biom-Editor

> **Spec v4.1** · Teil von `docs/spec/` (Übersicht: `00-INDEX.md`) · Quelle: Masterprompt v4 §14  
> **Lesen in Phase:** 0B, 4, 7  
> Abschnitte mit **[v4.1]** sind Ergänzungen gegenüber v4. Alles andere ist der v4-Text unverändert (nur Dokumentnamen an den zusammengelegten Dokumentensatz angepasst).

## 14. Biom-Editor (V1-Kernfeature)

- Zwei 3D-Positionen mit Raycast oder zuverlässigen Commands wählen; Partikelvorschau, inklusive Darstellung der **tatsächlich betroffenen** Zellen.
- Serverbiome sind im relevanten MC-Stand quartbasiert (`4 × 4 × 4` Blöcke). Alle Auswahlen auf entsprechende tatsächliche Zellgrenzen anwenden; negative Koordinaten und Dimension-Y prüfen.
- **Harte Biomgrenzen** sind beabsichtigt; nicht behaupten, dass visuelle Übergänge immer ohne clientseitiges Blending sind, wenn Clients etwas anderes darstellen.
- Erlaubte Biome aus echten Registries; Vanilla-Biome default, zusätzliche verifizierte Cobblemon-/Datapack-Biome konfigurierbar. Kein beliebiger ungültiger Registry-Key.
- Max. Auswahlvolumen, Regionen pro Insel, aktive Jobs, Undo-Speicher und Work-Budget pro Tick konfigurierbar. Edit darf nicht über aktuelle Insel-Border hinausgehen.
- Edit ist ein mehrschrittiger, nachvollziehbarer Job: Validierung → Ressourcen/Kostenprüfung → persistenter Intent/Undo-Snapshot → chunkweise Änderung → Save/Client-Sync → Finalisierung. Unterbrechung überlebt einen Restart als fortsetzbar oder sicher zurückrollbar.
- Client-Chunk-/Biome-Updates so senden, wie es 1.21.1 tatsächlich verlangt; kein blindes Mass-Resend in einem Tick.
- `/ob biome undo` zunächst eine **begrenzt gespeicherte, letzte erfolgreich abgeschlossene Änderung pro Insel**. Definiere Undo bei zwischenzeitlich veränderten Biomen und bei Restore/Reset.
- **Progressionskopplung:** Biome können durch Level/Challenge freigeschaltet werden; Ressourcenkosten sind optional und per Admin abschaltbar. Default einfach und verständlich, keine unspielbaren Startspawns.
- Test: geändertes Biom wirkt nachweislich auf Cobblemon-Spawns, sofern sonstige Spawnbedingungen erfüllt sind. Biome allein sind **keine garantierte Spawn-Erlaubnis**.


### Biom-Edit: Nebenläufigkeit, Kosten, Grenzen, echte Clientanzeige

- Biom-Editor arbeitet mit den **tatsächlich verwendeten 4×4×4-Biomzellen**, negativen Koordinaten und y-Grenzen. Vor Start zeigt Preview die wirksame, gerundete Auswahl, Zellenzahl, geplante Kosten, geschätzte Arbeit und gesperrte Zellen.
- Nur aktive und für die Bearbeitung freigegebene Inselbereiche. In einem inaktiven Archiv darf kein Edit weiterlaufen. `reset`, `restore`, `delete` warten auf sauber quiescierte bzw. abgebrochene Biome-Jobs.
- Bewahre Vorher-Werte und zugehörige **Content-/Chunk-Revision** auf. Undo darf fremde oder nachfolgende Edits nicht blind überschreiben; Konflikte mit Dry-Run melden.
- Client-Updates und reale Cobblemon-Spawn-Kontexte getrennt nachweisen; ein optisch geändertes Biom ohne korrektes Server-Biom gilt nicht als erfolgreich.
- Es gibt ein **Budget pro Tick und Gesamtkapazitätslimit**. Large Selections und parallele Edit-Jobs werden fair über aktive Inseln verteilt; Admin kann Queue und Pausenstatus einsehen.
