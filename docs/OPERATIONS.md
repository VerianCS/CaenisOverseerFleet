# Operating Caenis

## Local installation on Windows

Requirements: PowerShell 7 and Docker Desktop with Linux containers. To package the
plugin and the host manager you also need JDK 21. Node is not required to run the
console through Docker. If you use Python to provision NiFi, use Conda.

From the repository root:

```powershell
pwsh ./scripts/Initialize-Caenis.ps1
pwsh ./scripts/Start-Caenis.ps1
```

Initialization creates `.env` and restricts its access to the current user. It does
not print passwords or modify an existing file. The initial account is `admin`;
its password is in `CAENIS_ADMIN_PASSWORD`. Open
[Caenis locally](http://localhost:3000), sign in and register an instance.

These are installation/startup commands and were not executed as part of this
delivery. Docker builds its images when the operator requests startup.
This delivery schedules no validations, tests or CI.

## Connecting a Paper server

1. Package the three JVM artifacts:
   `pwsh ./scripts/Package-Caenis.ps1`.
2. Place `dist/caenis-overseer.jar` in the `plugins/` directory of the Paper 1.21.11 server.
3. In Fleet, register an ID such as `srv-survival-01`, save the key and download
   `config.yml`. Place it at `plugins/CaenisOverseer/config.yml`.
4. If Paper runs on another machine, change `backend.url` to the reachable HTTPS
   address of the core. `localhost` always refers to the plugin's own machine.
5. Restart Paper. The agent enrolls with its key and renews its fifteen-minute
   token. Metrics are sent every five seconds.
6. Enable RCON in `server.properties`:
   `enable-rcon=true`, `rcon.port=25575` and your own `rcon.password`.
   Restart Paper and save those values in the instance settings.
   RCON must stay on a private network/VPN; the protocol does not encrypt traffic.

The agent key can also come from `CAENIS_AGENT_SECRET`; the ID from
`CAENIS_INSTANCE_ID`; the URL from `CAENIS_BACKEND_URL`. There is no global key
that would let one instance impersonate another.

## Starting and stopping Paper

RCON can operate a live process, but it cannot start a stopped one. For lifecycle
control, `caenis-manager.jar` is included: a manager that only runs predefined
directories and JARs.

- Create, for example, `servers/survival/paper.jar`.
- Read and accept the Minecraft EULA yourself in `servers/survival/eula.txt`.
  Caenis does not accept it automatically.
- Copy `docker/manager/application-manager.example.yml` to
  `runtime/application-manager.yml` and adjust the instances.
- Run `pwsh ./scripts/Start-Manager.ps1`.
- If the core runs in Docker Desktop, the manager must listen on a private
  interface reachable from the container: use `-Bind <private-IP>`, configure that
  URL in the console and allow port 8090 only from the core in the firewall.
  `host.docker.internal` resolves to the host from Docker Desktop.
- Save the `CAENIS_MANAGER_KEY` key in that instance's connection settings.
- Use Start / Stop / Restart on the instance page.

Commands are ProcessBuilder arguments, with no shell. The manager does not allow
executables or directories to be supplied by a web request. Stop sends
`stop` through stdin and waits for the normal save. If it takes longer than 35 seconds,
it reports that shutdown is still in progress; it does not kill the process or report
a restart as finished. Game logs remain in Paper's `logs/` directory.

The manager keeps process ownership in memory, plus a marker with the PID and
start time in the instance directory. After a manager crash, it refuses a second
start while that process is still alive. That process can be stopped through RCON
before normal control is regained. It does not adopt arbitrary processes.

## Roles

| Role | Access |
| --- | --- |
| SuperAdmin | Fleet, map, unrestricted RCON, lifecycle, users, connections, agents and baseline training. |
| Moderator | Health and players; map; predefined mitigations; command history. |
| Analyst / Observer | Map, alerts and counters. Player UUID/name replaced by a stable alias. No roster, RCON or settings. |

The API checks the session and the current role on every request. Next.js route
protection is an additional first barrier. Sessions use HttpOnly,
SameSite=Strict cookies, an eight-hour lifetime and persisted revocation.
Account changes revoke previous sessions. Web mutations require CSRF.

## Detection and containment

The plugin measures from BlockDamageEvent to BlockBreakEvent and discards timing
reliability if the tool, effects or player state change.
It never interprets the interval between two different blocks as mining time.
Unknown blocks are not given an invented hardness.

Occlusion considers the six faces without loading neighboring chunks. Incomplete
topology does not feed that detector. The window holds up to 25 valuable ores
from the last fifteen minutes, with a minimum of ten samples and an initial threshold of 75%.

The native speed reference exposed by Paper conservatively bounds the physical
threshold when game attributes are involved. The plugin captures that reference;
the comparison and the decision remain in the core.

Baselines are Beta-Binomial statistical models per instance/player/world.
They are trained from the console over a period explicitly reviewed as
legitimate, with at least one hundred samples. They persist across restarts.
No pre-trained supervised classifier is included and no measured accuracy is
claimed: no labeled data was supplied.

Automatic containment is disabled initially. When enabled, it requires three
critical fast-mining alerts within one minute; it limits each player to one action
every five minutes and each instance to five players per minute. Occlusion
never triggers containment on its own. Events older than fifteen seconds do not
trigger containment either when recovering from an interruption. Freeze lasts at
most five minutes and is cleared when the plugin restarts.

## Delivery, loss and retention

- SPSC ring buffer: default capacity 8,192. When full, the new event is discarded
  and the drop counter increases.
- The agent's single worker serializes, writes to the spool and performs HTTP.
  No network call or spool write happens on the tick.
- Local spool: 128 MiB by default, configurable up to 1 GiB. The oldest batches
  are deleted first when the limit is reached. Maximum delivery age: 24 hours.
- Exponential retries between 1 and 30 seconds. The nonce and event IDs are
  preserved. Permanently invalid responses go to a bounded quarantine.
- On abrupt termination, the queue not yet written to disk may be lost. Exactly-once
  delivery is not promised; persistence deduplicates retries.
- Historical metrics: seven days. Raw events: thirty days. Receipts:
  seven days. Alerts and audit are not deleted by automatic maintenance.
- Retention and the heartbeat detector are functional platform tasks,
  not validation tasks.

NiFi is optional. See [its procedure](../docker/nifi/README.md). Signatures
are verified before admission to the main queue. The original signature is checked
again when persisting. The JSON bytes are preserved without QueryRecord or
transformations that would invalidate the signature.

## RCON and audit

Connections are persistent, with one in-flight command per node and a bounded
global queue. The response is delimited with a second RCON request and
limited to 256 KiB; it is not silently cut off at the first packet.

Recorded states: QUEUED, DISPATCHING, SUCCEEDED, REJECTED or UNCERTAIN.
A timeout does not prove that the command was not executed. Check the server
before repeating it. The terminal receives fragments over STOMP and retrieves the
final result from the history if the web connection is lost.

The application user lacks UPDATE/DELETE on the audit table; a trigger
also rejects UPDATE/DELETE/TRUNCATE. A PostgreSQL administrator retains
authority over their database: this protection is not an external WORM certification.

## HTTPS

To publish on your own domain:

1. Point DNS to your host and allow ports 80/443.
2. In `.env`, set:
   `CAENIS_PUBLIC_ORIGIN=https://your-domain`,
   `CAENIS_HOST=your-domain`, `CAENIS_SECURE_COOKIES=true`,
   `CAENIS_BIND=0.0.0.0`, `CAENIS_HTTP_PORT=80` and
   `ACME_EMAIL=your-email`.
3. Run
   `docker compose -f docker-compose.yml -f docker-compose.https.yml up --build -d`.
4. To use the conduit on that same address, add `--profile nifi` before
   `up` and configure `https://your-domain/telemetry` as the telemetry URL.

Traefik keeps certificates in a volume and routes /ws to the core.
A published deployment has not been performed here: no domain,
target host or real instances were provided.

## Vault

RCON can use an AES-256-GCM encrypted password or a reference to Vault KV v2.
For Vault, set `VAULT_ADDR=https://vault...` and `VAULT_TOKEN_FILE` on the
core, mount the token file read-only and save a path such as
`secret/data/caenis/survival` in the console. The secret must contain
`data.data.password`. Each command fetches the current value and opens a new
connection, so a rotation applied in Vault/Paper takes effect immediately.
Vault does not turn standard RCON into ephemeral credentials: its rotation must
be coordinated with Paper's configuration and restart.

## OpenTelemetry metrics

The core includes the Micrometer OTLP registry. To connect it to your collector,
add `OTEL_METRICS_ENABLED=true` and
`OTEL_METRICS_URL=http://<private-collector>:4318/v1/metrics` to its environment.
The exporter is disabled until a real destination is available. The Actuator
metrics endpoint requires a SuperAdmin session and is not published separately.

## Backups and recovery

`pwsh ./scripts/Backup-Caenis.ps1` creates a binary dump in
`runtime/backups/`. Protect `.env` separately, especially
`CAENIS_ENCRYPTION_KEY`, the NiFi volumes and the Paper worlds.

To restore a backup into a new database, stopped for writes:

```powershell
docker compose stop core web
docker compose cp ./runtime/backups/YOUR-BACKUP.dump postgres:/tmp/restore.dump
docker compose exec -T postgres pg_restore -U caenis_owner -d caenis_overseer --no-owner /tmp/restore.dump
docker compose up -d core web
```

Use an empty database; no destructive cleanup flags are included. The
PostgreSQL backup does not contain the worlds, the local spool or the certificates.

## Limits of this delivery

The code and procedures are delivered, but no builds, tests, visual validations,
scans or deployments have been run.
There are no production performance or compliance results.
The design uses a single active core and supports up to 256 registered instances.
The availability of a real network, the credentials and the EULA acceptance
belong to the operator's installation.
