# Caenis Overseer

*A distributed anomaly-detection and spatial-observation platform for PaperMC servers.*

---

## 1. Purpose of This Document

This is a self-authored architecture review written for a portfolio audience — engineers and hiring managers evaluating design judgment, not just working code. It covers what the system does, why it's built the way it is, which trade-offs were made deliberately versus which are known gaps, and what the project demonstrates as a body of work. Where a decision has a real cost, that cost is stated plainly rather than glossed over — an honest account of trade-offs is more useful to a reviewer than a polished one.

---

## 2. Problem Statement

Conventional Minecraft anti-cheat plugins run their detection heuristics inside the server's main tick loop. That creates a direct trade-off between detection thoroughness and server performance: heavier heuristics degrade tick rate (TPS) for every player, all the time, whether or not anyone is actually cheating.

Caenis Overseer's core thesis is **asynchronous segregation**: the game server should only ever do the cheapest possible thing — record a raw event and hand it off — and every heavier computation (statistical analysis, spatial reasoning, persistence, visualization) should happen entirely off the main thread, in a separate process, where it cannot affect gameplay.

---

## 3. System Architecture

```
PaperMC Server (Kotlin plugin)
        │  raw block-break events, non-blocking, ring-buffered
        │  HTTP POST → :8085/telemetry
        ▼
Apache NiFi (conduit / routing layer)
        │  SQL-based stream partitioning, backpressure buffering
        │  HTTP POST → :8080/api/v1/telemetry/mining/batch
        ▼
Spring Boot / Kotlin (analytical core)
        │  detection heuristics, WebSocket/STOMP broker
        ├──→ PostgreSQL + PostGIS  (persistence, spatial index)
        └──→ Next.js dashboard     (live tactical view)
```

Four layers, each with exactly one job:

| Layer | Responsibility | What it deliberately does *not* do |
|---|---|---|
| Paper plugin | Capture block-break events, occlusion-sample the 6 adjacent faces, buffer, dispatch | No analysis, no scoring, no blocking calls |
| NiFi | Route, batch, buffer, shape traffic | No detection logic |
| Spring Boot core | Run the detection heuristics, persist, broadcast | No game-state access — it never talks to the server directly |
| Next.js dashboard | Poll/subscribe and render | No detection logic — it's a pure observer |

This separation is the single most important architectural property of the system: each layer can be reasoned about, tested, and scaled independently, and a bug or slowdown in the dashboard or the analytics layer is structurally incapable of touching server TPS.

---

## 4. Detection Design

### 4.1 Temporal Kinetic Verification (fast-mining detection)

Rather than a fixed "blocks broken per minute" threshold, the engine computes a physically-grounded lower bound on how fast a block *could* legitimately be broken:

```
Theoretical Time = (1.5 × Hardness) / (ToolMultiplier × Efficiency × StatusEffects)
```

with efficiency modeled as `1 + Level²`, haste applying a `+20%` per amplifier, and mining fatigue applying a `×0.3ⁿ` penalty. An observed break faster than this bound, minus a network-jitter tolerance window, is flagged.

**Why this matters architecturally:** because the formula is derived from the game's actual mechanics rather than tuned against sample data, it generalizes across every tool tier and enchantment combination without per-case special-casing — a new tool tier or a config change to hardness values doesn't require re-tuning a heuristic, just updating a constant.

### 4.2 Topological Occlusion Analysis (x-ray detection)

At the moment of block destruction, the plugin inspects all six adjacent faces (`X±1, Y±1, Z±1`) for `AIR`, `CAVE_AIR`, or liquid media. A break where no adjacent face was naturally exposed is an "unexposed" break. The engine tracks a sliding-window ratio:

```
R(occluded) = (unexposed ore breaks) / (total ore breaks)
```

and flags when `R(occluded) ≥ τ` (currently `τ ≈ 0.75`).

**Honest limitation:** a static, population-wide threshold cannot distinguish a legitimate strip-miner who happens to tunnel through several veins in sequence from an x-ray user — both can produce a high unexposed-break ratio in a short window. This is a known, acknowledged gap rather than an edge case discovered after the fact — see §7.

---

## 5. Key Design Decisions and Their Trade-offs

Presented as decisions, not defaults — each one was chosen over a real alternative for a stated reason, and each has a cost that's worth naming.

**Apache NiFi as the conduit layer.**
NiFi is heavier than this workload strictly requires — a lightweight queue or even in-process batching in the Spring service would satisfy the same backpressure/buffering need with far less operational surface (no JVM cluster, no admin UI, no ZooKeeper coordination in older versions). It was chosen deliberately here as a place to build real NiFi flow-routing skill (`RouteOnAttribute`, `QueryRecord` with Calcite SQL, `InvokeHTTP`) that transfers directly to production data-engineering work, at the cost of two extra HTTP hops between the plugin and the analytics core and a genuine question of what happens to telemetry if the NiFi buffer fills under load (currently unhandled — see §7).

**Physically-derived heuristics over black-box thresholds.**
Both detectors are built from first principles (game mechanics, geometric adjacency) rather than statistically fitted against labeled data. This makes the system's decisions interpretable — an admin can see *why* an event was flagged, not just a confidence score — at the cost of the static-threshold false-positive risk described in §4.2, which a purely statistical model with per-player baselining could reduce.

**Canvas rendering over a mapping library for the tactical view.**
The dashboard renders the coordinate space on a raw `<canvas>` with hand-rolled pan/zoom/hit-testing rather than pulling in a mapping library (Leaflet, deck.gl, etc.). This keeps the bundle small and gives full control over a non-standard coordinate system (Minecraft's X/Z plane, not lat/long) at the cost of having to hand-implement viewport culling, hover detection, and resize handling that a library would provide for free.

**Deterministic detectors as the foundation for a future ML layer, not a replacement for it.**
The planned AI-based pattern recognition is scoped to sit *on top of* the two deterministic detectors — consuming engineered features (occlusion-ratio trend, break-time variance, per-session tool-switch patterns) rather than replacing them outright. This preserves interpretability for the high-confidence fast path while adding a place for per-player behavioral baselining to live.

---

## 6. Frontend Architecture

The dashboard is a Next.js (App Router) client polling the backend every 2.5 seconds for three resources — overview stats, spatial map events, and threat alerts — rendered across three purpose-built surfaces:

- **`TacticalMap`** — canvas-based spatial view with pan/zoom, marker culling, and severity-coded coloring (teal = naturally exposed, crimson = occluded/flagged, gold ring = high-value ore).
- **`AlertFeed`** — a filterable, live-updating incident log. Search matches against player name, breach type, severity, coordinates, and the raw diagnostic string.
- **`StatsHeader`** — three running counters (telemetry ingested, heuristic infractions, critical interventions).

**Layout evolution:** the dashboard was restructured from a fixed 8/4 grid into a sidebar (search → counters → alert feed) with the map as the primary full-width canvas — a deliberate choice to make the spatially densest, most characteristic view of the tool the hero element, with the log-style data secondary and scannable alongside it.

**Motion design:** a single orchestrated loading sequence (a resolving "chunk grid" reminiscent of world generation, sonar rings, and a hand-drawn guardian sigil evoking the project's Caenis namesake) runs once per session and is gated on the dashboard's *actual* first successful data fetch — not a fixed timer — so the loading state never lies about readiness. Everywhere else, motion is deliberately restrained: a live-uplink pulse, a soft tick on changed stat values, and a slide-in reserved for a genuinely new incoming alert, rather than hover-triggered animation on every element.

**A hydration bug and its fix, as a worked example of a real debugging process:**
The initial loading-sequence implementation generated its randomized cell-animation delays inside a `useMemo`. That runs once during the server-side render and *again* during the client's first render — two different calls to `Math.random()` producing two different values for what should be identical markup, which is exactly what triggers a React hydration mismatch. The fix was to seed that state as `null` and populate it only inside a `useEffect`, which by design only ever executes client-side, after hydration has already completed and reconciled against the server's HTML. This was verified directly by diffing the server-rendered HTML before and after the fix (zero instances of the randomized `animation` property in the initial payload, confirming the server and the client's first paint are now provably identical) rather than assuming the fix worked from the absence of a console warning.

---

## 7. Known Limitations & Future Work

Stated explicitly, because a portfolio piece that only lists strengths reads as less credible than one that shows awareness of its own gaps:

1. **No authentication between the plugin, NiFi, and the backend.** Any process that can reach the ingestion ports can currently inject fabricated telemetry. The planned fix is a shared-secret header validated via a NiFi `RouteOnAttribute` processor at the edge, plus a separate key (or HMAC-signed payload) between NiFi and the Spring Boot core, so a compromised edge key doesn't also compromise the analytics layer.
2. **No defined backpressure behavior.** If the NiFi buffer fills under sustained load, telemetry currently has no defined fallback (drop oldest, block the plugin, spool to disk) — this needs an explicit policy decision, not just capacity.
3. **Static occlusion threshold (`τ ≈ 0.75`) is population-wide, not per-player.** It cannot currently distinguish a legitimate strip-miner from an x-ray user within a short observation window. The planned mitigation is per-player behavioral baselining (see §5) rather than a single tuned constant.
4. **NiFi is heavier than the workload requires** at this scale — a conscious trade-off for skill-building (see §5), worth naming rather than presenting as the objectively optimal choice.
5. **No dashboard authentication.** The tactical dashboard currently has no access control of its own; it assumes network-level trust (e.g., a private VPN or LAN).

---

## 8. Technology Stack

| Component | Technology | Why |
|---|---|---|
| Game-server agent | Kotlin, PaperMC API | Native performance, first-class Kotlin support in the Paper ecosystem |
| Shared contracts | Kotlinx Serialization | Type-safe DTOs shared between plugin and backend without duplicating schemas |
| Conduit / routing | Apache NiFi 2.1.0 | Visual, SQL-filterable stream routing (see §5 for the honest trade-off) |
| Analytical core | Spring Boot, Kotlin | Mature ecosystem, WebSocket/STOMP support for real-time push |
| Persistence | PostgreSQL + PostGIS | Native spatial indexing and querying for coordinate-based telemetry |
| Dashboard | Next.js (App Router), TypeScript, Tailwind CSS v4 | Modern React patterns, fast iteration, no external font/asset fetch dependency |
| Infra | Docker Compose | Single-command environment bring-up |

---

## 9. What This Project Demonstrates

- **Distributed systems design** — a four-layer pipeline with a deliberately enforced separation of concerns (async segregation), reasoned about and justified rather than defaulted into.
- **Domain-grounded algorithm design** — detection heuristics derived from actual game mechanics and geometry rather than arbitrary thresholds, with an honest account of where that approach's limits are.
- **Full-stack range** — a Kotlin/JVM game-server plugin, a Kotlin/Spring Boot backend, a stream-processing layer, a PostGIS-backed persistence layer, and a TypeScript/Next.js real-time frontend, all in one coherent system.
- **Real debugging discipline** — the hydration-mismatch fix in §6 is included specifically because it shows a genuine defect, a correct root-cause diagnosis, and verification of the fix against actual server output rather than an assumption that the error stopped appearing.
- **Security awareness** — the unauthenticated-ingestion-endpoint gap (§7.1) was identified and designed against before shipping, rather than discovered by a reviewer.
- **Deliberate, subject-grounded visual design** — a cohesive design system (palette, typography, and a single orchestrated motion sequence) built around the project's own mythic namesake rather than a generic dashboard template.

---
