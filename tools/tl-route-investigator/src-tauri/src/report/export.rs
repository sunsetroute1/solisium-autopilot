use super::DiagnosticReport;

#[derive(Debug, Clone, Copy)]
pub enum ReportFormat {
    Json,
    Csv,
    Html,
}

impl ReportFormat {
    pub fn from_str(s: &str) -> Self {
        match s.to_lowercase().as_str() {
            "csv" => Self::Csv,
            "html" => Self::Html,
            _ => Self::Json,
        }
    }

    pub fn extension(self) -> &'static str {
        match self {
            Self::Json => "json",
            Self::Csv => "csv",
            Self::Html => "html",
        }
    }
}

pub fn render(report: &DiagnosticReport, format: ReportFormat) -> Result<String, String> {
    match format {
        ReportFormat::Json => serde_json::to_string_pretty(report).map_err(|e| e.to_string()),
        ReportFormat::Csv => Ok(to_csv(report)),
        ReportFormat::Html => Ok(to_html(report)),
    }
}

pub fn export_report_to_path(
    report: &DiagnosticReport,
    format: ReportFormat,
) -> Result<Option<String>, String> {
    let default = format!(
        "tl-route-report-{}.{}",
        chrono::Utc::now().format("%Y%m%d-%H%M%S"),
        format.extension()
    );
    let Some(path) = rfd::FileDialog::new().set_file_name(&default).save_file() else {
        return Ok(None);
    };
    let content = render(report, format)?;
    std::fs::write(&path, content).map_err(|e| e.to_string())?;
    Ok(Some(path.to_string_lossy().to_string()))
}

fn to_csv(report: &DiagnosticReport) -> String {
    let mut lines = vec![
        "section,key,value".to_string(),
        format!("meta,generated_at,{}", csv_escape(&report.generated_at)),
        format!(
            "connection,isp_approx,{}",
            csv_escape(report.connection.isp_approx.as_deref().unwrap_or(""))
        ),
        format!(
            "connection,public_ipv4,{}",
            csv_escape(report.connection.public_ipv4.as_deref().unwrap_or("masked"))
        ),
    ];
    for c in &report.tl.connections {
        lines.push(format!(
            "connection_row,{},{},{},{},{}",
            csv_escape(&c.protocol),
            csv_escape(&c.local),
            csv_escape(&c.remote),
            c.observations,
            csv_escape(&c.user_tag),
        ));
    }
    for m in &report.performance.measurements {
        lines.push(format!(
            "measurement,{},{},{},{},{}",
            csv_escape(&m.target_ip),
            csv_escape(&m.probed_at),
            m.median_ms.map(|v| v.to_string()).unwrap_or_default(),
            m.p95_ms.map(|v| v.to_string()).unwrap_or_default(),
            m.packet_loss_pct,
        ));
    }
    for r in &report.routes {
        lines.push(format!(
            "route,{},{},{},{}",
            csv_escape(&r.target_ip),
            csv_escape(&r.probed_at),
            csv_escape(r.fingerprint_asn.as_deref().unwrap_or("")),
            r.reached_destination,
        ));
    }
    for line in &report.conclusions {
        lines.push(format!("conclusion,,{}", csv_escape(line)));
    }
    lines.join("\n")
}

fn csv_escape(s: &str) -> String {
    if s.contains(',') || s.contains('"') || s.contains('\n') {
        format!("\"{}\"", s.replace('"', "\"\""))
    } else {
        s.to_string()
    }
}

fn to_html(report: &DiagnosticReport) -> String {
    let conn_rows: String = report
        .tl
        .connections
        .iter()
        .map(|c| {
            format!(
                "<tr><td>{}</td><td>{}</td><td>{}</td><td>{}</td><td>{}</td></tr>",
                html_escape(&c.protocol),
                html_escape(&c.local),
                html_escape(&c.remote),
                c.observations,
                html_escape(&c.user_tag),
            )
        })
        .collect();
    let measure_rows: String = report
        .performance
        .measurements
        .iter()
        .map(|m| {
            format!(
                "<tr><td>{}</td><td>{}</td><td>{}</td><td>{}</td><td>{}%</td></tr>",
                html_escape(&m.target_ip),
                html_escape(&m.probed_at),
                fmt_opt(m.median_ms),
                fmt_opt(m.p95_ms),
                m.packet_loss_pct,
            )
        })
        .collect();
    let route_rows: String = report
        .routes
        .iter()
        .map(|r| {
            format!(
                "<tr><td>{}</td><td>{}</td><td>{}</td><td>{}</td></tr>",
                html_escape(&r.target_ip),
                html_escape(&r.probed_at),
                html_escape(r.fingerprint_asn.as_deref().unwrap_or("—")),
                r.reached_destination,
            )
        })
        .collect();
    let conclusions: String = report
        .conclusions
        .iter()
        .map(|c| format!("<li>{}</li>", html_escape(c)))
        .collect();
    let relay_section = report
        .relay_analysis
        .as_ref()
        .map(|r| {
            let ops: String = r
                .opportunities
                .iter()
                .map(|o| {
                    format!(
                        "<li><strong>[{}]</strong> {} <span class=\"muted\">({})</span></li>",
                        html_escape(&o.confidence),
                        html_escape(&o.summary),
                        html_escape(&o.evidence),
                    )
                })
                .collect();
            format!(
                "<p class=\"muted\">{}</p><ul>{ops}</ul>",
                html_escape(&r.disclaimer),
                ops = ops
            )
        })
        .unwrap_or_else(|| "<p class=\"muted\">No relay analysis (run AWS Comparison + latency probes first).</p>".to_string());

    format!(
        r#"<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"/><title>TL Route Investigator Report</title>
<style>
body {{ font-family: Segoe UI, sans-serif; margin: 2rem; color: #1a1a1a; line-height: 1.45; }}
h1 {{ font-size: 1.4rem; }} h2 {{ font-size: 1.1rem; margin-top: 1.5rem; }}
table {{ border-collapse: collapse; width: 100%; font-size: 0.9rem; margin: 0.5rem 0; }}
th, td {{ border: 1px solid #ccc; padding: 0.35rem 0.5rem; text-align: left; }}
th {{ background: #f0f0f0; }} .muted {{ color: #555; font-size: 0.85rem; }}
</style></head><body>
<h1>TL Route Investigator — Diagnostic Report</h1>
<p class="muted">Generated {gen} · Public IP in report: {pub_included}</p>
<h2>Connection</h2>
<ul>
<li>ISP (approx): {isp}</li>
<li>ASN (approx): {asn}</li>
<li>Public IPv4: {pub}</li>
<li>Gateway: {gw}</li>
<li>DNS: {dns}</li>
<li>LAN: {lan}</li>
</ul>
<h2>T&amp;L — observed connections</h2>
<p class="muted">From live process sockets only; not a official server list.</p>
<table><thead><tr><th>Proto</th><th>Local</th><th>Remote</th><th>Obs</th><th>Your tag</th></tr></thead><tbody>{conn_rows}</tbody></table>
<h2>Performance (saved probes)</h2>
<table><thead><tr><th>Target</th><th>Time</th><th>P50 ms</th><th>P95 ms</th><th>Loss</th></tr></thead><tbody>{measure_rows}</tbody></table>
<h2>Routes</h2>
<table><thead><tr><th>Target</th><th>Time</th><th>ASN fingerprint</th><th>Reached dest</th></tr></thead><tbody>{route_rows}</tbody></table>
<h2>Relay opportunities (hypotheses)</h2>
{relay_section}
<h2>Conclusions</h2>
<ul>{conclusions}</ul>
<p class="muted">This report is observational. ICMP/traceroute paths may differ from game traffic.</p>
</body></html>"#,
        gen = html_escape(&report.generated_at),
        pub_included = report.include_public_ip,
        isp = html_escape(report.connection.isp_approx.as_deref().unwrap_or("—")),
        asn = html_escape(report.connection.asn_approx.as_deref().unwrap_or("—")),
        pub = html_escape(report.connection.public_ipv4.as_deref().unwrap_or("masked")),
        gw = html_escape(report.connection.gateway_ipv4.as_deref().unwrap_or("—")),
        dns = html_escape(&report.connection.dns_ipv4.join(", ")),
        lan = html_escape(report.connection.lan_adapter.as_deref().unwrap_or("—")),
        conn_rows = conn_rows,
        measure_rows = measure_rows,
        route_rows = route_rows,
        conclusions = conclusions,
        relay_section = relay_section,
    )
}

fn fmt_opt(v: Option<f64>) -> String {
    v.map(|x| format!("{x:.1}")).unwrap_or_else(|| "—".to_string())
}

fn html_escape(s: &str) -> String {
    s.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
}
