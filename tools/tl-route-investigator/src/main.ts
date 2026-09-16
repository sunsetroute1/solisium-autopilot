import { invoke } from "@tauri-apps/api/core";

import "./styles.css";



type AdapterInfo = {

  name: string;

  description: string;

  status: string;

  media_type: string;

  link_speed_mbps: number | null;

  ipv4: string[];

  ipv6: string[];

  is_up: boolean;

};



type LocalNetworkSnapshot = {

  adapters: AdapterInfo[];

  default_gateway_ipv4: string | null;

  default_gateway_ipv6: string | null;

  dns_servers_ipv4: string[];

  dns_servers_ipv6: string[];

  mtu_hints: string[];

  ipv4_works: boolean;

  ipv6_works: boolean;

  elevated: boolean;

};



type PublicNetworkSnapshot = {

  public_ipv4: string | null;

  public_ipv4_masked: string | null;

  isp: string | null;

  org: string | null;

  asn: string | null;

  country: string | null;

  region: string | null;

  city: string | null;

  source: string;

  approximate: boolean;

  lookup_error: string | null;

};



type ProcessRow = {

  pid: number;

  name: string;

  exe_path: string | null;

  started_at: string | null;

  tl_candidate: boolean;

  tl_match_reason: string | null;

};



type ConnectionRow = {

  key: string;

  local_ip: string;

  local_port: number;

  remote_ip: string;

  remote_port: number;

  protocol: "tcp" | "udp";

  state: string | null;

  pid: number;

  first_seen: string;

  last_seen: string;

  observations: number;

  classification: string;

};



type LatencyProbeResult = {

  target: string;

  probes: number;

  received: number;

  packet_loss_pct: number;

  min_ms: number | null;

  mean_ms: number | null;

  median_ms: number | null;

  p90_ms: number | null;

  p95_ms: number | null;

  p99_ms: number | null;

  max_ms: number | null;

  stddev_ms: number | null;

  jitter_ms: number | null;

  samples_ms: number[];

  error: string | null;

};



type Phase1Snapshot = {

  elevated: boolean;

  local: LocalNetworkSnapshot;

  public: PublicNetworkSnapshot;

  processes: ProcessRow[];

  selected_pid: number | null;

  connections: ConnectionRow[];

  gateway_latency: LatencyProbeResult | null;

  connections_error: string | null;

  active_session_id: number | null;

  session_connection_rows: number;

  db_path: string;

};



type LiveTick = {

  elevated: boolean;

  selected_pid: number | null;

  connections: ConnectionRow[];

  connections_error: string | null;

  active_session_id: number | null;

  session_connection_rows: number;

  tick_at: string;

};

type IpIntelRecord = {
  ip: string;
  reverse_dns: string | null;
  asn: string | null;
  isp: string | null;
  org: string | null;
  country: string | null;
  region: string | null;
  city: string | null;
  aws_region_hint: string | null;
  source: string;
  approximate: boolean;
  fetched_at: string;
  lookup_error: string | null;
  from_cache: boolean;
};

type MeasurementSummary = {
  id: number;
  target_ip: string;
  probed_at: string;
  median_ms: number | null;
  p95_ms: number | null;
  packet_loss_pct: number;
  note: string | null;
};

type RouteHop = {
  hop: number;
  hop_ip: string | null;
  hostname: string | null;
  rtt_ms: number[];
  rtt_median_ms: number | null;
  hop_silent: boolean;
  asn: string | null;
  org: string | null;
};

type RouteTraceResult = {
  target: string;
  method: string;
  probed_at: string;
  hops: RouteHop[];
  reached_destination: boolean;
  fingerprint_raw: string;
  fingerprint_asn: string;
  raw_output: string;
  error: string | null;
};

type RouteSummary = {
  id: number;
  target_ip: string;
  probed_at: string;
  method: string;
  reached_destination: boolean;
  hop_count: number;
  fingerprint_asn: string | null;
};

let routeTarget = "1.1.1.1";
let lastRouteTrace: RouteTraceResult | null = null;
let lastComparison: ComparisonReport | null = null;

type ComparisonReport = {
  references: ReferenceBenchmarkRow[];
  game_endpoints: GameBenchmarkRow[];
  hints: RelayHint[];
  disclaimer: string;
};

type ReferenceBenchmarkRow = {
  reference: ResolvedReference;
  asn: string | null;
  org: string | null;
  latency: LatencyProbeResult;
  route_fingerprint_asn: string | null;
};

type ResolvedReference = {
  id: string;
  label: string;
  kind: "neutral_internet" | "aws_regional_reference";
  hostname: string;
  aws_region_id: string | null;
  geo_hint: string;
  resolved_ipv4: string | null;
  resolve_error: string | null;
};

type GameBenchmarkRow = {
  ip: string;
  user_tag: string | null;
  latency: LatencyProbeResult;
  route_fingerprint_asn: string | null;
};

type RelayHint = {
  summary: string;
  evidence: string;
};

let monitorTimer: number | null = null;

let fullRefreshTimer: number | null = null;

let historyPollTimer: number | null = null;

let selectedPid: number | null = null;

let probeTarget = "1.1.1.1";

let lastSnapshot: Phase1Snapshot | null = null;

let lastConnections: ConnectionRow[] = [];

const intelByIp: Record<string, IpIntelRecord> = {};



const app = document.querySelector<HTMLDivElement>("#app")!;



app.innerHTML = `

  <header class="header">

    <div>

      <h1>TL Route Investigator</h1>

      <p class="sub">Phase 10 — relay opportunity analysis (evidence-based). Observe only.</p>

    </div>

    <div class="header-actions">

      <label class="chk"><input type="checkbox" id="includePublicIp" /> Include public IP in UI</label>

      <button id="startBtn" class="primary">Start monitoring</button>

    </div>

  </header>

  <nav class="tabs">

    <button class="tab active" data-tab="live">Live</button>

    <button class="tab" data-tab="connections">Connections</button>

    <button class="tab" data-tab="processes">Processes</button>

    <button class="tab" data-tab="latency">Latency</button>

    <button class="tab" data-tab="asn">ASN</button>

    <button class="tab" data-tab="routes">Routes</button>

    <button class="tab" data-tab="comparison">AWS Comparison</button>

    <button class="tab" data-tab="opportunities">Opportunities</button>

    <button class="tab" data-tab="history">History</button>

    <button class="tab" data-tab="reports">Reports</button>

    <button class="tab" data-tab="settings">Settings</button>

  </nav>

  <main id="panel"></main>

`;



const panel = document.querySelector<HTMLDivElement>("#panel")!;

const startBtn = document.querySelector<HTMLButtonElement>("#startBtn")!;

const includePublicIp = document.querySelector<HTMLInputElement>("#includePublicIp")!;



function fmtMs(v: number | null) {

  return v == null ? "—" : `${v.toFixed(1)} ms`;

}



function gameplayPick(conns: ConnectionRow[]): ConnectionRow | null {

  const tagged = conns.find((c) => c.classification === "likely_gameplay");

  if (tagged) return tagged;

  return (

    conns.find(

      (c) => c.remote_ip !== "*" && c.protocol === "tcp" && c.remote_port !== 443 && c.remote_port !== 80,

    ) ?? conns.find((c) => c.remote_ip !== "*") ?? null

  );

}



function renderLive(s: Phase1Snapshot) {

  const upEth = s.local.adapters.find((a) => a.is_up && a.media_type.toLowerCase().includes("802.3"));

  const gw = s.local.default_gateway_ipv4 ?? "—";

  const lat = s.gateway_latency;

  const pick = gameplayPick(s.connections);

  const pickIntel = pick ? intelByIp[pick.remote_ip] : undefined;

  panel.innerHTML = `

    <section class="card grid2">

      <div>

        <h2>Connection</h2>

        <p><strong>ISP (approx):</strong> ${s.public.isp ?? "—"} ${s.public.asn ? `(${s.public.asn})` : ""}</p>

        <p><strong>Public IP:</strong> ${s.public.public_ipv4 ?? s.public.public_ipv4_masked ?? "masked"}</p>

        <p><strong>Gateway:</strong> ${gw}</p>

        <p><strong>DNS:</strong> ${s.local.dns_servers_ipv4.join(", ") || "—"}</p>

        <p><strong>LAN:</strong> ${upEth ? `${upEth.name} · ${upEth.link_speed_mbps ?? "?"} Mbps · ${upEth.media_type}` : "—"}</p>

        <p><strong>MTU:</strong> ${s.local.mtu_hints.slice(0, 2).join("; ") || "—"}</p>

        <p><strong>Admin:</strong> ${s.elevated ? "Yes" : "No (UDP owner tables may be incomplete)"}</p>

      </div>

      <div>

        <h2>T&amp;L monitor</h2>

        <p><strong>Session:</strong> ${s.active_session_id ?? "none"} · ${s.session_connection_rows} rows stored</p>

        <p><strong>Selected PID:</strong> ${s.selected_pid ?? "none"}</p>

        <p><strong>Live sockets:</strong> ${s.connections.length}</p>

        ${s.connections_error ? `<p class="warn">${s.connections_error}</p>` : ""}

        ${

          pick

            ? `<p><strong>Highlighted endpoint:</strong> ${pick.remote_ip}:${pick.remote_port} (${pick.protocol.toUpperCase()}) — not classified unless you tag it</p>
            ${pickIntel?.asn ? `<p><strong>ASN (approx):</strong> ${pickIntel.asn} · ${pickIntel.org ?? pickIntel.isp ?? ""}</p>` : ""}`

            : ""

        }

        <p><strong>Gateway latency (5 ICMP, cached):</strong> P50 ${fmtMs(lat?.median_ms ?? null)} · P95 ${fmtMs(lat?.p95_ms ?? null)} · loss ${lat?.packet_loss_pct?.toFixed(1) ?? "—"}%
          <button type="button" id="pingGateway" class="small">Refresh ping</button></p>

      </div>

    </section>

  `;

  document.querySelector<HTMLButtonElement>("#pingGateway")?.addEventListener("click", () => {

    void refreshFull(true, false);

  });

}



async function onTagChange(c: ConnectionRow, tag: string) {

  await invoke("set_endpoint_user_tag", {

    remoteIp: c.remote_ip,

    remotePort: c.remote_port,

    protocol: c.protocol,

    userTag: tag,

  });

  await refreshConnectionsOnly();

  if (lastSnapshot) {

    lastSnapshot.connections = lastConnections;

    if (activeTab === "live") renderLive(lastSnapshot);

  }

}



function renderConnectionsTable(conns: ConnectionRow[], pid: number | null) {

  if (!conns.length) {

    return `<section class="card"><p>No connections for PID ${pid ?? "—"}. Run the game in-world or pick another process.</p></section>`;

  }

  const rows = conns

    .map((c) => {

      const opts = ["unknown", "likely_gameplay", "not_gameplay"]

        .map(

          (t) =>

            `<option value="${t}" ${c.classification === t ? "selected" : ""}>${t.replace(/_/g, " ")}</option>`,

        )

        .join("");

      return `<tr data-key="${c.key}">

        <td>${c.protocol.toUpperCase()}</td>

        <td>${c.local_ip}:${c.local_port}</td>

        <td>${c.remote_ip}:${c.remote_port}</td>

        <td>${c.state ?? "—"}</td>

        <td>${c.observations}</td>

        <td><select class="tagSelect" data-ip="${c.remote_ip}" data-port="${c.remote_port}" data-proto="${c.protocol}">${opts}</select></td>

      </tr>`;

    })

    .join("");

  return `

    <section class="card">

      <h2>Live connections (PID ${pid ?? "—"})</h2>

      <table><thead><tr><th>Proto</th><th>Local</th><th>Remote</th><th>State</th><th>Obs</th><th>Your tag</th></tr></thead><tbody>${rows}</tbody></table>

      <p class="muted">Tags are stored locally (SQLite). They are evidence you provide — not auto-detected gameplay.</p>

    </section>`;

}



function renderConnections(s: Phase1Snapshot) {

  panel.innerHTML = renderConnectionsTable(s.connections, s.selected_pid);

  wireTagSelects(s.connections);

}



function wireTagSelects(conns: ConnectionRow[]) {

  panel.querySelectorAll<HTMLSelectElement>(".tagSelect").forEach((sel) => {

    sel.onchange = async () => {

      const ip = sel.dataset.ip!;

      const port = Number(sel.dataset.port);

      const proto = sel.dataset.proto!;

      const row = conns.find((c) => c.remote_ip === ip && c.remote_port === port && c.protocol === proto);

      if (row) await onTagChange(row, sel.value);

    };

  });

}



function renderProcesses(s: Phase1Snapshot) {

  const filter = (document.querySelector<HTMLInputElement>("#procFilter")?.value ?? "").toLowerCase();

  const rows = s.processes

    .filter((p) => !filter || p.name.toLowerCase().includes(filter) || String(p.pid).includes(filter))

    .slice(0, 200)

    .map((p) => {

      const sel = p.pid === selectedPid ? "selected" : "";

      const hint = p.tl_candidate ? `<span class="tag">T&amp;L candidate</span>` : "";

      return `<tr class="${sel}" data-pid="${p.pid}"><td>${p.pid}</td><td>${p.name} ${hint}</td><td class="mono">${p.exe_path ?? ""}</td><td>${p.started_at ?? ""}</td><td>${p.tl_match_reason ?? ""}</td></tr>`;

    })

    .join("");

  panel.innerHTML = `

    <section class="card">

      <h2>Process picker</h2>

      <input id="procFilter" placeholder="Filter by name or PID" />

      <table><thead><tr><th>PID</th><th>Name</th><th>Path</th><th>Started</th><th>Hint</th></tr></thead><tbody>${rows}</tbody></table>

      <p class="muted">Click a row to monitor that PID. TL.exe is preferred automatically when none is selected.</p>

    </section>`;

  document.querySelector<HTMLInputElement>("#procFilter")?.addEventListener("input", () => renderProcesses(s));

  panel.querySelectorAll("tbody tr[data-pid]").forEach((tr) => {

    tr.addEventListener("click", async () => {

      const pid = Number((tr as HTMLElement).dataset.pid);

      selectedPid = pid;

      await invoke("select_process", { pid });

      await refreshFull(false);

    });

  });

}



function uniqueRemoteIps(conns: ConnectionRow[]): string[] {
  return conns
    .filter((c) => c.remote_ip !== "*")
    .map((c) => c.remote_ip)
    .filter((ip, i, a) => a.indexOf(ip) === i);
}

function formatProbe(r: LatencyProbeResult): string {
  return [
    `target=${r.target} probes=${r.probes} received=${r.received} loss=${r.packet_loss_pct.toFixed(2)}%`,
    `P50=${fmtMs(r.median_ms)} P90=${fmtMs(r.p90_ms)} P95=${fmtMs(r.p95_ms)} P99=${fmtMs(r.p99_ms)} max=${fmtMs(r.max_ms)}`,
    `jitter=${fmtMs(r.jitter_ms)} stddev=${fmtMs(r.stddev_ms)}`,
    r.error ? `error: ${r.error}` : "",
  ].join("\n");
}

async function hydrateIntelFromDb(ips: string[]) {
  if (!ips.length) return;
  const cached = await invoke<IpIntelRecord[]>("load_cached_ip_intel", { ips });
  for (const rec of cached) intelByIp[rec.ip] = rec;
}

async function renderAsn(s: Phase1Snapshot) {
  const ips = uniqueRemoteIps(s.connections);
  await hydrateIntelFromDb(ips);
  const rows = ips
    .map((ip) => {
      const intel = intelByIp[ip];
      if (!intel) {
        return `<tr><td>${ip}</td><td colspan="6" class="muted">Not looked up yet</td></tr>`;
      }
      return `<tr>
        <td>${intel.ip}</td>
        <td class="mono">${intel.reverse_dns ?? "—"}</td>
        <td>${intel.asn ?? "—"}</td>
        <td>${intel.org ?? intel.isp ?? "—"}</td>
        <td>${[intel.city, intel.region, intel.country].filter(Boolean).join(", ") || "—"}</td>
        <td>${intel.aws_region_hint ?? "—"}</td>
        <td>${intel.from_cache ? "cache" : "live"} · ${intel.source}</td>
      </tr>`;
    })
    .join("");
  panel.innerHTML = `
    <section class="card">
      <h2>IP / ASN lookup (approximate)</h2>
      <button id="lookupRemotes" class="primary">Lookup connection remotes</button>
      <label class="chk"><input type="checkbox" id="forceIntelRefresh" /> Force refresh (ignore 7-day cache)</label>
      <p class="muted">Uses ipwho.is (no API key) + reverse DNS via Windows. Geolocation is approximate.</p>
      <table><thead><tr><th>IP</th><th>rDNS</th><th>ASN</th><th>Org/ISP</th><th>Geo (approx)</th><th>AWS hint</th><th>Source</th></tr></thead><tbody>${rows || `<tr><td colspan="7">No public remotes in connection table.</td></tr>`}</tbody></table>
    </section>`;
  document.querySelector<HTMLButtonElement>("#lookupRemotes")!.onclick = async () => {
    const force = (document.querySelector<HTMLInputElement>("#forceIntelRefresh")!).checked;
    const list = await invoke<IpIntelRecord[]>("lookup_ips_intel", { ips, forceRefresh: force });
    for (const rec of list) intelByIp[rec.ip] = rec;
    await renderAsn(s);
    if (activeTab === "live" && lastSnapshot) renderLive(lastSnapshot);
  };
}

function renderLatency(s: Phase1Snapshot) {
  const targets = uniqueRemoteIps(s.connections);
  const tagged = s.connections
    .filter((c) => c.classification === "likely_gameplay" && c.remote_ip !== "*")
    .map((c) => c.remote_ip)
    .filter((ip, i, a) => a.indexOf(ip) === i);
  const options = targets.map((t) => `<option value="${t}">${t}</option>`).join("");
  panel.innerHTML = `
    <section class="card">
      <h2>ICMP latency probe</h2>
      <label>Target IP
        <input id="probeTarget" list="probeTargets" value="${probeTarget}" />
        <datalist id="probeTargets">${options}</datalist>
      </label>
      <label>Probes <input id="probeCount" type="number" min="1" max="1000" value="100" /></label>
      <label class="chk"><input type="checkbox" id="saveProbe" checked /> Save to SQLite when a monitoring session is active</label>
      <button id="runProbe" class="primary">Run probe</button>
      <button id="probeTagged">Probe all "likely gameplay" IPs (${tagged.length})</button>
      <button id="probeAllRemotes">Probe all connection remotes (${targets.length})</button>
      <p class="muted">ICMP may not reflect UDP game traffic. Many AWS hosts block echo — 100% loss does not prove the game path is down.</p>
      <pre id="probeOut" class="mono"></pre>
      <h3>Recent saved probes</h3>
      <pre id="probeHistory" class="mono">Loading…</pre>
    </section>`;
  void invoke<MeasurementSummary[]>("list_recent_measurements", { limit: 15 }).then((rows) => {
    const el = document.querySelector<HTMLPreElement>("#probeHistory");
    if (!el) return;
    el.textContent =
      rows.length === 0
        ? "No saved probes yet."
        : rows
            .map(
              (m) =>
                `${m.probed_at} ${m.target_ip} P50=${fmtMs(m.median_ms)} P95=${fmtMs(m.p95_ms)} loss=${m.packet_loss_pct.toFixed(1)}%`,
            )
            .join("\n");
  });
  document.querySelector<HTMLButtonElement>("#runProbe")!.onclick = async () => {
    probeTarget = (document.querySelector<HTMLInputElement>("#probeTarget")!.value || "1.1.1.1").trim();
    const probes = Number(document.querySelector<HTMLInputElement>("#probeCount")!.value || 100);
    const save = (document.querySelector<HTMLInputElement>("#saveProbe")!).checked;
    const out = document.querySelector<HTMLPreElement>("#probeOut")!;
    out.textContent = "Running…";
    const r = await invoke<LatencyProbeResult>("run_latency_probe", {
      target: probeTarget,
      probes,
      save,
      note: "manual single probe",
    });
    out.textContent = formatProbe(r);
  };
  const runBatch = async (list: string[]) => {
    const out = document.querySelector<HTMLPreElement>("#probeOut")!;
    if (!list.length) {
      out.textContent = "No targets.";
      return;
    }
    out.textContent = `Running ${list.length} endpoint(s)…`;
    const probes = Number(document.querySelector<HTMLInputElement>("#probeCount")!.value || 100);
    const results = await invoke<LatencyProbeResult[]>("probe_endpoints_latency", {
      ips: list,
      probes,
      save: true,
      note: "batch icmp",
    });
    out.textContent = results.map(formatProbe).join("\n\n");
  };
  document.querySelector<HTMLButtonElement>("#probeTagged")!.onclick = () => void runBatch(tagged);
  document.querySelector<HTMLButtonElement>("#probeAllRemotes")!.onclick = () => void runBatch(targets);
}



function renderRoutes(s: Phase1Snapshot) {
  const targets = uniqueRemoteIps(s.connections);
  const options = targets.map((t) => `<option value="${t}"></option>`).join("");
  const hops = lastRouteTrace?.hops ?? [];
  const hopRows = hops
    .map((h) => {
      const status = h.hop_silent
        ? "silent (no ICMP reply — not end-to-end loss)"
        : h.rtt_median_ms != null
          ? `${h.rtt_median_ms.toFixed(1)} ms`
          : "—";
      return `<tr>
        <td>${h.hop}</td>
        <td>${h.hop_ip ?? "—"}</td>
        <td class="mono">${h.hostname ?? "—"}</td>
        <td>${h.asn ?? "—"}</td>
        <td>${h.org ?? "—"}</td>
        <td>${status}</td>
      </tr>`;
    })
    .join("");
  panel.innerHTML = `
    <section class="card">
      <h2>Route trace (ICMP via Windows tracert)</h2>
      <label>Target
        <input id="routeTarget" list="routeTargets" value="${routeTarget}" />
        <datalist id="routeTargets">${options}</datalist>
      </label>
      <label class="chk"><input type="checkbox" id="usePathping" /> Also run pathping (slow, extra hop loss hints)</label>
      <button id="runTrace" class="primary">Run traceroute</button>
      <p class="muted">ICMP path may differ from UDP/TCP game traffic. A hop showing * means that router often ignores traceroute — not proof packets drop to the game server.</p>
      ${
        lastRouteTrace
          ? `<p><strong>Reached destination:</strong> ${lastRouteTrace.reached_destination ? "yes" : "no"} · <strong>ASN path:</strong> ${lastRouteTrace.fingerprint_asn}</p>
             <p class="mono"><strong>IP path:</strong> ${lastRouteTrace.fingerprint_raw}</p>
             <table><thead><tr><th>#</th><th>IP</th><th>rDNS</th><th>ASN</th><th>Org</th><th>RTT / status</th></tr></thead><tbody>${hopRows || `<tr><td colspan="6">No hops parsed.</td></tr>`}</tbody></table>`
          : ""
      }
      ${lastRouteTrace?.error ? `<p class="warn">${lastRouteTrace.error}</p>` : ""}
      <h3>Saved routes</h3>
      <pre id="routeHistory" class="mono">Loading…</pre>
    </section>`;
  void invoke<RouteSummary[]>("list_recent_routes", { limit: 12 }).then((rows) => {
    const el = document.querySelector<HTMLPreElement>("#routeHistory");
    if (!el) return;
    el.textContent =
      rows.length === 0
        ? "No saved routes yet."
        : rows
            .map(
              (r) =>
                `#${r.id} ${r.probed_at} ${r.target_ip} hops=${r.hop_count} reached=${r.reached_destination} ASN: ${r.fingerprint_asn ?? "—"}`,
            )
            .join("\n");
  });
  document.querySelector<HTMLButtonElement>("#runTrace")!.onclick = async () => {
    routeTarget = (document.querySelector<HTMLInputElement>("#routeTarget")!.value || "1.1.1.1").trim();
    const usePathping = (document.querySelector<HTMLInputElement>("#usePathping")!).checked;
    const btn = document.querySelector<HTMLButtonElement>("#runTrace")!;
    btn.disabled = true;
    btn.textContent = usePathping ? "Running (may take minutes)…" : "Running…";
    try {
      lastRouteTrace = await invoke<RouteTraceResult>("run_traceroute", {
        target: routeTarget,
        usePathping,
        save: true,
        note: "ui trace",
      });
      renderRoutes(s);
    } finally {
      btn.disabled = false;
      btn.textContent = "Run traceroute";
    }
  };
}

function sparkline(values: (number | null)[], width = 320, height = 48): string {
  const pts = values.filter((v): v is number => v != null);
  if (pts.length < 2) return `<span class="muted">Need more samples</span>`;
  const min = Math.min(...pts);
  const max = Math.max(...pts);
  const span = max - min || 1;
  const coords = pts
    .map((v, i) => {
      const x = (i / (pts.length - 1)) * width;
      const y = height - ((v - min) / span) * (height - 4) - 2;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(" ");
  return `<svg width="${width}" height="${height}" class="spark"><polyline fill="none" stroke="#3d8bfd" stroke-width="2" points="${coords}" /></svg>`;
}

type StudyStatus = {
  active: boolean;
  study: {
    id: number;
    started_at: string;
    ends_at: string;
    interval_secs: number;
    status: string;
    target_ips: string[];
  } | null;
  samples_recorded: number;
  recent_events: { event_at: string; target_ip: string | null; event_type: string; detail: string }[];
};

type StudySampleRow = {
  sampled_at: string;
  target_ip: string;
  median_ms: number | null;
  p95_ms: number | null;
  packet_loss_pct: number;
  jitter_ms: number | null;
  route_fingerprint_asn: string | null;
  route_changed: boolean;
};

async function renderHistory(s: Phase1Snapshot) {
  const status = await invoke<StudyStatus>("get_monitor_study_status");
  const gameIps = s.connections
    .filter((c) => c.classification === "likely_gameplay" && c.remote_ip !== "*")
    .map((c) => c.remote_ip)
    .filter((ip, i, a) => a.indexOf(ip) === i);
  const compareIps = gameIps.length ? gameIps : uniqueRemoteIps(s.connections).slice(0, 3);
  let charts = "";
  if (status.study) {
    for (const ip of status.study.target_ips.slice(0, 4)) {
      const samples = await invoke<StudySampleRow[]>("get_monitor_study_samples", {
        studyId: status.study.id,
        targetIp: ip,
      });
      charts += `<div class="chart-block"><h4>${ip}</h4>
        <p>P50 ${sparkline(samples.map((x) => x.median_ms))}</p>
        <p>P95 ${sparkline(samples.map((x) => x.p95_ms))}</p>
        <p>Loss ${sparkline(samples.map((x) => x.packet_loss_pct))}</p>
      </div>`;
    }
  }
  const events = status.recent_events
    .map((e) => `${e.event_at} ${e.target_ip ?? ""} ${e.event_type}: ${e.detail}`)
    .join("\n");
  panel.innerHTML = `
    <section class="card">
      <h2>Time-of-day study</h2>
      <p class="muted">Runs while this app is open. Each interval: ICMP (20 probes) + tracert every 3rd cycle for route fingerprint.</p>
      <p><strong>Targets:</strong> ${compareIps.join(", ") || "—"}</p>
      <label>Duration
        <select id="studyDuration">
          <option value="30">30 minutes</option>
          <option value="60" selected>1 hour</option>
          <option value="240">4 hours</option>
          <option value="480">8 hours</option>
          <option value="1440">24 hours</option>
        </select>
      </label>
      <label>Interval
        <select id="studyInterval">
          <option value="1">1 min</option>
          <option value="2" selected>2 min</option>
          <option value="5">5 min</option>
          <option value="10">10 min</option>
        </select>
      </label>
      <label class="chk"><input type="checkbox" id="studyGateway" checked /> Include default gateway</label>
      ${
        status.active
          ? `<p><strong>Running</strong> study #${status.study?.id} · ${status.samples_recorded} samples · until ${status.study?.ends_at}</p>
             <button id="stopStudy" class="primary">Stop study</button>`
          : `<button id="startStudy" class="primary">Start study</button>`
      }
      <div class="charts">${charts}</div>
      <h3>Route / latency events</h3>
      <pre class="mono">${events || "No events yet."}</pre>
    </section>`;
  document.querySelector<HTMLButtonElement>("#startStudy")?.addEventListener("click", async () => {
    const durationMinutes = Number((document.querySelector<HTMLSelectElement>("#studyDuration")!).value);
    const intervalMinutes = Number((document.querySelector<HTMLSelectElement>("#studyInterval")!).value);
    const includeGateway = (document.querySelector<HTMLInputElement>("#studyGateway")!).checked;
    await invoke("start_monitor_study", {
      durationMinutes,
      intervalMinutes,
      targetIps: compareIps,
      includeGateway,
    });
    if (historyPollTimer == null) {
      historyPollTimer = window.setInterval(() => {
        if (activeTab === "history" && lastSnapshot) void renderHistory(lastSnapshot);
      }, 8000);
    }
    if (lastSnapshot) await renderHistory(lastSnapshot);
  });
  document.querySelector<HTMLButtonElement>("#stopStudy")?.addEventListener("click", async () => {
    await invoke("stop_monitor_study");
    if (lastSnapshot) await renderHistory(lastSnapshot);
  });
}

type RelayAnalysisReport = {
  disclaimer: string;
  game_targets: { label: string; ip: string; median_ms: number | null; p95_ms: number | null }[];
  reference_baselines: { label: string; ip: string; median_ms: number | null; p95_ms: number | null }[];
  opportunities: { opportunity_type: string; location_label: string; summary: string; evidence: string; confidence: string }[];
  limitations: string[];
  methodology: { delta_threshold_ms: number; p95_gap_threshold_ms: number; icmp_note: string };
};

async function renderOpportunities(s: Phase1Snapshot) {
  const gameIps = s.connections
    .filter((c) => c.classification === "likely_gameplay" && c.remote_ip !== "*")
    .map((c) => c.remote_ip)
    .filter((ip, i, a) => a.indexOf(ip) === i);
  const fallback = uniqueRemoteIps(s.connections).slice(0, 2);
  const ips = gameIps.length ? gameIps : fallback;
  panel.innerHTML = `<section class="card"><p>Analyzing saved probes…</p></section>`;
  const report = await invoke<RelayAnalysisReport>("analyze_relay_opportunities_cmd", { gameIps: ips });
  const gameRows = report.game_targets
    .map(
      (g) =>
        `<tr><td>${g.label}</td><td>${g.ip}</td><td>${fmtMs(g.median_ms)}</td><td>${fmtMs(g.p95_ms)}</td></tr>`,
    )
    .join("");
  const refRows = report.reference_baselines
    .map(
      (r) =>
        `<tr><td>${r.label}</td><td>${r.ip}</td><td>${fmtMs(r.median_ms)}</td><td>${fmtMs(r.p95_ms)}</td></tr>`,
    )
    .join("");
  const ops = report.opportunities
    .map(
      (o) =>
        `<li><span class="tag">${o.confidence}</span> <strong>${o.location_label}</strong> (${o.opportunity_type}): ${o.summary}<br/><span class="muted">${o.evidence}</span></li>`,
    )
    .join("");
  panel.innerHTML = `
    <section class="card">
      <h2>ExitLag-style opportunities (hypotheses only)</h2>
      <p class="muted">${report.disclaimer}</p>
      <p class="muted">${report.methodology.icmp_note} Flag if P50 gap ≥ ${report.methodology.delta_threshold_ms} ms or P95 gap ≥ ${report.methodology.p95_gap_threshold_ms} ms vs references.</p>
      <ul class="muted">${report.limitations.map((l) => `<li>${l}</li>`).join("")}</ul>
      <h3>Your game targets</h3>
      <table><thead><tr><th>Label</th><th>IP</th><th>P50</th><th>P95</th></tr></thead><tbody>${gameRows || `<tr><td colspan="4">Tag likely gameplay + run latency probes.</td></tr>`}</tbody></table>
      <h3>AWS reference baselines (saved)</h3>
      <table><thead><tr><th>Label</th><th>IP</th><th>P50</th><th>P95</th></tr></thead><tbody>${refRows || `<tr><td colspan="4">Run AWS Comparison first.</td></tr>`}</tbody></table>
      <h3>Opportunities</h3>
      <ul>${ops || `<li class="muted">No opportunities met thresholds with current data.</li>`}</ul>
    </section>`;
}

function renderComparison(s: Phase1Snapshot) {
  const gameIps = s.connections
    .filter((c) => c.classification === "likely_gameplay" && c.remote_ip !== "*")
    .map((c) => c.remote_ip)
    .filter((ip, i, a) => a.indexOf(ip) === i);
  const fallbackGame = uniqueRemoteIps(s.connections).slice(0, 2);
  const compareIps = gameIps.length ? gameIps : fallbackGame;
  const refRows =
    lastComparison?.references
      .map((r) => {
        const lat = r.latency;
        const kind =
          r.reference.kind === "aws_regional_reference" ? "AWS reference" : "Neutral";
        return `<tr>
          <td>${kind}</td>
          <td>${r.reference.label}</td>
          <td class="mono">${r.reference.resolved_ipv4 ?? "—"}</td>
          <td>${r.asn ?? "—"}</td>
          <td>${fmtMs(lat.median_ms)}</td>
          <td>${fmtMs(lat.p95_ms)}</td>
          <td>${lat.packet_loss_pct.toFixed(1)}%</td>
          <td class="mono">${r.route_fingerprint_asn ?? "—"}</td>
        </tr>`;
      })
      .join("") ?? "";
  const gameRows =
    lastComparison?.game_endpoints
      .map(
        (g) => `<tr>
        <td>T&amp;L socket (your selection)</td>
        <td>${g.ip}</td>
        <td>${fmtMs(g.latency.median_ms)}</td>
        <td>${fmtMs(g.latency.p95_ms)}</td>
        <td>${g.latency.packet_loss_pct.toFixed(1)}%</td>
      </tr>`,
      )
      .join("") ?? "";
  const hints =
    lastComparison?.hints.map((h) => `<li><strong>${h.summary}</strong><br/><span class="muted">${h.evidence}</span></li>`).join("") ?? "";
  panel.innerHTML = `
    <section class="card">
      <h2>AWS / neutral reference comparison</h2>
      <p class="muted">References are public AWS regional endpoints (DNS-resolved) and neutral DNS — <em>not</em> Throne and Liberty servers.</p>
      <p><strong>Game IPs for compare:</strong> ${compareIps.length ? compareIps.join(", ") : "none — tag a connection as likely gameplay or ensure sockets are visible"}</p>
      <label>Probes per target <input id="cmpProbes" type="number" min="10" max="200" value="50" /></label>
      <label class="chk"><input type="checkbox" id="cmpTrace" /> Include traceroute per target (very slow)</label>
      <button id="runComparison" class="primary">Run comparison</button>
      ${lastComparison ? `<p class="muted">${lastComparison.disclaimer}</p>` : ""}
      ${hints ? `<ul>${hints}</ul>` : ""}
      <h3>References</h3>
      <table><thead><tr><th>Type</th><th>Label</th><th>Resolved IP</th><th>ASN</th><th>P50</th><th>P95</th><th>Loss</th><th>ASN path</th></tr></thead><tbody>${refRows || `<tr><td colspan="8">Run comparison to populate.</td></tr>`}</tbody></table>
      <h3>T&amp;L / selected game IPs</h3>
      <table><thead><tr><th>Type</th><th>IP</th><th>P50</th><th>P95</th><th>Loss</th></tr></thead><tbody>${gameRows || `<tr><td colspan="5">—</td></tr>`}</tbody></table>
    </section>`;
  document.querySelector<HTMLButtonElement>("#runComparison")!.onclick = async () => {
    const probes = Number(document.querySelector<HTMLInputElement>("#cmpProbes")!.value || 50);
    const includeTraceroute = (document.querySelector<HTMLInputElement>("#cmpTrace")!).checked;
    const btn = document.querySelector<HTMLButtonElement>("#runComparison")!;
    btn.disabled = true;
    btn.textContent = "Running…";
    try {
      lastComparison = await invoke<ComparisonReport>("run_reference_comparison", {
        gameIps: compareIps,
        probes,
        includeTraceroute,
      });
      renderComparison(s);
    } finally {
      btn.disabled = false;
      btn.textContent = "Run comparison";
    }
  };
}

function renderReports(_s: Phase1Snapshot) {
  panel.innerHTML = `
    <section class="card">
      <h2>Export diagnostic report</h2>
      <p class="muted">Aggregates latest session connections, saved probes, routes, and monitor studies from SQLite. No packet payloads.</p>
      <label class="chk"><input type="checkbox" id="reportIncludePublicIp" /> Include full public IP in export (default: masked)</label>
      <div class="header-actions" style="margin-top:0.75rem">
        <button id="exportJson" class="primary">Save JSON</button>
        <button id="exportCsv">Save CSV</button>
        <button id="exportHtml">Save HTML</button>
        <button id="previewHtml">Preview HTML</button>
      </div>
      <p id="exportStatus" class="muted"></p>
      <iframe id="reportPreview" title="Report preview" style="width:100%;height:420px;border:1px solid #2a3544;margin-top:1rem;background:#fff;display:none"></iframe>
    </section>`;
  const status = document.querySelector<HTMLParagraphElement>("#exportStatus")!;
  const include = () => (document.querySelector<HTMLInputElement>("#reportIncludePublicIp")!).checked;
  const doExport = async (format: string) => {
    status.textContent = "Building report…";
    const r = await invoke<{ saved: boolean; path: string | null }>("export_diagnostic_report", {
      format,
      includePublicIp: include(),
    });
    status.textContent = r.saved && r.path ? `Saved: ${r.path}` : "Export cancelled.";
  };
  document.querySelector("#exportJson")!.addEventListener("click", () => void doExport("json"));
  document.querySelector("#exportCsv")!.addEventListener("click", () => void doExport("csv"));
  document.querySelector("#exportHtml")!.addEventListener("click", () => void doExport("html"));
  document.querySelector("#previewHtml")!.addEventListener("click", async () => {
    status.textContent = "Rendering preview…";
    const html = await invoke<string>("preview_diagnostic_report_html", { includePublicIp: include() });
    const frame = document.querySelector<HTMLIFrameElement>("#reportPreview")!;
    frame.style.display = "block";
    frame.srcdoc = html;
    status.textContent = "Preview below (not saved to disk).";
  });
}

function renderSettings(s: Phase1Snapshot) {

  panel.innerHTML = `

    <section class="card">

      <h2>Settings</h2>

      <p>Public geo/ISP source: ${s.public.source}</p>

      <p>SQLite: <code class="mono">${s.db_path}</code></p>

      <p>Data is approximate. No packet payloads are stored.</p>

      <p>See <code>docs/API_CHOICES.md</code> for Windows API and admin requirements.</p>

    </section>`;

}



let activeTab = "live";



async function refreshConnectionsOnly() {

  const tick = await invoke<LiveTick>("poll_live_tick");

  lastConnections = tick.connections;

  if (lastSnapshot) {

    lastSnapshot.connections = tick.connections;

    lastSnapshot.connections_error = tick.connections_error;

    lastSnapshot.active_session_id = tick.active_session_id;

    lastSnapshot.session_connection_rows = tick.session_connection_rows;

  }

  if (activeTab === "connections") {

    panel.innerHTML = renderConnectionsTable(tick.connections, tick.selected_pid);

    wireTagSelects(tick.connections);

  }

  if (activeTab === "live" && lastSnapshot) renderLive(lastSnapshot);

}



async function refreshFull(measureGateway = false, refreshProcesses = false) {

  const snap = await invoke<Phase1Snapshot>("collect_phase1_snapshot", {

    measureGateway,

    refreshProcesses,

    refreshLocal: refreshProcesses,

  });

  lastSnapshot = snap;

  lastConnections = snap.connections;

  selectedPid = snap.selected_pid;

  await renderActiveTab(snap);

}



async function renderActiveTab(snap: Phase1Snapshot) {

  switch (activeTab) {

    case "live":

      renderLive(snap);

      break;

    case "connections":

      renderConnections(snap);

      break;

    case "processes":

      renderProcesses(snap);

      break;

    case "latency":

      renderLatency(snap);

      break;

    case "asn":

      await renderAsn(snap);

      break;

    case "routes":

      renderRoutes(snap);

      break;

    case "comparison":

      renderComparison(snap);

      break;

    case "opportunities":

      await renderOpportunities(snap);

      break;

    case "history":

      await renderHistory(snap);

      break;

    case "reports":

      renderReports(snap);

      break;

    case "settings":

      renderSettings(snap);

      break;

  }

}



document.querySelectorAll(".tab[data-tab]").forEach((btn) => {

  btn.addEventListener("click", () => {

    document.querySelectorAll(".tab").forEach((t) => t.classList.remove("active"));

    btn.classList.add("active");

    activeTab = (btn as HTMLElement).dataset.tab!;

    if ((activeTab === "settings" || activeTab === "reports") && lastSnapshot) {

      void renderActiveTab(lastSnapshot);

      return;

    }

    void refreshFull(false, activeTab === "processes");

  });

});



includePublicIp.addEventListener("change", async () => {

  await invoke("set_include_public_ip", { include: includePublicIp.checked });

  await refreshFull(false, true);

});



startBtn.addEventListener("click", async () => {

  if (monitorTimer != null) {

    window.clearInterval(monitorTimer);

    monitorTimer = null;

    if (fullRefreshTimer != null) {

      window.clearInterval(fullRefreshTimer);

      fullRefreshTimer = null;

    }

    await invoke("stop_monitoring_session");

    startBtn.textContent = "Start monitoring";

    return;

  }

  await invoke("start_monitoring_session");

  await refreshFull(true, false);

  monitorTimer = window.setInterval(() => void refreshConnectionsOnly(), 3000);

  fullRefreshTimer = window.setInterval(() => void refreshFull(false, false), 60000);

  startBtn.textContent = "Stop monitoring";

});



void refreshFull(false, false);

