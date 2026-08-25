# Threat model

Solisium Autopilot is a **read-only** companion. Easy Anti-Cheat and the game client are out of scope as targets. Any design that would look like a cheat is rejected, even if framed as "my own machine" or "offline."

## Assets we protect

- The user's game account (ban risk).
- The user's character and combat-log privacy.
- Integrity of Solisium's own database (no silent mixing of snapshots).
- Third-party copyright (NCSoft/Amazon data, Questlog, TL-Helper).

## Trust boundaries

```text
[Game process + EAC]     forbidden to touch
[Game install files]     read metadata and hashes only
[TL-Helper warehouse]    read SQLite produced outside this repo
[Combat log folder]      read text the game already wrote
[Solisium DB]            we own this file
[Optional LLM]           receives structured facts only; never authority
[Network]                off by default; market/Questlog optional later
```

## Allowed

- Read Steam `appmanifest_*.acf` and pak file hashes/mtimes.
- Read TL-Helper warehouse SQLite and data-build receipts.
- Read official combat logs under `%LOCALAPPDATA%\TL\Saved\CombatLogs`.
- Maintain `solisium.sqlite` (or test in-memory databases) with migrations.
- Parse user-supplied JSON, clipboard text, and later user-supplied screenshots.
- Display recommendations derived from stored facts.

## Rejected forever

- Inject DLLs or other code into the game.
- Read or write game process memory.
- Hook game functions or the renderer.
- Intercept, modify, or replay packets.
- Write into the game installation or pak files.
- Automate clicks, combat, movement, or purchases.
- Bypass or evade Easy Anti-Cheat.
- Send commands or input to the game client.
- Brute-force, dump, or search the executable for archive keys.
- Link FModel or other GPL pak explorers into this binary.
- Ship decoded game tables, icons, or localization in the public repository.

AES keys are **out of scope**. They stay in TL-Helper's gitignored `aes.txt` if that project uses them. Solisium never stores or transmits keys.

## Threats and mitigations

| Threat | Mitigation |
| --- | --- |
| Ban / ToS violation via client tampering | No process or packet interaction. Combat logs are an official client feature. |
| User thinks we "read the live character" | Capability matrix: loadout is not in local saves. UI must label manual/imported state. |
| LLM fabricates DPS | Engine is the only numeric authority. LLM gets structured JSON. Phase 1 has no LLM. |
| Wrong patch used for advice | Every static row and every recommendation references `dataset_snapshot.id`. Previous snapshots are preserved. |
| Warehouse schema mistaken for ours | Mapper copies values into Solisium tables. Tests use a tiny synthetic warehouse, not a cloned TL-Helper schema as the app DB. |
| Accidental redistribution of game IP | `.gitignore` for sqlite dumps and `data/`. Fixtures are fake rows. |
| Questlog ToS | No default live scrape. User-pasted JSON only. |
| Path traversal on import | Importers read a caller-supplied file path; they do not execute it. SQL is parameterized via SQLDelight. |
| Combat log malformation | Version header required. Bad lines recorded as parse errors, not silently coerced into skills. |
| Secret leakage to a cloud LLM | No cloud required. If an LLM is added, default is local. Combat logs and character names stay on disk unless the user opts in. |

## Combat-log privacy

Logs contain character names, skill names, and damage. They stay on the user's machine. Do not upload them in CI or commit them.

## What "detect new game data" is allowed to do

1. Read Steam build id.
2. Hash or mtime pak files.
3. Compare to the last imported snapshot's recorded build/hash.
4. Ask the user to run TL-Helper's update pipeline and import the new warehouse.

It is not allowed to unpack paks inside Solisium.

## Residual risk

NCSoft/Amazon may still object to static datamining even when it is read-only. This project does not perform that extraction; it consumes an already-normalized local warehouse. Users should keep decoded data private.

Community DPS overlays (CKdps, STOOP) also only read logs; Solisium follows the same boundary and does not copy those codebases.
