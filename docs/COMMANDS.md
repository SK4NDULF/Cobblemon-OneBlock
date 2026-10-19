# Befehle

> Stand: Phase 1 (2026-10-19). Rechte werden im Befehl selbst geprüft, nicht nur in der Befehlsliste.

| Befehl | Recht | Wirkung |
|---|---|---|
| `/ob status` | alle | Zeigt Mod-Version, Zustand mit Erklärung und, falls vorhanden, Gründe. Spieler sehen bei Gründen nur die Diagnose-ID. |
| `/ob status` als Admin (Op-Level 2) | Op 2 | Zusätzlich: DB-Pfad, Schema-Version, Journal-Modus, offene Vorgänge, DB-Queue, Config-Gültigkeit, die letzten 5 Probleme |
| `/ob reload` | Op 2 | Liest und prüft `config/oneblock/config.json5` und übernimmt bei Erfolg die live-Werte. Bei einem Fehler wird nichts übernommen; alle Fehler werden mit Zeile und Spalte gelistet. |

Texte gibt es auf Deutsch und Englisch, je nach Sprache des Clients. Konsole und unbekannte Sprachen bekommen Englisch.

## Zustände

| Zustand | Bedeutung | Gameplay |
|---|---|---|
| `STARTING` | Mod prüft noch die Daten | aus |
| `SETUP_PENDING` | Daten ok, Einrichtung fehlt oder Config ungültig | aus |
| `READY` | alles bereit | an (ab Phase 2) |
| `READ_ONLY` | Datenbankproblem zur Laufzeit; nur Lesen. Definiert, wird aber erst ab Phase 2 ausgelöst (Phase 1 schreibt zur Laufzeit nichts). | aus |
| `RECOVERY_MODE` | Daten nicht vertrauenswürdig (siehe `docs/RECOVERY.md`) | aus, bis zum Neustart nach der Reparatur |
| `STOPPING` | Server fährt herunter | aus |

In Phase 1 gibt es noch keinen Setup-Wizard. Ein gesunder Server steht deshalb immer auf `SETUP_PENDING`.
