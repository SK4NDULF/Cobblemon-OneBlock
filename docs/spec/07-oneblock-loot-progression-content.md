# OneBlock-Kern, Loot, Progression und Content-Packs

> **Spec v4.1** · Teil von `docs/spec/` (Übersicht: `00-INDEX.md`) · Quelle: Masterprompt v4 §11, §12, §26  
> **Lesen in Phase:** 0B (Hologramm-Spike), 2 (Kern), 3  
> Abschnitte mit **[v4.1]** sind Ergänzungen gegenüber v4. Alles andere ist der v4-Text unverändert (nur Dokumentnamen an den zusammengelegten Dokumentensatz angepasst).

## 11. OneBlock-Kern und Loot-System

### Grundmechanik

- Zentraler OneBlock ist an einer persistent gespeicherten Ankerposition. Erfolgreicher Abbau startet **einen** serverautoritativen Regenerationsvorgang; konkurrierende Break-Events dürfen weder mehrfach zählen noch zweimal Loot erzeugen.
- Nach Abbau entsteht der nächste zulässige Block aus der aktuellen Phase. Wenn Ergebnis ein Item/Pokémon/Event ist, muss definiert sein, was sofort als Ersatzblock erscheint, sodass der OneBlock nicht verschwindet oder unabbaubar wird.
- Prüfe Abbaumodi: Survival, Creative, Explosion, Piston, Haste, Silk Touch, Fortune, Auto-Miner, Cobblemon-Effekte und simultane Abbauversuche. Nur explizit erlaubte Aktionen zählen für Progression.
- Hologramm/Actionbar zeigt Phase, Abbauten bis Ereignis und aktiven Eventstatus; bei Chunk-Reload/Restart keine persistenten Fake-Entities oder Duplikate. **Paketbasiertes Text-Display nur bei nachgewiesener Server/Client-Kompatibilität**, sonst stabiler Fallback.

### Loot als typsicheres, datengetriebenes Ergebnis

`BLOCK`, `ITEM`, `POKEMON`, `EVENT`, optional `NOTHING`.

- Gewichtete Loot-Tabelle pro Phase; Parser/Schema mit IDs, Gewichten, Bedingungen, Limits, benötigten Plugins und Fallback-Blöcken.
- `blocked_blocks.json` enthält nie generierbare Blöcke: Bedrock, Portalrahmen, Spawner, Command-Blöcke und weitere exploit-gefährdete Inhalte. Zusätzlich eine **Allowlist/Validator** für Falling Blocks, Flüssigkeiten, Block-Entities, komplexe NBT und nicht unterstützte Zustände.
- **Falling Blocks** (z. B. Sand) müssen sicher an ihrem Platz funktionieren oder in definierte sichere Alternativen umgewandelt werden. Keine unkontrollierte Entity-Spam-/Block-Fall-Kette.
- Cobblemon-Gegenstände: Aprikokos, Bälle, Entwicklungssteine, Beeren, Fossilien, Minzen, weitere verifizierte Inhalte. Registry-IDs aus der Target-Version ableiten, nicht raten.
- Loot-/Event-Provider müssen kontrolliert mit Version, Priorität, Validation und Fehlerisolation registriert werden können.
- **Resource-Audit:** Automatischer Test je Phase: Keine essenzielle Crafting-/Cobblemon-Progression führt in einen Softlock. Admin erhält Diagnostik für unerreichbare Pflichtressourcen oder Spawnbedingungen.

### Acht thematische Phasen

1. Wiese / Start
2. Höhle / Erze
3. Schnee / Eis
4. Ozean / Wasser
5. Dschungel / Pflanzen
6. Wüste / Ruinen
7. Nether / seltene Ressourcen
8. End / Endgame

Reihenfolge und Inhalt sind per Dateien konfigurierbar; die acht Default-Phasen bleiben der kanonische, getestete Pfad. Wahrscheinlichkeit und Belohnungsstärke sind unabhängig vom Inselgrößen-Level feinjustierbar.


### Blockabbau-Vertrag: explizit entscheiden und testen

- Verhindere gleichzeitige Vanilla-Drops und Custom-Loot. Dokumentiere, **ob** OneBlock-Block selbst als Item droppt, ob Werkzeugverschleiß/Enchantments wirken, wann XP ausgegeben wird und was bei `break`-Cancels passiert. Default: **eine** serverautorisierte Loot-Ausgabe pro erfolgreicher legitimer Abbau-Aktion; niemals automatisch Originalblock **und** vollwertige zufällige Ersatzbelohnung doppelt, sofern Lootdefinition das nicht ausdrücklich vorsieht.
- Der zentrale Anker darf nie verloren gehen: Kein Verschieben durch Piston, kein Überbauen, kein Explosion-Wipe, keine geerbte Block-Entity mit gefährlichem NBT, kein Zerlegen durch Auto-Mining-Mods ohne verifizierte Hook-Unterstützung.
- Ressourcenvergabe via **sichere Ausgabeprotokolle**: direkte normale Vanilla-Drops für alltägliche Mining-Ressourcen mit dokumentiertem Crash-Risiko; besondere/eventbezogene Rewards nur über persistente Anspruchs-/Claim-Entscheidung. Keine unbewiesene Exactly-once-Behauptung über Inventar + SQLite.
- RNG-Auswahl wird vor der Ausgabe validiert und gegen Limits geprüft. Für Tests injizierbarer Seed; produktive seltene Ergebnisse nicht durch Spielerpakete vorhersagbar. Jede seltene Reward-ID/Entscheidung für Debug/Audit nachvollziehbar, ohne geheime Seeds offen zu legen.
- **Notfall-Fallback** bei fehlerhaftem Lootpool: Nicht auf unerwartete Bedrock-/Command-Blöcke fallen. Letzte valide Config beibehalten; bei unrettbar inkonsistentem Pool OneBlock-Abbau pausieren und Admin alarmieren.

## 12. Progression und Langzeit-Gameplay

### Drei verzahnte Ziele statt drei unnötiger Währungen

1. **OneBlock-Progress:** Mining, Loot-Phasen und Block-Meilensteine.
2. **Island-Progress:** freigeschaltete Fläche, Herausforderungen, optionale Biome.
3. **Cobblemon-Progress:** Pokémon-Arten/Typen, Capture-/Trainer-Ziele und besondere Begegnungen.

Sie dürfen eine gemeinsame Insel-XP-/Point-Basis verwenden. **Nicht drei Pflichtwährungen einführen.** Fortschritt soll auch durch sinnvolle Challenges statt reiner Mining-Wiederholung erreichbar sein.

### Border-Level 1–8

- Level 1 `17 × 17`, Level 8 default `2049 × 2049`.
- Default-Radiuskurve: `r(L) = r1 * (r8 / r1)^((L - 1) / 7)`; sauber auf Integer runden, streng monoton und auf Level 1/8 exakt. Bei den Default-Werten entstehen die Radien `8, 16, 32, 64, 128, 256, 512, 1024`.
- Punkte-Schwellen vollständig als validierte Daten konfigurierbar, streng monoton und testbar. Konkrete Launch-Werte nach Gameplay-Test festlegen, nicht willkürlich als endgültig behaupten.
- Island-Progress gehört zur Insel; Mitglieder tragen bei. Normale legitime Abbauten zählen **ab Beitritt**, damit echte neue Mitspieler nicht 30 Minuten leer ausgehen. Anti-Alt-Farming über sinnvolle Rate-Limits, AFK-/Makro-Erkennung und **konfigurierbare Mindest-Aktivzeit (Default 30 Minuten) nur für besondere Bonusbelohnungen oder accountgebundene Milestones**.
- Level-Up = Border wächst, Phase wechselt, Sound/Titel/Status und Freischaltungen werden ausgelöst. Level-Up-Rewards sind dedupliziert und persistent, keine Wiederholung durch Restore/Restart.
- Default-Caps für Progressions-Raten, Event-Trigger und mögliche Automatisierung dokumentieren; konfigurativ anpassbar statt versteckt.

### Challenges und Belohnungen

- `challenges.json`: `id`, Version, Kategorie, Bedingungen, Fortschritt, Rewards, Repeatability, Cooldown und Scope (`ISLAND` / `PLAYER`).
- Beispiele: Erster Pokéball, bestimmter Pokémontyp gefangen, bestimmte Pflanzen angebaut, neues Biom gebaut, Trainer-Duell gewonnen.
- Wiederholbare und einmalige Ziele klar unterscheiden. Account-/Insel-Ledger verhindert Doppelbelohnungen nach Inselwechsel.
- Ingame `/ob guide`: nächste Freischaltung, anstehende Herausforderungen, fehlende Ressourcen, aktive Phase, mögliche Pokémon-Spawnvoraussetzungen. Erste Version über Chat/Kistenmenü; keine Pflicht-Client-GUI.


### Balancingvertrag und überprüfbare Spielerziele

Erstelle in Phase 0/3 `docs/GAME_DESIGN.md` (mit den Abschnitten „Balancing“ und „Playtests“), das **nicht** nur Ideen, sondern auswertbare Tabellen und Simulationen enthält:

- Pro Phase: thematischer Ressourcenpool, gewichtete Chancen, Seltenheitsgrenzen, neue Crafting-Möglichkeiten, bekannte Cobblemon-Pokémon-Gruppen, Aufstiegsziel, notwendige Materialien, geschätzte Spielzeit, mögliche Softlocks.
- Zielgruppen-Presets als **Datendateien**: `casual`, `standard`, `challenging`; **`standard` default**, keine zusätzlichen Wizard-Fragen. Ein Preset-Wechsel darf keine doppelte Milestone-Ausgabe erlauben.
- Fortschrittskurve über Solo und Gruppen (1/2/4 Spieler) simulieren; Missbrauch durch Idle-, Macro- oder Alt-Account-Farming nicht durch pauschale Bestrafung echter Mitspieler kompensieren.
- Starte mit einem messbaren **First-Session-Loop**: Spieler hat in 2 Minuten eine verständliche Aufgabe, in 10 Minuten einen ersten Erfolg und innerhalb einer Test-Session einen eindeutigen Ausblick auf die nächste Phase. Dies sind **UX-Testziele**, keine unprüfbaren Garantien.
- Keine Fortschritts-Softlocks: Wasser/Lava, Holz, Erde, Stein, Erze, landwirtschaftliche Grundlagen und Cobblemon-Fortschritt müssen im vorgesehenen Regelset erreichbar sein. Prüfe Loot-Ketten + Rezepte + notwendige Biome/Spawnflächen und dokumentiere gewollte Einschränkungen.
- Automatische Tests simulieren mindestens **100.000 gewichtete Ziehungen pro Default-Phase** (statistische Toleranzen definiert, keine flakigen Zufallschecks) sowie Testläufe bis Level 8 mit konfigurierbar beschleunigtem Fortschritt.

### Sinnvolles Endgame nach Level 8 — ohne sinnlosen Power-Creep

- Acht Level bleiben erhalten; danach **optionale wiederholbare Mastery-/Forschungsziele**, thematische Expeditionen/Events, Pokémon-Sammlungsziele und kooperative Challenges. Kein unbegrenztes Borderwachstum oder automatisch eskalierende Shiny-/IV-Buffs.
- Endgame-Rewards bevorzugen Kosmetik, Trophäen, neue wählbare Event-Varianten oder begrenzte Ressourcen; keine permanente, exponential stärkere Ressourcengenerierung.
- Ein optionaler Prestige-/Neustart-Modus ist **nicht v1**. Reset/Restore bleibt eine Sicherheits-/Neustartfunktion und darf nicht als unbegrenzte Farmmechanik für einmalige Meilensteine fungieren.
- Der Abschnitt „Playtests“ in `docs/GAME_DESIGN.md` erfasst reale Tests: Sessiondauer, Blockabbauten, Ressourcenknappheit, Pokémon-Funde, Missionsverständnis, Frustrationen. Balancing-Änderungen werden datenbasiert und versioniert vorgenommen.

## 26. Inhalte und Datapacks als versioniertes Produkt

### Default Content-Pack

- Liefere ein **eingebautes, getestetes Standardpaket** für alle acht Phasen, Loot-Definitionen, Fortschrittsschwellen, erste Challenges, Event-Basistypen, biombezogene Unlocks und zugehörige Übersetzungen. Keine „hier Konfiguration einfügen“-Stelle im Release.
- Jeder Content-Eintrag nutzt stabile IDs und `schemaVersion`; Spawn-/Reward-/Block-/Itemreferenzen müssen zur **aktiven gepinnten Cobblemon- und Minecraft-Version** passen.
- Content-Schema enthält gültige Werte, Fehlertexte, Beispiele und eine **automatische Lint-/Validation-Aufgabe**, ohne einen Server starten zu müssen.
- Ein Default-Paket sollte auch ohne Worldgen-Features alle wichtigen Vanilla-/Cobblemon-Rezepte versorgen. Fehlende Nether-/End-Baumaterialien und spezielle Evolutionsmöglichkeiten **per Ressourcenkatalog prüfen**, nicht mit einer unvollständigen Handliste abdecken.
- **Content-Pack-Priorität und Konflikte** definieren: Bundled Default, Admin-Overrides, externe Addon-Provider. Gleichnamige IDs und Zyklen werden bei Startup/Reload abgewiesen, nicht zufällig überschrieben.

### Migration laufender Spielerinhalte

- Änderungen an Phase/Challenge/Reward werden **nicht rückwirkend** auf bereits ausgegebene Rewards angewandt. Ein Spieler darf einen einmaligen Meilenstein durch Definition-Umbenennung nicht erneut erhalten; notwendige Alias-/Migrationstabelle einführen.
- Entfernte/umbenannte Pokémon-/Item-IDs in einer neuen Cobblemon-Version müssen **erkannt** und dem Admin mit Migrationsvorschlag gemeldet werden. Kein stilles Ersetzen durch einen anderen Pokémon-Typ.
- Rollback des Content-Packs ist nur sicher, wenn gespeicherte Event-/Challengezustände damit kompatibel sind; andernfalls vorher Sicherung und kontrollierter Konvertierungslauf.
