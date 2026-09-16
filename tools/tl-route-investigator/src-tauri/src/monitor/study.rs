use serde::Serialize;
use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use crate::db::{self, MonitorEventRow, MonitorSampleRow, MonitorStudyRow};
use crate::network::local;
use crate::probe;

pub struct StudyController {
    running: Mutex<Arc<AtomicBool>>,
    study_id: Mutex<Option<i64>>,
}

impl Default for StudyController {
    fn default() -> Self {
        Self {
            running: Mutex::new(Arc::new(AtomicBool::new(false))),
            study_id: Mutex::new(None),
        }
    }
}

impl StudyController {
    fn running_flag(&self) -> Arc<AtomicBool> {
        self.running.lock().unwrap().clone()
    }

    fn set_running_flag(&self, flag: Arc<AtomicBool>) {
        *self.running.lock().unwrap() = flag;
    }

    pub fn stop(&self) {
        self.running_flag().store(false, Ordering::SeqCst);
    }

    pub fn active_study_id(&self) -> Option<i64> {
        self.study_id.lock().ok()?.clone()
    }

    pub fn set_study_id(&self, id: Option<i64>) {
        if let Ok(mut g) = self.study_id.lock() {
            *g = id;
        }
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct StudySummary {
    pub id: i64,
    pub started_at: String,
    pub ends_at: String,
    pub interval_secs: u64,
    pub status: String,
    pub target_ips: Vec<String>,
}

impl From<MonitorStudyRow> for StudySummary {
    fn from(r: MonitorStudyRow) -> Self {
        StudySummary {
            id: r.id,
            started_at: r.started_at,
            ends_at: r.ends_at,
            interval_secs: r.interval_secs,
            status: r.status,
            target_ips: r.target_ips,
        }
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct StudyStatus {
    pub active: bool,
    pub study: Option<StudySummary>,
    pub samples_recorded: u64,
    pub recent_events: Vec<MonitorEventRow>,
}

pub type StudySampleRow = MonitorSampleRow;
pub type StudyEventRow = MonitorEventRow;

pub fn stop_study(controller: &StudyController) -> Result<(), String> {
    controller.stop();
    if let Some(id) = controller.active_study_id() {
        db::finish_monitor_study(id, "stopped")?;
    }
    controller.set_study_id(None);
    Ok(())
}

pub async fn start_study(
    controller: &StudyController,
    session_id: Option<i64>,
    duration_minutes: u64,
    interval_minutes: u64,
    mut target_ips: Vec<String>,
    include_gateway: bool,
) -> Result<StudySummary, String> {
    stop_study(controller)?;

    if include_gateway {
        let elevated = crate::util::is_elevated();
        if let Ok(local) = local::collect_local(elevated) {
            if let Some(gw) = local.default_gateway_ipv4 {
                if !target_ips.contains(&gw) {
                    target_ips.push(gw);
                }
            }
        }
    }

    target_ips.sort();
    target_ips.dedup();
    if target_ips.is_empty() {
        return Err("No targets — add game IPs or enable gateway.".to_string());
    }

    let duration_minutes = duration_minutes.clamp(5, 24 * 60);
    let interval_secs = interval_minutes.clamp(1, 60) * 60;
    let study_id = db::create_monitor_study(
        session_id,
        duration_minutes,
        interval_secs,
        &target_ips,
    )?;

    let running = Arc::new(AtomicBool::new(true));
    controller.set_running_flag(running.clone());
    controller.set_study_id(Some(study_id));

    let ends_at = chrono::Utc::now() + chrono::Duration::minutes(duration_minutes as i64);
    let targets = target_ips.clone();

    tauri::async_runtime::spawn(async move {
        study_loop(study_id, targets, interval_secs, ends_at, running).await;
    });

    Ok(db::get_monitor_study(study_id)?.into())
}

async fn study_loop(
    study_id: i64,
    targets: Vec<String>,
    interval_secs: u64,
    ends_at: chrono::DateTime<chrono::Utc>,
    running: Arc<AtomicBool>,
) {
    let mut last_fp: HashMap<String, String> = HashMap::new();
    let mut cycle: u32 = 0;
    while running.load(Ordering::SeqCst) && chrono::Utc::now() < ends_at {
        for ip in &targets {
            if !running.load(Ordering::SeqCst) {
                break;
            }
            let lat = probe::latency::icmp_probe(ip, 20).await;
            let trace_every = 3;
            let fingerprint = if cycle % trace_every == 0 {
                probe::traceroute::tracert_windows(ip, 22).await.fingerprint_asn
            } else {
                last_fp.get(ip).cloned().unwrap_or_default()
            };
            let prev = last_fp.get(ip);
            let route_changed =
                prev.map(|p| p != &fingerprint).unwrap_or(false) && prev.is_some();
            if route_changed {
                let detail = format!("ASN path: {} → {}", prev.unwrap(), fingerprint);
                let _ = db::insert_monitor_event(study_id, ip, "route_change", &detail);
            }
            if !fingerprint.is_empty() {
                last_fp.insert(ip.clone(), fingerprint.clone());
            }
            let _ = db::insert_monitor_sample(
                study_id,
                ip,
                &lat,
                if fingerprint.is_empty() {
                    None
                } else {
                    Some(&fingerprint)
                },
                route_changed,
            );
        }
        cycle += 1;
        tokio::time::sleep(Duration::from_secs(interval_secs)).await;
    }
    running.store(false, Ordering::SeqCst);
    let status = if chrono::Utc::now() >= ends_at {
        "completed"
    } else {
        "stopped"
    };
    let _ = db::finish_monitor_study(study_id, status);
}

pub fn study_status(controller: &StudyController) -> Result<StudyStatus, String> {
    let study_id = controller.active_study_id();
    let study = study_id
        .map(db::get_monitor_study)
        .transpose()?
        .map(StudySummary::from);
    let active = study
        .as_ref()
        .map(|s| s.status == "running")
        .unwrap_or(false);
    let samples_recorded = study_id
        .map(db::monitor_sample_count)
        .transpose()?
        .unwrap_or(0);
    let recent_events = study_id
        .map(|id| db::recent_monitor_events(id, 20))
        .transpose()?
        .unwrap_or_default();
    Ok(StudyStatus {
        active,
        study,
        samples_recorded,
        recent_events,
    })
}

pub fn study_samples(study_id: i64, target_ip: Option<String>) -> Result<Vec<StudySampleRow>, String> {
    db::list_monitor_samples(study_id, target_ip.as_deref())
}

pub fn list_studies(limit: u32) -> Result<Vec<StudySummary>, String> {
    Ok(db::list_monitor_studies(limit)?
        .into_iter()
        .map(StudySummary::from)
        .collect())
}
