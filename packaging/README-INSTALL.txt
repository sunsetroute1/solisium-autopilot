Solisium Autopilot - Windows install
====================================

A read-only Throne and Liberty companion. It reads game data you have already
extracted and never writes to the game or its files.

Install
-------
1. Extract this whole zip somewhere (Downloads is fine).
2. Double-click install.cmd.

That installs to %LOCALAPPDATA%\Programs\Solisium Autopilot and adds Start Menu
and Desktop shortcuts. No administrator rights are needed, and Java does not need
to be installed - a Java runtime is already bundled inside.

Uninstall
---------
Run in PowerShell from the extracted folder:

    .\Install-Solisium.ps1 -Uninstall

Or delete %LOCALAPPDATA%\Programs\Solisium Autopilot and its shortcuts by hand.

Options
-------
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
