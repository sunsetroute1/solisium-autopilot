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
2. Run "Solisium Autopilot-0.1.1.msi" and follow the prompts.

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

What's in this build (0.1.1)
----------------------------
- Build screen with goal picker, T&L class types, and Questlog-style modeled CP/GS
- Gear catalog with warehouse stats, curves, and optional Questlog community detail
- Character sheet editor with full loadout, mastery, and build layers
- Combat analyzer: import all T&L logs from disk, portfolio DPS trends, per-skill
  crit/heavy rates, session compare, and build cross-check vs your skill bar
- Drops screen with farm-time estimates from observed drop rates
- In-app combat logging setup guide (Ring Menu / Record Combat Log)

T&L combat logs save to:

    %LOCALAPPDATA%\TL\Saved\CombatLogs

Use Combat > Import all after a fight ends. Duplicate files are skipped automatically.
