# Solisium Autopilot — Windows releases

Pre-built install packages. Both include a bundled Java runtime (no JDK required)
and install per-user (no administrator rights).

| File | Install |
| --- | --- |
| `Solisium-Autopilot-*-installer.zip.partNN` | Join, extract, run the `.msi` |
| `Solisium-Autopilot-*-portable.zip.partNN` | Join, extract, run `install.cmd` |

A full zip is over GitHub's 100 MB git limit, so each archive is stored as 45 MB
parts. Double-click `assemble.cmd` (or run `..\packaging\Join-Release.ps1`) to
rebuild the zips, then extract and install as usual.

See [packaging/README-INSTALL.txt](../packaging/README-INSTALL.txt) for full instructions.

To rebuild after a version bump:

```powershell
.\gradlew.bat :desktopApp:packageRelease
```

Whole zips are also on the [GitHub Releases](https://github.com/sunsetroute1/solisium-autopilot/releases) page.
