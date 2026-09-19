# Install Solisium Autopilot (Windows)

Give someone this zip. They do **not** need Git, Java, or a compiler.

**[Solisium-Autopilot-0.1.20-windows-x64-installer.zip](Solisium-Autopilot-0.1.20-windows-x64-installer.zip)**

1. Copy the zip to the other PC (or clone this repo, then `git lfs pull`).
2. Extract the zip.
3. Double-click **`install.cmd`**.
4. Finish the MSI wizard. No administrator rights.

The MSI includes a Java runtime and a starter catalog. First launch seeds the local database so every screen works. No archive key is bundled.

## If `git clone` shows a tiny zip

That file is a Git LFS pointer. Run:

```powershell
git lfs pull
```

Then the zip is the real ~130 MB installer.

## Build it yourself

```powershell
.\gradlew.bat :desktopApp:packageRelease
```

That writes the same zip under `releases/` and a copy under `dist/windows-releases/` (gitignored).
