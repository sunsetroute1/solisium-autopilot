use serde::{Deserialize, Serialize};
use std::net::IpAddr;
use std::time::Duration;

use crate::db;
use crate::util::run_powershell;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct IpIntelRecord {
    pub ip: String,
    pub reverse_dns: Option<String>,
    pub asn: Option<String>,
    pub isp: Option<String>,
    pub org: Option<String>,
    pub country: Option<String>,
    pub region: Option<String>,
    pub city: Option<String>,
    pub aws_region_hint: Option<String>,
    pub source: String,
    pub approximate: bool,
    pub fetched_at: String,
    pub lookup_error: Option<String>,
    pub from_cache: bool,
}

#[derive(Debug, Deserialize)]
struct IpWhoResponse {
    success: Option<bool>,
    message: Option<String>,
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

pub fn is_lookupable_ip(ip: &str) -> bool {
    if ip == "*" || ip.is_empty() {
        return false;
    }
    let Ok(addr) = ip.parse::<IpAddr>() else {
        return false;
    };
    match addr {
        IpAddr::V4(v4) => {
            let o = v4.octets();
            !(v4.is_private()
                || v4.is_loopback()
                || v4.is_link_local()
                || o[0] == 100 && o[1] >= 64 && o[1] <= 127) // CGNAT 100.64/10
        }
        IpAddr::V6(v6) => !(v6.is_loopback() || v6.is_unique_local()),
    }
}

pub async fn lookup_ip(ip: &str, force_refresh: bool) -> IpIntelRecord {
    if !is_lookupable_ip(ip) {
        return IpIntelRecord {
            ip: ip.to_string(),
            source: "skipped".to_string(),
            approximate: true,
            fetched_at: chrono::Utc::now().to_rfc3339(),
            lookup_error: Some("Private or non-lookupable address".to_string()),
            from_cache: false,
            reverse_dns: None,
            asn: None,
            isp: None,
            org: None,
            country: None,
            region: None,
            city: None,
            aws_region_hint: None,
        };
    }

    if !force_refresh {
        if let Ok(Some(cached)) = db::get_ip_intel_cached(ip) {
            if !db::ip_intel_cache_stale(&cached.fetched_at) {
                let mut rec = cached;
                rec.from_cache = true;
                return rec;
            }
        }
    }

    let reverse_dns = reverse_dns_lookup(ip);
    let mut rec = match fetch_ipwho(ip).await {
        Ok(mut r) => {
            r.reverse_dns = reverse_dns;
            r.aws_region_hint = aws_region_hint(r.org.as_deref(), r.region.as_deref(), r.city.as_deref());
            r
        }
        Err(e) => IpIntelRecord {
            ip: ip.to_string(),
            reverse_dns,
            asn: None,
            isp: None,
            org: None,
            country: None,
            region: None,
            city: None,
            aws_region_hint: None,
            source: "none".to_string(),
            approximate: true,
            fetched_at: chrono::Utc::now().to_rfc3339(),
            lookup_error: Some(e),
            from_cache: false,
        },
    };
    rec.from_cache = false;
    let _ = db::upsert_ip_intel_cache(&rec);
    rec
}

pub async fn lookup_ips(ips: Vec<String>, force_refresh: bool) -> Vec<IpIntelRecord> {
    let mut unique = ips;
    unique.sort();
    unique.dedup();
    let mut out = Vec::new();
    for ip in unique {
        out.push(lookup_ip(&ip, force_refresh).await);
        tokio::time::sleep(Duration::from_millis(250)).await;
    }
    out
}

async fn fetch_ipwho(ip: &str) -> Result<IpIntelRecord, String> {
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(8))
        .build()
        .map_err(|e| e.to_string())?;
    let url = format!("https://ipwho.is/{ip}");
    let resp: IpWhoResponse = client
        .get(&url)
        .send()
        .await
        .map_err(|e| e.to_string())?
        .json()
        .await
        .map_err(|e| e.to_string())?;
    if resp.success == Some(false) {
        return Err(resp
            .message
            .unwrap_or_else(|| "ipwho.is success=false".to_string()));
    }
    let conn = resp.connection.unwrap_or(IpWhoConnection {
        isp: None,
        org: None,
        asn: None,
    });
    let aws_hint = aws_region_hint(
        conn.org.as_deref(),
        resp.region.as_deref(),
        resp.city.as_deref(),
    );
    Ok(IpIntelRecord {
        ip: ip.to_string(),
        reverse_dns: None,
        asn: conn.asn.map(|a| format!("AS{a}")),
        isp: conn.isp,
        org: conn.org,
        country: resp.country,
        region: resp.region,
        city: resp.city,
        aws_region_hint: aws_hint,
        source: "ipwho.is (approximate geo/ASN)".to_string(),
        approximate: true,
        fetched_at: chrono::Utc::now().to_rfc3339(),
        lookup_error: None,
        from_cache: false,
    })
}

fn reverse_dns_lookup(ip: &str) -> Option<String> {
    let script = format!(
        "try {{ [System.Net.Dns]::GetHostEntry('{ip}').HostName }} catch {{ '' }}"
    );
    run_powershell(&script)
        .ok()
        .filter(|s| !s.is_empty())
}

fn aws_region_hint(org: Option<&str>, region: Option<&str>, city: Option<&str>) -> Option<String> {
    let org_l = org.unwrap_or("").to_lowercase();
    if !(org_l.contains("amazon") || org_l.contains("aws")) {
        return None;
    }
    let mut parts: Vec<&str> = Vec::new();
    if let Some(r) = region {
        parts.push(r);
    }
    if let Some(c) = city {
        parts.push(c);
    }
    if parts.is_empty() {
        Some("AWS (region unknown from geo — approximate)".to_string())
    } else {
        Some(format!(
            "AWS-ish location hint: {} (approximate, not EC2 region ID)",
            parts.join(", ")
        ))
    }
}
