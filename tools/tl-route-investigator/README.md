# TL Route Investigator

Windows network diagnostic tool for investigating ISP routing to **Throne and Liberty (Americas)** — observation only, no VPN/proxy/traffic manipulation.

## Stack

- **Rust** — measurements, Windows sockets, ICMP
- **Tauri 2 + TypeScript** — desktop UI

See [docs/API_CHOICES.md](docs/API_CHOICES.md) for Windows APIs and privilege notes.

## Phase 1–5 (partial)

- **Phase 4:** IP/ASN/rDNS lookup with SQLite cache (`ip_intel_cache`)
- **Phase 5:** ICMP latency batches + saved `measurements` rows
- **Phase 6:** Windows `tracert` / optional `pathping`, hop ASN enrichment, route fingerprints in SQLite
- **Phase 7:** DNS-resolved AWS regional + neutral DNS references; ICMP comparison vs tagged game IPs; cautious relay hints
- **Phase 8:** Scheduled studies (SQLite samples + route-change events + History sparklines)
- **Phase 9:** JSON / CSV / HTML report export (public IP masked unless opted in)
- **Phase 10:** Relay opportunity analysis from saved ICMP + routes (transparent thresholds, no guaranteed wins)

## Phase 1–3

- Local network snapshot (adapters, gateway, DNS, link speed, MTU hints)
- Approximate ISP/ASN via ipwho.is (public IP masked by default)
- Process list with weak T&L name/path hints; auto-prefers `TL.exe`
- Manual PID selection
- TCP/UDP connections merged from `netstat2` + `netstat -ano`
- Live polling (3s, lightweight tick) with SQLite session history under `%LOCALAPPDATA%\TL Route Investigator\data.db`
- User endpoint tags: unknown / likely gameplay / not gameplay
- ICMP latency (1–1000 probes) with percentiles; optional targets from live connections

Headless probe: `cargo run --bin phase1_probe` from `src-tauri/`.

## Build & run

```powershell
cd tools/tl-route-investigator
npm install
npm run tauri dev
```

Release build:

```powershell
npm run tauri build
```

Packaged Windows installers (after build) live under `src-tauri/target/release/bundle/`.
To produce the install zip in `releases/` (and optionally copy to your Desktop):

```powershell
.\tools\tl-route-investigator\packaging\Package-Release.ps1 -DesktopCopy
```

Extract the zip, then double-click **`install.cmd`**.

## Roadmap

Phases 2–10 are described in the product spec (classification, traceroute/ASN, AWS baselines, SQLite history, reports, relay hints). Do not proceed to the next phase until the current one is verified on real hardware with T&L running.
