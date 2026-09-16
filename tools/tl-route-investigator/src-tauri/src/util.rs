use std::process::Command;
use std::sync::{LazyLock, Mutex};
use std::time::{Duration, Instant};

static ELEVATED_CACHE: LazyLock<Mutex<Option<(Instant, bool)>>> = LazyLock::new(|| Mutex::new(None));
const ELEVATED_TTL: Duration = Duration::from_secs(120);

pub fn run_powershell(script: &str) -> Result<String, String> {
    let output = Command::new("powershell")
        .args([
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy",
            "Bypass",
            "-Command",
            script,
        ])
        .output()
        .map_err(|e| format!("failed to start PowerShell: {e}"))?;
    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        return Err(format!("PowerShell error: {stderr}"));
    }
    Ok(String::from_utf8_lossy(&output.stdout).trim().to_string())
}

pub fn mask_ipv4(ip: &str) -> String {
    let parts: Vec<&str> = ip.split('.').collect();
    if parts.len() != 4 {
        return "masked".to_string();
    }
    format!("{}.xxx.xxx.{}", parts[0], parts[3])
}

fn is_elevated_uncached() -> bool {
    run_powershell(
        "[bool](([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator))",
    )
    .map(|s| s.eq_ignore_ascii_case("true"))
    .unwrap_or(false)
}

pub fn is_elevated() -> bool {
    if let Ok(guard) = ELEVATED_CACHE.lock() {
        if let Some((at, v)) = guard.as_ref() {
            if at.elapsed() < ELEVATED_TTL {
                return *v;
            }
        }
    }
    let v = is_elevated_uncached();
    if let Ok(mut guard) = ELEVATED_CACHE.lock() {
        *guard = Some((Instant::now(), v));
    }
    v
}
