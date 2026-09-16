use serde::Serialize;
use std::net::IpAddr;
use std::time::Duration;
use surge_ping::{Client, Config, IcmpPacket, PingIdentifier, PingSequence};

#[derive(Debug, Clone, Serialize)]
pub struct LatencyProbeResult {
    pub target: String,
    pub probes: u32,
    pub received: u32,
    pub packet_loss_pct: f64,
    pub min_ms: Option<f64>,
    pub mean_ms: Option<f64>,
    pub median_ms: Option<f64>,
    pub p90_ms: Option<f64>,
    pub p95_ms: Option<f64>,
    pub p99_ms: Option<f64>,
    pub max_ms: Option<f64>,
    pub stddev_ms: Option<f64>,
    pub jitter_ms: Option<f64>,
    pub samples_ms: Vec<f64>,
    pub error: Option<String>,
}

/// Fewer probes and shorter spacing for dashboard refresh (not a full study sample).
pub async fn icmp_probe_quick(target: &str, probes: u32) -> LatencyProbeResult {
    icmp_probe_inner(target, probes, 80).await
}

pub async fn icmp_probe(target: &str, probes: u32) -> LatencyProbeResult {
    icmp_probe_inner(target, probes, 200).await
}

async fn icmp_probe_inner(target: &str, probes: u32, interval_ms: u64) -> LatencyProbeResult {
    let mut result = LatencyProbeResult {
        target: target.to_string(),
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
        samples_ms: Vec::new(),
        error: None,
    };
    let ip: IpAddr = match target.parse() {
        Ok(v) => v,
        Err(_) => {
            result.error = Some("Target must be an IPv4/IPv6 address in Phase 1".to_string());
            return result;
        }
    };
    let config = Config::default();
    let client = match Client::new(&config) {
        Ok(c) => c,
        Err(e) => {
            result.error = Some(format!("ICMP client: {e}"));
            return result;
        }
    };
    let mut pinger = client.pinger(ip, PingIdentifier(42)).await;
    pinger.timeout(Duration::from_secs(2));

    for i in 0..probes {
        match pinger.ping(PingSequence(i as u16), &[]).await {
            Ok((IcmpPacket::V4(_), dur)) | Ok((IcmpPacket::V6(_), dur)) => {
                let ms = dur.as_secs_f64() * 1000.0;
                result.samples_ms.push(ms);
            }
            Err(_) => {}
        }
        if i + 1 < probes {
            tokio::time::sleep(Duration::from_millis(interval_ms)).await;
        }
    }

    finalize_probe_result(&mut result, probes);
    result
}

fn finalize_probe_result(result: &mut LatencyProbeResult, probes: u32) {
    result.received = result.samples_ms.len() as u32;
    if result.received == 0 {
        result.error = Some(
            "No ICMP replies (host may block echo, or run as Administrator)".to_string(),
        );
        return;
    }
    result.packet_loss_pct =
        ((probes - result.received) as f64 / probes as f64) * 100.0;
    result.samples_ms.sort_by(|a, b| a.partial_cmp(b).unwrap());
    let n = result.samples_ms.len();
    result.min_ms = Some(result.samples_ms[0]);
    result.max_ms = Some(result.samples_ms[n - 1]);
    result.mean_ms = Some(result.samples_ms.iter().sum::<f64>() / n as f64);
    result.median_ms = Some(percentile(&result.samples_ms, 50.0));
    result.p90_ms = Some(percentile(&result.samples_ms, 90.0));
    result.p95_ms = Some(percentile(&result.samples_ms, 95.0));
    result.p99_ms = Some(percentile(&result.samples_ms, 99.0));
    if n > 1 {
        let mean = result.mean_ms.unwrap();
        let var = result
            .samples_ms
            .iter()
            .map(|v| {
                let d = v - mean;
                d * d
            })
            .sum::<f64>()
            / (n as f64 - 1.0);
        result.stddev_ms = Some(var.sqrt());
        let jitter_samples: Vec<f64> = result
            .samples_ms
            .windows(2)
            .map(|w| (w[1] - w[0]).abs())
            .collect();
        result.jitter_ms = Some(jitter_samples.iter().sum::<f64>() / jitter_samples.len() as f64);
    }
}

fn percentile(sorted: &[f64], pct: f64) -> f64 {
    if sorted.is_empty() {
        return 0.0;
    }
    let rank = ((pct / 100.0) * (sorted.len() as f64 - 1.0)).round() as usize;
    sorted[rank.min(sorted.len() - 1)]
}
