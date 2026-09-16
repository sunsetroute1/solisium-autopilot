pub mod targets;

use serde::Serialize;
use targets::{ReferenceKind, ReferenceTarget};

use crate::network::ip_intel;
use crate::probe::latency::LatencyProbeResult;
use crate::probe::traceroute;

#[derive(Debug, Clone, Serialize)]
pub struct ResolvedReference {
    pub id: String,
    pub label: String,
    pub kind: ReferenceKind,
    pub hostname: String,
    pub aws_region_id: Option<String>,
    pub geo_hint: String,
    pub resolved_ipv4: Option<String>,
    pub resolve_error: Option<String>,
}

pub async fn resolve_all_references() -> Vec<ResolvedReference> {
    let mut out = Vec::new();
    for t in targets::all_references() {
        out.push(resolve_one(t).await);
    }
    out
}

async fn resolve_one(target: ReferenceTarget) -> ResolvedReference {
    let (resolved_ipv4, resolve_error) = match resolve_ipv4(&target.hostname).await {
        Ok(ip) => (Some(ip), None),
        Err(e) => (None, Some(e)),
    };
    ResolvedReference {
        id: target.id,
        label: target.label,
        kind: target.kind,
        hostname: target.hostname,
        aws_region_id: target.aws_region_id,
        geo_hint: target.geo_hint,
        resolved_ipv4,
        resolve_error,
    }
}

async fn resolve_ipv4(host: &str) -> Result<String, String> {
    if let Ok(ip) = host.parse::<std::net::IpAddr>() {
        return Ok(ip.to_string());
    }
    let addrs = tokio::net::lookup_host((host, 443))
        .await
        .map_err(|e| format!("DNS: {e}"))?;
    for addr in addrs {
        if addr.is_ipv4() {
            return Ok(addr.ip().to_string());
        }
    }
    Err("No IPv4 address in DNS response".to_string())
}

#[derive(Debug, Clone, Serialize)]
pub struct ReferenceBenchmarkRow {
    pub reference: ResolvedReference,
    pub asn: Option<String>,
    pub org: Option<String>,
    pub latency: LatencyProbeResult,
    pub route_fingerprint_asn: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct GameBenchmarkRow {
    pub ip: String,
    pub user_tag: Option<String>,
    pub latency: LatencyProbeResult,
    pub route_fingerprint_asn: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct RelayHint {
    pub summary: String,
    pub evidence: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct ComparisonReport {
    pub references: Vec<ReferenceBenchmarkRow>,
    pub game_endpoints: Vec<GameBenchmarkRow>,
    pub hints: Vec<RelayHint>,
    pub disclaimer: String,
}

pub async fn run_comparison(
    game_ips: Vec<String>,
    probes: u32,
    include_traceroute: bool,
) -> ComparisonReport {
    let refs = resolve_all_references().await;
    let mut reference_rows = Vec::new();
    for r in refs {
        let Some(ip) = r.resolved_ipv4.clone() else {
            let hostname = r.hostname.clone();
            reference_rows.push(ReferenceBenchmarkRow {
                reference: r,
                asn: None,
                org: None,
                latency: LatencyProbeResult {
                    target: hostname,
                    probes,
                    received: 0,
                    packet_loss_pct: 100.0,
                    min_ms: None,
                    mean_ms: None,
                    median_ms: None,
                    p90_ms: None,
                    p95_ms: None,
                    p99_ms: None,
                    max_ms: None,
                    stddev_ms: None,
                    jitter_ms: None,
                    samples_ms: vec![],
                    error: Some("DNS resolution failed".to_string()),
                },
                route_fingerprint_asn: None,
            });
            continue;
        };
        let intel = ip_intel::lookup_ip(&ip, false).await;
        let latency = crate::probe::latency::icmp_probe(&ip, probes).await;
        let route_fingerprint_asn = if include_traceroute {
            let trace = traceroute::tracert_windows(&ip, 25).await;
            Some(trace.fingerprint_asn)
        } else {
            None
        };
        reference_rows.push(ReferenceBenchmarkRow {
            reference: r,
            asn: intel.asn,
            org: intel.org.or(intel.isp),
            latency,
            route_fingerprint_asn,
        });
    }

    let mut game_rows = Vec::new();
    for ip in game_ips {
        if !ip_intel::is_lookupable_ip(&ip) {
            continue;
        }
        let latency = crate::probe::latency::icmp_probe(&ip, probes).await;
        let route_fingerprint_asn = if include_traceroute {
            let trace = traceroute::tracert_windows(&ip, 25).await;
            Some(trace.fingerprint_asn)
        } else {
            None
        };
        game_rows.push(GameBenchmarkRow {
            ip: ip.clone(),
            user_tag: None,
            latency,
            route_fingerprint_asn,
        });
    }

    let hints = build_relay_hints(&reference_rows, &game_rows);
    ComparisonReport {
        references: reference_rows,
        game_endpoints: game_rows,
        hints,
        disclaimer: "Reference targets are public AWS/neutral endpoints — not Throne and Liberty servers. Lower RTT to a reference does not prove a relay would improve game traffic.".to_string(),
    }
}

fn build_relay_hints(refs: &[ReferenceBenchmarkRow], games: &[GameBenchmarkRow]) -> Vec<RelayHint> {
    let mut hints = Vec::new();
    let game_medians: Vec<f64> = games
        .iter()
        .filter_map(|g| g.latency.median_ms)
        .collect();
    if game_medians.is_empty() {
        return hints;
    }
    let game_p50 = median_f64(&game_medians);
    let mut ref_by_median: Vec<(&ReferenceBenchmarkRow, f64)> = refs
        .iter()
        .filter_map(|r| r.latency.median_ms.map(|m| (r, m)))
        .collect();
    ref_by_median.sort_by(|a, b| a.1.partial_cmp(&b.1).unwrap());
    if let Some((best, best_ms)) = ref_by_median.first() {
        let delta = game_p50 - best_ms;
        if delta >= 12.0 {
            hints.push(RelayHint {
                summary: format!(
                    "{} appears {:.0} ms lower P50 RTT than your tagged/selected game IP median ({:.0} ms).",
                    best.reference.label, delta, game_p50
                ),
                evidence: format!(
                    "ICMP P50: game ~{game_p50:.1} ms vs {} ({}) ~{best_ms:.1} ms. Paths may differ from UDP gameplay.",
                    best.reference.label, best.reference.resolved_ipv4.as_deref().unwrap_or("?")
                ),
            });
        }
    }
    hints
}

fn median_f64(values: &[f64]) -> f64 {
    let mut v = values.to_vec();
    v.sort_by(|a, b| a.partial_cmp(b).unwrap());
    v[v.len() / 2]
}
