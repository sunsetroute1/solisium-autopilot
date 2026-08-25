Solisium Autopilot - Windows install
====================================

A read-only Throne and Liberty companion. It reads game data you have already
extracted and never writes to the game or its files.

There are two downloads. Either one works; pick whichever you prefer. Both
already contain a Java runtime, so Java does not need to be installed, and both
install per-user, so neither needs administrator rights.

Install (installer zip)
-----------------------
1. Extract the zip.
2. Run "Solisium Autopilot-0.1.0.msi" and follow the prompts.

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

If you have a key, the app can find it for you: open the app, go to Data, and use
the key finder. It searches a few likely folders, shows you only a short
fingerprint rather than the key, and asks before storing anything.

A stored key goes in:

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
