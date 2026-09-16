use network_interface::{NetworkInterface, NetworkInterfaceConfig};
use serde::Serialize;
use std::sync::{LazyLock, Mutex};
use std::time::{Duration, Instant};

use crate::util::run_powershell;

static LOCAL_CACHE: LazyLock<Mutex<Option<(Instant, LocalNetworkSnapshot)>>> =
    LazyLock::new(|| Mutex::new(None));
const LOCAL_CACHE_TTL: Duration = Duration::from_secs(45);

#[derive(Debug, Clone, Serialize)]
pub struct AdapterInfo {
    pub name: String,
    pub description: String,
    pub status: String,
    pub media_type: String,
    pub link_speed_mbps: Option<u64>,
    pub ipv4: Vec<String>,
    pub ipv6: Vec<String>,
    pub is_up: bool,
}

#[derive(Debug, Clone, Serialize)]
pub struct LocalNetworkSnapshot {
    pub adapters: Vec<AdapterInfo>,
    pub default_gateway_ipv4: Option<String>,
    pub default_gateway_ipv6: Option<String>,
    pub dns_servers_ipv4: Vec<String>,
    pub dns_servers_ipv6: Vec<String>,
    pub mtu_hints: Vec<String>,
    pub ipv4_works: bool,
    pub ipv6_works: bool,
    pub elevated: bool,
}

pub fn collect_local_cached(elevated: bool, refresh: bool) -> Result<LocalNetworkSnapshot, String> {
    if !refresh {
        if let Ok(guard) = LOCAL_CACHE.lock() {
            if let Some((at, snap)) = guard.as_ref() {
                if at.elapsed() < LOCAL_CACHE_TTL {
                    let mut s = snap.clone();
                    s.elevated = elevated;
                    return Ok(s);
                }
            }
        }
    }
    let snap = collect_local(elevated)?;
    if let Ok(mut guard) = LOCAL_CACHE.lock() {
        *guard = Some((Instant::now(), snap.clone()));
    }
    Ok(snap)
}

pub fn collect_local(elevated: bool) -> Result<LocalNetworkSnapshot, String> {
    let adapters = list_adapters()?;
    let (gw4, gw6) = default_gateways()?;
    let (dns4, dns6) = dns_servers()?;
    let mtu_hints = mtu_from_interfaces();
    let ipv4_works = !dns4.is_empty() || gw4.is_some();
    let ipv6_works = !dns6.is_empty() || gw6.is_some();
    Ok(LocalNetworkSnapshot {
        adapters,
        default_gateway_ipv4: gw4,
        default_gateway_ipv6: gw6,
        dns_servers_ipv4: dns4,
        dns_servers_ipv6: dns6,
        mtu_hints,
        ipv4_works,
        ipv6_works,
        elevated,
    })
}

fn list_adapters() -> Result<Vec<AdapterInfo>, String> {
    let ps = r#"
Get-NetAdapter | Where-Object { $_.Status -ne 'Not Present' } | ForEach-Object {
  $cfg = Get-NetIPConfiguration -InterfaceAlias $_.Name -ErrorAction SilentlyContinue
  $v4 = @($cfg.IPv4Address.IPAddress) -join ','
  $v6 = @($cfg.IPv6Address.IPAddress) -join ','
  $ls = $_.LinkSpeed
  if ($ls -is [string]) { $ls } else { [string]$ls }
  "$($_.Name)|$($_.InterfaceDescription)|$($_.Status)|$($_.MediaType)|$ls|$v4|$v6"
}
"#;
    let raw = run_powershell(ps)?;
    let mut out = Vec::new();
    for line in raw.lines() {
        if line.trim().is_empty() {
            continue;
        }
        let parts: Vec<&str> = line.split('|').collect();
        if parts.len() < 7 {
            continue;
        }
        let link_mbps = parse_link_speed_mbps(parts[4]);
        let ipv4: Vec<String> = parts[5]
            .split(',')
            .filter(|s| !s.is_empty())
            .map(str::to_string)
            .collect();
        let ipv6: Vec<String> = parts[6]
            .split(',')
            .filter(|s| !s.is_empty() && *s != "::1")
            .map(str::to_string)
            .collect();
        let status = parts[2].to_string();
        out.push(AdapterInfo {
            name: parts[0].to_string(),
            description: parts[1].to_string(),
            status: status.clone(),
            media_type: parts[3].to_string(),
            link_speed_mbps: link_mbps,
            ipv4: ipv4.clone(),
            ipv6,
            is_up: status.eq_ignore_ascii_case("Up"),
        });
    }
    if out.is_empty() {
        out = fallback_interfaces()?;
    }
    Ok(out)
}

fn fallback_interfaces() -> Result<Vec<AdapterInfo>, String> {
    let ifaces = NetworkInterface::show().map_err(|e| e.to_string())?;
    Ok(ifaces
        .into_iter()
        .filter_map(|iface| {
            let mut v4 = Vec::new();
            let mut v6 = Vec::new();
            for addr in iface.addr {
                match addr {
                    network_interface::Addr::V4(v) => v4.push(v.ip.to_string()),
                    network_interface::Addr::V6(v) => v6.push(v.ip.to_string()),
                }
            }
            if v4.is_empty() && v6.is_empty() {
                return None;
            }
            Some(AdapterInfo {
                name: iface.name.clone(),
                description: iface.name,
                status: "unknown".to_string(),
                media_type: "unknown".to_string(),
                link_speed_mbps: None,
                ipv4: v4,
                ipv6: v6,
                is_up: true,
            })
        })
        .collect())
}

fn default_gateways() -> Result<(Option<String>, Option<String>), String> {
    let ps = r#"
$v4 = (Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue | Sort-Object RouteMetric | Select-Object -First 1).NextHop
$v6 = (Get-NetRoute -DestinationPrefix '::/0' -ErrorAction SilentlyContinue | Sort-Object RouteMetric | Select-Object -First 1).NextHop
Write-Output "$v4|$v6"
"#;
    let raw = run_powershell(ps)?;
    let parts: Vec<&str> = raw.split('|').collect();
    let gw4 = parts.first().filter(|s| !s.is_empty()).map(|s| s.to_string());
    let gw6 = parts.get(1).filter(|s| !s.is_empty()).map(|s| s.to_string());
    Ok((gw4, gw6))
}

fn dns_servers() -> Result<(Vec<String>, Vec<String>), String> {
    let ps = r#"
$v4 = (Get-DnsClientServerAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | ForEach-Object { $_.ServerAddresses }) | Sort-Object -Unique
$v6 = (Get-DnsClientServerAddress -AddressFamily IPv6 -ErrorAction SilentlyContinue | ForEach-Object { $_.ServerAddresses }) | Sort-Object -Unique
Write-Output (($v4 -join ',') + '|' + ($v6 -join ','))
"#;
    let raw = run_powershell(ps)?;
    let parts: Vec<&str> = raw.split('|').collect();
    let split = |s: &str| {
        s.split(',')
            .filter(|p| !p.is_empty())
            .map(str::to_string)
            .collect()
    };
    Ok((
        parts.first().map(|s| split(s)).unwrap_or_default(),
        parts.get(1).map(|s| split(s)).unwrap_or_default(),
    ))
}

fn mtu_from_interfaces() -> Vec<String> {
    let ps = r#"
Get-NetIPInterface -AddressFamily IPv4 -ErrorAction SilentlyContinue |
  Where-Object { $_.ConnectionState -eq 'Connected' } |
  ForEach-Object {
    $alias = (Get-NetAdapter -InterfaceIndex $_.InterfaceIndex -ErrorAction SilentlyContinue).Name
    if (-not $alias) { $alias = $_.InterfaceAlias }
    "$alias|v4|$($_.NlMtu)"
  }
"#;
    if let Ok(raw) = run_powershell(ps) {
        let hints: Vec<String> = raw
            .lines()
            .filter(|l| !l.trim().is_empty())
            .map(|l| {
                let p: Vec<&str> = l.split('|').collect();
                if p.len() >= 3 {
                    format!("{} {} MTU {}", p[0], p[1], p[2])
                } else {
                    l.to_string()
                }
            })
            .collect();
        if !hints.is_empty() {
            return hints;
        }
    }
    NetworkInterface::show()
        .map(|ifaces| {
            ifaces
                .into_iter()
                .map(|i| format!("{}: MTU unknown (interface index {})", i.name, i.index))
                .collect()
        })
        .unwrap_or_default()
}

fn parse_link_speed_mbps(raw: &str) -> Option<u64> {
    let s = raw.trim();
    if s.is_empty() {
        return None;
    }
    if let Ok(n) = s.parse::<u64>() {
        // PowerShell sometimes returns bits/sec as integer.
        if n > 10_000_000 {
            return Some(n / 1_000_000);
        }
        return Some(n);
    }
    let lower = s.to_lowercase();
    let digits: String = lower.chars().filter(|c| c.is_ascii_digit() || *c == '.').collect();
    let value: f64 = digits.parse().ok()?;
    if lower.contains("gbps") {
        Some((value * 1000.0).round() as u64)
    } else if lower.contains("mbps") || lower.contains("mb/s") {
        Some(value.round() as u64)
    } else if lower.contains("kbps") {
        Some((value / 1000.0).round().max(1.0) as u64)
    } else {
        None
    }
}
