# Schutz, Berechtigungen und Threat Model

> **Spec v4.1** · Teil von `docs/spec/` (Übersicht: `00-INDEX.md`) · Quelle: Masterprompt v4 §9, §28  
> **Lesen in Phase:** 2, 4 (Fangrechte), 5, 8  
> Abschnitte mit **[v4.1]** sind Ergänzungen gegenüber v4. Alles andere ist der v4-Text unverändert (nur Dokumentnamen an den zusammengelegten Dokumentensatz angepasst).

### [v4.1] Priorisierung: Was die Void-Lücke zwischen den Inseln bereits erledigt

Zwischen zwei Inseln liegen mindestens 1000 Blöcke Void (`stride − maxSize`). Kolben, Trichter, Flüssigkeiten, Redstone, Feuer und Explosionen können eine **Nachbarinsel** physisch nicht erreichen. Die Schutzmatrix unten bleibt vollständig gültig, wird aber in genau **drei Kontexten** getestet statt pauschal gegen jede denkbare Nachbarschaft:

1. **Rechte auf der eigenen Insel:** Was dürfen Owner, Member und Public (Besucher) auf einer Insel? Hier läuft jede Kategorie der Matrix.
2. **Border-Eindämmung:** Keine Wirkung über den **aktuellen** Inselradius hinaus (Progressions-Schutz, kein Bauen ins noch gesperrte Gebiet). Jede Kategorie mit einer räumlichen Wirkung (Flüssigkeiten, Explosionen, Kolben, Falling Blocks, Feuer, Trichter) wird an der eigenen Border getestet.
3. **Eintrittskontrolle:** Der einzige realistische Weg zu einer fremden Insel ist **Reisen**: zu Fuß über gebaute Brücken, Elytra, Fahrzeuge, Mounts, Enderperlen, Teleports. Hier liegen die Tests gegen fremde Inseln.

Ein Test „Nachbarinsel“ ist nur dort Pflicht, wo eine Reichweite > 1000 Blöcke technisch möglich ist oder ein anderer Mod (Tier B/C) sie ermöglicht. Das spart Testaufwand, ohne eine Schutzanforderung zu streichen.

## 9. Sicherheitsmodell, Privatsphäre und Berechtigungen

### Rollen und Flags

- **Owner, Member, Public** (Public ist eine Zugriffsklasse, keine gespeicherte Mitgliedschaft).
- Reihenfolge: Admin-Bypass > Owner > Member > Public, **aber** Admin-Bypass nur mit expliziter Permission und Audit für destruktive Aktionen.
- Public-Flags für Türen/Knöpfe, Container, Blockinteraktionen, Entityinteraktionen, Pokémon-Fangen, Besuch, PvP. Standard: privat, Container/Pokémon/PvP aus, Türen optional an.
- Permission-Check zentral als Policy-Funktion, nicht verteilt in Chat-Commands.

### Schutzmatrix (je Kategorie automatisiert testen)

- Blockabbau, Placement und Multi-Block-Aktionen, inklusive OneBlock-Sonderfall.
- Container/Inventare, Items-Frames, Armor-Stands, Schilder, Redstone, Hopper, Dispenser, Dropper, sonstige Automation.
- Eimer, Wasser/Lava, Flüssigkeitsfluss, Feuer, TNT/Explosionen, Pistons (inkl. bewegter Nachbarblöcke), Falling Blocks.
- Projektile, Tridents, Fishing, Enderperlen, Chorusfrüchte, Teleport-Befehle, Fahrzeuge, Elytra und Mod-Mounts.
- Vanilla-Mob-Griefing, Endermen, Explosionen, spawnende/teleportierende Entities, Pokémon- und NPC-Interaktionen.
- Bewusst auch **Cross-Border-Aktionen** prüfen: Verursacher und Wirkung dürfen in unterschiedlichen Zellen liegen. Nicht nur Position des auslösenden Blocks prüfen.
- Void-Zellen ohne aktive Insel blockieren Änderungen und Interaktionen; gewöhnliches Bewegen zur sicheren Rettung bleibt möglich.

### Bewegung, Pokémon-Mounts und Void-Rettung

- Private Insel: nicht berechtigten Spielern Eintritt verwehren und sicher zurücksetzen; bei Flug/Mount/Fahrzeug gemeinsam einen validen Rücksetzpunkt wählen, ohne Mount-Duplikation oder Desync.
- Bewegungsprüfungen event-/grenzübertrittsbasiert optimieren. **Sicherheit geht vor Tick-Drosselung:** Teleports, Hochgeschwindigkeitsbewegung, Chunkwechsel und Plugin-/Mod-Bypasses abfangen, soweit serverseitig möglich. Kein blinder 20-Tick-Prüfer, durch den Spieler kurzzeitig griefen können.
- Void-Rettung default an: Island-Home oder Hub, mit Cooldown gegen Teleport-Loops. Rescue muss gültige Dimension, sicheren Block, Spielerstatus und ggf. Battle-/Mount-Zustand berücksichtigen.
- Kein Schutzsystem behauptet „vollständig“, solange dokumentierte Ereignisklassen von Fabric/Cobblemon nicht abgedeckt und getestet wurden.


### Exploit-Schutz: Trust Boundaries und Action Context

- Für jede schützbare Aktion genau ein **`ActionContext`**: Actor (oder Ursache einer indirekten Aktion), Welt, Quellposition, Wirkungsposition(en), Island/Hub/Void, Besitzer, Berechtigung, Event-/Mod-Kontext, Timestamp/Revision. Autorisierung geschieht **vor** nicht rücknehmbaren Vanilla-Änderungen.
- **Indirekte Aktionen** (Trichter, Hopper-Minecarts, Item-Pickup, Item-Drop, XP-Orbs, Block-Entity-Inventar, Wasserströme, TNT-Ketten, Redstone-Observer, Mob-Targeting, Spawning, Teleports, Pistons, Armor-Stand/Item-Frames, Leinen) brauchen eigene Tests. Ein Item-Transfer über die Border ist ebenso eine Grenze wie Blockplacement.
- Aktionen ohne identifizierbaren Verursacher erhalten eine **konservative Umwelt-Policy**, nicht pauschal Owner-Rechte. Kein pauschaler `null actor => allow`-Shortcut.
- Eigentümer darf nicht automatisch globale Admin-Flags ändern. Admin-Bypass pro Befehl explizit, mit Audit, und darf die Isolation zweier fremder Inseln nicht still deaktivieren.
- Race zwischen Grenzwachstum/-schrumpfung, Restore, Teleport und alter GUI-Aktion durch Revisionsprüfung verhindern. Ein Access-Check muss für die **Wirkung** gelten, nicht nur für den Ursprung.
- **Anti-AFK-/Alt-Account-Systeme sind heuristisch**, keine Identitätsgarantie. Keine intrusive Fingerprint-/IP-Verknüpfung, keine falschen Betrugsbeschuldigungen, stattdessen serverseitige Rate-Limits und reward-spezifische Caps.

## 28. Sicherheits-Threat-Model mit verpflichtenden Angriffsszenarien

**Threat Actors:** öffentliche Besucher, vertrauenswürdige Member mit abweichenden Absichten, bösartige/fehlerhafte andere Mods, OP mit versehentlichem Fehlklick, nicht kooperierende Client-Pakete, Servercrash, fehlerhafter Storage, inkompatibles Upgrade.

| Risiko | Angriffs-/Fehlerszenario | Unverhandelbares Gegenmittel |
|---|---|---|
| Dupe | Gleichzeitiger OneBlock-Break | Single-authority Abbau + atomic-ish drop contract + Test |
| Dupe | Reset/Restore mehrfach/parallel | Persistierte Request-ID, Revision, durable Saga |
| Dupe | Event-Replay/Disconnect | Claim-/Reward-Ledger + Battle/Capture-Abgleich |
| Grief | Wasser/Lava/Explosion/Piston über Border | Source + Target + Effect-Zellen prüfen |
| Stehlen | Hopper, Item-Transfer, Projectile, Pickup | Inventar-/Entity-/Transferpolicy statt nur Blocks |
| Teleport | Enderperle, Elytra, Mount, externe Teleport-Mods | Eintrittsprüfung und sichere Rückführung |
| Permission | stale GUI/Invite/Cache | Revision auf ausführendem Thread erneut prüfen |
| Datenverlust | DB-Commit vor World-Save | Durable Operation State + Reconciliation/Wartungsweg |
| Datenverlust | `.mca`-Wipe bei geladener Welt | Offline/Quiesced Wipe + Ownership-Proof |
| Performance | Spawn-/Event-/Edit-Spam | Globale + per-Island Budgets, bounded queues |
| Supply chain | Bösartige Libraries/Abhängigkeiten | Lockfiles, Checksums, Lizenz-/Advisory-Review |
| Privacy | Export von Team-/Spielerinformationen | Berechtigungen, Redaction, sichere Dateipfade |

Jeder Tabellenzeile wird in `docs/SECURITY.md` (Abschnitt „Threat Model“) ein Test oder dokumentierter, vom Owner akzeptierter Restrisiko-Eintrag zugeordnet. Unvermeidbare Angriffslücken nicht mit „fully protected“ bewerben.
