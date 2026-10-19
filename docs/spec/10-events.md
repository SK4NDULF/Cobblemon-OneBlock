# Cobblemon- und OneBlock-Events

> **Spec v4.1** · Teil von `docs/spec/` (Übersicht: `00-INDEX.md`) · Quelle: Masterprompt v4 §15  
> **Lesen in Phase:** 6  
> Abschnitte mit **[v4.1]** sind Ergänzungen gegenüber v4. Alles andere ist der v4-Text unverändert (nur Dokumentnamen an den zusammengelegten Dokumentensatz angepasst).

## 15. Cobblemon- und OneBlock-Events

### Auslösung

- Nach konfigurierbaren zählenden Abbauten (Default **100**) wird ein Event `READY`.
- Start per Rechtsklick auf den OneBlock oder `/ob event start`. Auto-Start **standardmäßig aus**; falls Admin ihn explizit aktiviert, erst nach sichtbarem Countdown und nur bei aktivem, nicht AFK-Mitglied in der Nähe.
- Pro Insel **maximal ein laufendes Event**; begrenzte READY-/Trigger-Queue, Cooldowns und persistenter Zustandsautomat.
- Crash während Event → sicherer Recovery-Zustand; Restart macht Event wieder `READY`, sofern zuvor nicht dauerhaft belohnt. Alte Event-Entities nach Identifikationsprüfung entfernen, nicht fremde Pokémon löschen.

### Event-Typen (schrittweise statt alle gleichzeitig)

1. **Resource Burst:** kurzzeitig zusätzliche thematische Belohnungen.
2. **Pokémon-Schwarm:** zeitlich begrenzte, lokal begrenzte Spawns einer zulässigen Art.
3. **Alpha-Begegnung:** starkes/großes wildes Pokémon nach verifizierten APIs.
4. **Trainer-Duell:** Cobblemon-NPC/Trainer, **nur bei positivem Phase-0-Spike**.
5. **Legendary Milestone:** seltene, einmalige bzw. accountgebunden begrenzte Meilenstein-Chance (z. B. Level 4/6/8).
6. **Vanilla-Mob-Welle:** optional, Default aus.

- Stärke skaliert mit der Phase und dem Insel-Level, aber nicht unkontrolliert mit der Zahl neuer Alt-Accounts.
- Legendäre/meilensteinbezogene Belohnungen haben eindeutige IDs und Account- sowie Island-Progress-Prüfung. **Reset und Restore dürfen keine neue Einmalchance erschaffen.**
- Event-Cleanup berücksichtigt aktive Kämpfe, Captures, Mounts, Spieler-Disconnects, Chunk-Unload, Serverrestart und NPC-Despawn.
- Belohnungen sind vor Ausgabe gegen Ledger/Operation-Status geprüft. Bei unklarer Teilabwicklung fail-closed plus `doctor`-Reconciliation, kein unbegrenztes Retry-Duping.
- Event-System über kleine API (`registerEventType`) und data-driven Definitionen erweiterbar; eingehende Implementierungen auf Sicherheit/Budget validieren.


### Eventdesign, Fairness und Belohnungsgrenzen

- Jede Eventdefinition enthält: `id`, `version`, `minLevel`, `eligibility`, `cooldown`, `duration`, `spawnBudget`, `failurePolicy`, `rewardPolicy`, `cleanupPolicy`, `restartPolicy`, `visibility` und `contentPrerequisites`.
- Events sind **spieleraktiviert und erklärbar**: `READY` überlebt Logout und Neustart; Auto-Start im Default **aus**, wenn das Verhalten unbemerkt Belohnungen verbrauchen könnte; bei explizit aktivierter Auto-Start-Option nur nach informativem Countdown.
- Kein Event erzeugt weltweite Pokémon-Schwärme, CPU-Spikes, Chunk-Tickets ohne Limit oder Items in fremden Inselzellen.
- **Reward-Modi** werden sauber unterschieden: gewöhnlicher physischer Drop (kein End-to-End Exactly-once), persistenter einmaliger Reward-Anspruch (eventuell `CLAIM_PENDING`), Fangchance eines wilden Pokémon (kein garantierter Fang), rein kosmetische Statusauszeichnung.
- Ein seltener Encounter wird bei Restart nur erneut `READY`, wenn **nicht bereits** ein entsprechender Fang, Claim oder irreversibler Milestone registriert wurde; bei Unklarheit `REVIEW_REQUIRED`, keine zweite Gratis-Chance.
- Bereits gefangene Pokémon gehen vollständig an Cobblemon/Spielerzustand über und sind danach **niemals** Eigentum eines noch aufzuräumenden Event-Entities-Sets.
