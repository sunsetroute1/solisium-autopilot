use serde::{Deserialize, Serialize};
use std::sync::Mutex;
use std::time::{Duration, Instant};

use crate::util::{mask_ipv4, run_powershell};

#[derive(Debug, Clone, Serialize, Default)]
pub struct PublicNetworkSnapshot {
    pub public_ipv4: Option<String>,
    pub public_ipv4_masked: Option<String>,
    pub isp: Option<String>,
    pub org: Option<String>,
    pub asn: Option<String>,
    pub country: Option<String>,
    pub region: Option<String>,
    pub city: Option<String>,
    pub source: String,
    pub approximate: bool,
    pub lookup_error: Option<String>,
}

#[derive(Debug, Deserialize)]
struct IpWhoResponse {
    success: Option<bool>,
    ip: Option<String>,
    connection: Option<IpWhoConnection>,
    country: Option<String>,
    region: Option<String>,
    city: Option<String>,
}

#[derive(Debug, Deserialize)]
struct IpWhoConnection {
    isp: Option<String>,
    org: Option<String>,
    asn: Option<u64>,
}

static CACHE: Mutex<Option<(Instant, PublicNetworkSnapshot)>> = Mutex::new(None);
const CACHE_TTL: Duration = Duration::from_secs(300);

pub async fn collect_public(include_public_ip: bool) -> PublicNetworkSnapshot {
    if let Ok(guard) = CACHE.lock() {
        if let Some((at, snap)) = guard.as_ref() {
            if at.elapsed() < CACHE_TTL {
                return mask_snapshot(snap.clone(), include_public_ip);
            }
        }
    }

    let mut snap = match fetch_ipwho().await {
        Ok(s) => s,
        Err(err) => {
            let mut fallback = PublicNetworkSnapshot {
                lookup_error: Some(err),
                approximate: true,
                source: "none".to_string(),
                ..Default::default()
            };
            if let Ok(ip) = cloudflare_ip_only().await {
                fallback.public_ipv4 = Some(ip.clone());
                fallback.public_ipv4_masked = Some(mask_ipv4(&ip));
                fallback.source = "cloudflare trace (IP only)".to_string();
            }
            fallback
        }
    };

    if !include_public_ip {
        snap.public_ipv4 = None;
    }
    if let Ok(mut guard) = CACHE.lock() {
        *guard = Some((Instant::now(), snap.clone()));
    }
    snap
}

fn mask_snapshot(mut snap: PublicNetworkSnapshot, include_public_ip: bool) -> PublicNetworkSnapshot {
    if !include_public_ip {
        snap.public_ipv4 = None;
    }
    snap
}

async fn fetch_ipwho() -> Result<PublicNetworkSnapshot, String> {
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(8))
        .build()
        .map_err(|e| e.to_string())?;
    let resp: IpWhoResponse = client
        .get("https://ipwho.is/")
        .send()
        .await
        .map_err(|e| e.to_string())?
        .json()
        .await
        .map_err(|e| e.to_string())?;
    if resp.success == Some(false) {
        return Err("ipwho.is returned success=false".to_string());
    }
    let ip = resp.ip.ok_or("ipwho.is missing ip")?;
    let conn = resp.connection.unwrap_or(IpWhoConnection {
        isp: None,
        org: None,
        asn: None,
    });
    Ok(PublicNetworkSnapshot {
        public_ipv4: Some(ip.clone()),
        public_ipv4_masked: Some(mask_ipv4(&ip)),
        isp: conn.isp,
        org: conn.org,
        asn: conn.asn.map(|a| format!("AS{a}")),
        country: resp.country,
        region: resp.region,
        city: resp.city,
        source: "ipwho.is (approximate geo/ISP)".to_string(),
        approximate: true,
        lookup_error: None,
    })
}

async fn cloudflare_ip_only() -> Result<String, String> {
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(6))
        .build()
        .map_err(|e| e.to_string())?;
    let text = client
        .get("https://1.1.1.1/cdn-cgi/trace")
        .send()
        .await
        .map_err(|e| e.to_string())?
        .text()
        .await
        .map_err(|e| e.to_string())?;
    for line in text.lines() {
        if let Some(ip) = line.strip_prefix("ip=") {
            return Ok(ip.trim().to_string());
        }
    }
    Err("cloudflare trace missing ip".to_string())
}

pub fn default_gateway_latency_hint() -> Option<String> {
    run_powershell("(Get-NetRoute -DestinationPrefix '0.0.0.0/0' | Sort-Object RouteMetric | Select-Object -First 1).NextHop")
        .ok()
        .filter(|s| !s.is_empty())
}
