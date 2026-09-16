pub mod connections;
mod db;
mod error;
pub mod network;
pub mod probe;
pub mod process;
pub mod reference;
pub mod monitor;
pub mod report;
pub mod analysis;
pub mod util;

use connections::ConnectionRow;
use db::{MeasurementSummary, RouteDetail, RouteSummary};
use monitor::{StudyController, StudySampleRow, StudyStatus, StudySummary};
use probe::traceroute::RouteTraceResult;
use network::ip_intel::IpIntelRecord;
use network::local::LocalNetworkSnapshot;
use network::public_ip::PublicNetworkSnapshot;
use probe::latency::LatencyProbeResult;
use process::ProcessRow;
use serde::Serialize;
use std::sync::Mutex;
use std::time::{Duration, Instant};
use tauri::State;

struct AppState {
    selected_pid: Mutex<Option<u32>>,
    include_public_ip: Mutex<bool>,
    active_session: Mutex<Option<i64>>,
    public_cache: Mutex<Option<(PublicNetworkSnapshot, Instant)>>,
    last_gateway_latency: Mutex<Option<LatencyProbeResult>>,
}

#[derive(Debug, Serialize)]
struct Phase1Snapshot {
    elevated: bool,
    local: LocalNetworkSnapshot,
    public: PublicNetworkSnapshot,
    processes: Vec<ProcessRow>,
    selected_pid: Option<u32>,
    connections: Vec<ConnectionRow>,
    gateway_latency: Option<LatencyProbeResult>,
    connections_error: Option<String>,
    active_session_id: Option<i64>,
    session_connection_rows: u64,
    db_path: String,
}

#[derive(Debug, Serialize)]
struct LiveTick {
    elevated: bool,
    selected_pid: Option<u32>,
    connections: Vec<ConnectionRow>,
    connections_error: Option<String>,
    active_session_id: Option<i64>,
    session_connection_rows: u64,
    tick_at: String,
}

fn resolve_selected_pid(state: &AppState, processes: Option<&[ProcessRow]>) -> Result<Option<u32>, String> {
    let mut selected_pid = *state.selected_pid.lock().map_err(|_| "lock")?;
    if selected_pid.is_none() {
        if let Some(procs) = processes {
            if let Some(pid) = process::best_tl_candidate(procs).map(|p| p.pid) {
                *state.selected_pid.lock().map_err(|_| "lock")? = Some(pid);
                selected_pid = Some(pid);
            }
        }
    }
    Ok(selected_pid)
}

fn poll_connections(
    state: &AppState,
    selected_pid: Option<u32>,
    elevated: bool,
) -> Result<(Vec<ConnectionRow>, Option<String>), String> {
    if let Some(pid) = selected_pid {
        match connections::connections_for_pid(pid, elevated) {
            Ok(rows) => {
                if let Some(sid) = *state.active_session.lock().map_err(|_| "lock")? {
                    db::upsert_connections(sid, &rows)?;
                }
                Ok((rows, None))
            }
            Err(e) => Ok((Vec::new(), Some(e))),
        }
    } else {
        Ok((Vec::new(), None))
    }
}

#[tauri::command]
fn list_processes_cmd(refresh: Option<bool>) -> Vec<ProcessRow> {
    process::list_processes_cached(refresh.unwrap_or(true))
}

#[tauri::command]
fn select_process(pid: u32, state: State<'_, AppState>) -> Result<(), String> {
    *state.selected_pid.lock().map_err(|_| "lock")? = Some(pid);
    Ok(())
}

#[tauri::command]
fn set_include_public_ip(include: bool, state: State<'_, AppState>) -> Result<(), String> {
    *state.include_public_ip.lock().map_err(|_| "lock")? = include;
    *state.public_cache.lock().map_err(|_| "lock")? = None;
    Ok(())
}

#[tauri::command]
fn start_monitoring_session(state: State<'_, AppState>) -> Result<i64, String> {
    let processes = process::list_processes_cached(false);
    let pid = resolve_selected_pid(&state, Some(&processes))?;
    let name = pid.and_then(|p| {
        processes
            .iter()
            .find(|r| r.pid == p)
            .map(|r| r.name.as_str())
    });
    let id = db::start_session(pid, name)?;
    *state.active_session.lock().map_err(|_| "lock")? = Some(id);
    Ok(id)
}

#[tauri::command]
fn stop_monitoring_session(state: State<'_, AppState>) -> Result<(), String> {
    if let Some(id) = *state.active_session.lock().map_err(|_| "lock")? {
        db::end_session(id)?;
    }
    *state.active_session.lock().map_err(|_| "lock")? = None;
    Ok(())
}

#[tauri::command]
fn set_endpoint_user_tag(
    remote_ip: String,
    remote_port: u16,
    protocol: String,
    user_tag: String,
) -> Result<(), String> {
    let allowed = ["unknown", "likely_gameplay", "not_gameplay"];
    if !allowed.contains(&user_tag.as_str()) {
        return Err("Invalid tag".to_string());
    }
    db::set_endpoint_tag(&remote_ip, remote_port, &protocol, &user_tag)
}

#[tauri::command]
fn poll_live_tick(state: State<'_, AppState>) -> Result<LiveTick, String> {
    let elevated = util::is_elevated();
    let selected_pid = resolve_selected_pid(&state, None)?;
    let (connections, connections_error) = poll_connections(&state, selected_pid, elevated)?;
    let active_session_id = *state.active_session.lock().map_err(|_| "lock")?;
    let session_connection_rows = active_session_id
        .map(db::session_connection_count)
        .transpose()?
        .unwrap_or(0);
    Ok(LiveTick {
        elevated,
        selected_pid,
        connections,
        connections_error,
        active_session_id,
        session_connection_rows,
        tick_at: chrono::Utc::now().to_rfc3339(),
    })
}

#[tauri::command]
async fn collect_phase1_snapshot(
    state: State<'_, AppState>,
    measure_gateway: Option<bool>,
    refresh_processes: Option<bool>,
    refresh_local: Option<bool>,
) -> Result<Phase1Snapshot, String> {
    let elevated = util::is_elevated();
    let local = network::local::collect_local_cached(elevated, refresh_local.unwrap_or(false))?;
    let include_public = *state.include_public_ip.lock().map_err(|_| "lock")?;
    let need_fetch = {
        let cache = state.public_cache.lock().map_err(|_| "lock")?;
        cache
            .as_ref()
            .map(|(_, t)| t.elapsed() > Duration::from_secs(300))
            .unwrap_or(true)
    };
    let public = if need_fetch {
        let snap = network::public_ip::collect_public(include_public).await;
        *state.public_cache.lock().map_err(|_| "lock")? = Some((snap.clone(), Instant::now()));
        snap
    } else {
        state
            .public_cache
            .lock()
            .map_err(|_| "lock")?
            .as_ref()
            .map(|(s, _)| s.clone())
            .unwrap_or_default()
    };

    let processes = process::list_processes_cached(refresh_processes.unwrap_or(false));
    let selected_pid = resolve_selected_pid(&state, Some(&processes))?;
    let (connections, connections_error) = poll_connections(&state, selected_pid, elevated)?;

    let do_gw = measure_gateway.unwrap_or(false);
    let gateway_latency = if do_gw {
        if let Some(gw) = local.default_gateway_ipv4.clone() {
            let lat = probe::latency::icmp_probe_quick(&gw, 5).await;
            *state.last_gateway_latency.lock().map_err(|_| "lock")? = Some(lat.clone());
            Some(lat)
        } else {
            None
        }
    } else {
        state
            .last_gateway_latency
            .lock()
            .map_err(|_| "lock")?
            .clone()
    };

    let active_session_id = *state.active_session.lock().map_err(|_| "lock")?;
    let session_connection_rows = active_session_id
        .map(db::session_connection_count)
        .transpose()?
        .unwrap_or(0);

    Ok(Phase1Snapshot {
        elevated,
        local,
        public,
        processes,
        selected_pid,
        connections,
        gateway_latency,
        connections_error,
        active_session_id,
        session_connection_rows,
        db_path: db::db_path().to_string_lossy().to_string(),
    })
}

#[tauri::command]
async fn run_latency_probe(
    target: String,
    probes: u32,
    save: Option<bool>,
    note: Option<String>,
    state: State<'_, AppState>,
) -> Result<LatencyProbeResult, String> {
    let count = probes.clamp(1, 1000);
    let result = probe::latency::icmp_probe(&target, count).await;
    if save.unwrap_or(false) {
        let session_id = *state.active_session.lock().map_err(|_| "lock")?;
        db::insert_measurement(session_id, &result, note.as_deref())?;
    }
    Ok(result)
}

#[tauri::command]
async fn lookup_ips_intel(
    ips: Vec<String>,
    force_refresh: Option<bool>,
) -> Result<Vec<IpIntelRecord>, String> {
    Ok(network::ip_intel::lookup_ips(ips, force_refresh.unwrap_or(false)).await)
}

#[tauri::command]
fn load_cached_ip_intel(ips: Vec<String>) -> Result<Vec<IpIntelRecord>, String> {
    db::get_ip_intel_cached_many(&ips)
}

#[tauri::command]
async fn probe_endpoints_latency(
    ips: Vec<String>,
    probes: Option<u32>,
    save: Option<bool>,
    note: Option<String>,
    state: State<'_, AppState>,
) -> Result<Vec<LatencyProbeResult>, String> {
    let count = probes.unwrap_or(100).clamp(1, 1000);
    let session_id = *state.active_session.lock().map_err(|_| "lock")?;
    let mut out = Vec::new();
    for ip in ips {
        if !network::ip_intel::is_lookupable_ip(&ip) {
            continue;
        }
        let result = probe::latency::icmp_probe(&ip, count).await;
        if save.unwrap_or(true) {
            db::insert_measurement(session_id, &result, note.as_deref())?;
        }
        out.push(result);
    }
    Ok(out)
}

#[tauri::command]
fn list_recent_measurements(limit: Option<u32>) -> Result<Vec<MeasurementSummary>, String> {
    db::recent_measurements(limit.unwrap_or(30))
}

#[tauri::command]
async fn run_traceroute(
    target: String,
    use_pathping: Option<bool>,
    save: Option<bool>,
    note: Option<String>,
    state: State<'_, AppState>,
) -> Result<RouteTraceResult, String> {
    let trace = if use_pathping.unwrap_or(false) {
        probe::traceroute::pathping_windows(&target, 30, 5).await
    } else {
        probe::traceroute::tracert_windows(&target, 30).await
    };
    if save.unwrap_or(true) {
        let session_id = *state.active_session.lock().map_err(|_| "lock")?;
        db::insert_route_trace(session_id, &trace, note.as_deref())?;
    }
    Ok(trace)
}

#[tauri::command]
fn list_recent_routes(limit: Option<u32>) -> Result<Vec<RouteSummary>, String> {
    db::recent_routes(limit.unwrap_or(20))
}

#[tauri::command]
fn get_route_detail(route_id: i64) -> Result<RouteDetail, String> {
    db::route_detail(route_id)
}

#[tauri::command]
async fn list_reference_targets() -> Result<Vec<reference::ResolvedReference>, String> {
    Ok(reference::resolve_all_references().await)
}

#[tauri::command]
async fn start_monitor_study(
    duration_minutes: u64,
    interval_minutes: u64,
    target_ips: Vec<String>,
    include_gateway: Option<bool>,
    state: State<'_, AppState>,
    study: State<'_, StudyController>,
) -> Result<StudySummary, String> {
    let session_id = *state.active_session.lock().map_err(|_| "lock")?;
    monitor::start_study(
        &study,
        session_id,
        duration_minutes,
        interval_minutes,
        target_ips,
        include_gateway.unwrap_or(true),
    )
    .await
}

#[tauri::command]
fn stop_monitor_study(study: State<'_, StudyController>) -> Result<(), String> {
    monitor::stop_study(&study)
}

#[tauri::command]
fn get_monitor_study_status(study: State<'_, StudyController>) -> Result<StudyStatus, String> {
    monitor::study_status(&study)
}

#[tauri::command]
fn get_monitor_study_samples(
    study_id: i64,
    target_ip: Option<String>,
) -> Result<Vec<StudySampleRow>, String> {
    monitor::study_samples(study_id, target_ip)
}

#[tauri::command]
fn list_monitor_studies(limit: Option<u32>) -> Result<Vec<StudySummary>, String> {
    monitor::list_studies(limit.unwrap_or(10))
}

#[tauri::command]
async fn run_reference_comparison(
    game_ips: Vec<String>,
    probes: Option<u32>,
    include_traceroute: Option<bool>,
    state: State<'_, AppState>,
) -> Result<reference::ComparisonReport, String> {
    let count = probes.unwrap_or(50).clamp(10, 200);
    let report = reference::run_comparison(
        game_ips,
        count,
        include_traceroute.unwrap_or(false),
    )
    .await;
    let session_id = *state.active_session.lock().map_err(|_| "lock")?;
    for row in &report.references {
        if row.reference.resolved_ipv4.is_some() {
            let note = format!("reference:{}", row.reference.id);
            let _ = db::insert_measurement(session_id, &row.latency, Some(&note));
        }
    }
    for row in &report.game_endpoints {
        let note = "game_comparison".to_string();
        let _ = db::insert_measurement(session_id, &row.latency, Some(&note));
    }
    Ok(report)
}

#[derive(Debug, Serialize)]
struct ExportReportResult {
    saved: bool,
    path: Option<String>,
}

#[tauri::command]
async fn export_diagnostic_report(
    format: String,
    include_public_ip: bool,
) -> Result<ExportReportResult, String> {
    let report = report::build_report(include_public_ip).await?;
    let fmt = report::ReportFormat::from_str(&format);
    let path = report::export_report_to_path(&report, fmt)?;
    Ok(ExportReportResult {
        saved: path.is_some(),
        path,
    })
}

#[tauri::command]
fn analyze_relay_opportunities_cmd(game_ips: Vec<String>) -> Result<analysis::RelayAnalysisReport, String> {
    analysis::analyze_relay_opportunities(game_ips)
}

#[tauri::command]
async fn preview_diagnostic_report_html(include_public_ip: bool) -> Result<String, String> {
    let report = report::build_report(include_public_ip).await?;
    report::render(&report, report::ReportFormat::Html)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .manage(AppState {
            selected_pid: Mutex::new(None),
            include_public_ip: Mutex::new(false),
            active_session: Mutex::new(None),
            public_cache: Mutex::new(None),
            last_gateway_latency: Mutex::new(None),
        })
        .manage(StudyController::default())
        .invoke_handler(tauri::generate_handler![
            list_processes_cmd,
            select_process,
            set_include_public_ip,
            start_monitoring_session,
            stop_monitoring_session,
            set_endpoint_user_tag,
            poll_live_tick,
            collect_phase1_snapshot,
            run_latency_probe,
            lookup_ips_intel,
            load_cached_ip_intel,
            probe_endpoints_latency,
            list_recent_measurements,
            run_traceroute,
            list_recent_routes,
            get_route_detail,
            list_reference_targets,
            run_reference_comparison,
            start_monitor_study,
            stop_monitor_study,
            get_monitor_study_status,
            get_monitor_study_samples,
            list_monitor_studies,
            export_diagnostic_report,
            preview_diagnostic_report_html,
            analyze_relay_opportunities_cmd,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
