# Architecture

Solisium Autopilot is a read-only Throne and Liberty companion. It stores versioned static game data separately from user state, then ranks next actions with a deterministic engine. An optional LLM may explain those results. It must never invent statistics the engine did not produce.

This document describes the intended system. Phase 1 implements docs, schema, adapters, and a CLI import/query prototype only.

## Source-of-truth hierarchy

1. Decoded client tables imported through a `DataSource` adapter (today: TL-Helper warehouse).
2. Official combat logs the game writes after combat.
3. User-supplied character/build documents.
4. Optional community references (Questlog, TLDB) labeled as such, never as extracted client data.

OCR is not a source of truth. It may appear later as an emergency import fallback.

Do not treat [TL-Helper `extraction-report.md`](https://github.com/NoxyTree/tl-helper/blob/master/extraction-report.md) (2026-07-08) as current. That report found encrypted paks and used Questlog. [TL-Helper `STATUS.md`](https://github.com/NoxyTree/tl-helper/blob/master/STATUS.md) (2026-07-14) supersedes it: installed archives → decoded `TLJsonDataTable` rows → build-scoped SQLite warehouse.

Verified snapshot used for research (do not invent a newer warehouse in this repo):

- Steam build `24118850`
- Game version `1.431.22.7761`
- Decoder `0.2.0`
- 55 decoded tables / 159,448 records of 1,387 inventoried tables

A later local probe on this Windows machine found Steam build **`24829515`** at `D:\SteamLibrary\steamapps\common\Throne and Liberty` and a gitignored warehouse at `D:\TL_Data\warehouse\tl-24829515.sqlite` (46 decoded tables / 138,209 records of 1,415 inventoried tables). Installed build and imported snapshot must be compared explicitly; they are not assumed to match.

"T4" and "Nix 4.0.0" are human aliases. Primary keys are Steam build ID + game version.

## Layers

```text
GAME INSTALLATION (Windows)
        |
        v
STATIC DATA EXTRACTOR          (lives in TL-Helper, not this repo)
        |
        v
NORMALIZATION PIPELINE         (Solisium DataSource adapters)
        |
        v
LOCAL VERSIONED DATABASE       (SQLDelight / SQLite)
        |
        +--------------------+
        |                    |
        v                    v
CHARACTER STATE         GAME KNOWLEDGE
        |                    |
        +---------+----------+
                  |
                  v
          DECISION ENGINE      (not implemented in Phase 1)
                  |
                  v
          USER-FACING UI       (CLI now; Compose later)
```

## Product boundary vs TL-Helper

TL-Helper is a sibling data platform: pak collection, table decoder, warehouse, Armory, combat lab, local Ollama adviser.

Solisium Autopilot is the decision companion. Rules:

- Do not copy TL-Helper code.
- Do not adopt TL-Helper `records` / `raw_json` as the application schema.
- Do not reimplement pak decryption, FModel, or `decode-tljson-table.mjs`.
- Do implement `TLHelperDataSource`: read `tl-<build>.sqlite` and map into Solisium entities.
- Pak change detection here is Steam build ID + pak hashes only. The prompt is: "New game data detected. Import updated TL-Helper warehouse?"

## Modules

| Module | Targets | Responsibility |
| --- | --- | --- |
| `core` | common / jvm / android | Domain, SQLDelight, `DataSource` contracts, combat-log parser, mapping |
| `cli` | jvm | Windows command line: import, query, parse-log |
| `androidApp` | android | Empty shell until Phase 10. No game-install integration. |

Windows-only code (Steam detection, warehouse import from `D:\TL_Data`) lives in `jvmMain`. Shared parsers and schema live in `commonMain`.

## DataSource contract

```kotlin
interface DataSource {
    val id: String
    fun probe(): SourceCapability
    fun importInto(db: SolisiumDatabase, request: ImportRequest): ImportReceipt
}
```

Implementations, in order:

| Adapter | Where | Phase 1 status |
| --- | --- | --- |
| `TLHelperDataSource` | jvm | Maps warehouse `records` into `game_item`, typed gear, runes/synergies, skills/effects, recipes, stats |
| `CombatLogDataSource` | common | Parses official `CombatLogVersion,4` CSV; stores events |
| `ManualImportDataSource` | common | Typed character/build JSON |
| `InstalledGameDataSource` | jvm | Detect install path and Steam build. No AES. No FModel. |
| `PublicRepositoryDataSource` | common | Stub until a license-clear dataset exists. Questlog scrape is off by default. |

Every static import writes a `dataset_snapshot` row: source, extraction time, game build/version, schema version, source path, source hash, decoder version when known.

Multiple snapshots may exist. Exactly one may be `active`. Recommendations must record which snapshot they used.

## Database split

Static game rows (`game_*`) always foreign-key to `dataset_snapshot`. User rows (`user_*`) do not store patch-wide item stats. A Lucent balance never lives on a `game_item`. A trait definition never lives only on `user_traits`.

See [docs/SCHEMA.md](docs/SCHEMA.md).

## Character state

Throne and Liberty is server-authoritative. Local Unreal `Saved` files have not been verified to contain loadout, inventory, or currency. Appearance presets under `Documents\TL\Customize` are not a build source.

Reliable non-OCR inputs today:

- Manual entry / typed JSON
- User-supplied Questlog/TL-Helper build JSON (paste), default off for live tRPC fetch
- Combat logs for observed damage events, not for gear

See [DATA_SOURCES.md](DATA_SOURCES.md).

## Combat logs

Official path: `%LOCALAPPDATA%\TL\Saved\CombatLogs`.

Verified v4 row:

```text
Timestamp, LogType, SkillName, SkillId, Damage,
HitCritical, HitDouble, HitType, CasterName, TargetName
```

Reviewed evidence (TL-Helper, 2026-07-11, 531 dummy hits) covers `LogType=DamageDone` only. `SkillId` is an effect/variant id, not a unique localized skill id. The game writes the file after combat ends.

Parser rules:

- Require a version header.
- Preserve unknown `LogType` values; do not drop them.
- Do not infer heals, buffs, or uptime from DamageDone-only files.
- Do not compute modeled DPS. Observed `sum(damage)/duration` is a log statistic, not a formula.

## Decision engine (later)

The engine is deterministic. The LLM never calculates. Each candidate action carries cost, materials, expected benefit, confidence (`HIGH` / `MEDIUM` / `LOW`), prerequisites, opportunity cost, and assumptions.

If skill formulas are incomplete, refuse exact modeled DPS. Fall back to stat deltas, item-level/trait comparisons, material cost, and an explicit "insufficient formula coverage" flag.

## LLM boundary (later)

The model receives structured engine facts only. It may summarize and answer questions. It must not invent game statistics. Local Ollama is the intended optional backend, matching TL-Helper's local adviser pattern. No cloud inference is required.

## Frontend

Phase 1 surface is the JVM CLI. Compose Multiplatform desktop and Android UI wait until the data representation is trustworthy. Do not duplicate business logic in UI modules.

## What Phase 1 proves

A fixture warehouse can be imported into Solisium's schema with provenance, queried as Solisium entities, and a redacted combat log can be parsed without claiming unverified mechanics.
