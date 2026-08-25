# Solisium Autopilot

Read-only Throne and Liberty companion for Windows and Android. It maintains a local, versioned database of game data and character state, then uses a deterministic decision engine (with an optional LLM explanation layer) to answer: **what should I do next?**

This is not a bot, overlay injector, or game client. It never writes to the game install, touches process memory, or automates gameplay.

## Current status

Phase 1–5 plus a live `24829515` catalog import: architecture, schema (version 3), CLI import/query, a Compose Desktop app that both imports and reads, typed gear/trait/effect/material/stat mapping, per-item base stat values, shared enchant and item-level curves, and manual character JSON.

Stat values are stored as raw client integers, curve values as the client's cumulative total at a level, and neither is combined with the other because that stacking rule is unverified. Formula rows are never used to produce damage numbers.

Read these first:

1. [ARCHITECTURE.md](ARCHITECTURE.md)
2. [DATA_SOURCES.md](DATA_SOURCES.md)
3. [THREAT_MODEL.md](THREAT_MODEL.md)
4. [ROADMAP.md](ROADMAP.md)
5. [docs/SCHEMA.md](docs/SCHEMA.md)

## Build

```powershell
.\gradlew.bat :core:jvmTest :cli:run --args="help"
```

## Desktop app

```powershell
.\gradlew.bat :desktopApp:run
```

Compose Desktop on JVM 17, reading the same `~/.solisium/solisium.sqlite` as the CLI (override with `SOLISIUM_DB`). Five screens: Overview (provenance and coverage), Catalog (search with a stats and curve detail pane), Character (resolved loadout), Combat (observed per-skill damage), Data (import and snapshot activation).

The app is read-only with respect to the *game*; it does import into its own database. Data can import a TL-Helper warehouse, a combat log, or a character JSON, so the CLI is optional.

Package a Windows installer with `.\gradlew.bat :desktopApp:packageMsi`.

## CLI

```text
solisium probe
solisium import --source tl-helper --path <warehouse.sqlite>
solisium import --source manual --path examples/character.json
solisium query snapshots
solisium query counts
solisium query items --name Longbow
solisium query weapons --name Sword
solisium query traits --name Critical
solisium query materials --name Obsidian
solisium query formulas --name Struggle
solisium query item-stats --row bow_c_t1_nomal_001
solisium query item-curves --row bow_c_t1_nomal_001
solisium query characters
solisium query character --id <id>
solisium query lookup --table TLItemLooks_Equip --row <row-id>
solisium query sessions
solisium query session --id <session-id>
solisium logs
solisium activate --snapshot <id-or-alias>
solisium parse-log --path <CombatLog.txt>
```

`solisium probe` reports whether a TL-Helper warehouse, Steam install, or combat-log folder exists. Missing sources stay missing; they are not invented.

## What this repo will not contain

Decoded game tables, icons, localization dumps, TL-Helper warehouse files, AES keys, or combat logs from live play. Tests use tiny synthetic fixtures only.
