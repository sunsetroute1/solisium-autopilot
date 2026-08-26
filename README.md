# Solisium Autopilot

Read-only Throne and Liberty companion for Windows and Android. It maintains a local, versioned database of game data and character state, then uses a deterministic decision engine (with an optional LLM explanation layer) to answer: **what should I do next?**

This is not a bot, overlay injector, or game client. It never writes to the game install, touches process memory, or automates gameplay.

## Current status

Phase 1–5 plus a live `24829515` catalog import: architecture, schema (version 8), CLI import/query, a Compose Desktop app that both imports and reads, typed gear/trait/effect/material/stat mapping, per-item base stat values, shared enchant and item-level curves, and a manual character sheet (combat power, gear score, allocated stat points, weapon-pair class, every equipment slot, bag inventory, weapon mastery levels, and the skills-screen layers).

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

Compose Desktop on JVM 17, reading the same `~/.solisium/solisium.sqlite` as the CLI (override with `SOLISIUM_DB`). Six screens: Overview (provenance, coverage, stale-dataset banner, patch watch), Build (goal picker, T&L class types, Questlog-style modeled CP/GS from warehouse item weights plus typed window targets, extracted ranks, skills-screen influence coverage, optional Questlog/TLDB overlay), Catalog (search with a stats and curve detail pane), Character (resolved loadout plus mastery and sidebar layers), Combat (observed per-skill damage), Data (import and snapshot activation).

The app is read-only with respect to the *game*; it does import into its own database. Data can import a TL-Helper warehouse, a combat log, or a character JSON, so the CLI is optional.

## Packaging

```powershell
.\gradlew.bat :desktopApp:packageRelease
```

Writes two zips to `desktopApp/build/distributions/`:

| Artifact | Contents |
| --- | --- |
| `...-windows-x64-installer.zip` | An MSI. Installs per-user, uninstalls through Settings. |
| `...-windows-x64-portable.zip` | The app plus `install.cmd`, which copies it to `%LOCALAPPDATA%\Programs` and makes shortcuts. Uninstall with `.\Install-Solisium.ps1 -Uninstall`. |

Both bundle a Java runtime, so a target machine needs no JDK, and both install
per-user, so neither needs administrator rights. The Gradle plugin fetches the WiX
toolset it needs for the MSI, so there is nothing to install first.

Packaging depends on a secret scan of the sources and of the built application image,
and fails if a key or credential file is found in either. Test fixtures that need
key-shaped constants opt out with a `secret-scan-allow-fixture` marker, so every
exemption is visible in review.

## Archive keys

No key ships in a build, and none is needed to use the app on an already-imported
dataset. If one is already on the machine, the first run offers to store it, naming the
fingerprint and the file it came from; declining is remembered. **Data → Archive key**
does the same on demand, and is the way through when there are several candidates.

A stored key stays on the machine, is never transmitted, is never displayed (only its
fingerprint), and can be deleted at any time.

```text
solisium keys scan                                   look for a key on this machine
solisium keys add --name archive --from <folder>      store the one found there
solisium keys add --name archive --value <hex>        store one you paste in
solisium keys list                                    fingerprints only
solisium keys remove --name archive
```

Keys live in `%LOCALAPPDATA%\Solisium\secrets.properties`, outside this repository and
outside any installed copy. See [THREAT_MODEL.md](THREAT_MODEL.md) for what the finder
will and will not touch.

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
solisium query advise [--goal ranged|melee|magic|tank|support] [--class <Gladiator>] [--character <id>] [--meta] [--slug <questlog-slug>] [--desired-cp <n>] [--desired-gs <n>] [--axes hit,evasion,endurance] [--stat <key>]
solisium query sessions
solisium query session --id <session-id>
solisium logs
solisium detect-install
solisium patch-check [--import]
solisium activate --snapshot <id-or-alias>
solisium parse-log --path <CombatLog.txt>
```

`solisium probe` reports whether a TL-Helper warehouse, Steam install, or combat-log folder exists. Missing sources stay missing; they are not invented.

## What this repo will not contain

Decoded game tables, icons, localization dumps, TL-Helper warehouse files, AES keys, or combat logs from live play. Tests use tiny synthetic fixtures only.
