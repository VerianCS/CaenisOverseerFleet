Caenis Overseer & Caenis Fleet Console

A distributed fleet-management, remote-orchestration, and spatial
anomaly-detection platform for PaperMC server networks.

1. Purpose of This Document

This is an architectural specification and portfolio review written for senior
software engineers, systems architects, and technical leads. It details the
end-to-end design of Caenis, a unified platform that bridges game-server fleet
operations (multi-instance lifecycle, RCON command dispatch, live health
telemetry) with asynchronous, physics-based anti-cheat analytics.

The document documents:

  - What the platform does across both operational and analytical planes.
  - Why specific distributed design choices were made (and their operational
    costs).
  - Concrete derivations of detection heuristics and communication protocols.
  - An explicit, honest breakdown of what is fully implemented versus what
    remains in active development.

2. Problem Statement & Operational Philosophy

2.1 The Anti-Cheat Tick-Loop Bottleneck

Conventional Minecraft anti-cheat plugins execute detection heuristics within
the main server tick loop (50ms budget at 20 TPS). Complex calculations—such as
geometric raycasts, topological face occlusion checks, and statistical
rollups—rob the main thread of execution time. This forces server administrators
to choose between detection thoroughness and server responsiveness.

The Core Thesis — Asynchronous Segregation: The game server agent should only
perform the cheapest possible operation: collect a raw event, sample local block
topology in a non-blocking queue, and hand it off. Heavier computations
(heuristics, spatial indexing, historical baselining, and visualization) are
decoupled into a dedicated distributed pipeline.

2.2 The Fleet Fragmentation Problem

Managing multi-instance Minecraft server networks (e.g., Lobby, Survival,
Minigames) typically requires disjointed tooling:

  - Disconnected web panels (Pterodactyl/Multicraft) for process lifecycle.
  - Standalone command-line tools or insecure web scripts for RCON execution.
  - Disparate in-game logging plugins with separate databases.
  - Isolated anti-cheat instances that lack cross-server player tracking.

Caenis unifies these concerns: the caenis-overseer plugin acts as both a spatial
telemetry probe and an operational node, reporting to a unified backend (Spring
Boot) and controlled via an authenticated web platform (caenis-website in
Next.js).

3. Unified System Architecture

                                  ┌────────────────────────┐
                                  │   Public Landing Page  │
                                  └───────────┬────────────┘
                                              │ (Auth Boundary / RBAC)
                                              ▼
                                  ┌────────────────────────┐
                                  │  Caenis Web Dashboard   │
                                  │  - Tactical Map Canvas │
                                  │  - Fleet Status Monitor│
                                  │  - Web RCON Terminal   │
                                  │  - Incident Alert Feed │
                                  └───────────┬────────────┘
                                              │ REST / WebSocket (STOMP)
                                              ▼
                                  ┌────────────────────────┐
                                  │   Analytical Core &    │
                                  │   Management API       │
                                  │   (Spring Boot 3.x)    │
                                  └──┬────────┬──────────┬─┘
                 ┌───────────────────┘        │          └──────────────────┐
                 │                            │                             │
                 ▼                            ▼                             ▼
       ┌──────────────────┐         ┌──────────────────┐          ┌───────────────────┐
       │   PostgreSQL +   │         │   Apache NiFi    │          │  RCON Dispatcher  │
       │     PostGIS      │         │ (Routing/Buffer) │          │  & Health Poller  │
       └──────────────────┘         └─────────▲────────┘          └─────────┬─────────┘
                                              │ HTTP Telemetry Batch        │ TCP / RCON
                     ┌────────────────────────┴────────────────────────┐    │ (Bi-directional)
                     │                                                 │    │
       ┌─────────────┴──────────────┐                   ┌──────────────┴────▼────────┐
       │ PaperMC Node A (Survival)   │                   │ PaperMC Node B (Lobby/SMP) │
       │  - caenis-overseer plugin  │                   │  - caenis-overseer plugin  │
       │  - Ring buffer telemetry   │                   │  - Ring buffer telemetry   │
       │  - Server metrics reporter │                   │  - Server metrics reporter │
       └────────────────────────────┘                   └────────────────────────────┘

Layer Responsibilities

| Subsystem                       | Primary Responsibilities                                                                                                      | Structural Invariants ("What it does *not* do")                                                             |
| ------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| **`caenis-overseer` (PaperMC)** | Non-blocking block-break capture, 6-face occlusion sampling, server health telemetry dispatch, RCON command receiver          | Never runs heuristic math; never blocks the tick thread; never performs synchronous database or network I/O |
| **Apache NiFi**                 | High-throughput telemetry ingestion, SQL-based payload routing (`QueryRecord`), batching, backpressure management             | Contains zero business logic and zero detection algorithms                                                  |
| **Spring Boot Core**            | Deterministic detection engines, spatial PostGIS persistence, fleet registry, RCON connection pooling, WebSocket broadcasting | Never alters game state directly without explicit administrative or automated command trigger               |
| **`caenis-website` (Next.js)**  | Public showcase landing page, authenticated fleet console, interactive canvas map, multi-server status monitors, web terminal | Never computes heuristics; acts purely as an authenticated presentation and command-dispatch interface      |

4. Core Subsystems

4.1 Detection Engine

A. Temporal Kinetic Verification (Fast-Mining Detection)

Instead of static thresholds (e.g., "flag if > 15 blocks/sec"), the engine
derives a physically grounded minimum break time:

\text{Theoretical Time} = \frac{1.5 \times \text{Hardness}}{\text{ToolMultiplier} \times \text{Efficiency} \times \text{StatusEffects}}

  - Efficiency scaling: 1 + \text{Level}^2
  - Haste scaling: +20\% mining speed per amplifier tier
    (1 + 0.2 \times \text{HasteLevel})
  - Mining Fatigue scaling: 0.3^n penalty multiplier
  - Submerged/Airborne states: \times 5 penalty when not touching ground or
    underwater without Aqua Affinity.

Any block broken faster than (\text{Theoretical Time} - \Delta_{\text{jitter}})
is flagged as an illegitimate break.

B. Topological Occlusion Analysis (X-Ray Detection)

When an ore block is broken, the caenis-overseer plugin samples the 6 orthogonal
faces (X \pm 1, Y \pm 1, Z \pm 1) for transparent or fluid media (AIR, CAVE_AIR,
WATER, LAVA). If no face is exposed, the break is classified as occluded.

The core maintains a sliding temporal window per player:

R_{\text{occluded}} = \frac{\sum \text{Occluded Ore Breaks}}{\sum \text{Total Ore Breaks}}

An infraction is flagged when R_{\text{occluded}} \ge \tau (default threshold
\tau = 0.75) across a minimum sample size N \ge 10.

4.2 Fleet Management & Multi-Instance Control Plane

The platform scales beyond a single server to orchestrate a distributed network
of PaperMC nodes.

┌─────────────────────────────────────────────────────────────┐
│                     Caenis Fleet Service                    │
├─────────────────┬─────────────────────────┬─────────────────┤
│ Node Identity   │ Instance Health         │ Control Session │
├─────────────────┼─────────────────────────┼─────────────────┤
│ srv-survival-01 │ 20.0 TPS · 42ms MSPT    │ RCON Connected  │
│ srv-mining-01   │ 19.4 TPS · 48ms MSPT    │ RCON Connected  │
│ srv-hub-01      │ 20.0 TPS · 12ms MSPT    │ Standby         │
└─────────────────┴─────────────────────────┴─────────────────┘

Node Lifecycle & Discovery

1.  Agent Registration: On startup, caenis-overseer reads its instance
    configuration (instanceId, clusterGroup, region) and sends an enrollment
    handshake to the backend API.
2.  Heartbeat Protocol: The agent dispatches a lightweight status heartbeat
    containing:
      - TPS: 1m, 5m, and 15m moving averages.
      - MSPT: Mean Tick Time in milliseconds (Paper
        MinecraftServer.getServer().tickTimes).
      - Memory: JVM Heap allocated, used, and free space.
      - Entity / Chunk Load: Active loaded chunks and living entity counts.
      - Player Roster: List of active player UUIDs, usernames, and latency
        values.
3.  Dead-Node Detection: If a node misses 3 consecutive heartbeat cycles (15s
    threshold), the management plane marks it as UNREACHABLE and raises an alert
    in the dashboard.

4.3 RCON Engine & Interactive Command Gateway

To execute operational actions (e.g., kick, ban, whitelist, tps, or custom
dispatch), the backend includes an RCON engine:

  - Multiplexed Pool: The Spring Boot core maintains persistent, authenticated
    RCON socket connections per registered server instance using non-blocking
    I/O.
  - Web Terminal: An interactive CLI in the Next.js frontend connects via
    WebSocket to the Spring Boot gateway, enabling low-latency remote command
    execution and streaming stdout.
  - Audit Logging: Every command issued through the web console or automated
    intervention pipeline is written to an immutable PostgreSQL audit log with
    user ID, target instance ID, timestamp, and command body.
  - Automated Interventions: When heuristic detectors reach a critical
    confidence threshold, the core can optionally dispatch automated containment
    commands (e.g., freeze <player> or kick <player> "Automated Telemetry Flag")
    via RCON without human intervention.

4.4 The caenis-overseer Plugin Architecture

The game-server plugin is engineered for near-zero tick-loop footprint:

[Main Server Thread]
   │ BlockBreakEvent / PlayerMoveEvent
   ▼
[Quick Occlusion Bitmask Check] (reads adjacent block IDs in memory)
   │
   ▼ (No DB queries / No network calls)
[Disruptor / Ring Buffer Queue] ──────────┐
                                          │ (Off-thread consumption)
                                          ▼
                             [Async Telemetry Worker]
                                  │ Batches events
                                  ▼
                             [HTTP Dispatch to NiFi / Backend]

  - Ring-Buffered Queue: Uses an in-memory queue to absorb event spikes (e.g.,
    explosions or multi-block breaks) without allocating excess heap memory or
    blocking the main thread.
  - Memory Footprint: Avoids heavy object allocations; coordinates and face
    states are packed into compact data-transfer objects (DTOs) serialized via
    kotlinx.serialization.
  - Dynamic Configuration Sync: The plugin exposes endpoints for remote
    configuration updates (e.g., toggling debug sampling, tuning jitter
    tolerance windows) pushed from the central management API.

5. Frontend & Experience Architecture (caenis-website)

The web layer is built with Next.js (App Router), TypeScript, and Tailwind CSS.
It is split into two primary zones: a Public Showcase Landing Page and an
Authenticated Fleet Operations Console.

caenis-website/
├── app/
│   ├── (public)/
│   │   ├── page.tsx               # Public Landing Page (Showcase, Vision, Architecture)
│   │   └── layout.tsx
│   ├── (auth)/
│   │   ├── login/page.tsx         # Secure Authentication Gateway
│   │   └── error/page.tsx
│   └── (dashboard)/               # Protected Operational Route Group
│       ├── layout.tsx             # RBAC Gate, AppShell, Global Uplink Indicators
│       ├── fleet/                 # Multi-Instance Health & Management
│       │   ├── page.tsx           # Server overview cards, TPS/MSPT grid
│       │   └── [instanceId]/      # Node deep-dive, console, player list
│       ├── tactical/              # Spatial Anti-Cheat Canvas & Alert Feed
│       │   └── page.tsx
│       ├── rcon/                  # Global Multi-Instance Terminal
│       │   └── page.tsx
│       └── audit/                 # Command & Breach History Logs

5.1 Public Landing Page

The public landing page acts as the system showcase:

  - Interactive Explainer: Demonstrates the core thesis (Asynchronous
    Segregation) with interactive diagrams comparing conventional tick-bound
    anti-cheat against Caenis's decoupled pipeline.
  - Feature Showcase: Deep-dives into the physics-based Temporal Kinetic
    calculations and Topological Occlusion models.
  - Live Sandbox Simulation: A canvas component illustrating how adjacent face
    detection identifies occluded ore stripping in real-time.
  - Access Portal: Serves as the entry point to the authenticated operations
    console.

5.2 Authentication & Role-Based Access Control (RBAC)

The dashboard enforces granular permission boundaries:

| Role                   | Permissions                                                                                                            |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| **SuperAdmin**         | Full instance lifecycle control, raw RCON command execution, detector parameter tuning, user management                |
| **Moderator**          | Read access to fleet health, view tactical alerts, issue predefined mitigation commands (`kick`, `freeze`, `spectate`) |
| **Analyst / Observer** | Read-only access to tactical canvas, alert feed, and anonymized player telemetry                                       |

  - Session Security: Token-based authentication using HTTP-only secure cookies.
  - Route Protection: Handled via Next.js Edge Middleware, verifying
    cryptographic session tokens before streaming dashboard routes.

5.3 Real-Time Fleet Observation & Status Following

The fleet management view monitors instance health:

  - Performance Dials: Real-time gauges for TPS (color-coded: \ge 19.5 teal,
    18.0 - 19.4 amber, < 18.0 red) and MSPT.
  - Memory Pressure Gauges: JVM Heap utilization trends over time.
  - Player Density Monitors: Active connections per node, distribution across
    cluster groups, and ping distribution.

5.4 Tactical Spatial Dashboard

  - TacticalMap: High-performance <canvas> surface rendering Minecraft
    coordinate space (X/Z). Features custom view-matrix transformations
    (pan/zoom), viewport culling, and multi-tier rendering:
      - Cyan pings: naturally exposed mining breaks.
      - Crimson markers: occluded/flagged breaks.
      - Gold rings: rare mineral nodes (Ancient Debris, Diamond).
  - AlertFeed: Live-updating, searchable event stream with debounced text
    filtering across player UUIDs, coordinates, infraction types, and severity
    scores.
  - StatsHeader: Live counters reflecting global fleet ingestion rate, total
    infractions, and automated interventions.

Case Study: Hydration Bug & Root-Cause Resolution

Context: The dashboard's world-generation-inspired chunk loading sequence
rendered with a layout flicker and emitted React hydration mismatch errors (Text
content does not match server-rendered HTML).

Root Cause: Randomized cell delays were generated via Math.random() inside a
useMemo hook. This computed one set of animation timing values during Node.js
server-side rendering and a different set on the browser's first paint, causing
hydration reconciliation failures.

Fix: Delays were re-architected to remain null during server rendering and
populate only within a client-side useEffect hook. Server-rendered output was
diffed against client hydration snapshots to verify zero discrepancies in
generated markup.

6. Implementation Status Matrix

An honest, granular assessment of what is working today versus what is actively
being built.

| Component                    | Feature / Capability        | Status          | Implementation Details / Current Limitations                             |
| ---------------------------- | --------------------------- | --------------- | ------------------------------------------------------------------------ |
| **`caenis-overseer` Plugin** | Block-Break Telemetry       | **DONE**        | Event capture, non-blocking queue, 6-face occlusion bitmask check.       |
|                              | Health & Metric Exporter    | **IN PROGRESS** | TPS & MSPT gathering functional; entity/chunk counts in progress.        |
|                              | Dynamic Token Handshake     | **PLANNED**     | Plugin currently relies on network-level IP isolation.                   |
| **Conduit (Apache NiFi)**    | Stream Routing & Batches    | **DONE**        | Visual routing via `RouteOnAttribute`, Calcite SQL query batching.       |
|                              | Dynamic Backpressure Policy | **IN PROGRESS** | Buffering functions; graceful disk spooling under backpressure pending.  |
| **Analytical Core**          | Kinetic Heuristic           | **DONE**        | Theoretical time lower-bound algorithm active.                           |
|                              | Occlusion Heuristic         | **DONE**        | Ratio calculation over sliding time window operational.                  |
|                              | PostGIS Persistence         | **DONE**        | Spatial schema and index queries active.                                 |
|                              | Multi-Instance RCON Pool    | **DONE**        | Persistent socket pool and command executor implemented.                 |
|                              | Automated Interventions     | **IN PROGRESS** | Automated `kick`/`freeze` trigger rules defined; rate-limiting underway. |
|                              | ML-Based Baselining         | **PLANNED**     | Architecture designed; model training pipeline not yet implemented.      |
| **`caenis-website`**         | Public Landing Page         | **DONE**        | Responsive marketing page, visual design system, architecture explainer. |
|                              | Tactical Canvas Map         | **DONE**        | Custom coordinate rendering, viewport culling, pan/zoom engine.          |
|                              | Incident Feed & Counters    | **DONE**        | Live updates, multi-parameter filtering, debounced search.               |
|                              | Web RCON Terminal           | **IN PROGRESS** | Interactive UI built; multi-line command output streaming in test.       |
|                              | Fleet Status Dashboard      | **IN PROGRESS** | Instance summary cards active; historic time-series charts pending.      |
|                              | Auth & RBAC Security        | **IN PROGRESS** | Session foundation implemented; fine-grained role gating active on API.  |

7. Key Architectural Decisions & Their Trade-offs

1. Dedicated NiFi Routing Layer vs. Direct Spring Boot Ingestion

  - Choice: Routing game telemetry through Apache NiFi before reaching the
    analytics core.
  - Trade-off: Introduces operational overhead (JVM memory footprint, NiFi
    cluster management) and network hops.
  - Justification: Decouples ingestion spikes from application state. NiFi
    handles high-throughput backpressure, buffering, and data transformation,
    mirroring enterprise streaming architectures.

2. Physical First-Principles Heuristics vs. Black-Box ML Models

  - Choice: Calculating explicit break times from Minecraft's mining equations
    rather than training a statistical classifier.
  - Trade-off: Static parameters require explicit updates if the game mechanics
    change (e.g., changes to Haste modifiers).
  - Justification: Deterministic heuristics provide transparent audit trails.
    When an administrator questions a flag, the system references verifiable
    physics calculations rather than an opaque confidence score.

3. Raw Canvas Rendering vs. Mapping Libraries (Leaflet, Deck.gl)

  - Choice: Hand-rolling coordinate space transformations on an HTML5 <canvas>.
  - Trade-off: Requires custom implementations of hit-testing, zoom bounds,
    viewport culling, and window resize listeners.
  - Justification: Minecraft uses a 3D Cartesian coordinate system (X/Y/Z), not
    spherical GIS coordinates (Latitude/Longitude). Standard mapping libraries
    incur coordinate-projection overhead and inflate bundle sizes.

4. Centralized Control Plane vs. Per-Server Management Panels

  - Choice: Managing multiple server instances and RCON sessions from a single
    unified control plane.
  - Trade-off: Creates a centralized point of failure; if the core platform goes
    offline, remote console management across all servers is interrupted.
  - Justification: Enables network-wide threat correlation. A player flagged for
    anomalies on an SMP server can be flagged or monitored across the entire
    network.

8. Known Limitations & Security Roadmap

1.  Ingestion Layer Authentication: Current Gap: Ingestion endpoints rely on
    network isolation (private Docker bridge/VPN). Roadmap: Implement HMAC
    payload signatures generated by the plugin agent and verified via NiFi
    before processing.
2.  Population-Wide Static Occlusion Threshold: Current Gap: The static
    threshold (\tau = 0.75) can yield false positives for legitimate players
    strip-mining through consecutive ore veins. Roadmap: Transition to
    per-player rolling baselines, evaluating deviation from an individual's
    historical mining patterns.
3.  RCON Credential Vaulting: Current Gap: Instance RCON credentials are stored
    in encrypted configuration tables. Roadmap: Integrate HashiCorp Vault for
    dynamic secret rotation and temporary RCON session provisioning.
4.  WebSocket Connection Multiplexing: Current Gap: High player volumes
    streaming telemetry simultaneously can saturate front-end browser memory.
    Roadmap: Implement server-side spatial clustering, aggregating distant pings
    before dispatching packets to the dashboard client.

9. Technology Stack

Agent Subsystem
  PaperMC API (1.20+) · Kotlin 1.9+ · kotlinx.serialization · Disruptor Ring-Buffer

Streaming & Ingestion
  Apache NiFi 2.x · Calcite SQL · HTTP/2 Multiplexing

Core & Analytical Engine
  Spring Boot 3.x · Kotlin · Spring WebSocket (STOMP) · PostGIS · HikariCP · RCON Java Engine

Web Presentation & Control
  Next.js 14+ (App Router) · TypeScript · Tailwind CSS v4 · HTML5 Canvas API · Lucide Icons

Data Persistence
  PostgreSQL 16 · PostGIS 3.4 (Spatial indexes on coordinates)

Infrastructure & Orchestration
  Docker Compose · Traefik Reverse Proxy · OpenTelemetry Metrics

10. Summary of Technical Competencies

  - Distributed Systems: Multi-tier architectural design with strict
    asynchronous boundaries between real-time game loops, data streaming,
    analytical engines, and client presentation.
  - Domain Modeling & Applied Physics: Converting reverse-engineered game
    mechanics into deterministic verification algorithms.
  - Low-Latency Full-Stack Engineering: High-performance Kotlin game agent
    development, Spring Boot microservices, and React/Next.js canvas rendering.
  - Operational Systems Design: Scalable multi-instance management, RCON
    protocol integration, and defensive security architectures.
