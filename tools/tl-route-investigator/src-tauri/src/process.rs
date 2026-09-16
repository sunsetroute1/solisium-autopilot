use chrono::{DateTime, Local, TimeZone, Utc};
use regex::Regex;
use serde::Serialize;
use std::sync::{LazyLock, Mutex};
use std::time::{Duration, Instant};
use sysinfo::{ProcessRefreshKind, ProcessesToUpdate, System};

static PROCESS_CACHE: LazyLock<Mutex<Option<(Instant, Vec<ProcessRow>)>>> =
    LazyLock::new(|| Mutex::new(None));
const PROCESS_CACHE_TTL: Duration = Duration::from_secs(10);

#[derive(Debug, Clone, Serialize)]
pub struct ProcessRow {
    pub pid: u32,
    pub name: String,
    pub exe_path: Option<String>,
    pub started_at: Option<String>,
    pub tl_candidate: bool,
    pub tl_match_reason: Option<String>,
}

pub fn list_processes_cached(refresh: bool) -> Vec<ProcessRow> {
    if !refresh {
        if let Ok(guard) = PROCESS_CACHE.lock() {
            if let Some((at, rows)) = guard.as_ref() {
                if at.elapsed() < PROCESS_CACHE_TTL {
                    return rows.clone();
                }
            }
        }
    }
    let rows = list_processes();
    if let Ok(mut guard) = PROCESS_CACHE.lock() {
        *guard = Some((Instant::now(), rows.clone()));
    }
    rows
}

pub fn list_processes() -> Vec<ProcessRow> {
    let mut system = System::new();
    system.refresh_processes_specifics(
        ProcessesToUpdate::All,
        true,
        ProcessRefreshKind::everything(),
    );
    let mut rows: Vec<ProcessRow> = system
        .processes()
        .iter()
        .map(|(pid, proc_)| {
            let name = proc_.name().to_string_lossy().to_string();
            let exe = proc_
                .exe()
                .map(|p| p.to_string_lossy().to_string());
            let started = {
                let secs = proc_.start_time();
                Utc.timestamp_opt(secs as i64, 0)
                    .single()
                    .map(|dt: DateTime<Utc>| dt.with_timezone(&Local).to_rfc3339())
            };
            let (tl_candidate, reason) = score_tl_candidate(&name, exe.as_deref());
            ProcessRow {
                pid: pid.as_u32(),
                name,
                exe_path: exe,
                started_at: started,
                tl_candidate,
                tl_match_reason: reason,
            }
        })
        .collect();
    rows.sort_by(|a, b| {
        b.tl_candidate
            .cmp(&a.tl_candidate)
            .then_with(|| tl_candidate_rank(b).cmp(&tl_candidate_rank(a)))
            .then_with(|| a.name.to_lowercase().cmp(&b.name.to_lowercase()))
    });
    rows
}

/// Prefer the main game executable over launcher helpers / crash reporters.
pub fn best_tl_candidate<'a>(rows: &'a [ProcessRow]) -> Option<&'a ProcessRow> {
    rows.iter()
        .filter(|r| r.tl_candidate)
        .max_by_key(|r| tl_candidate_rank(r))
}

fn tl_candidate_rank(row: &ProcessRow) -> i32 {
    let name = row.name.to_lowercase();
    let exe = row
        .exe_path
        .as_deref()
        .unwrap_or("")
        .to_lowercase();
    let hay = format!("{name} {exe}");
    if name == "tl.exe" || hay.contains("\\tl.exe") {
        return 100;
    }
    if hay.contains("tlgame") {
        return 90;
    }
    if hay.contains("throneandliberty") {
        return 85;
    }
    if name.contains("crashpad") || name.contains("epicwebhelper") {
        return 5;
    }
    if hay.contains("throne") || hay.contains("liberty") {
        return 50;
    }
    if hay.contains("ncsoft") {
        return 10;
    }
    1
}

fn score_tl_candidate(name: &str, exe: Option<&str>) -> (bool, Option<String>) {
    let hay = format!(
        "{} {}",
        name.to_lowercase(),
        exe.unwrap_or("").to_lowercase()
    );
    let patterns: &[(&str, &str)] = &[
        (r"throne", "name/path contains 'throne'"),
        (r"liberty", "name/path contains 'liberty'"),
        (r"tlgame", "name/path contains 'tlgame'"),
        (r"\\tl\\", "path under TL install folder"),
        (r"throneandliberty", "throneandliberty token"),
        (r"ncsoft", "NCSoft-related path (weak hint)"),
    ];
    for (pat, reason) in patterns {
        if Regex::new(pat).map(|re| re.is_match(&hay)).unwrap_or(false) {
            return (true, Some(reason.to_string()));
        }
    }
    (false, None)
}
