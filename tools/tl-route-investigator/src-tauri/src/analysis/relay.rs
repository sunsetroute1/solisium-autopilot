use serde::Serialize;

use crate::db;
use crate::reference::targets::{self, ReferenceKind, ReferenceTarget};

const MIN_DELTA_MS: f64 = 12.0;
const STABILITY_P95_GAP_MS: f64 = 20.0;

#[derive(Debug, Clone, Serialize)]
pub struct RelayAnalysisReport {
    pub generated_at: String,
    pub disclaimer: String,
    pub game_targets: Vec<TargetLatencySummary>,
    pub reference_baselines: Vec<TargetLatencySummary>,
    pub neutral_baselines: Vec<TargetLatencySummary>,
    pub opportunities: Vec<RelayOpportunity>,
    pub limitations: Vec<String>,
    pub methodology: Methodology,
}

#[derive(Debug, Clone, Serialize)]
pub struct Methodology {
    pub icmp_note: String,
    pub delta_threshold_ms: f64,
    pub p95_gap_threshold_ms: f64,
    pub composite_formula: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct TargetLatencySummary {
    pub label: String,
    pub ip: String,
    pub kind: String,
    pub median_ms: Option<f64>,
    pub p95_ms: Option<f64>,
    pub packet_loss_pct: f64,
    pub probed_at: String,
    pub route_fingerprint_asn: Option<String>,
    pub source: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct RelayOpportunity {
    pub opportunity_type: String,
    pub location_label: String,
    pub summary: String,
    pub evidence: String,
    pub confidence: String,
}

pub fn analyze_relay_opportunities(game_ips: Vec<String>) -> Result<RelayAnalysisReport, String> {
    let latest = db::latest_measurement_per_target(100)?;
    let routes = db::recent_routes(30)?;
    let route_by_ip: std::collections::HashMap<String, String> = routes
        .into_iter()
        .filter_map(|r| r.fingerprint_asn.map(|fp| (r.target_ip, fp)))
        .collect();

    let ref_catalog: std::collections::HashMap<String, ReferenceTarget> = targets::all_references()
        .into_iter()
        .map(|t| (t.id.clone(), t))
        .collect();

    let mut game_targets = Vec::new();
    let mut reference_baselines = Vec::new();
    let mut neutral_baselines = Vec::new();

    for m in &latest {
        let is_game_ip = game_ips.contains(&m.target_ip);
        if let Some((kind, label)) = classify_measurement(m, &ref_catalog, is_game_ip) {
            let entry = TargetLatencySummary {
                label: label.clone(),
                ip: m.target_ip.clone(),
                kind: kind.to_string(),
                median_ms: m.median_ms,
                p95_ms: m.p95_ms,
                packet_loss_pct: m.packet_loss_pct,
                probed_at: m.probed_at.clone(),
                route_fingerprint_asn: route_by_ip.get(&m.target_ip).cloned(),
                source: m.note.clone().unwrap_or_else(|| "measurement".to_string()),
            };
            match kind {
                "game" => game_targets.push(entry),
                "aws_reference" => reference_baselines.push(entry),
                "neutral" => neutral_baselines.push(entry),
                _ => {}
            }
        }
    }

    // Explicit game IPs from live selection without measurements yet
    for ip in game_ips {
        if !game_targets.iter().any(|g| g.ip == ip) {
            game_targets.push(TargetLatencySummary {
                label: format!("T&L socket {ip}"),
                ip: ip.clone(),
                kind: "game".to_string(),
                median_ms: None,
                p95_ms: None,
                packet_loss_pct: 0.0,
                probed_at: String::new(),
                route_fingerprint_asn: route_by_ip.get(&ip).cloned(),
                source: "live selection (no saved probe)".to_string(),
            });
        }
    }

    let mut opportunities = Vec::new();
    let mut limitations = vec![
        "Relay/VPN services use different paths than ICMP; these hints only justify further testing.".to_string(),
        "AWS reference hosts are not Throne and Liberty servers.".to_string(),
    ];

    if game_targets.iter().all(|g| g.median_ms.is_none()) {
        limitations.push(
            "No saved latency probes for game IPs — run Latency or AWS Comparison first.".to_string(),
        );
    }

    let game_medians: Vec<f64> = game_targets.iter().filter_map(|g| g.median_ms).collect();
    if !game_medians.is_empty() {
        let game_p50 = median(&game_medians);
        let game_p95_vals: Vec<f64> = game_targets.iter().filter_map(|g| g.p95_ms).collect();
        let game_p95 = if game_p95_vals.is_empty() {
            None
        } else {
            Some(median(&game_p95_vals))
        };

        let mut refs_sorted: Vec<&TargetLatencySummary> = reference_baselines
            .iter()
            .chain(neutral_baselines.iter())
            .filter(|r| r.median_ms.is_some())
            .collect();
        refs_sorted.sort_by(|a, b| {
            a.median_ms
                .unwrap_or(f64::MAX)
                .partial_cmp(&b.median_ms.unwrap_or(f64::MAX))
                .unwrap()
        });

        for r in refs_sorted {
            let Some(ref_p50) = r.median_ms else { continue };
            let delta = game_p50 - ref_p50;
            if delta >= MIN_DELTA_MS {
                opportunities.push(RelayOpportunity {
                    opportunity_type: "lower_icmp_rtt".to_string(),
                    location_label: r.label.clone(),
                    summary: format!(
                        "{location} shows ~{delta:.0} ms lower ICMP P50 than your game target median ({game_p50:.0} ms). Worth testing as a relay geography — not proven.",
                        location = r.label,
                    ),
                    evidence: format!(
                        "Game ICMP P50 ~{game_p50:.1} ms vs {} ({}) P50 ~{ref_p50:.1} ms on {at}.",
                        r.label, r.ip, at = r.probed_at
                    ),
                    confidence: if delta >= 25.0 { "medium" } else { "low" }.to_string(),
                });
            }

            if let (Some(gp95), Some(rp95)) = (game_p95, r.p95_ms) {
                if gp95 - rp95 >= STABILITY_P95_GAP_MS {
                    opportunities.push(RelayOpportunity {
                        opportunity_type: "tail_latency_gap".to_string(),
                        location_label: r.label.clone(),
                        summary: format!(
                            "P95 to {} is ~{:.0} ms below game P95 — tail latency may be better toward that geography (ICMP only).",
                            r.label,
                            gp95 - rp95
                        ),
                        evidence: format!("Game P95 ~{gp95:.1} ms vs {} P95 ~{rp95:.1} ms.", r.label),
                        confidence: "low".to_string(),
                    });
                }
            }
        }

        // Route path differs but latency similar — still note for ExitLag-style path optimization
        for g in &game_targets {
            let Some(game_fp) = &g.route_fingerprint_asn else { continue };
            for r in reference_baselines.iter().filter(|x| x.median_ms.is_some()) {
                let Some(ref_fp) = &r.route_fingerprint_asn else { continue };
                if ref_fp == game_fp {
                    continue;
                }
                if let (Some(gm), Some(rm)) = (g.median_ms, r.median_ms) {
                    if (gm - rm).abs() < 8.0 {
                        opportunities.push(RelayOpportunity {
                            opportunity_type: "alternate_asn_path".to_string(),
                            location_label: r.label.clone(),
                            summary: format!(
                                "Similar RTT to {} but different ASN path — path optimization might help even if raw ping does not.",
                                r.label
                            ),
                            evidence: format!(
                                "Game path: {game_fp}. Reference path: {ref_fp}. P50 within 8 ms (game {gm:.0}, ref {rm:.0})."
                            ),
                            confidence: "low".to_string(),
                        });
                    }
                }
            }
        }
    }

    opportunities.sort_by(|a, b| a.opportunity_type.cmp(&b.opportunity_type));
    opportunities.dedup_by(|a, b| a.summary == b.summary);

    Ok(RelayAnalysisReport {
        generated_at: chrono::Utc::now().to_rfc3339(),
        disclaimer: "These are test hypotheses based on ICMP and public reference endpoints. A relay is not guaranteed to improve Throne and Liberty.".to_string(),
        game_targets,
        reference_baselines,
        neutral_baselines,
        opportunities,
        limitations,
        methodology: Methodology {
            icmp_note: "All RTT values from saved ICMP probes unless noted.".to_string(),
            delta_threshold_ms: MIN_DELTA_MS,
            p95_gap_threshold_ms: STABILITY_P95_GAP_MS,
            composite_formula: None,
        },
    })
}

fn classify_measurement(
    m: &db::MeasurementSummary,
    catalog: &std::collections::HashMap<String, ReferenceTarget>,
    is_game_ip: bool,
) -> Option<(&'static str, String)> {
    if is_game_ip {
        return Some(("game", format!("Game {}", m.target_ip)));
    }
    if let Some(note) = &m.note {
        if note == "game_comparison" || note.contains("game") || note.starts_with("manual") {
            return Some(("game", format!("Game {}", m.target_ip)));
        }
        if let Some(id) = note.strip_prefix("reference:") {
            if let Some(t) = catalog.get(id) {
                let kind = match t.kind {
                    ReferenceKind::AwsRegionalReference => "aws_reference",
                    ReferenceKind::NeutralInternet => "neutral",
                };
                return Some((kind, t.label.clone()));
            }
        }
    }
    None
}

fn median(values: &[f64]) -> f64 {
    let mut v = values.to_vec();
    v.sort_by(|a, b| a.partial_cmp(b).unwrap());
    v[v.len() / 2]
}
