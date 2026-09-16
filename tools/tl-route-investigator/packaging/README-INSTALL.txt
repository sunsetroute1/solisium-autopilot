TL Route Investigator — Windows install (0.1.1)
================================================

Observe-only network diagnostic for Throne and Liberty routing. No VPN, no proxy,
no guessed game server IPs — endpoints come from live process sockets only.

Install (pick one)
------------------
1. **Setup.exe** (recommended) — run the NSIS installer from this zip.
2. **.msi** — per-machine/per-user MSI if you prefer Windows Installer.

Both are produced by the same Tauri release build. Data (SQLite history) is stored
under %LOCALAPPDATA%\TL Route Investigator\ — not inside the install folder.

Admin note
----------
Run as administrator if UDP owner tables or full connection lists look incomplete.
The app works without admin but may show fewer sockets.

No secrets
----------
This package does not include API keys. Public IP lookup uses ipwho.is without
credentials; you can keep your public IP masked in Settings.
