# Solisium Autopilot

Read-only Throne and Liberty companion for Windows and Android. It maintains a local, versioned database of game data and character state, then uses a deterministic decision engine (with an optional LLM explanation layer) to answer: **what should I do next?**

This is not a bot, overlay injector, or game client. It never writes to the game install, touches process memory, or automates gameplay.

## Install from GitHub (Windows)

1. Download the whole zip from [the v0.1.16 release](https://github.com/sunsetroute1/solisium-autopilot/releases/tag/v0.1.16), **or** clone this repo and run [`releases/assemble.cmd`](releases/assemble.cmd) to join the git-sized `.partNN` files.
2. Install (portable: run `install.cmd`; MSI: run the `.msi`). No Java and no administrator rights. The installer ships the full TL-Helper checkout (no keys) and places it under `%LOCALAPPDATA%\Programs\TL-Helper`.
3. Open **Solisium Autopilot**. The starter catalog, demo character, and sample combat log load on first launch so every screen works.

You do **not** need an archive key to browse that starter data. **Data → Find my key** stores a key only if one is already on this PC (typically `source-manifest.json` or `aes.txt` next to TL-Helper / `TL_Data`). The app never ships a key and never invents one.

For live patch data you also need:

- [TL-Helper](https://github.com/sunsetroute1/tl-helper) — the full checkout is in `vendor/tl-helper`, bundled in the installer, and installed on first launch / `install.cmd` / **Get TL-Helper**. Keys are never shipped; the app searches this PC and asks before storing one.
- [Node.js](https://nodejs.org/) and the [.NET SDK](https://dotnet.microsoft.com/download)
- Throne and Liberty installed, plus a key you already have

Then use **Get TL-Helper** / **Run TL-Helper** on Home or Data. Solisium will import `tl-<steam-build>.sqlite` when extract finishes.

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
git clone --recurse-submodules https://github.com/sunsetroute1/solisium-autopilot.git
.\gradlew.bat :core:jvmTest :cli:run --args="help"
```

If the clone already exists without the submodule:

```powershell
git submodule update --init vendor/tl-helper
```

## Desktop app

```powershell
.\gradlew.bat :desktopApp:run
```

Compose Desktop on JVM 17, reading the same `~/.solisium/solisium.sqlite` as the CLI (override with `SOLISIUM_DB`). Screens: Overview (provenance, coverage, stale-dataset banner, patch watch), Build (I currently have / I would like to have paper dolls with warehouse icons when present), Events (server-selectable boss/event timetable), Catalog (search with a stats and curve detail pane), Character (resolved loadout plus mastery and sidebar layers), Combat (portfolio analytics, crit/heavy breakdown, session compare, build cross-check), Data (import and snapshot activation).

The app is read-only with respect to the *game*; it does import into its own database. Data can import a TL-Helper warehouse, a combat log, or a character JSON, so the CLI is optional.

## Packaging

```powershell
.\gradlew.bat :desktopApp:packageRelease
```

Writes two zips to [`releases/`](releases/):

| Artifact | Contents |
| --- | --- |
| `...-windows-x64-installer.zip` (+ `.partNN`) | An MSI. Installs per-user, uninstalls through Settings. |
| `...-windows-x64-portable.zip` (+ `.partNN`) | The app plus `install.cmd`, which copies it to `%LOCALAPPDATA%\Programs` and makes shortcuts. Uninstall with `.\Install-Solisium.ps1 -Uninstall`. |

The assembled zips are larger than GitHub allows in git, so `packageRelease` also writes 45 MB `.partNN` pieces. Run `releases\assemble.cmd` to put a zip back together.

Current build: **0.1.16** — download from the [v0.1.16 release](https://github.com/sunsetroute1/solisium-autopilot/releases/tag/v0.1.16) without building locally.

Both bundle a Java runtime, so a target machine needs no JDK, and both install
per-user, so neither needs administrator rights. A starter catalog, demo
character, and sample combat log are included; first launch seeds
`~/.solisium/solisium.sqlite` automatically. Import a TL-Helper warehouse from
Data when you want your live game build instead. The Gradle plugin fetches the
WiX toolset it needs for the MSI, so there is nothing to install first.

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
