use serde::Serialize;

use crate::db;
use crate::network;
use crate::util::{self, mask_ipv4};

#[derive(Debug, Serialize)]
pub struct DiagnosticReport {
    pub generated_at: String,
    pub tool: String,
    pub include_public_ip: bool,
    pub connection: ConnectionSection,
    pub tl: TlSection,
    pub routes: Vec<RouteSection>,
    pub performance: PerformanceSection,
    pub monitoring: MonitoringSection,
    pub comparison_notes: Vec<String>,
    pub conclusions: Vec<String>,
    pub relay_analysis: Option<crate::analysis::RelayAnalysisReport>,
}

#[derive(Debug, Serialize)]
pub struct ConnectionSection {
    pub isp_approx: Option<String>,
    pub asn_approx: Option<String>,
    pub public_ipv4: Option<String>,
    pub gateway_ipv4: Option<String>,
    pub dns_ipv4: Vec<String>,
    pub lan_adapter: Option<String>,
    pub link_speed_mbps: Option<u64>,
    pub mtu_hints: Vec<String>,
    pub elevated: bool,
}

#[derive(Debug, Serialize)]
pub struct TlSection {
    pub session_id: Option<i64>,
    pub selected_pid: Option<u32>,
    pub process_name: Option<String>,
    pub connections: Vec<ConnectionEntry>,
    pub endpoints_user_tags: Vec<EndpointTagEntry>,
}

#[derive(Debug, Clone, Serialize)]
pub struct ConnectionEntry {
    pub protocol: String,
    pub local: String,
    pub remote: String,
    pub observations: i64,
    pub user_tag: String,
}

#[derive(Debug, Serialize)]
pub struct EndpointTagEntry {
    pub remote_ip: String,
    pub remote_port: u16,
    pub protocol: String,
    pub user_tag: String,
}

#[derive(Debug, Serialize)]
pub struct RouteSection {
    pub target_ip: String,
    pub probed_at: String,
    pub fingerprint_asn: Option<String>,
    pub fingerprint_raw: Option<String>,
    pub hop_count: u32,
    pub reached_destination: bool,
}

#[derive(Debug, Serialize)]
pub struct PerformanceSection {
    pub measurements: Vec<db::MeasurementSummary>,
}

#[derive(Debug, Serialize)]
pub struct MonitoringSection {
    pub studies: Vec<StudySection>,
}

#[derive(Debug, Serialize)]
pub struct StudySection {
    pub id: i64,
    pub started_at: String,
    pub ends_at: String,
    pub status: String,
    pub target_ips: Vec<String>,
    pub sample_count: u64,
    pub route_change_events: u64,
}

pub async fn build_report(include_public_ip: bool) -> Result<DiagnosticReport, String> {
    let elevated = util::is_elevated();
    let local = network::local::collect_local(elevated)?;
    let public = network::public_ip::collect_public(include_public_ip).await;

    let up = local
        .adapters
        .iter()
        .find(|a| a.is_up && a.media_type.to_lowercase().contains("802.3"))
        .or_else(|| local.adapters.iter().find(|a| a.is_up));

    let public_ip = if include_public_ip {
        public.public_ipv4.clone()
    } else {
        public
            .public_ipv4_masked
            .clone()
            .or_else(|| public.public_ipv4.as_ref().map(|ip| mask_ipv4(ip)))
    };

    let session = db::latest_session()?;
    let connections = session
        .as_ref()
        .map(|s| db::list_session_connections(s.id))
        .transpose()?
        .unwrap_or_default();
    let tag_rows = db::list_all_endpoint_tags()?;
    let tag_lookup: std::collections::HashMap<String, String> = tag_rows
        .iter()
        .map(|t| {
            (
                format!("{}|{}:{}", t.protocol, t.remote_ip, t.remote_port),
                t.user_tag.clone(),
            )
        })
        .collect();

    let mut conn_entries = Vec::new();
    for c in connections {
        let tag_key = format!("{}|{}:{}", c.protocol, c.remote_ip, c.remote_port);
        let user_tag = tag_lookup
            .get(&tag_key)
            .cloned()
            .unwrap_or_else(|| "unknown".to_string());
        conn_entries.push(ConnectionEntry {
            protocol: c.protocol,
            local: format!("{}:{}", c.local_ip, c.local_port),
            remote: format!("{}:{}", c.remote_ip, c.remote_port),
            observations: c.observations,
            user_tag,
        });
    }

    let routes: Vec<RouteSection> = db::recent_routes(15)?
        .into_iter()
        .map(|r| RouteSection {
            target_ip: r.target_ip,
            probed_at: r.probed_at,
            fingerprint_asn: r.fingerprint_asn,
            fingerprint_raw: None,
            hop_count: r.hop_count,
            reached_destination: r.reached_destination,
        })
        .collect();

    let measurements = db::recent_measurements(50)?;
    let studies_raw = db::list_monitor_studies(5)?;
    let mut studies = Vec::new();
    for s in studies_raw {
        let sample_count = db::monitor_sample_count(s.id)?;
        let events = db::recent_monitor_events(s.id, 500)?;
        let route_change_events = events
            .iter()
            .filter(|e| e.event_type == "route_change")
            .count() as u64;
        studies.push(StudySection {
            id: s.id,
            started_at: s.started_at,
            ends_at: s.ends_at,
            status: s.status,
            target_ips: s.target_ips,
            sample_count,
            route_change_events,
        });
    }

    let connection = ConnectionSection {
        isp_approx: public.isp,
        asn_approx: public.asn,
        public_ipv4: public_ip,
        gateway_ipv4: local.default_gateway_ipv4,
        dns_ipv4: local.dns_servers_ipv4,
        lan_adapter: up.map(|a| format!("{} · {} Mbps", a.name, a.link_speed_mbps.unwrap_or(0))),
        link_speed_mbps: up.and_then(|a| a.link_speed_mbps),
        mtu_hints: local.mtu_hints,
        elevated,
    };

    let game_ips: Vec<String> = conn_entries
        .iter()
        .filter(|c| c.user_tag == "likely_gameplay")
        .map(|c| c.remote.split(':').next().unwrap_or("").to_string())
        .filter(|ip| !ip.is_empty() && ip != "*")
        .collect();
    let relay_analysis = crate::analysis::analyze_relay_opportunities(game_ips).ok();

    let tl = TlSection {
        session_id: session.as_ref().map(|s| s.id),
        selected_pid: session.as_ref().and_then(|s| s.selected_pid.map(|p| p as u32)),
        process_name: session.as_ref().and_then(|s| s.process_name.clone()),
        connections: conn_entries,
        endpoints_user_tags: tag_rows
            .into_iter()
            .map(|t| EndpointTagEntry {
                remote_ip: t.remote_ip,
                remote_port: t.remote_port,
                protocol: t.protocol,
                user_tag: t.user_tag,
            })
            .collect(),
    };

    let mut conclusions = derive_conclusions(&tl, &measurements, &studies);
    if let Some(relay) = &relay_analysis {
        for op in relay.opportunities.iter().take(5) {
            conclusions.push(format!("{} — {}", op.summary, op.evidence));
        }
    }
    let comparison_notes = vec![
        "Reference and AWS comparison data (if collected) use ICMP and public endpoints — not verified game UDP paths.".to_string(),
    ];

    Ok(DiagnosticReport {
        generated_at: chrono::Utc::now().to_rfc3339(),
        tool: "TL Route Investigator 0.1.0".to_string(),
        include_public_ip,
        connection,
        tl,
        routes,
        performance: PerformanceSection { measurements },
        monitoring: MonitoringSection { studies },
        comparison_notes,
        conclusions,
        relay_analysis,
    })
}

fn derive_conclusions(
    tl: &TlSection,
    measurements: &[db::MeasurementSummary],
    studies: &[StudySection],
) -> Vec<String> {
    let mut out = Vec::new();
    if tl.connections.is_empty() {
        out.push(
            "No connection rows were stored for the latest session — run monitoring with the game active to capture sockets.".to_string(),
        );
    } else {
        out.push(format!(
            "Latest session recorded {} distinct connection row(s) from live observation (not inferred server list).",
            tl.connections.len()
        ));
    }

    let tagged: Vec<_> = tl
        .connections
        .iter()
        .filter(|c| c.user_tag == "likely_gameplay")
        .collect();
    if !tagged.is_empty() {
        out.push(format!(
            "You tagged {} endpoint(s) as likely gameplay — use these for latency/route comparisons.",
            tagged.len()
        ));
    }

    if let Some(m) = measurements.first() {
        if m.packet_loss_pct == 0.0 {
            if let Some(med) = m.median_ms {
                out.push(format!(
                    "Most recent saved probe to {} reported 0% ICMP loss with P50 {:.1} ms (ICMP may not match game traffic).",
                    m.target_ip, med
                ));
            }
        } else if m.packet_loss_pct >= 1.0 {
            out.push(format!(
                "Recent probe to {} showed {:.1}% ICMP loss — confirm whether the host blocks echo vs true loss.",
                m.target_ip, m.packet_loss_pct
            ));
        }
    }

    for s in studies {
        if s.route_change_events > 0 {
            out.push(format!(
                "Monitor study #{} logged {} route fingerprint change event(s) between samples.",
                s.id, s.route_change_events
            ));
        }
    }

    if out.len() == 1 && tl.connections.is_empty() {
        out.push("Insufficient evidence for ISP quality judgments — collect more samples over time.".to_string());
    }

    out
}
