//! Headless Phase 1 snapshot for verification (no GUI).
use tl_route_investigator_lib::connections;
use tl_route_investigator_lib::network;
use tl_route_investigator_lib::probe;
use tl_route_investigator_lib::process;
use tl_route_investigator_lib::util;

#[tokio::main]
async fn main() {
    let elevated = util::is_elevated();
    println!("elevated={elevated}");
    let local = network::local::collect_local(elevated).expect("local network");
    println!("gateway={:?}", local.default_gateway_ipv4);
    println!("dns={:?}", local.dns_servers_ipv4);
    for a in &local.adapters {
        if a.is_up {
            println!(
                "adapter {} · {} · {:?} Mbps · v4={:?}",
                a.name, a.media_type, a.link_speed_mbps, a.ipv4
            );
        }
    }
    let public = network::public_ip::collect_public(false).await;
    println!(
        "public masked={:?} isp={:?} asn={:?} source={}",
        public.public_ipv4_masked, public.isp, public.asn, public.source
    );
    let all = process::list_processes();
    let procs: Vec<_> = all.iter().filter(|p| p.tl_candidate).take(10).collect();
    println!("tl_candidates={}", procs.len());
    for p in &procs {
        println!("  pid={} name={} hint={:?}", p.pid, p.name, p.tl_match_reason);
    }
    let selected = process::best_tl_candidate(&all);
    if let Some(gw) = &local.default_gateway_ipv4 {
        let lat = probe::latency::icmp_probe(gw, 10).await;
        println!(
            "gateway icmp p50={:?} p95={:?} loss={}% err={:?}",
            lat.median_ms, lat.p95_ms, lat.packet_loss_pct, lat.error
        );
    }
    let cloud = probe::latency::icmp_probe("1.1.1.1", 10).await;
    println!(
        "1.1.1.1 icmp p50={:?} p95={:?} loss={}%",
        cloud.median_ms, cloud.p95_ms, cloud.packet_loss_pct
    );
    if let Some(p) = selected {
        println!("selected pid={} name={}", p.pid, p.name);
        match connections::connections_for_pid(p.pid, elevated) {
            Ok(rows) => {
                println!("connections for pid {}: {}", p.pid, rows.len());
                for r in rows.iter().take(15) {
                    println!(
                        "  {:?} {}:{} -> {}:{} {:?}",
                        r.protocol, r.local_ip, r.local_port, r.remote_ip, r.remote_port, r.state
                    );
                }
            }
            Err(e) => println!("connections error: {e}"),
        }
    } else {
        println!("no TL candidate process running");
    }
}
