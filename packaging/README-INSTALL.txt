Solisium Autopilot - Windows install
====================================

A read-only Throne and Liberty companion. It reads game data you have already
extracted and never writes to the game or its files.

There are two downloads. Either one works; pick whichever you prefer. Both
already contain a Java runtime, so Java does not need to be installed, and both
install per-user, so neither needs administrator rights.

If you cloned the git repo instead of downloading a whole zip, the archives
are stored as 45 MB .partNN files under releases\. Run releases\assemble.cmd
to join them, then continue below.

Both installers also ship a starter dataset (sample catalog, character, and
combat log). On first launch the app copies that into your profile so every
screen works immediately. Import your own TL-Helper warehouse from Data when
you want live game data instead of the demo set.

Install (installer zip)
-----------------------
1. Extract the zip.
2. Run "Solisium Autopilot-0.1.16.msi" and follow the prompts.
   The MSI includes the full TL-Helper checkout. First launch (or
   Install-TLHelper.ps1 / Get TL-Helper) copies it onto disk. No key is included.

Uninstall through Settings > Apps > Installed apps, like any other program.

Install (portable zip)
----------------------
1. Extract the whole zip somewhere (Downloads is fine).
2. Double-click install.cmd.

That copies the app to %LOCALAPPDATA%\Programs\Solisium Autopilot, installs
TL-Helper to %LOCALAPPDATA%\Programs\TL-Helper (from the bundled copy, or a
download from github.com/sunsetroute1/tl-helper), and adds Start Menu and
Desktop shortcuts.

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

You do not need a key to browse the starter catalog. A key is only for
extracting game files yourself with TL-Helper. This build never contains one.

If one is already on this PC, Data > Find my key looks in TL-Helper, TL_Data,
and %LOCALAPPDATA%\Solisium. The first run also offers to store a found key,
showing a fingerprint and the file it came from. Say no and it will not ask
again. Search a folder if yours lives somewhere else.

Live extract is optional. The installer (or Get TL-Helper in the app) places
TL-Helper on disk from the bundled copy or https://github.com/sunsetroute1/tl-helper .
Install Node.js and the .NET SDK, then use Run TL-Helper.

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

What's in this build (0.1.16)
----------------------------
- Talking Wall tab: Gate of Memory countdown (MetaForge cadence) and 24h schedule
- Build tab: drop watermark calculator (Aragon datamine) with farm priority
- Build tab: click paper-doll slots to pick target gear from meta-ranked list
- Progress tab: live sync from NCStorageLocalData.ini plus clipboard paste
- Progress tab: ranked what-to-do-next from build gaps and progression catalog
- Gear inspector on Catalog detail with trait display and warehouse curves
- Skill-core descriptions show warehouse tooltip numbers, not $[row.tooltip] keys
- Cores list collapses rift/non-rift copies of the same named core (Double Trap)
- Cores (and Catalog) show extracted skill-core tooltips from locres
- Those descriptions follow a new warehouse / Game.locres after a patch extract
- Cores tab: searchable warehouse list of skill cores (perk items)
- The installer ships the full TL-Helper checkout (web, scripts, collector),
  never a key or config.local.json
- First launch installs that checkout and asks if a key is already on this PC
- TL-Helper is in this repo as vendor/tl-helper and is installed with Solisium
  (bundled copy, or a download from sunsetroute1/tl-helper)
- Get TL-Helper installs that checkout instead of only opening GitHub
- A GitHub install works on the starter catalog with no key and no TL-Helper
- Find my key also searches the TL-Helper folder and source-manifest.json
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
