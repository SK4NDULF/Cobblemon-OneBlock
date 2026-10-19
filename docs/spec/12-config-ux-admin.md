# Setup-Wizard, Config, Bedienung und Admin-Werkzeuge

> **Spec v4.1** · Teil von `docs/spec/` (Übersicht: `00-INDEX.md`) · Quelle: Masterprompt v4 §16, §18, §27  
> **Lesen in Phase:** 1 (Config), 2 (Wizard, UX), 3 (Guide), 7  
> Abschnitte mit **[v4.1]** sind Ergänzungen gegenüber v4. Alles andere ist der v4-Text unverändert (nur Dokumentnamen an den zusammengelegten Dokumentensatz angepasst).

## 16. Setup-Wizard, Config und sichere Reloads

### Wizard: vier Fragen, aber widerspruchsfreie Defaults

| # | Frage | Default | Gültige Werte |
|---|---|---|---|
| 1 | Maximale Level-8-Inselgröße (Kantenlänge) | **2049** | **501–10001**, ungerade; gerade Eingaben mit Erklärung ablehnen (oder nach ausdrücklicher Bestätigung normalisieren) |
| 2 | Hub-Schutzradius | **512** | Min. 100; max. `stride/2 - safetyMargin`, Standard-Sicherheitsabstand 500 |
| 3 | Maximale Mitglieder pro Insel | **4** | 1–20 |
| 4 | Öffentlicher Server? | **Ja** | Ja/Nein; setzt dokumentierte Sicherheits-/Besucher-Defaults |

- Jede Eingabe wird gegen **die bereits eingegebenen Werte** validiert; insbesondere kann der Hub-Radius nicht größer als die zulässige sichere Slotfläche werden. Für ungültige Kombinationen erklärende Korrektur statt kaputter Standardkonfiguration.
- Wizard beim ersten OP-Join oder per `/ob setup`; klare Buttons `[Ja] [Nein] [Standard]`. Werte per klickbarem Command-Prefill (`/ob setup set <key> `), nicht durch allgemeines Chat-Abfangen.
- Zusammenfassung inklusive Inselgrößen je Level, berechnetem `stride`, Hub-Radius, Datenbankpfad und Hinweisen zu unveränderlichen Werten. Dann `[Speichern] [Zurück] [Abbrechen]`.
- Setup vor erster Insel wiederholbar. Danach Grid-Konstanten und Dimension-ID gesperrt; keine riskante Live-Migration.
- Fehlerhaftes Setup aktiviert kein halbfertiges Gameplay. Saubere `SETUP_PENDING`-Statusmeldung.

### Config-Dateien

`config/oneblock/`:

- `config.json5` — Hub, Grenzen, Lifecycle, Speicher- und Laufzeitparameter.
- `loot_pool.json` — Phasen, Ergebnisse, gewichtete Pools.
- `blocked_blocks.json` — Ausschlüsse/Block-Validator-Regeln.
- `progression_config.json` — Level, Schwellen, Anti-Abuse, Freischaltungen.
- `event_config.json` — Trigger, Typen, Cooldowns, Belohnungsgrenzen.
- `cobblemon.json` — Spawn-Regeln/Budgets und Buffs.
- `biomes.json` — Editor-Limits, Freischaltungen, Kosten.
- `challenges.json` — Ziele und Rewards.
- `templates/` — optionale, sicher validierte Startstrukturen.

**Reload-Klassen:**

- **Live-reloadbar:** validierte Loot-/Event-Gewichte, Messages, die meisten Limits, Herausforderungen und sichere Spawn-Multiplikatoren, sofern Jobs vorher geordnet aktualisiert werden.
- **Nur Neustart:** Datenbankpfad/-Backend, Dimension/Registry-/Generator-Settings, tiefgreifende Bootstrapping-Optionen.
- **Nach Weltanlage unveränderlich:** Grid-Stride/Anker-Schema, entscheidende Slotgeometrie, Mod-/Dimension-Namespace ohne Migration.

`/ob reload` validiert **erst alle zusammengehörigen Dateien**, zeigt Konflikte und übernimmt nur eine konsistente Version. Keine halb aktualisierte Mischung zusammenhängender Gameplay-Configs. Für optional getrennte Domains darf dokumentiert atomar pro Domain geladen werden. Fehlgeschlagene Validierung belässt die letzte gültige Version; Fehler mit Datei/Feld/Zusammenhang.


### Config-Governance und sicherer Modpack-Betrieb

- Für jede Option: Datentyp, Default, Range, Reload-Klasse, Scope (`SERVER`/`WORLD`/`ISLAND`), Semantik, Beispiele, Migration, Invariante. Keine magischen Zahlen nur im Java-Code.
- Content-Packs verwenden **stabile Namespaced-IDs** (`<namespace>:<id>`), Schema-Version und Abhängigkeiten; Referenzen werden beim Laden validiert. Fehlende Pokémon-/Item-/Biome-IDs führen zu klaren Fehlern, nicht zu zufälligen NPEs.
- Config-Übernahme **atomar als konsistenter Snapshot**; aktive Events behalten die zum Start gepinnte Eventdefinition/Versions-ID oder werden definiert migriert. Bei Phasenänderung dürfen laufende Loot-Rolls nicht halb alte/halb neue Daten benutzen.
- Unbekannte Felder werden gemäß dokumentierter Schema-Policy behandelt. Kritische Tippfehler nie lautlos ignorieren; Modupdate erstellt Backup der vorherigen Config und protokolliert Änderungen.
- Live-Reload blockiert oder queued bei Konflikt mit kritischen Insel-, Event-, Spawn- und Biom-Jobs. **Keine** spontanen Player-Kicks oder ungesicherten Weltänderungen durch bloßes Neuladen.

## 18. Moderation, Bedienung und optionaler Komfort

### Administrationssystem (v1)

- `/ob admin info`, `tp`, `slot info`, `setlevel`, `event start`, `buff`, `ban/unban`, `forcereset`, `forcedelete`, `confirm`, `profile`, `doctor`, `repair`, `backup`, `export`, `operations`.
- Strukturiertes Audit-Log als Datei/Datenspeicher mit Rotation, Actor, Target, Operation-ID und Reason. Erweiterbares `AuditLogEvent`, sodass ein externer Discord-Webhook-Mod optional zuhören kann.
- User-Bans aus dem Insel-System müssen mit Join-/Home-/Spawnrouting konsistent bleiben; keine unspielbaren Spawn-Loops.
- `/ob admin export <player>` exportiert **konsistente** Daten und Region-Dateien des zugehörigen Slots. Export ist kein Ersatz für gesicherten Welt-/DB-Gesamtrestore.
- `/ob admin profile`: aktive Inseln, Chunk- und Pokémon-Counts, Spawnattempts/Ablehnungen, Tick-Zeiten/Quantile, Queue-Tiefen, DB-Latenz, Eventzustände, laufende Jobs und Speicherbedarf. Keine teuren Diagnosepolls im Normalbetrieb.

### Spielerbedienung (v1)

- `/ob` öffnet ein Vanilla-Kistenmenü **nur nach verifiziertem serverseitigem Ansatz**, sonst verlässlicher Chat-/Command-Fallback.
- `/ob create`, `home`, `sethome`, `info`, `settings`, `help`, `guide`, `progress`, `challenges`, `event start`.
- Party: `invite`, `accept`, `deny`, `kick`, `leave`, `transfer`, `members`.
- Lifecycle: `reset`, `restore`, `delete`, `confirm`, `status`.
- Öffentlichkeit: `visit`, `expel` (mit ausreichenden Cooldowns und Schutzflags).
- Biome: `biome pos1|pos2|preview|set|list|undo|cancel`.
- Rechtsklick-/Bestätigungs-Buttons wo praktikabel. Destruktive User-Aktionen erklären **genau**, welche Insel/Slot-Daten betroffen sind und welche Daten erhalten bleiben.

### Optional oder **nicht in v1**

- MariaDB/MySQL, Storage-Migration-Command, H2, Inselbewertungen, PvP-Regionen, Warps, Bestenlisten, `/ob near`, `/ob chat`, umfangreiche kosmetische Menüs, aggressive Pokémon-Spezial-KI, eigene Blöcke/Items, aufwändige Inselvorlagen, zusätzliche Mitgliederränge.
- pb4 Placeholder API als **weiche** Integration für Phase 7/8, ohne harte Dependency.
- Bei jedem optionalen Feature begründen: Nutzen, Wartungskosten, API- und Persistenzrisiko. **Keine Scope-Ausweitung ohne Freigabe.**


### Betreibererfahrung und Diagnose ohne Datenrisiko

- `/ob admin doctor --format summary|json` bleibt read-only. `/ob admin operations` listet persistierte Vorgänge mit ID/Stage/Alter/Fehler und erlaubt nur dokumentierte, autorisierte Aktionen (`inspect`, `retry`, `abort` **wo reversibel**).
- `/ob admin profile` zeigt eigene Kosten statt unrichtiger globaler TPS-Zuordnung; unterstützt kurze Stichproben, keine permanente aggressive Instrumentierung.
- Klare Fehlerklassen `CONFIG`, `STORAGE`, `WORLD`, `COBBLEMON`, `PERMISSIONS`, `RESOURCE`, `UNKNOWN`; jedes Admin-Problem erhält eine eindeutige Diagnose-ID. Spielern keine Stacktraces oder privaten Pfade anzeigen.
- Logs minimieren personenbezogene Daten; kein ungeschütztes Logging von Chat, IPs, Auth-Secrets oder Pokémon-Daten jenseits des notwendigen Betriebskontexts. Aufbewahrungs-/Rotationsregeln dokumentieren.
- Im Fail-Closed-Modus können ungefährliche Status-/Help-Befehle funktionieren; riskante Aktionen werden mit erklärbarer Meldung verweigert. Kein Silent-Success.

## 27. UX, Commands, Accessibility und Onboarding

- Jede wesentliche Funktion ist **über Commands** erreichbar. Inventarmenüs/Chat-Buttons sind Komfort, nicht die einzige Oberfläche. Das System muss auch funktionieren, wenn ein Vanilla-UI-Hook ausfällt.
- `/ob` zeigt bei vorhandenem Zustand ein verständliches Hauptmenü; `/ob help`, `/ob guide`, `/ob progress`, `/ob status` funktionieren ohne externe Wiki. Keine Command-Namen nur in Farbcodes unterscheiden.
- Feedback bei Aktionen: `SUCCESS`, `PENDING`, `DENIED`, `FAILED`, dazu **warum**, verbleibender Cooldown/fehlende Permission und hilfreicher Folgebefehl. Aktionen mit asynchronem Commit dürfen niemals „Erfolg“ vor dem Commit anzeigen.
- Benutzeroberflächen mit veralteten Slots/Revisionen müssen auf Knopfdruck neu validieren: Bestätigungslinks sind actor-/target-/requestgebunden und kurzlebig. Keine Replay-Angriffe durch klickbare Chattexte.
- Übersetzungen `en_us` und `de_de`; Farben plus Klartext; verständliche Tooltips; keine blinkenden Dauertitel, unendlicher Partikelspam, aufdringlicher Sound bei jedem Block. Einstellbares Feedback für Spieler.
- Statusmeldungen dürfen keine fremden Inselkoordinaten oder Teamdaten offenlegen, falls deren Sichtbarkeit nicht erlaubt ist. `visit` nur für erlaubte Ziele; sichere Teleport-Landing-Zone.
- **Anfänger-Test:** Drei nicht eingeweihte Spieler sollen ohne Anleitung Insel erstellen, ersten Pokéball erhalten, Herausforderung starten und den nächsten Fortschritt erklären können. Feedback dokumentieren und verwenden.
