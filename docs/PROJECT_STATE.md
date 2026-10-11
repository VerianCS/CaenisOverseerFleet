# Caenis Overseer — Project State

_Snapshot: 2026-10-11, `main` @ `7a34830` (4 commits: initial → "final MVP" → README/ops translation → merge of PR #1)._

## One-line summary

A feature-complete **MVP** of a self-hosted fleet console + out-of-tick anti-cheat for PaperMC networks. All five components exist and are wired end to end, but it has **never run in production**, has **no real tests**, shipped a hardcoded bootstrap admin password (finding 0, now fixed), and had some dead frontend code and repo clutter from earlier iterations (since removed — see Fix log).

## Components

| Module | Files / lines | Status |
| --- | --- | --- |
| `paper-plugin` (`caenis-overseer.jar`) | 10 / ~445 | Implemented: block-damage→break timing, 6-face exposure bitmask, SPSC ring buffer, disk spool, HMAC-signed dispatch, console-only freeze. Package is `com.sideplanetary.overseerPlugin` (rest of repo is `com.enderstorage.*`). |
| `common-dto` | 7 / ~174 | Shared kotlinx.serialization DTOs + HMAC request signing. `PlayerMovementTelemetry` is defined but **unused**. |
| `backend-service` (`caenis-core.jar`) | 38 / ~1,920 | Implemented: agent auth (enroll/heartbeat/mining/config), `FastMiningDetector`, `XrayDetector` with per-player baselines, PostGIS persistence, fleet registry, `RconPool`, `CommandGateway`, append-only audit, JWT sessions + RBAC, STOMP invalidation, opt-in `InterventionService`. 2 Flyway migrations (`V1__platform`, `V2__runtime_permissions`). |
| `backend-service-servermanager` (`caenis-manager.jar`) | 1 main file / 141 lines | Implemented: start/stop/restart of pre-configured Paper dirs, no shell, PID+lock guard, never accepts EULA. |
| `caenis-web` (Next.js 16.3, React 19, pnpm 10) | 81 / ~6,700 | Console routes live under `app/(console)/` (fleet, fleet/[id], tactical, rcon, audit, settings) + `login` + public landing with `PipelineDemo`. |
| `docker/` + compose | postgres, core, web, gateway (Traefik) | Working stack definition; HTTPS overlay with Let's Encrypt. |
| `scripts/` | 5 PowerShell 7 scripts | Initialize, Start, Start-Manager, Package, Backup. |
| `docs/` | CONTRACTS, OPERATIONS, architecture-brief, architecture-original | Contracts and ops guide are current; the two architecture docs are the original design brief. |

## What works (per code + docs)

- Signed telemetry path: plugin → core → PostGIS, idempotent by nonce and `eventId`.
- Deterministic detectors with explainable diagnostics; automation off by default and heavily rate-limited.
- Fleet health, 1–72 h history, dead-node detection, web RCON with role-limited actions, lifecycle via host manager.
- Security: HMAC agent auth with replay protection, HttpOnly/SameSite JWT + CSRF, AES-256-GCM secrets at rest, optional Vault, least-privilege DB role, audit-table mutation trigger.

## Known gaps (stated in README)

- Not validated in production; no accuracy, false-positive or performance numbers; no labelled data.
- Only gameplay signal ingested is mining; movement DTO not wired.
- Single active core, ≤ 256 instances.
- X-ray occlusion can over-report vein-followers (mitigated by baselines).

## Findings from the code (not in README)

0. ~~**Bootstrap admin password ignores `.env` (security bug).**~~ _Fixed — see Fix log._ `AuthService` reads `caenis.bootstrap-username` / `caenis.bootstrap-password`, and `backend-service/src/main/resources/application.yml:56-57` sets them to **literal values committed to the repo** (comment: "Change to use the .env instead"). Compose passes `CAENIS_ADMIN_USERNAME` / `CAENIS_ADMIN_PASSWORD`, which Spring binds to `caenis.admin-*`, not `caenis.bootstrap-*` — so the generated `.env` password never reaches the app, the README's sign-in instructions are wrong, and every deployment's first SuperAdmin has the public repo password. Fix: `bootstrap-password: ${CAENIS_ADMIN_PASSWORD}` (and the same for username), then rotate the password on any deployment that has booted.
1. **Tests are placeholders.** Only `OverseerApplicationTests` and `BackendServiceServermanagerApplicationTests` exist (context loads). Detectors, signing, `CommandGateway`, manager process logic and the web app have zero tests. `PRODUCT.md` records a user instruction to "not execute or schedule validations", which explains this.
2. ~~**Dead web code (~15 files).**~~ _Fixed — see Fix log._ Nothing imported:
   - `caenis-web/app/components/*` (`AdminPanelView`, `AlertFeed`, `DepthSounding`, `IncidentSearch`, `StatsHeader`, `TacticalMap`)
   - `caenis-web/app/services/api.ts`, `caenis-web/app/types/deck.ts`
   - `caenis-web/app/cheat-tracking/CheatTrackingView.tsx` and its `components/` and `services/` (incl. `demo.ts` with `DEMO_*` data)

   Only `app/cheat-tracking/types/deck.ts` is live (imported by `components/TacticalCanvas.tsx` and `components/TacticalView.tsx`) — it should move to `lib/types.ts` before deleting the rest.
3. **Unused backend repository.** `MiningEventRepository` (JPA) is never injected; ingestion goes through `JdbcTemplate`.
4. ~~**Repo clutter in `caenis-web`.**~~ _Fixed — see Fix log._
   - `minecraft-server-manager.zip` (75 KB, 39 files) committed — looks like an old export of the UI.
   - Three lockfiles: `pnpm-lock.yaml` (the one Docker uses), `package-lock.json`, `bun.lock`.
   - 14 files in `public/` (Next/v0 boilerplate icons and placeholders) — none referenced in source.
5. **Naming drift.** Plugin package `com.sideplanetary`, DTO package `com.enderstorage.sentinel`, core `com.enderstorage.overseer`, plugin config class `SentinelConfig` — three historical names (Sentinel / Overseer / Caenis) still coexist.
6. **OTLP target missing.** `application.yml` defaults the metrics URL to `http://otel:4318`, but compose has no `otel` service. Harmless while `OTEL_METRICS_ENABLED=false` (the default).
7. ~~**Docs disagree on NiFi.**~~ _Moot — NiFi removed, see Fix log._ `docs/architecture-brief.md` has NiFi using `QueryRecord`; `docker/nifi/README.md` forbids it because re-serialising breaks HMAC signatures. The NiFi README is the current rule; the brief is historical. Likewise the brief's "ML-based baselining (planned)" was delivered as the deterministic Beta-Binomial per-player baselines.

## Knowledge graph (graphify)

Built in `graphify-out/` (`graph.html`, `graph.json`, `GRAPH_REPORT.md`): 1,072 nodes, 2,017 edges, 84 communities, no import cycles.

- **Most connected backend classes:** `FleetService` (25), `CommandGateway` (23), `AuditService` (18), `AuthService` (18). `CommandGateway` is the main bridge (betweenness 0.31) — it ties controllers, RCON, credentials, secrets, audit and automated intervention together, so it is the riskiest class to change and the best test target after the detectors.
- **Frontend hubs:** `NodeView` (21) and the `api()` client (21). The "Console Pages & Views" community has low cohesion (0.07) — the views share little beyond the API client.
- The dead web code from finding 2 shows up as isolated communities ("Legacy Cheat-Tracking UI", "Legacy Admin Components", "Legacy Depth Sounding") with no edges into the live console.

## Suggested next steps (in order of value)

0. Rotate the SuperAdmin password on any deployment that booted before the fix (Settings → Users).
1. Unit tests for `FastMiningDetector`, `XrayDetector` and `RequestSigning` — pure functions, highest risk, cheapest to test.
2. Decide on movement telemetry: wire `PlayerMovementTelemetry` or delete it.
3. Run the stack against a real Paper server and record baseline TPS/MSPT impact and alert rates — the README's own "not validated" caveat.

## Fix log

1. **NiFi removed.** Deleted `docker/nifi/` and the `nifi` compose service + volumes, the Traefik `/telemetry` routers, `-WithNiFi` in `Start-Caenis.ps1`, `NIFI_*` vars in `Initialize-Caenis.ps1` / `.env.example`, the core's NiFi-only `/agent/{id}/authorize` endpoint, and the plugin's `backend.telemetry-url` option (agents always post to `/api/v1/agent/{id}/mining`). README, CONTRACTS and OPERATIONS updated; the two architecture docs got a "historical" note.
2. **Next.js / v0 boilerplate removed.** Deleted `public/` (14 unused assets) and the Dockerfile line that copied it, the default `app/favicon.ico`, unused `app/minecraft-background.css`, the dead legacy UI (`app/components`, `app/services`, `app/types`, `app/cheat-tracking` — its live deck types moved to `lib/types.ts`), `minecraft-server-manager.zip`, `package-lock.json`, `bun.lock`, the unused `@vercel/analytics` dependency, and the v0 sandbox block in `.gitignore`.
3. **Bootstrap admin password bound to `.env`.** `application.yml` now reads `bootstrap-username: ${CAENIS_ADMIN_USERNAME:admin}` and `bootstrap-password: ${CAENIS_ADMIN_PASSWORD}` with no default, so the core refuses to start without an operator-chosen password. `.env.example` no longer carries sample admin/session/encryption secrets, so a copied example fails loudly in compose instead of running with public values. The old literal stays in git history: existing deployments keep that password (the bootstrap insert is `ON CONFLICT DO NOTHING`) and must change it in the console.
