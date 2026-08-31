Solisium Autopilot - Windows install
====================================

A read-only Throne and Liberty companion. It reads game data you have already
extracted and never writes to the game or its files.

There are two downloads. Either one works; pick whichever you prefer. Both
already contain a Java runtime, so Java does not need to be installed, and both
install per-user, so neither needs administrator rights.

Both installers also ship a starter dataset (sample catalog, character, and
combat log). On first launch the app copies that into your profile so every
screen works immediately. Import your own TL-Helper warehouse from Data when
you want live game data instead of the demo set.

Install (installer zip)
-----------------------
1. Extract the zip.
2. Run "Solisium Autopilot-0.1.9.msi" and follow the prompts.

Uninstall through Settings > Apps > Installed apps, like any other program.

Install (portable zip)
----------------------
1. Extract the whole zip somewhere (Downloads is fine).
2. Double-click install.cmd.

That copies the app to %LOCALAPPDATA%\Programs\Solisium Autopilot and adds Start
Menu and Desktop shortcuts.

To uninstall, run in PowerShell from the extracted folder:

    .\Install-Solisium.ps1 -Uninstall

Or delete %LOCALAPPDATA%\Programs\Solisium Autopilot and its shortcuts by hand.

Options for the portable installer:

    .\Install-Solisium.ps1 -NoShortcuts
    .\Install-Solisium.ps1 -InstallRoot "D:\Apps"

About keys
----------
This build contains no archive key, and the build fails if one is ever found in
the packaged files.

You do not need a key to browse data you have already imported. A key is only
for extracting game files yourself.

If one is already on this PC, the app finds it for you. The first time you run
it, it asks whether to store the key it found, showing you a short fingerprint
and the file it came from so you can tell it is yours. Say no and it will not ask
again. You can also do it later from Data > Archive key, which is where you go if
you have several keys or want to point at a specific folder.

A stored key:

  - stays on this PC. It is never sent anywhere and never shared.
  - is never displayed. The app shows a fingerprint, never the key.
  - can be deleted at any time from Data > Archive key.

It is kept in:

    %LOCALAPPDATA%\Solisium\secrets.properties

That is outside the install folder on purpose, so installing, upgrading, and
uninstalling never move or delete a key, and packaging cannot pick one up.

Equivalent command line, if you prefer:

    solisium keys scan
    solisium keys add --name archive --from <folder>
    solisium keys list

Where your data lives
---------------------
    %USERPROFILE%\.solisium\solisium.sqlite    imported catalog and your character
    %LOCALAPPDATA%\Solisium\                   local keys, if you add any

Neither is touched by install or uninstall.

What's in this build (0.1.9)
----------------------------
- Run TL-Helper prepares warehouse inputs (loc, Questlog snapshots, baseline)
  after decode so a new Steam build can produce tl-<build>.sqlite
- Run TL-Helper opens a visible Command Prompt (hidden consoles made clicks
  look like a no-op)
- Failed extract is shown on the banners instead of a stuck Decode 50% bar
- Stale/waiting banners show collector, decode, and warehouse progress percent
  while extract runs
- Run TL-Helper now starts collector, then decode, then warehouse (a full
  pipeline preflight-fails on a new Steam build)
- Stale/waiting banners show the last TL-Helper extract status when it failed
- New build influences on Home are clickable and list warehouse names
- After a matching warehouse import, stale/waiting banners clear and a current
  confirmation is shown; Run TL-Helper also polls for the new sqlite
- Stale-data and patch-watch warnings include a Run TL-Helper button that opens
  extract in a new window (D:\TL_Helper, or a folder you pick)
- Build screen: side-by-side "I currently have" / "I would like to have" paper dolls
  (head, chest, arms, weapons) with warehouse icon paths when extracted PNGs exist
- Events screen: T&L-style hour timetable, server/region picker, community cadence,
  and a Questlog-named boss/riftstone roster (not live Amazon spawn times)
- Talking Wall tab: searchable true/false answers (blue TRUE / red FALSE)
- Gear and Drops rarity colors from warehouse grades and row-id hints
- Gear catalog with warehouse stats, curves, and optional Questlog community detail
- Character sheet editor with full loadout, mastery, and build layers
- Combat analyzer: import all T&L logs, portfolio DPS, session compare
- Drops screen with farm-time estimates from observed drop rates

T&L combat logs save to:

    %LOCALAPPDATA%\TL\Saved\CombatLogs

Use Combat > Import all after a fight ends. Duplicate files are skipped automatically.
