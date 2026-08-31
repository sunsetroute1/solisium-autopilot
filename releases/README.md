# Solisium Autopilot — Windows releases

Pre-built install packages. Both include a bundled Java runtime (no JDK required)
and install per-user (no administrator rights).

| File | Install |
| --- | --- |
| `Solisium-Autopilot-*-installer.zip` | Extract, run the `.msi`, uninstall via Settings > Apps |
| `Solisium-Autopilot-*-portable.zip` | Extract, run `install.cmd` (or `Install-Solisium.ps1`) |

See [packaging/README-INSTALL.txt](../packaging/README-INSTALL.txt) for full instructions.

To rebuild after a version bump:

```powershell
.\gradlew.bat :desktopApp:packageRelease
```

The current build is also on the [GitHub Releases](https://github.com/sunsetroute1/solisium-autopilot/releases) page. Zips here can be committed and downloaded from the repo.
