# Protocol and API contracts

## Agent requests

`POST /api/v1/agent/{instanceId}/{enroll|heartbeat|mining|config}`

Content type: application/json. Limit: 2 MiB and 500 mining events per request.
Signed headers: `X-Caenis-Timestamp` (epoch milliseconds),
`X-Caenis-Nonce` (UUID), `X-Caenis-Signature` (base64url without padding).
Heartbeat/config additionally send `X-Caenis-Token` from enrollment.

The canonical HMAC-SHA256 input, UTF-8 with LF separators and no final LF:

```text
instanceId
purpose
timestamp
nonce
base64(SHA256(raw HTTP body))
```

Keys are literal UTF-8 strings. The per-instance secret is stored encrypted.
Only the core can issue an enrollment token. It expires after fifteen minutes.
Control messages accept sixty seconds of clock skew. Mining signatures may be
queued up to 24 hours; timestamps more than one minute in the future are rejected.

Mining records contain `instanceId`, `eventId`, UUID/name, world, X/Y/Z, time,
block/tool, enchantments/effects, exposure bitmask, timing reliability and
environment flags. See the canonical Kotlin DTOs in `common-dto/src/main`.
Unknown topology and unreliable timings explicitly disable their corresponding
detection path. Idempotency is both per request and per event. No legacy unsigned
`/api/v1/telemetry/mining` endpoint remains.

NiFi calls `/api/v1/agent/{id}/authorize` with the same mining signature and exact
body. This authorizes and checks payload bounds without creating a receipt or
running a detector. On delivery, the normal mining endpoint repeats authorization.

## Browser REST

All endpoints below are under `/api/v1`.

| Endpoint | Method | Contract |
| --- | --- | --- |
| /auth/csrf | GET | Cookie-backed CSRF token and header name |
| /auth/login | POST | username/password → HttpOnly session and current account |
| /auth/me | GET | Current persisted account and role |
| /auth/logout | POST | Revoke session and expire cookie |
| /fleet | GET, POST | Health snapshots / SuperAdmin instance registration |
| /fleet/{id} | GET | One instance; no secrets |
| /fleet/{id}/history | GET | Minute aggregates; hours=1..72 |
| /fleet/{id}/connections | PUT | SuperAdmin RCON/Vault/manager settings; blank password retains existing secret |
| /fleet/{id}/settings | PUT | Enablement, agent settings, containment opt-in |
| /fleet/{id}/rotate-key | POST | Revoke agent token and return replacement key once |
| /fleet/{id}/lifecycle | POST | start, stop, restart → command correlation ID |
| /commands | POST | SuperAdmin raw command or moderator action/player UUID |
| /commands/{uuid} | GET | Requesting user's persisted command progress/result |
| /deck/scopes | GET | Instance and world names without rosters |
| /deck/map | GET | Required instance/world, bounding box, time window, limit ≤2,000, optional cellSize |
| /deck/alerts | GET | Optional instance and search, limit ≤200 |
| /deck/stats | GET | Retained events, alerts, last-minute ingestion rate, completed interventions |
| /audit | GET | Descending ID cursor (before), optional instanceId and q |
| /users | GET, POST | SuperAdmin account list/create |
| /users/{uuid} | PUT | Role, enabled, optional replacement password; revokes sessions |
| /settings/baselines | GET | Trained player/world models |
| /settings/baselines/train | POST | Reviewed interval, at least 100 samples/player |

Runtime rules reject role bypasses even when UI controls are manipulated.
Queries have bounds and pagination; spatial lookups use a PostGIS GiST index
inside an instance/world scope.

## WebSocket/STOMP

Endpoint `/ws`, same origin and authenticated session.
CONNECT supplies the same `X-XSRF-TOKEN` as browser REST mutations.
Only subscriptions to `/topic/changes` (payload-free invalidation) and
`/user/queue/commands` (the requesting user's output) are accepted.
SEND is permitted only to `/app/command`, using the same command gateway.
Wildcards and arbitrary topics are rejected.

REST polling remains available during reconnects, never substitutes demo data,
and never claims a failed link is live. Frame/send buffers and client lists are
bounded. Wide map views cluster on the server.

## Host manager

`POST /manager/v1/instances/{id}/lifecycle` with
`X-Caenis-Manager-Key` and body `{"action":"start"}`, `stop` or `restart`.
Only configured IDs, server directories and JARs are accepted. No command text,
environment variable injection or uploaded program is accepted from this API.
