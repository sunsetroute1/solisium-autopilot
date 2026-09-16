# TL Route Investigator — Windows API choices (Phase 1)

## Design principles

- **Observe only** — no packet capture payloads, no WFP filters, no drivers.
- **Prefer user mode** — escalate only when Windows refuses data without admin.
- **No guessed game servers** — endpoints come from live process sockets only.

## Process list

| Approach | Crate / API | Admin? |
| --- | --- | --- |
| Process name, PID, exe path, start time | `sysinfo` | No |

## Connection table (PID → sockets)

| Approach | API | Admin? |
| --- | --- | --- |
| Primary | `netstat2` → `GetExtendedTcpTable` / `GetExtendedUdpTable` with owner PID | Often **yes** for all processes on Windows |
| Fallback | Parse `netstat -ano` stdout | Usually **no** (may omit some sockets) |

If PID ownership is missing, the UI warns and suggests re-running as administrator for full per-process attribution.

## Local network (adapter, gateway, DNS, MTU, link speed)

| Approach | API | Admin? |
| --- | --- | --- |
| Adapters + IPv4/IPv6 addresses | `network-interface` | No |
| Default gateway, DNS servers, interface metrics | PowerShell `Get-NetIPConfiguration`, `Get-NetAdapter`, `Get-DnsClientServerAddress` | No |

## Public IP / ISP / ASN (approximate)

| Service | Key? | Notes |
| --- | --- | --- |
| `https://ipwho.is/` | No | ISP, ASN, org, country — cached locally in memory for Phase 1 |
| `https://1.1.1.1/cdn-cgi/trace` | No | Fallback for public IP only |

Geolocation and ISP labels are **approximate**, never exact server location.

## Per-IP ASN / geo (Phase 4)

| Service | Key? | Notes |
| --- | --- | --- |
| `https://ipwho.is/{ip}` | No | ASN, org, ISP, country — **SQLite cache** 7 days |
| Reverse DNS | No | PowerShell `[System.Net.Dns]::GetHostEntry` |

Private/CGNAT addresses are skipped. AWS “region hint” is a text heuristic only, not an EC2 region ID.

## Latency (Phase 1: ICMP ping)

| Approach | Crate | Admin? |
| --- | --- | --- |
| ICMP echo | `surge-ping` | Usually **no** on Windows 10/11 for outbound ping |

## Traceroute (Phase 6)

| Approach | API | Admin? |
| --- | --- | --- |
| ICMP traceroute | Windows `tracert -d` (parsed stdout) | No |
| Hop loss hints (optional) | Windows `pathping -n -q` (slow) | No |

Hop `*` / timeout = **router did not respond to probe**, not end-to-end packet loss. Hop ASNs come from the Phase 4 SQLite cache / ipwho.is.

## AWS regional references (Phase 7)

| Source | Notes |
| --- | --- |
| Public AWS hostnames (`ec2.{region}.amazonaws.com`, S3, DynamoDB) | **DNS-resolved at runtime** — not T&L, not fixed game IPs |
| Neutral DNS (`1.1.1.1`, `8.8.8.8`, `9.9.9.9`) | ISP/general Internet baseline |

AWS does not publish single stable “Dallas/Denver/Chicago” game POP IPs; Midwest labels map to honest AWS regions (e.g. Ohio) plus neutral baselines.

## Not used (future phases)

- ETW / packet sniffing — Phase 3+ metadata only if needed
- WinDivert / npcap — not planned for diagnostic mode
- Hard-coded T&L or AWS game IPs — forbidden
