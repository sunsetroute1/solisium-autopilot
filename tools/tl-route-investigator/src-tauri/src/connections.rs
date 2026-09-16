use chrono::Utc;
use netstat2::{get_sockets_info, AddressFamilyFlags, ProtocolFlags, ProtocolSocketInfo};
use serde::Serialize;
use std::collections::HashMap;
use std::process::Command;
use std::sync::{LazyLock, Mutex};

#[derive(Debug, Clone, Serialize, PartialEq, Eq, Hash)]
#[serde(rename_all = "snake_case")]
pub enum ProtocolKind {
    Tcp,
    Udp,
}

#[derive(Debug, Clone, Serialize, PartialEq)]
pub struct ConnectionRow {
    pub key: String,
    pub local_ip: String,
    pub local_port: u16,
    pub remote_ip: String,
    pub remote_port: u16,
    pub protocol: ProtocolKind,
    pub state: Option<String>,
    pub pid: u32,
    pub first_seen: String,
    pub last_seen: String,
    pub observations: u64,
    pub classification: String,
}

#[derive(Debug, Default)]
struct ConnectionTracker {
    rows: HashMap<String, Tracked>,
}

#[derive(Debug, Clone)]
struct Tracked {
    row: ConnectionRow,
}

static TRACKER: LazyLock<Mutex<ConnectionTracker>> = LazyLock::new(|| Mutex::new(ConnectionTracker::default()));

pub fn connections_for_pid(pid: u32, elevated: bool) -> Result<Vec<ConnectionRow>, String> {
    let cli = enumerate_netstat_cli(pid).unwrap_or_default();
    let ns2 = enumerate_netstat2(pid).unwrap_or_default();
    let found = merge_sources(cli, ns2);
    if found.is_empty() {
        let hint = if elevated {
            "No connections found for this PID (process may be idle or using handles not visible to netstat)."
        } else {
            "No connections found for this PID. Some UDP owner data requires Administrator; TCP should still appear via netstat when active."
        };
        return Err(hint.to_string());
    }
    let tags = crate::db::load_endpoint_tags().unwrap_or_default();
    merge_tracker(found, &tags)
}

fn merge_sources(cli: Vec<ConnectionRow>, ns2: Vec<ConnectionRow>) -> Vec<ConnectionRow> {
    let cli_copy = cli.clone();
    let mut by_key: HashMap<String, ConnectionRow> = HashMap::new();
    for row in cli {
        by_key.insert(row.key.clone(), row);
    }
    for row in ns2 {
        match by_key.get_mut(&row.key) {
            Some(existing) => {
                if existing.remote_ip == "*" && row.remote_ip != "*" {
                    existing.remote_ip = row.remote_ip.clone();
                    existing.remote_port = row.remote_port;
                    existing.key = row.key.clone();
                }
                if existing.state.is_none() {
                    existing.state = row.state.clone();
                }
            }
            None => {
                by_key.insert(row.key.clone(), row);
            }
        }
    }
    // UDP rows from netstat2 often lack remote; match netstat -ano by local port + PID.
    let mut rekey: Vec<(String, ConnectionRow)> = Vec::new();
    for (key, mut row) in by_key {
        if row.protocol == ProtocolKind::Udp && row.remote_ip == "*" {
            if let Some(m) = cli_copy.iter().find(|c| {
                c.pid == row.pid
                    && c.local_port == row.local_port
                    && c.remote_ip != "*"
                    && c.protocol == ProtocolKind::Udp
            }) {
                row.remote_ip = m.remote_ip.clone();
                row.remote_port = m.remote_port;
                row.key = format!(
                    "udp|{}:{}|{}:{}",
                    row.local_ip, row.local_port, row.remote_ip, row.remote_port
                );
            }
        }
        rekey.push((key, row));
    }
    let mut out: HashMap<String, ConnectionRow> = HashMap::new();
    for (old_key, row) in rekey {
        out.entry(row.key.clone())
            .and_modify(|existing| {
                if existing.remote_ip == "*" && row.remote_ip != "*" {
                    *existing = row.clone();
                }
            })
            .or_insert_with(|| row);
        let _ = old_key;
    }
    out.into_values().collect()
}

fn merge_tracker(
    mut batch: Vec<ConnectionRow>,
    endpoint_tags: &HashMap<String, String>,
) -> Result<Vec<ConnectionRow>, String> {
    let now = Utc::now().to_rfc3339();
    let mut guard = TRACKER.lock().map_err(|_| "tracker lock poisoned")?;
    for row in &mut batch {
        let tag_key = format!(
            "{}|{}:{}",
            match row.protocol {
                ProtocolKind::Tcp => "tcp",
                ProtocolKind::Udp => "udp",
            },
            row.remote_ip,
            row.remote_port
        );
        if let Some(tag) = endpoint_tags.get(&tag_key) {
            row.classification = tag.clone();
        }
        if let Some(existing) = guard.rows.get_mut(&row.key) {
            existing.row.observations += 1;
            existing.row.last_seen = now.clone();
            existing.row.state = row.state.clone();
            if row.classification != "unknown" {
                existing.row.classification = row.classification.clone();
            }
            *row = existing.row.clone();
        } else {
            row.first_seen = now.clone();
            row.last_seen = now.clone();
            row.observations = 1;
            guard.rows.insert(row.key.clone(), Tracked { row: row.clone() });
        }
    }
    batch.sort_by(|a, b| {
        a.remote_ip
            .cmp(&b.remote_ip)
            .then(a.remote_port.cmp(&b.remote_port))
    });
    Ok(batch)
}

fn enumerate_netstat2(pid: u32) -> Result<Vec<ConnectionRow>, String> {
    let flags = AddressFamilyFlags::IPV4 | AddressFamilyFlags::IPV6;
    let protos = ProtocolFlags::TCP | ProtocolFlags::UDP;
    let sockets = get_sockets_info(flags, protos).map_err(|e| e.to_string())?;
    let mut out = Vec::new();
    for info in sockets {
        if !info.associated_pids.contains(&pid) {
            continue;
        }
        match info.protocol_socket_info {
            ProtocolSocketInfo::Tcp(tcp) => {
                let local_ip = tcp.local_addr.to_string();
                let remote_ip = tcp.remote_addr.to_string();
                if remote_ip == "0.0.0.0" || remote_ip == "::" || remote_ip == "::1" {
                    continue;
                }
                let key = format!("tcp|{local_ip}:{}|{remote_ip}:{}", tcp.local_port, tcp.remote_port);
                out.push(ConnectionRow {
                    key,
                    local_ip,
                    local_port: tcp.local_port,
                    remote_ip,
                    remote_port: tcp.remote_port,
                    protocol: ProtocolKind::Tcp,
                    state: Some(format!("{:?}", tcp.state)),
                    pid,
                    first_seen: String::new(),
                    last_seen: String::new(),
                    observations: 0,
                    classification: "unknown".to_string(),
                });
            }
            ProtocolSocketInfo::Udp(udp) => {
                let local_ip = udp.local_addr.to_string();
                let key = format!("udp|{local_ip}:{}|*:*", udp.local_port);
                out.push(ConnectionRow {
                    key,
                    local_ip,
                    local_port: udp.local_port,
                    remote_ip: "*".to_string(),
                    remote_port: 0,
                    protocol: ProtocolKind::Udp,
                    state: None,
                    pid,
                    first_seen: String::new(),
                    last_seen: String::new(),
                    observations: 0,
                    classification: "unknown".to_string(),
                });
            }
        }
    }
    Ok(out)
}

fn enumerate_netstat_cli(pid: u32) -> Result<Vec<ConnectionRow>, String> {
    let output = Command::new("netstat")
        .args(["-ano"])
        .output()
        .map_err(|e| format!("netstat failed: {e}"))?;
    if !output.status.success() {
        return Err("netstat returned non-zero".to_string());
    }
    let text = String::from_utf8_lossy(&output.stdout);
    let mut out = Vec::new();
    for line in text.lines().skip(4) {
        let cols: Vec<&str> = line.split_whitespace().collect();
        if cols.len() < 5 {
            continue;
        }
        let proto = cols[0].to_lowercase();
        let local = cols[1];
        let remote = cols[2];
        let state = cols.get(3).map(|s| s.to_string());
        let pid_col = if proto == "tcp" {
            cols.get(4)
        } else {
            cols.get(3)
        };
        let Some(pid_str) = pid_col else { continue };
        let Ok(row_pid) = pid_str.parse::<u32>() else {
            continue;
        };
        if row_pid != pid {
            continue;
        }
        let (lip, lport) = split_host_port(local)?;
        let (rip, rport) = split_host_port(remote)?;
        if rip == "0.0.0.0" || rip == "[::]" || rip == "*" {
            continue;
        }
        let protocol = if proto.starts_with("tcp") {
            ProtocolKind::Tcp
        } else if proto.starts_with("udp") {
            ProtocolKind::Udp
        } else {
            continue;
        };
        let key = format!("{proto}|{lip}:{lport}|{rip}:{rport}");
        out.push(ConnectionRow {
            key,
            local_ip: lip,
            local_port: lport,
            remote_ip: rip,
            remote_port: rport,
            protocol,
            state,
            pid,
            first_seen: String::new(),
            last_seen: String::new(),
            observations: 0,
            classification: "unknown".to_string(),
        });
    }
    Ok(out)
}

fn split_host_port(addr: &str) -> Result<(String, u16), String> {
    if addr.starts_with('[') {
        let end = addr.find(']').ok_or("bad v6 addr")?;
        let ip = addr[1..end].to_string();
        let port = addr[end + 2..].parse().map_err(|_| "bad port")?;
        return Ok((ip, port));
    }
    let mut parts = addr.rsplitn(2, ':');
    let port: u16 = parts
        .next()
        .ok_or("missing port")?
        .parse()
        .map_err(|_| "bad port")?;
    let ip = parts.next().ok_or("missing ip")?.to_string();
    Ok((ip, port))
}

