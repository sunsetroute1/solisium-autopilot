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

The **rebuild pipeline** packages, splits for git, and reinstalls the local copy:

```powershell
.\packaging\Rebuild-Pipeline.ps1
```

That runs `:desktopApp:packageRelease` (MSI + portable zip + 45 MB `.partNN` pieces), then `Install-Solisium.ps1` into `%LOCALAPPDATA%\Programs`. It does not commit. After it finishes, add the new `releases\*.partNN` files and push `master` plus tag `vX.Y.Z`.

Package only:

```powershell
.\gradlew.bat :desktopApp:packageRelease
```

Whole zips are also on the [GitHub Releases](https://github.com/sunsetroute1/solisium-autopilot/releases) page.

## TL Route Investigator

| File | Install |
| --- | --- |
| `TL-Route-Investigator-*-installer.zip.partNN` | Join with `assemble.cmd` / `Join-Release.ps1`, extract, run `*-setup.exe` or the `.msi` |

Build from source: `tools/tl-route-investigator/`. Package:

```powershell
.\tools\tl-route-investigator\packaging\Package-Release.ps1
```
