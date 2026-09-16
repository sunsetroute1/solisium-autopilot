# Windows release downloads

Installers are **not stored in git** (they bloated the repo to ~3 GB). Use one of:

1. **[GitHub Releases](https://github.com/sunsetroute1/solisium-autopilot/releases)** — pre-built Solisium Autopilot zips when published.
2. **Build locally** — outputs go to `dist/windows-releases/` (gitignored).

## Solisium Autopilot

```powershell
.\gradlew.bat :desktopApp:packageRelease
```

Produces under `dist/windows-releases/`:

| Zip | Install |
| --- | --- |
| `Solisium-Autopilot-*-installer.zip` | Extract, run the `.msi` |
| `Solisium-Autopilot-*-portable.zip` | Extract, run `install.cmd` |

Full rebuild + local install:

```powershell
.\packaging\Rebuild-Pipeline.ps1
```

See [packaging/README-INSTALL.txt](../packaging/README-INSTALL.txt).

## TL Route Investigator

```powershell
.\tools\tl-route-investigator\packaging\Package-Release.ps1 -DesktopCopy
```

Extract **`TL-Route-Investigator-*-Install.zip`**, double-click **`install.cmd`**.

Output default: `dist/windows-releases/TL-Route-Investigator-*-Install.zip`.

## Legacy `assemble.cmd`

If you still have old `.partNN` files from an ancient clone, `assemble.cmd` runs
`packaging/Join-Release.ps1` to rebuild a full zip. New builds do not create parts.
