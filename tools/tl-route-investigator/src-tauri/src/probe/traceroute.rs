use regex::Regex;
use serde::Serialize;
use std::process::Command;
use std::time::Duration;

use crate::network::ip_intel;

#[derive(Debug, Clone, Serialize)]
pub struct RouteHop {
    pub hop: u32,
    pub hop_ip: Option<String>,
    pub hostname: Option<String>,
    pub rtt_ms: Vec<f64>,
    pub rtt_median_ms: Option<f64>,
    /// Router did not respond to ICMP TTL probes (not the same as end-to-end loss).
    pub hop_silent: bool,
    pub asn: Option<String>,
    pub org: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct RouteTraceResult {
    pub target: String,
    pub method: String,
    pub probed_at: String,
    pub hops: Vec<RouteHop>,
    pub reached_destination: bool,
    pub fingerprint_raw: String,
    pub fingerprint_asn: String,
    pub raw_output: String,
    pub error: Option<String>,
}

pub async fn tracert_windows(target: &str, max_hops: u32) -> RouteTraceResult {
    let probed_at = chrono::Utc::now().to_rfc3339();
    let mut result = RouteTraceResult {
        target: target.to_string(),
        method: "windows_tracert_icmp".to_string(),
        probed_at,
        hops: Vec::new(),
        reached_destination: false,
        fingerprint_raw: String::new(),
        fingerprint_asn: String::new(),
        raw_output: String::new(),
        error: None,
    };

    if !ip_intel::is_lookupable_ip(target) {
        result.error = Some("Target must be a public IPv4/IPv6 address".to_string());
        return result;
    }

    let output = Command::new("tracert")
        .args([
            "-d",
            "-w",
            "2000",
            "-h",
            &max_hops.clamp(1, 64).to_string(),
            target,
        ])
        .output();

    match output {
        Ok(out) => {
            result.raw_output = String::from_utf8_lossy(&out.stdout).to_string();
            if !out.status.success() && result.raw_output.is_empty() {
                result.error = Some(format!(
                    "tracert failed: {}",
                    String::from_utf8_lossy(&out.stderr)
                ));
                return result;
            }
        }
        Err(e) => {
            result.error = Some(format!("failed to run tracert: {e}"));
            return result;
        }
    }

    result.hops = parse_tracert_stdout(&result.raw_output);
    enrich_hops(&mut result.hops).await;
    result.reached_destination = result
        .hops
        .last()
        .and_then(|h| h.hop_ip.as_deref())
        .map(|ip| ip == target)
        .unwrap_or(false);
    result.fingerprint_raw = fingerprint_raw(&result.hops);
    result.fingerprint_asn = fingerprint_asn(&result.hops);
    result
}

fn parse_tracert_stdout(text: &str) -> Vec<RouteHop> {
    let hop_line = Regex::new(r"^\s*(\d+)\s+(.+)$").expect("regex");
    let ip_v4 = Regex::new(r"\b(\d{1,3}(?:\.\d{1,3}){3})\b").expect("regex");
    let ms_val = Regex::new(r"(?:<(\d+)|(\d+))\s*ms").expect("regex");

    let mut hops = Vec::new();
    for line in text.lines() {
        let Some(caps) = hop_line.captures(line) else {
            continue;
        };
        let hop: u32 = caps[1].parse().unwrap_or(0);
        if hop == 0 {
            continue;
        }
        let rest = caps[2].trim();
        let hop_ip = ip_v4
            .find(rest)
            .map(|m| m.as_str().to_string())
            .filter(|ip| ip != "0.0.0.0");
        let hop_silent = hop_ip.is_none();
        let mut rtt_ms = Vec::new();
        for cap in ms_val.captures_iter(rest) {
            let v: f64 = cap
                .get(1)
                .or_else(|| cap.get(2))
                .and_then(|m| m.as_str().parse().ok())
                .unwrap_or(0.0);
            rtt_ms.push(v);
        }
        let rtt_median_ms = median(&rtt_ms);
        hops.push(RouteHop {
            hop,
            hop_ip,
            hostname: None,
            rtt_ms,
            rtt_median_ms,
            hop_silent,
            asn: None,
            org: None,
        });
    }
    hops
}

async fn enrich_hops(hops: &mut [RouteHop]) {
    for hop in hops.iter_mut() {
        let Some(ip) = hop.hop_ip.clone() else {
            continue;
        };
        if !ip_intel::is_lookupable_ip(&ip) {
            hop.asn = Some("private".to_string());
            continue;
        }
        let intel = ip_intel::lookup_ip(&ip, false).await;
        hop.hostname = intel.reverse_dns;
        hop.asn = intel.asn;
        hop.org = intel.org.or(intel.isp);
    }
}

fn median(samples: &[f64]) -> Option<f64> {
    if samples.is_empty() {
        return None;
    }
    let mut v = samples.to_vec();
    v.sort_by(|a, b| a.partial_cmp(b).unwrap());
    Some(v[v.len() / 2])
}

pub fn fingerprint_raw(hops: &[RouteHop]) -> String {
    hops.iter()
        .map(|h| h.hop_ip.as_deref().unwrap_or("*"))
        .collect::<Vec<_>>()
        .join(" → ")
}

pub fn fingerprint_asn(hops: &[RouteHop]) -> String {
    let mut parts: Vec<String> = Vec::new();
    let mut last = String::new();
    for h in hops {
        let label = if h.hop_silent && h.hop_ip.is_none() {
            "silent_hop".to_string()
        } else if let Some(asn) = &h.asn {
            asn.clone()
        } else if h.hop_ip.is_some() {
            "unknown_asn".to_string()
        } else {
            "silent_hop".to_string()
        };
        if label != last {
            parts.push(label);
            last = parts.last().cloned().unwrap_or_default();
        }
    }
    parts.join(" → ")
}

/// Optional slow pathping (Windows), loss per hop — can take several minutes.
pub async fn pathping_windows(target: &str, max_hops: u32, pings_per_hop: u32) -> RouteTraceResult {
    let probed_at = chrono::Utc::now().to_rfc3339();
    let mut base = tracert_windows(target, max_hops).await;
    base.method = "windows_pathping_icmp".to_string();
    base.probed_at = probed_at;

    let pings = pings_per_hop.clamp(3, 20);
    let hops = max_hops.clamp(1, 30);
    let output = Command::new("pathping")
        .args([
            "-n",
            "-q",
            "-h",
            &hops.to_string(),
            "-p",
            &pings.to_string(),
            "-w",
            "1000",
            target,
        ])
        .output();

    match output {
        Ok(out) => {
            let text = String::from_utf8_lossy(&out.stdout);
            base.raw_output.push_str("\n\n--- pathping ---\n");
            base.raw_output.push_str(&text);
            apply_pathping_loss(&mut base.hops, &text);
        }
        Err(e) => {
            base.error = Some(format!("pathping failed: {e}"));
        }
    }
    base.fingerprint_asn = fingerprint_asn(&base.hops);
    base
}

fn apply_pathping_loss(hops: &mut [RouteHop], text: &str) {
    // pathping -q: rows like "  0  192.168.1.1 ..."
    let row = Regex::new(r"^\s*(\d+)\s+(\S+)\s+(\d+)\s+([\d.]+)%").expect("regex");
    for line in text.lines() {
        let Some(caps) = row.captures(line) else {
            continue;
        };
        let hop: u32 = caps[1].parse().unwrap_or(0);
        let loss: f64 = caps[4].parse().unwrap_or(0.0);
        if let Some(h) = hops.iter_mut().find(|h| h.hop == hop) {
            if loss > 0.0 && h.hop_silent {
                // pathping may show loss at silent hops — annotate org field as note
                h.org = Some(format!("pathping hop loss {loss}% (ICMP to hop, not game traffic)"));
            }
        }
    }
}

#[allow(dead_code)]
pub fn tracert_timeout() -> Duration {
    Duration::from_secs(120)
}
