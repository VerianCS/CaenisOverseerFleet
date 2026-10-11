# Plan: upgrade `paper-plugin` to the latest Minecraft

_Written 2026-10-11._

## Target

| | Version | Status |
| --- | --- | --- |
| Plugin today | Paper API `1.21.11-R0.1-SNAPSHOT`, `api-version: '1.21.11'`, JVM 21 | — |
| Latest Minecraft Java release | **26.3** (2026-09-15) | Stable from Mojang |
| Paper for 26.3 | builds up to `26.3.build.174-beta` | **Beta only, no stable build yet** |
| Paper for 26.2 | `26.2.build.133-stable` (2026-10-09) | **Latest stable Paper** |

**Recommendation:** build and ship against **26.2** now, which is Paper's newest stable line. Move to **26.3** in a one-line follow-up (step 8) once Paper publishes a `-stable` 26.3 build. Plugin code written for 26.2 should carry over, because Paper's 26.3 notes list only additions and deprecations, none of which this plugin uses.

## Progress (branch `claude/paper-26.3`)

The user chose to go straight to **26.3**, so the API is pinned to the newest beta build, `26.3.build.174-beta`. Re-pin when Paper publishes a stable build.

| Step | Status |
| --- | --- |
| 1. Toolchain | Done. The plugin compiles with a **JDK 26 toolchain** to **Java 25 bytecode** (`jvmTarget = JVM_25`, `--release 25`), because JDK 25 is not installed locally. Kotlin moved to **2.3.21** across the repo, since 2.2.21 has no JVM 25 target. The Spring modules' `kotlin.version` moved with it. Shadow 8.3.8 relocates Java 25 classes without problems. The core's Docker build is unaffected: it still builds with only JDK 21 available. |
| 2. Dependency + descriptor | Done. `paper-api:26.3.build.174-beta` and `api-version: '26.3'`. |
| 3. API breaks | None. The code compiles against 26.3 unchanged. The only API warning is `Player#isOnGround` being deprecated as client-controlled. It was already deprecated in 1.21.11, and spoofing it can only make the expected dig time longer, so it is left as is. |
| 4. World identity | Not started. This is a separate change, because `getName()` is obsolete but not deprecated. |
| 5. Detector data | Checked. Comparing the `Material` enums of 1.21.11 and 26.3 shows 157 new constants, and none of them are ores, pickaxes or tracked hard blocks. The table needs no 26.3 changes. The gaps it already had are covered in the PR. |
| 6. Real-server verification | **Pending.** It needs a Paper 26.3 server, which means downloading the jar and having the operator accept the Minecraft EULA. |
| 7. Docs | README and OPERATIONS updated to Paper 26.3, Java 25 and Kotlin 2.3. |

## What changed upstream that affects us

1. **Java 25 is required from 26.1.** Paper 26.x servers need Java 25, and the `paper-api` 26.x jars are Java 25 class files.
2. **New version scheme.** Minecraft numbers releases `YY.drop.hotfix` with no leading `1.`. Paper publishes the API as `<mc>.build.<n>-<channel>`, for example `26.2.build.133-stable`. Paper says to keep `build` in a dynamic version (`26.2.build.+`), because `26.2.+` could resolve to a breaking hotfix.
3. **No more remapper.** Paper 26.1 dropped Spigot-mapped and obfuscated plugin support. This plugin only uses the public Bukkit/Paper API with no NMS, so it is not affected.
4. **World names are being phased out.** `WorldInfo#getName` is now obsolete in favour of `getKey()`. The plugin sends `world` names in mining telemetry, and the backend stores and filters on them.
5. **26.3 deprecations:** `PlayerAnimationEvent`, `PotionEffectTypeCategory` and `PotionBrewer`. The plugin uses none of them.
6. **26.4 (upcoming):** Paper will merge the Bukkit, Spigot and Paper config files. This only matters to server owners, not to plugin code.

## Steps

### 1. Toolchain: Java 25 for the plugin only
- In `paper-plugin/build.gradle.kts`, override the root `jvmToolchain(21)` with `kotlin { jvmToolchain(25) }`. The core, `common-dto` and the host manager stay on 21. A Java 25 server can load `common-dto`'s 21-level bytecode.
- Kotlin is pinned at `2.2.21` in the root `build.gradle.kts`. If it cannot target JVM 25, bump the whole repo to Kotlin 2.3.x, since the version is shared by all modules. Then re-run the core build, because Spring and kotlin-spring/jpa follow that bump.
- Shadow `8.3.8` must be able to relocate Java 25 class files, which needs a recent ASM. If `shadowJar` fails with "Unsupported class file major version 69", move to Shadow 9.x.
- The Gradle 8.14.3 wrapper can stay. Toolchains let Gradle running on JDK 21 compile with JDK 25. Developers need JDK 25 installed, or toolchain auto-provisioning turned on.

### 2. Dependency and descriptor
- `compileOnly("io.papermc.paper:paper-api:26.2.build.133-stable")`. Pin the exact build so releases are reproducible, rather than using `build.+`.
- `plugin.yml`: change to `api-version: '26.2'`. Confirm that Paper accepts the new format by loading the plugin (step 6).

### 3. Compile and fix API breaks
- Run `./gradlew :paper-plugin:compileKotlin` and fix any errors. The API surface the plugin uses is small:
  - `Enchantment.EFFICIENCY` / `AQUA_AFFINITY`
  - `PotionEffectType.HASTE` / `MINING_FATIGUE`
  - `Block#getBreakSpeed(Player)`
  - `Material` checks: `WATER`, `isOccluding`, `isLiquid` and the `_ORE` name suffix
  - `BlockDamageEvent` / `BlockDamageAbortEvent` / `BlockBreakEvent`
  - `EntityPotionEffectEvent`, `server.scheduler.runTaskTimer`
- Treat new deprecation warnings as work items, not blockers.

### 4. World identity (`getName` → `getKey`)
- Agree on one world identifier with the backend. **Recommendation:** keep sending the name for now, because the core's `XrayDetector` windows, the PostGIS queries and the deck's `scopes` filter all key on it. Add a `worldKey` field (namespaced, e.g. `minecraft:overworld`) next to it in `BlockBreakTelemetry`. Switch the backend over in a separate change.
- On 26.1+, dimensions live under `world/dimensions/minecraft/`. Check that `getName()` still returns what operators see in the console.

### 5. Detector data (backend, same release)
- `FastMiningDetector` recomputes vanilla dig time from `MinecraftMechanicsRegistry` (40 lines, about 21 hardcoded entries). Check every entry against 26.2/26.3 block hardness and tool data. Add new ore-like or hard blocks from the 26.x drops if `tracked()` will see them.
- The plugin already sends Paper's `getBreakSpeed` as `nativeBreakSpeed`, and the core caps the theoretical time with it. Keep this, because it absorbs attribute-based mining modifiers (`mining_efficiency`, `block_break_speed`, `submerged_mining_speed`) that the hand-written formula ignores.

### 6. Verify on a real server (this cannot be skipped)
- Run Paper `26.2.build.133-stable` on **Java 25**, using a **copy** of a world. A world upgraded to 26.x cannot be downgraded.
- Check that:
  - the plugin enables, enrolls and sends heartbeats every 5 s;
  - mining batches arrive and `/caenis freeze|unfreeze` works from the console;
  - TPS and MSPT match the 1.21.11 baseline;
  - normal mining with Efficiency V + Haste II produces **no** fast-mining alerts (false-positive check);
  - fully occluded ore breaks set the exposure mask correctly.

### 7. Fleet and operations
- Host manager: Paper 26.x needs Java 25, so set `caenis.manager.java-command` to a Java 25 binary on each game host. It defaults to `java` from the PATH.
- Update the docs for the new version: the README badge `Paper-1.21.11` and the "Paper **1.21.11**" requirement, the OPERATIONS install section, and `Package-Caenis.ps1` if it checks the JDK.
- Release notes for operators: Java 25 on game hosts, back up worlds before upgrading, no downgrade.

### 8. Follow-up: 26.3
- When Paper ships a `26.3.build.N-stable` build, update the pin and set `api-version: '26.3'`. Repeat steps 3 and 6. Re-check the hardness table against 26.3's new blocks (wool and concrete stairs and slabs, cushions, straw beds). None of them are ores.

## Out of scope
- Supporting 1.21.11 and 26.x from one jar. Paper 26.x needs Java 25 and different API coordinates, so ship separate builds or drop 1.21.11.
- Movement telemetry, Folia support, and moving off `plugin.yml` to `paper-plugin.yml`.

## Sources
- [Mojang version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json): latest release 26.3
- [Paper downloads API](https://fill.papermc.io/v3/projects/paper): build channels per version and Java 25 minimum
- [Paper 26.1 announcement](https://papermc.io/news/26-1/): version scheme, remapper removal, world key API
- [Paper 26.3 announcement](https://papermc.io/news/26-3/): deprecations, no downgrade, 26.4 config merge
- [Updating your server version (GameServerKings)](https://www.gameserverkings.com/knowledge-base/minecraft/updating-your-server-version/): 2026 numbering change
