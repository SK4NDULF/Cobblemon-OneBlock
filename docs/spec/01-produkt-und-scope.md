# Produkt, Designprinzipien und Scope

> **Spec v4.1** · Teil von `docs/spec/` (Übersicht: `00-INDEX.md`) · Quelle: Masterprompt v4 §2, §25, §36  
> **Lesen in Phase:** 0A, bei jeder Scope-Frage  
> Abschnitte mit **[v4.1]** sind Ergänzungen gegenüber v4. Alles andere ist der v4-Text unverändert (nur Dokumentnamen an den zusammengelegten Dokumentensatz angepasst).

## 2. Produktvision und Grenzen

### Muss-Ziele

1. **Drag-and-drop für Admins:** Fabric Loader + Fabric API + Cobblemon + dieses Mod-JAR (einschließlich der von Cobblemon selbst benötigten Dependencies) ergeben ohne externe Datenbank einen funktionierenden OneBlock-Spielmodus. Alle zusätzlich benötigten, rechtlich und technisch zulässig bündelbaren Bibliotheken werden korrekt eingebunden.
2. **Serverseitiger Mod:** Clients benötigen **keinen zusätzlichen OneBlock-Clientmod**; ein für den Server geeigneter Cobblemon-Fabric-Client genügt. Verwende keine eigenen Registry-Inhalte, Netzwerkkanäle oder clientseitigen Assets, die einen neuen Clientmod voraussetzen. Ein Server muss normalen Cobblemon-Spielern den Join erlauben.
3. **Eigene Void-Dimension:** Die normale Overworld, Nether und das End dürfen nicht umgeschrieben oder unbeabsichtigt durch die OneBlock-Systeme beeinflusst werden.
4. **Insel-Gameplay:** Ein einzelner regenerierender Block, acht Inselgrößen-Level, acht Loot-Themen, Challenges, Biome, Pokémon-Spawns, Co-op, Events und eine bedienbare Admin-Konfiguration.
5. **Keine vermeidbaren Datenverluste/Dupes:** Kritische Metadatenaktionen sind dauerhaft bestätigt, überprüfbar, crash-konsistent und wiederanlaufbar; cross-system Grenzen explizit dokumentieren. Bei Unsicherheit **sicher abbrechen statt stillschweigend fortfahren**.
6. **Erweiterbarkeit:** Kleine, dokumentierte öffentliche API und datengetriebenes Content-System; andere Mods dürfen Events/Belohnungen ergänzen, ohne Corecode zu patchen.
7. **Angenehmes Spielerlebnis:** Verständlicher Fortschritt, verfügbare Pflichtressourcen, sinnvolle Cobblemon-Interaktion, barrierearme Bedienung über Commands/Chat/Vanilla-Inventarmenü.

### Grundsätze

- **Kein P2W-System.** Keine eingebauten Shop- oder Monetarisierungspflichten.
- Kein Abhängigmachen von LuckPerms, WorldEdit, Discord, Placeholder API, einem externen Datenbankserver oder Client-GUI-Mods.
- Optionale Integrationen dürfen Kernfunktionen nicht destabilisieren.
- Keine versprochenen Cobblemon-NPC-, Spawn-, Kampf-, Mount- oder GUI-Funktionen, deren technische Machbarkeit für **1.8.2** nicht geprüft ist.
- Bewusst definierte Fallbacks: Wenn ein virtuelles Hologramm nicht zuverlässig serverseitig möglich ist, benutze in v1 Actionbar und Vanilla-Text, bis eine verifizierte Lösung vorliegt. **Kein** stiller Ersatz, der den Gameplay- oder Sicherheitsvertrag verändert.


### Produktvertrag: Was „fertig“ tatsächlich bedeutet

**Kein Prompt kann Fehlerfreiheit garantieren.** Unser Anspruch ist ein **nachweisbar stabiler, wartbarer und veröffentlichbarer Release** mit transparenten Grenzen. Behaupte gegenüber dem Owner niemals, der Mod sei „perfekt“, wenn die nachfolgenden Nachweise fehlen. Jedes Feature bekommt einen Zustand: `PROPOSED`, `FEASIBILITY_VERIFIED`, `IMPLEMENTED`, `AUTOMATED_TESTED`, `DEDICATED_SERVER_VERIFIED`, `OWNER_ACCEPTED`, `RELEASED` oder `BLOCKED`. `IMPLEMENTED` ist **nicht** `RELEASED`.

**Das Produkt darf Spieler nicht überraschend bestrafen:** kein ungefragter Reset, keine automatische Löschung aktiver Inseln, keine stillen Item-/Pokémon-Entfernungen, keine kostenpflichtige Abhängigkeit, keine unerklärten harten Content-Sperren. Vor unumkehrbaren Operationen zeigen Vorschau und Bestätigung den exakten Effekt.

**V1-Erfolg aus Spielersicht:** Auf einem sauberen Server binnen weniger Minuten eine Insel erstellen; 30 Minuten ohne Nachschlagen sinnvoll spielen; über alle acht Phasen voranschreiten; wichtige Pokémon und Ressourcen erreichen; gemeinsam bauen; Events verstehen; nach Restart/Crash in einem konsistenten Zustand zurückkehren. Diese Kriterien sind mit Testspielern zu überprüfen, nicht nur im Code zu behaupten.

## 25. Einheitliche Designprinzipien: Spaß, Klarheit, Fairness

**Prioritätsregel:** Sicherheits- und Korrektheitsinvarianten sind harte Gates. Innerhalb des sicheren Lösungsraums optimiere **Spielspaß, Verständlichkeit und Wartbarkeit**; vermeide „maximal technisch komplex“ als Selbstzweck. Eine einfachere verifizierbare Implementierung schlägt eine elegante, aber ungetestete Architektur.

### Gameplay-Pfeiler

1. **Always something to do:** Mindestens ein klarer nächster Schritt für Anfänger, Bauende, Pokémon-Sammler und fortgeschrittene Spieler.
2. **Progress without punishment:** Fortschritt über legitime Aktivitäten; keine verpflichtende Offline-Grind-Schleife und keine zwangsweisen Prestige-Resets.
3. **Cobblemon matters:** Biome und Pokémon bestimmen real den Spielablauf; kein bloßes Pokémon-Dekor.
4. **Creative freedom:** Builder können Inseln gestalten, ohne dass das System ihnen durch exotische globale Weltregeln versehentlich Builds zerstört.
5. **Multiplayer clarity:** Gemeinsame Ziele, eindeutig erklärte Eigentums-/Resetfolgen und keine versteckten Rechteänderungen.
6. **Server-admin freedom:** Alles Wichtige konfigurierbar, aber robuste Defaults und keine Pflicht, Dutzende JSON-Dateien zu verstehen.

### Zeit- und Schwierigkeitsmodell

- Standard-Preset mit geplanter „Time to first Pokéball“, „Time to level 2“, „Time to first viable Pokémon-spawn“, „Time to first event“, „Time to level 8“. **Messwerte im Playtest erfassen, erst danach konkrete Targets endgültig einfrieren.**
- Jede Phase hat **neue Handlungsmöglichkeiten**, nicht bloß mehr HP/höhere Dropchancen: neue Bauteile/Materialien, Spawns, Herausforderungen, Progressionsziele, kosmetische Varianten.
- Wähle wenige gut erklärte Mechaniken statt unzähliger permanenter Buffs und versteckter Multiplikatoren. Zeige im UI bei Bedarf „Warum kann dieses Pokémon hier nicht spawnen?“ anhand verifizierter Spawn-Bedingungen.
- Befristete Multiplikatoren müssen angezeigt werden: tatsächlicher Effekt, Scope, Ende (Serverzeit), Stack-Limit; keine geheimen dauerhaften Vorteile für Erstnutzer.

## 36. Scope-Management: Was NICHT unbemerkt in v1 rutschen darf

**V1 Pflicht:** 1.21.1/Fabric/Cobblemon 1.8.2, sichere eigene Void-Dimension, Hub/Wizard, echte OneBlock-Mechanik und acht Phasen, Grid, Schutz, SQLite/Recovery, mindestens grundlegende Challenges, biomabhängiges Cobblemon-Spawning, Fangrechte, Co-op, Reset/Archiv/Restore, Basisevents Resource Burst/Swarm, Basis-Biomeditor, Admin-Diagnose, öffentliche schmale API, Example-Addon, Dokumentation/Tests.

**V1 nach positivem Spike + Owner Go:** Paket-Hologramm, Cobblemon-Trainer-/Alpha-spezifische Mechaniken, legendäre Spezialfälle, extern unterstützte Mount-Grenzlogik über alle Drittmods, erweiterte Auto-Backups mit Online-Snapshot.

**V1.x/2:** MariaDB/MySQL, vollständiges Prestige, Ranked-/PvP-Zonen, Inselbewertungen, Warps, mehrere Member-Rollen, umfangreiche kosmetische Menüs, automatische Cross-World-Migration, komplexe NPC-Kampagnen und sonstige Features ohne reale Nutzeranforderung.

**Eskalationsregel:** Wenn ein v1-Pflichtfeature sich in Phase 0 als unlösbar erweist, **nicht** still streichen. Zeige 2–3 verifizierbare Alternativen, Einschränkungen, Aufwand und Risiken; lasse den Owner eine Scope-Änderung abnehmen. **Kein Feature gilt als fertig, nur weil es über einen Fallback nominell sichtbar ist.**
