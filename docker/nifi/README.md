# NiFi conduit

The optional NiFi profile preserves the agent's exact JSON bytes and HMAC headers.
It calls the core's signed `authorize` endpoint before acknowledging a request or
placing it on the delivery queue. Authentication and payload limits run in the core;
NiFi contains no detector logic and no copy of per-node secrets.

The authorize output is committed to both an HTTP acknowledgment and a persistent
delivery queue. HTTP 202 means the conduit accepted responsibility, not that a
detector has finished. A lost reply is safe to retry: the core deduplicates by node,
nonce, body hash and event ID. The 24-hour spool age and receipt retention bound
replay handling. Do not use QueryRecord or reserialize the signed payload.
RouteOnAttribute routes instance identities; the producer already creates batches.

## Provision

1. Run `pwsh ./scripts/Start-Caenis.ps1 -WithNiFi`.
2. Trust/export the NiFi instance certificate from your own deployment into
   `runtime/nifi-ca.pem`. Preserve hostname verification: its subject must match
   the URL you use. For an organization deployment, use your normal CA.
3. With a Conda Python environment, run:
   `conda run -n caenis-tools python docker/nifi/configure_flow.py --ca-file runtime/nifi-ca.pem`.
4. Set each plugin's `backend.telemetry-url` to
   `http://<private-conduit-address>:8085/telemetry` and restart that plugin/server.
   Enrollment and heartbeat still use the core/public origin.

The script is an operational provisioning action and has not been executed here.
It configures only the named Caenis process group and can resume a partially
created group. The API credentials are read from .env and never printed.

## Queue policy

- Pre-authorization connections: 100 FlowFiles / 16 MB per connection.
- Authenticated delivery: 1,000 FlowFiles / 256 MB per connection, persistent
  FlowFile/content repositories on Docker volumes.
- Retry/transport failure: penalized loop, 30 seconds.
- Permanent rejection: stopped inspection queue, 100 FlowFiles / 32 MB.
- Queued FlowFiles expire after 24 hours. Monitor NiFi queue and provenance
  counters; retained data is bounded and cannot survive an unlimited outage.
- A full queue backpressures its source. The plugin continues its bounded local
  spool and eventually drops the oldest disk batches, exposing drop counters.
- Keep 8085 on a private network/VPN or terminate TLS before exposing it.
  Default published ports bind loopback. Do not publish the NiFi admin UI publicly.
- Keep rejected FlowFiles stopped; their attributes include authentication metadata.
  Inspect through the authenticated NiFi UI, then remove or requeue deliberately.

NiFi administration documentation:
[HandleHttpRequest](https://nifi.apache.org/components/org.apache.nifi.processors.standard.HandleHttpRequest/),
[InvokeHTTP](https://nifi.apache.org/components/org.apache.nifi.processors.standard.InvokeHTTP/).
