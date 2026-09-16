use rusqlite::{params, Connection};
use std::path::PathBuf;
use std::sync::Mutex;

pub const SCHEMA_VERSION: i32 = 4;
const IP_INTEL_CACHE_TTL_SECS: i64 = 7 * 24 * 3600;

static DB: Mutex<Option<Connection>> = Mutex::new(None);

pub fn db_path() -> PathBuf {
    let base = std::env::var("LOCALAPPDATA")
        .map(PathBuf::from)
        .unwrap_or_else(|_| PathBuf::from("."));
    base.join("TL Route Investigator").join("data.db")
}

pub fn with_db<F, T>(f: F) -> Result<T, String>
where
    F: FnOnce(&Connection) -> Result<T, String>,
{
    let mut guard = DB.lock().map_err(|_| "db lock poisoned")?;
    if guard.is_none() {
        let path = db_path();
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        }
        let conn = Connection::open(&path).map_err(|e| e.to_string())?;
        migrate(&conn)?;
        *guard = Some(conn);
    }
    let conn = guard.as_ref().ok_or("db not open")?;
    f(conn)
}

fn migrate(conn: &Connection) -> Result<(), String> {
    conn.execute_batch(
        r#"
CREATE TABLE IF NOT EXISTS schema_version (
  version INTEGER NOT NULL PRIMARY KEY
);
INSERT OR IGNORE INTO schema_version (version) VALUES (0);

CREATE TABLE IF NOT EXISTS sessions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  started_at TEXT NOT NULL,
  ended_at TEXT,
  selected_pid INTEGER,
  process_name TEXT,
  notes TEXT
);

CREATE TABLE IF NOT EXISTS connections (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id INTEGER NOT NULL,
  conn_key TEXT NOT NULL,
  protocol TEXT NOT NULL,
  local_ip TEXT NOT NULL,
  local_port INTEGER NOT NULL,
  remote_ip TEXT NOT NULL,
  remote_port INTEGER NOT NULL,
  state TEXT,
  pid INTEGER NOT NULL,
  first_seen TEXT NOT NULL,
  last_seen TEXT NOT NULL,
  observations INTEGER NOT NULL,
  FOREIGN KEY (session_id) REFERENCES sessions(id),
  UNIQUE (session_id, conn_key)
);

CREATE TABLE IF NOT EXISTS endpoints (
  remote_ip TEXT NOT NULL,
  remote_port INTEGER NOT NULL,
  protocol TEXT NOT NULL,
  user_tag TEXT NOT NULL DEFAULT 'unknown',
  updated_at TEXT NOT NULL,
  PRIMARY KEY (remote_ip, remote_port, protocol)
);

CREATE INDEX IF NOT EXISTS idx_connections_session ON connections(session_id);
"#,
    )
    .map_err(|e| e.to_string())?;

    let version: i32 = conn
        .query_row(
            "SELECT COALESCE(MAX(version), 0) FROM schema_version",
            [],
            |row| row.get(0),
        )
        .map_err(|e| e.to_string())?;

    if version < 2 {
        conn.execute_batch(
            r#"
CREATE TABLE IF NOT EXISTS ip_intel_cache (
  ip TEXT PRIMARY KEY,
  reverse_dns TEXT,
  asn TEXT,
  isp TEXT,
  org TEXT,
  country TEXT,
  region TEXT,
  city TEXT,
  aws_region_hint TEXT,
  source TEXT NOT NULL,
  approximate INTEGER NOT NULL DEFAULT 1,
  fetched_at TEXT NOT NULL,
  lookup_error TEXT
);

CREATE TABLE IF NOT EXISTS measurements (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id INTEGER,
  target_ip TEXT NOT NULL,
  probed_at TEXT NOT NULL,
  probes INTEGER NOT NULL,
  received INTEGER NOT NULL,
  packet_loss_pct REAL NOT NULL,
  median_ms REAL,
  p95_ms REAL,
  p99_ms REAL,
  max_ms REAL,
  jitter_ms REAL,
  method TEXT NOT NULL DEFAULT 'icmp',
  note TEXT,
  FOREIGN KEY (session_id) REFERENCES sessions(id)
);

CREATE INDEX IF NOT EXISTS idx_measurements_session ON measurements(session_id);
CREATE INDEX IF NOT EXISTS idx_measurements_target ON measurements(target_ip);
"#,
        )
        .map_err(|e| e.to_string())?;
        conn.execute(
            "INSERT OR REPLACE INTO schema_version (version) VALUES (2)",
            [],
        )
        .map_err(|e| e.to_string())?;
    }

    let version: i32 = conn
        .query_row(
            "SELECT COALESCE(MAX(version), 0) FROM schema_version",
            [],
            |row| row.get(0),
        )
        .map_err(|e| e.to_string())?;

    if version < 3 {
        conn.execute_batch(
            r#"
CREATE TABLE IF NOT EXISTS routes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id INTEGER,
  target_ip TEXT NOT NULL,
  probed_at TEXT NOT NULL,
  method TEXT NOT NULL,
  reached_destination INTEGER NOT NULL DEFAULT 0,
  hop_count INTEGER NOT NULL DEFAULT 0,
  fingerprint_raw TEXT,
  fingerprint_asn TEXT,
  note TEXT,
  FOREIGN KEY (session_id) REFERENCES sessions(id)
);

CREATE TABLE IF NOT EXISTS route_hops (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  route_id INTEGER NOT NULL,
  hop_number INTEGER NOT NULL,
  hop_ip TEXT,
  hostname TEXT,
  rtt_median_ms REAL,
  hop_silent INTEGER NOT NULL DEFAULT 0,
  asn TEXT,
  org TEXT,
  FOREIGN KEY (route_id) REFERENCES routes(id)
);

CREATE INDEX IF NOT EXISTS idx_routes_target ON routes(target_ip);
CREATE INDEX IF NOT EXISTS idx_route_hops_route ON route_hops(route_id);
"#,
        )
        .map_err(|e| e.to_string())?;
        conn.execute(
            "INSERT OR REPLACE INTO schema_version (version) VALUES (3)",
            [],
        )
        .map_err(|e| e.to_string())?;
    }

    let version: i32 = conn
        .query_row(
            "SELECT COALESCE(MAX(version), 0) FROM schema_version",
            [],
            |row| row.get(0),
        )
        .map_err(|e| e.to_string())?;

    if version < 4 {
        conn.execute_batch(
            r#"
CREATE TABLE IF NOT EXISTS monitor_studies (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id INTEGER,
  started_at TEXT NOT NULL,
  ends_at TEXT NOT NULL,
  interval_secs INTEGER NOT NULL,
  status TEXT NOT NULL,
  target_ips_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS monitor_samples (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  study_id INTEGER NOT NULL,
  sampled_at TEXT NOT NULL,
  target_ip TEXT NOT NULL,
  median_ms REAL,
  p95_ms REAL,
  packet_loss_pct REAL NOT NULL,
  jitter_ms REAL,
  route_fingerprint_asn TEXT,
  route_changed INTEGER NOT NULL DEFAULT 0,
  FOREIGN KEY (study_id) REFERENCES monitor_studies(id)
);

CREATE TABLE IF NOT EXISTS monitor_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  study_id INTEGER NOT NULL,
  event_at TEXT NOT NULL,
  target_ip TEXT,
  event_type TEXT NOT NULL,
  detail TEXT NOT NULL,
  FOREIGN KEY (study_id) REFERENCES monitor_studies(id)
);

CREATE INDEX IF NOT EXISTS idx_monitor_samples_study ON monitor_samples(study_id);
CREATE INDEX IF NOT EXISTS idx_monitor_events_study ON monitor_events(study_id);
"#,
        )
        .map_err(|e| e.to_string())?;
        conn.execute(
            "INSERT OR REPLACE INTO schema_version (version) VALUES (4)",
            [],
        )
        .map_err(|e| e.to_string())?;
    }
    Ok(())
}

pub fn ip_intel_cache_stale(fetched_at: &str) -> bool {
    let Ok(dt) = chrono::DateTime::parse_from_rfc3339(fetched_at) else {
        return true;
    };
    let age = chrono::Utc::now().signed_duration_since(dt.with_timezone(&chrono::Utc));
    age.num_seconds() > IP_INTEL_CACHE_TTL_SECS
}

pub fn get_ip_intel_cached_many(ips: &[String]) -> Result<Vec<crate::network::ip_intel::IpIntelRecord>, String> {
    let mut out = Vec::new();
    for ip in ips {
        if let Some(rec) = get_ip_intel_cached(ip)? {
            out.push(rec);
        }
    }
    Ok(out)
}

pub fn get_ip_intel_cached(ip: &str) -> Result<Option<crate::network::ip_intel::IpIntelRecord>, String> {
    with_db(|conn| {
        let mut stmt = conn
            .prepare(
                r#"SELECT ip, reverse_dns, asn, isp, org, country, region, city,
                   aws_region_hint, source, approximate, fetched_at, lookup_error
                   FROM ip_intel_cache WHERE ip = ?1"#,
            )
            .map_err(|e| e.to_string())?;
        let mut rows = stmt.query(params![ip]).map_err(|e| e.to_string())?;
        if let Some(row) = rows.next().map_err(|e| e.to_string())? {
            Ok(Some(row_to_intel(row)?))
        } else {
            Ok(None)
        }
    })
}

pub fn upsert_ip_intel_cache(rec: &crate::network::ip_intel::IpIntelRecord) -> Result<(), String> {
    with_db(|conn| {
        conn.execute(
            r#"
INSERT INTO ip_intel_cache (
  ip, reverse_dns, asn, isp, org, country, region, city, aws_region_hint,
  source, approximate, fetched_at, lookup_error
) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13)
ON CONFLICT(ip) DO UPDATE SET
  reverse_dns=excluded.reverse_dns, asn=excluded.asn, isp=excluded.isp, org=excluded.org,
  country=excluded.country, region=excluded.region, city=excluded.city,
  aws_region_hint=excluded.aws_region_hint, source=excluded.source,
  approximate=excluded.approximate, fetched_at=excluded.fetched_at, lookup_error=excluded.lookup_error
"#,
            params![
                rec.ip,
                rec.reverse_dns,
                rec.asn,
                rec.isp,
                rec.org,
                rec.country,
                rec.region,
                rec.city,
                rec.aws_region_hint,
                rec.source,
                if rec.approximate { 1 } else { 0 },
                rec.fetched_at,
                rec.lookup_error,
            ],
        )
        .map_err(|e| e.to_string())?;
        Ok(())
    })
}

fn row_to_intel(row: &rusqlite::Row<'_>) -> Result<crate::network::ip_intel::IpIntelRecord, String> {
    Ok(crate::network::ip_intel::IpIntelRecord {
        ip: row.get(0).map_err(|e| e.to_string())?,
        reverse_dns: row.get(1).map_err(|e| e.to_string())?,
        asn: row.get(2).map_err(|e| e.to_string())?,
        isp: row.get(3).map_err(|e| e.to_string())?,
        org: row.get(4).map_err(|e| e.to_string())?,
        country: row.get(5).map_err(|e| e.to_string())?,
        region: row.get(6).map_err(|e| e.to_string())?,
        city: row.get(7).map_err(|e| e.to_string())?,
        aws_region_hint: row.get(8).map_err(|e| e.to_string())?,
        source: row.get(9).map_err(|e| e.to_string())?,
        approximate: row.get::<_, i32>(10).map_err(|e| e.to_string())? != 0,
        fetched_at: row.get(11).map_err(|e| e.to_string())?,
        lookup_error: row.get(12).map_err(|e| e.to_string())?,
        from_cache: true,
    })
}

pub fn insert_measurement(
    session_id: Option<i64>,
    result: &crate::probe::latency::LatencyProbeResult,
    note: Option<&str>,
) -> Result<i64, String> {
    let probed_at = chrono::Utc::now().to_rfc3339();
    with_db(|conn| {
        conn.execute(
            r#"
INSERT INTO measurements (
  session_id, target_ip, probed_at, probes, received, packet_loss_pct,
  median_ms, p95_ms, p99_ms, max_ms, jitter_ms, method, note
) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,'icmp',?12)
"#,
            params![
                session_id,
                result.target,
                probed_at,
                result.probes,
                result.received,
                result.packet_loss_pct,
                result.median_ms,
                result.p95_ms,
                result.p99_ms,
                result.max_ms,
                result.jitter_ms,
                note,
            ],
        )
        .map_err(|e| e.to_string())?;
        Ok(conn.last_insert_rowid())
    })
}

#[derive(Debug, Clone, serde::Serialize)]
pub struct MeasurementSummary {
    pub id: i64,
    pub target_ip: String,
    pub probed_at: String,
    pub median_ms: Option<f64>,
    pub p95_ms: Option<f64>,
    pub packet_loss_pct: f64,
    pub note: Option<String>,
}

pub fn latest_measurement_per_target(limit: u32) -> Result<Vec<MeasurementSummary>, String> {
    with_db(|conn| {
        let mut stmt = conn
            .prepare(
                r#"SELECT id, target_ip, probed_at, median_ms, p95_ms, packet_loss_pct, note
                   FROM measurements m
                   WHERE id = (
                     SELECT id FROM measurements m2 WHERE m2.target_ip = m.target_ip ORDER BY id DESC LIMIT 1
                   )
                   ORDER BY id DESC LIMIT ?1"#,
            )
            .map_err(|e| e.to_string())?;
        let rows = stmt
            .query_map(params![limit], |row| {
                Ok(MeasurementSummary {
                    id: row.get(0)?,
                    target_ip: row.get(1)?,
                    probed_at: row.get(2)?,
                    median_ms: row.get(3)?,
                    p95_ms: row.get(4)?,
                    packet_loss_pct: row.get(5)?,
                    note: row.get(6)?,
                })
            })
            .map_err(|e| e.to_string())?;
        let mut out = Vec::new();
        for r in rows {
            out.push(r.map_err(|e| e.to_string())?);
        }
        Ok(out)
    })
}

pub fn recent_measurements(limit: u32) -> Result<Vec<MeasurementSummary>, String> {
    with_db(|conn| {
        let mut stmt = conn
            .prepare(
                r#"SELECT id, target_ip, probed_at, median_ms, p95_ms, packet_loss_pct, note
                   FROM measurements ORDER BY id DESC LIMIT ?1"#,
            )
            .map_err(|e| e.to_string())?;
        let rows = stmt
            .query_map(params![limit], |row| {
                Ok(MeasurementSummary {
                    id: row.get(0)?,
                    target_ip: row.get(1)?,
                    probed_at: row.get(2)?,
                    median_ms: row.get(3)?,
                    p95_ms: row.get(4)?,
                    packet_loss_pct: row.get(5)?,
                    note: row.get(6)?,
                })
            })
            .map_err(|e| e.to_string())?;
        let mut out = Vec::new();
        for r in rows {
            out.push(r.map_err(|e| e.to_string())?);
        }
        Ok(out)
    })
}

pub fn start_session(pid: Option<u32>, process_name: Option<&str>) -> Result<i64, String> {
    let started = chrono::Utc::now().to_rfc3339();
    with_db(|conn| {
        conn.execute(
            "INSERT INTO sessions (started_at, selected_pid, process_name) VALUES (?1, ?2, ?3)",
            params![started, pid, process_name],
        )
        .map_err(|e| e.to_string())?;
        Ok(conn.last_insert_rowid())
    })
}

pub fn end_session(session_id: i64) -> Result<(), String> {
    let ended = chrono::Utc::now().to_rfc3339();
    with_db(|conn| {
        conn.execute(
            "UPDATE sessions SET ended_at = ?1 WHERE id = ?2",
            params![ended, session_id],
        )
        .map_err(|e| e.to_string())?;
        Ok(())
    })
}

pub fn upsert_connections(session_id: i64, rows: &[crate::connections::ConnectionRow]) -> Result<(), String> {
    with_db(|conn| {
        for row in rows {
            conn.execute(
                r#"
INSERT INTO connections (
  session_id, conn_key, protocol, local_ip, local_port, remote_ip, remote_port,
  state, pid, first_seen, last_seen, observations
) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12)
ON CONFLICT(session_id, conn_key) DO UPDATE SET
  last_seen = excluded.last_seen,
  observations = excluded.observations,
  state = excluded.state
"#,
                params![
                    session_id,
                    row.key,
                    protocol_str(&row.protocol),
                    row.local_ip,
                    row.local_port,
                    row.remote_ip,
                    row.remote_port,
                    row.state,
                    row.pid,
                    row.first_seen,
                    row.last_seen,
                    row.observations,
                ],
            )
            .map_err(|e| e.to_string())?;
        }
        Ok(())
    })
}

fn protocol_str(p: &crate::connections::ProtocolKind) -> &'static str {
    match p {
        crate::connections::ProtocolKind::Tcp => "tcp",
        crate::connections::ProtocolKind::Udp => "udp",
    }
}

pub fn load_endpoint_tags() -> Result<std::collections::HashMap<String, String>, String> {
    with_db(|conn| {
        let mut stmt = conn
            .prepare("SELECT remote_ip, remote_port, protocol, user_tag FROM endpoints")
            .map_err(|e| e.to_string())?;
        let mut map = std::collections::HashMap::new();
        let rows = stmt
            .query_map([], |row| {
                Ok((
                    row.get::<_, String>(0)?,
                    row.get::<_, u16>(1)?,
                    row.get::<_, String>(2)?,
                    row.get::<_, String>(3)?,
                ))
            })
            .map_err(|e| e.to_string())?;
        for r in rows {
            let (ip, port, proto, tag) = r.map_err(|e| e.to_string())?;
            let key = format!("{proto}|{ip}:{port}");
            map.insert(key, tag);
        }
        Ok(map)
    })
}

pub fn set_endpoint_tag(
    remote_ip: &str,
    remote_port: u16,
    protocol: &str,
    user_tag: &str,
) -> Result<(), String> {
    let updated = chrono::Utc::now().to_rfc3339();
    with_db(|conn| {
        conn.execute(
            r#"
INSERT INTO endpoints (remote_ip, remote_port, protocol, user_tag, updated_at)
VALUES (?1, ?2, ?3, ?4, ?5)
ON CONFLICT(remote_ip, remote_port, protocol) DO UPDATE SET
  user_tag = excluded.user_tag,
  updated_at = excluded.updated_at
"#,
            params![remote_ip, remote_port, protocol, user_tag, updated],
        )
        .map_err(|e| e.to_string())?;
        Ok(())
    })
}

pub fn session_connection_count(session_id: i64) -> Result<u64, String> {
    with_db(|conn| {
        let n: i64 = conn
            .query_row(
                "SELECT COUNT(*) FROM connections WHERE session_id = ?1",
                params![session_id],
                |row| row.get(0),
            )
            .map_err(|e| e.to_string())?;
        Ok(n as u64)
    })
}

pub fn insert_route_trace(
    session_id: Option<i64>,
    trace: &crate::probe::traceroute::RouteTraceResult,
    note: Option<&str>,
) -> Result<i64, String> {
    with_db(|conn| {
        conn.execute(
            r#"
INSERT INTO routes (
  session_id, target_ip, probed_at, method, reached_destination, hop_count,
  fingerprint_raw, fingerprint_asn, note
) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9)
"#,
            params![
                session_id,
                trace.target,
                trace.probed_at,
                trace.method,
                if trace.reached_destination { 1 } else { 0 },
                trace.hops.len() as i64,
                trace.fingerprint_raw,
                trace.fingerprint_asn,
                note,
            ],
        )
        .map_err(|e| e.to_string())?;
        let route_id = conn.last_insert_rowid();
        for h in &trace.hops {
            conn.execute(
                r#"
INSERT INTO route_hops (
  route_id, hop_number, hop_ip, hostname, rtt_median_ms, hop_silent, asn, org
) VALUES (?1,?2,?3,?4,?5,?6,?7,?8)
"#,
                params![
                    route_id,
                    h.hop,
                    h.hop_ip,
                    h.hostname,
                    h.rtt_median_ms,
                    if h.hop_silent { 1 } else { 0 },
                    h.asn,
                    h.org,
                ],
            )
            .map_err(|e| e.to_string())?;
        }
        Ok(route_id)
    })
}

#[derive(Debug, Clone, serde::Serialize)]
pub struct RouteSummary {
    pub id: i64,
    pub target_ip: String,
    pub probed_at: String,
    pub method: String,
    pub reached_destination: bool,
    pub hop_count: u32,
    pub fingerprint_asn: Option<String>,
}

pub fn recent_routes(limit: u32) -> Result<Vec<RouteSummary>, String> {
    with_db(|conn| {
        let mut stmt = conn
            .prepare(
                r#"SELECT id, target_ip, probed_at, method, reached_destination, hop_count, fingerprint_asn
                   FROM routes ORDER BY id DESC LIMIT ?1"#,
            )
            .map_err(|e| e.to_string())?;
        let rows = stmt
            .query_map(params![limit], |row| {
                Ok(RouteSummary {
                    id: row.get(0)?,
                    target_ip: row.get(1)?,
                    probed_at: row.get(2)?,
                    method: row.get(3)?,
                    reached_destination: row.get::<_, i32>(4)? != 0,
                    hop_count: row.get::<_, i32>(5)? as u32,
                    fingerprint_asn: row.get(6)?,
                })
            })
            .map_err(|e| e.to_string())?;
        let mut out = Vec::new();
        for r in rows {
            out.push(r.map_err(|e| e.to_string())?);
        }
        Ok(out)
    })
}

#[derive(Debug, Clone, serde::Serialize)]
pub struct RouteDetail {
    pub summary: RouteSummary,
    pub fingerprint_raw: Option<String>,
    pub hops: Vec<crate::probe::traceroute::RouteHop>,
}

pub fn route_detail(route_id: i64) -> Result<RouteDetail, String> {
    with_db(|conn| {
        let summary = conn.query_row(
            r#"SELECT id, target_ip, probed_at, method, reached_destination, hop_count, fingerprint_asn, fingerprint_raw
               FROM routes WHERE id = ?1"#,
            params![route_id],
            |row| {
                Ok((
                    RouteSummary {
                        id: row.get(0)?,
                        target_ip: row.get(1)?,
                        probed_at: row.get(2)?,
                        method: row.get(3)?,
                        reached_destination: row.get::<_, i32>(4)? != 0,
                        hop_count: row.get::<_, i32>(5)? as u32,
                        fingerprint_asn: row.get(6)?,
                    },
                    row.get::<_, Option<String>>(7)?,
                ))
            },
        ).map_err(|e| e.to_string())?;
        let mut stmt = conn
            .prepare(
                r#"SELECT hop_number, hop_ip, hostname, rtt_median_ms, hop_silent, asn, org
                   FROM route_hops WHERE route_id = ?1 ORDER BY hop_number"#,
            )
            .map_err(|e| e.to_string())?;
        let rows = stmt
            .query_map(params![route_id], |row| {
                Ok(crate::probe::traceroute::RouteHop {
                    hop: row.get::<_, i32>(0)? as u32,
                    hop_ip: row.get(1)?,
                    hostname: row.get(2)?,
                    rtt_ms: Vec::new(),
                    rtt_median_ms: row.get(3)?,
                    hop_silent: row.get::<_, i32>(4)? != 0,
                    asn: row.get(5)?,
                    org: row.get(6)?,
                })
            })
            .map_err(|e| e.to_string())?;
        let mut hops = Vec::new();
        for r in rows {
            hops.push(r.map_err(|e| e.to_string())?);
        }
        Ok(RouteDetail {
            summary: summary.0,
            fingerprint_raw: summary.1,
            hops,
        })
    })
}

#[derive(Debug, Clone)]
pub struct MonitorStudyRow {
    pub id: i64,
    pub started_at: String,
    pub ends_at: String,
    pub interval_secs: u64,
    pub status: String,
    pub target_ips: Vec<String>,
}

pub fn create_monitor_study(
    session_id: Option<i64>,
    duration_minutes: u64,
    interval_secs: u64,
    target_ips: &[String],
) -> Result<i64, String> {
    let started_at = chrono::Utc::now();
    let ends_at = started_at + chrono::Duration::minutes(duration_minutes as i64);
    let json = serde_json::to_string(target_ips).map_err(|e| e.to_string())?;
    with_db(|conn| {
        conn.execute(
            r#"INSERT INTO monitor_studies (session_id, started_at, ends_at, interval_secs, status, target_ips_json)
               VALUES (?1,?2,?3,?4,'running',?5)"#,
            params![
                session_id,
                started_at.to_rfc3339(),
                ends_at.to_rfc3339(),
                interval_secs as i64,
                json,
            ],
        )
        .map_err(|e| e.to_string())?;
        Ok(conn.last_insert_rowid())
    })
}

pub fn finish_monitor_study(study_id: i64, status: &str) -> Result<(), String> {
    with_db(|conn| {
        conn.execute(
            "UPDATE monitor_studies SET status = ?1 WHERE id = ?2",
            params![status, study_id],
        )
        .map_err(|e| e.to_string())?;
        Ok(())
    })
}

fn row_to_monitor_study(row: &rusqlite::Row<'_>) -> Result<MonitorStudyRow, String> {
    let json: String = row.get(5).map_err(|e| e.to_string())?;
    let target_ips: Vec<String> = serde_json::from_str(&json).unwrap_or_default();
    Ok(MonitorStudyRow {
        id: row.get(0).map_err(|e| e.to_string())?,
        started_at: row.get(1).map_err(|e| e.to_string())?,
        ends_at: row.get(2).map_err(|e| e.to_string())?,
        interval_secs: row.get::<_, i64>(3).map_err(|e| e.to_string())? as u64,
        status: row.get(4).map_err(|e| e.to_string())?,
        target_ips,
    })
}

pub fn get_monitor_study(study_id: i64) -> Result<MonitorStudyRow, String> {
    with_db(|conn| {
        let mut stmt = conn
            .prepare(
                "SELECT id, started_at, ends_at, interval_secs, status, target_ips_json FROM monitor_studies WHERE id = ?1",
            )
            .map_err(|e| e.to_string())?;
        let mut rows = stmt.query(params![study_id]).map_err(|e| e.to_string())?;
        let Some(row) = rows.next().map_err(|e| e.to_string())? else {
            return Err("study not found".to_string());
        };
        row_to_monitor_study(row)
    })
}

pub fn list_monitor_studies(limit: u32) -> Result<Vec<MonitorStudyRow>, String> {
    with_db(|conn| {
        let mut stmt = conn
            .prepare(
                "SELECT id, started_at, ends_at, interval_secs, status, target_ips_json FROM monitor_studies ORDER BY id DESC LIMIT ?1",
            )
            .map_err(|e| e.to_string())?;
        let mut rows = stmt.query(params![limit]).map_err(|e| e.to_string())?;
        let mut out = Vec::new();
        while let Some(row) = rows.next().map_err(|e| e.to_string())? {
            out.push(row_to_monitor_study(row)?);
        }
        Ok(out)
    })
}

pub fn insert_monitor_sample(
    study_id: i64,
    target_ip: &str,
    lat: &crate::probe::latency::LatencyProbeResult,
    fingerprint: Option<&str>,
    route_changed: bool,
) -> Result<(), String> {
    let sampled_at = chrono::Utc::now().to_rfc3339();
    with_db(|conn| {
        conn.execute(
            r#"INSERT INTO monitor_samples (
              study_id, sampled_at, target_ip, median_ms, p95_ms, packet_loss_pct, jitter_ms,
              route_fingerprint_asn, route_changed
            ) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9)"#,
            params![
                study_id,
                sampled_at,
                target_ip,
                lat.median_ms,
                lat.p95_ms,
                lat.packet_loss_pct,
                lat.jitter_ms,
                fingerprint,
                if route_changed { 1 } else { 0 },
            ],
        )
        .map_err(|e| e.to_string())?;
        Ok(())
    })
}

pub fn insert_monitor_event(
    study_id: i64,
    target_ip: &str,
    event_type: &str,
    detail: &str,
) -> Result<(), String> {
    let event_at = chrono::Utc::now().to_rfc3339();
    with_db(|conn| {
        conn.execute(
            "INSERT INTO monitor_events (study_id, event_at, target_ip, event_type, detail) VALUES (?1,?2,?3,?4,?5)",
            params![study_id, event_at, target_ip, event_type, detail],
        )
        .map_err(|e| e.to_string())?;
        Ok(())
    })
}

pub fn monitor_sample_count(study_id: i64) -> Result<u64, String> {
    with_db(|conn| {
        let n: i64 = conn
            .query_row(
                "SELECT COUNT(*) FROM monitor_samples WHERE study_id = ?1",
                params![study_id],
                |row| row.get(0),
            )
            .map_err(|e| e.to_string())?;
        Ok(n as u64)
    })
}

#[derive(Debug, Clone, serde::Serialize)]
pub struct MonitorEventRow {
    pub event_at: String,
    pub target_ip: Option<String>,
    pub event_type: String,
    pub detail: String,
}

#[derive(Debug, Clone, serde::Serialize)]
pub struct MonitorSampleRow {
    pub sampled_at: String,
    pub target_ip: String,
    pub median_ms: Option<f64>,
    pub p95_ms: Option<f64>,
    pub packet_loss_pct: f64,
    pub jitter_ms: Option<f64>,
    pub route_fingerprint_asn: Option<String>,
    pub route_changed: bool,
}

pub fn recent_monitor_events(study_id: i64, limit: u32) -> Result<Vec<MonitorEventRow>, String> {
    with_db(|conn| {
        let mut stmt = conn
            .prepare(
                "SELECT event_at, target_ip, event_type, detail FROM monitor_events WHERE study_id = ?1 ORDER BY id DESC LIMIT ?2",
            )
            .map_err(|e| e.to_string())?;
        let rows = stmt
            .query_map(params![study_id, limit], |row| {
                Ok(MonitorEventRow {
                    event_at: row.get(0)?,
                    target_ip: row.get(1)?,
                    event_type: row.get(2)?,
                    detail: row.get(3)?,
                })
            })
            .map_err(|e| e.to_string())?;
        let mut out = Vec::new();
        for r in rows {
            out.push(r.map_err(|e| e.to_string())?);
        }
        Ok(out)
    })
}

pub fn list_monitor_samples(
    study_id: i64,
    target_ip: Option<&str>,
) -> Result<Vec<MonitorSampleRow>, String> {
    with_db(|conn| {
        let mut out = Vec::new();
        if let Some(ip) = target_ip {
            let mut stmt = conn
                .prepare(
                    r#"SELECT sampled_at, target_ip, median_ms, p95_ms, packet_loss_pct, jitter_ms, route_fingerprint_asn, route_changed
                       FROM monitor_samples WHERE study_id = ?1 AND target_ip = ?2 ORDER BY id ASC"#,
                )
                .map_err(|e| e.to_string())?;
            let rows = stmt
                .query_map(params![study_id, ip], map_sample_row)
                .map_err(|e| e.to_string())?;
            for r in rows {
                out.push(r.map_err(|e| e.to_string())?);
            }
        } else {
            let mut stmt = conn
                .prepare(
                    r#"SELECT sampled_at, target_ip, median_ms, p95_ms, packet_loss_pct, jitter_ms, route_fingerprint_asn, route_changed
                       FROM monitor_samples WHERE study_id = ?1 ORDER BY id ASC"#,
                )
                .map_err(|e| e.to_string())?;
            let rows = stmt
                .query_map(params![study_id], map_sample_row)
                .map_err(|e| e.to_string())?;
            for r in rows {
                out.push(r.map_err(|e| e.to_string())?);
            }
        }
        Ok(out)
    })
}

#[derive(Debug, Clone)]
pub struct SessionRow {
    pub id: i64,
    pub started_at: String,
    pub selected_pid: Option<i64>,
    pub process_name: Option<String>,
}

#[derive(Debug, Clone)]
pub struct StoredConnectionRow {
    pub protocol: String,
    pub local_ip: String,
    pub local_port: i64,
    pub remote_ip: String,
    pub remote_port: i64,
    pub observations: i64,
}

#[derive(Debug, Clone)]
pub struct EndpointTagRow {
    pub remote_ip: String,
    pub remote_port: u16,
    pub protocol: String,
    pub user_tag: String,
}

pub fn latest_session() -> Result<Option<SessionRow>, String> {
    with_db(|conn| {
        let mut stmt = conn
            .prepare(
                "SELECT id, started_at, selected_pid, process_name FROM sessions ORDER BY id DESC LIMIT 1",
            )
            .map_err(|e| e.to_string())?;
        let mut rows = stmt.query([]).map_err(|e| e.to_string())?;
        let Some(row) = rows.next().map_err(|e| e.to_string())? else {
            return Ok(None);
        };
        Ok(Some(SessionRow {
            id: row.get(0).map_err(|e| e.to_string())?,
            started_at: row.get(1).map_err(|e| e.to_string())?,
            selected_pid: row.get(2).map_err(|e| e.to_string())?,
            process_name: row.get(3).map_err(|e| e.to_string())?,
        }))
    })
}

pub fn list_session_connections(session_id: i64) -> Result<Vec<StoredConnectionRow>, String> {
    with_db(|conn| {
        let mut stmt = conn
            .prepare(
                r#"SELECT protocol, local_ip, local_port, remote_ip, remote_port, observations
                   FROM connections WHERE session_id = ?1 ORDER BY remote_ip, remote_port"#,
            )
            .map_err(|e| e.to_string())?;
        let mut rows = stmt.query(params![session_id]).map_err(|e| e.to_string())?;
        let mut out = Vec::new();
        while let Some(row) = rows.next().map_err(|e| e.to_string())? {
            out.push(StoredConnectionRow {
                protocol: row.get(0).map_err(|e| e.to_string())?,
                local_ip: row.get(1).map_err(|e| e.to_string())?,
                local_port: row.get(2).map_err(|e| e.to_string())?,
                remote_ip: row.get(3).map_err(|e| e.to_string())?,
                remote_port: row.get(4).map_err(|e| e.to_string())?,
                observations: row.get(5).map_err(|e| e.to_string())?,
            });
        }
        Ok(out)
    })
}

pub fn list_all_endpoint_tags() -> Result<Vec<EndpointTagRow>, String> {
    with_db(|conn| {
        let mut stmt = conn
            .prepare("SELECT remote_ip, remote_port, protocol, user_tag FROM endpoints")
            .map_err(|e| e.to_string())?;
        let mut rows = stmt.query([]).map_err(|e| e.to_string())?;
        let mut out = Vec::new();
        while let Some(row) = rows.next().map_err(|e| e.to_string())? {
            out.push(EndpointTagRow {
                remote_ip: row.get(0).map_err(|e| e.to_string())?,
                remote_port: row.get(1).map_err(|e| e.to_string())?,
                protocol: row.get(2).map_err(|e| e.to_string())?,
                user_tag: row.get(3).map_err(|e| e.to_string())?,
            });
        }
        Ok(out)
    })
}

fn map_sample_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<MonitorSampleRow> {
    Ok(MonitorSampleRow {
        sampled_at: row.get(0)?,
        target_ip: row.get(1)?,
        median_ms: row.get(2)?,
        p95_ms: row.get(3)?,
        packet_loss_pct: row.get(4)?,
        jitter_ms: row.get(5)?,
        route_fingerprint_asn: row.get(6)?,
        route_changed: row.get::<_, i32>(7)? != 0,
    })
}
