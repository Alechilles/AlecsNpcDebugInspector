# NPC Runtime Harness Flatworld Implementation Plan

> **Purpose:** Expand Alec's NPC Inspector from an in-game inspection mod into the runtime half of a larger AI-driven NPC development toolkit. The existing Python `HytaleNpcAssetTools` simulator remains the fast offline predictor; this repo becomes the optional live Hytale ground-truth harness.

## Goal

Build a server-side runtime test harness that can run NPC behavior scenarios autonomously in a dedicated flatworld test instance, keep the test area loaded without a player when the engine allows it, write structured trace output, and let external tools compare live Hytale behavior against static simulation.

The harness must be safe, bounded, and development-only by default. It must not expose arbitrary server command execution.

## Current Repo Fit

This repo already has the right foundation:

- `AlecsNpcDebugInspector` owns plugin startup/shutdown and command registration.
- `NpcDebugCommand` already exposes `/npcdebug` and can be extended with subcommands or accompanied by a new command.
- `NpcDebugSnapshotService` already knows how to inspect live NPC state: role, state, targeting, pathing, timers, alarms, combat, inventory, flags, flock, components, and Tamework sections.
- Existing tests are conventional JUnit 5 tests.
- The Maven `run-server` profile already supports local server launch with this mod installed.

## Product Direction

Short-term name in code can remain `alecsnpcdebuginspector` to avoid churn. User-facing docs can start positioning it as:

- **Alec's NPC Inspector**: interactive in-game inspection UI.
- **Alec's NPC Runtime Harness**: automated flatworld scenario runner and trace recorder.
- Future umbrella: **Alec's NPC Toolkit** or **Alec's NPC Dev Tools**.

Do not rename Java packages or artifact IDs until the runtime harness proves itself.

## Architecture

```text
AI / CLI
  writes request JSON
  waits for result JSON
  ingests trace JSONL
        |
        v
Alec's NPC Inspector Runtime Harness
  polls request directory or receives command
  validates request against allowlist
  loads flatworld test instance / arena
  resets fixtures
  spawns NPC + targets/items/blocks
  records tick snapshots/events
  writes result + trace files
        |
        v
Dedicated NPC Runtime Flatworld
  controlled time/weather/lighting
  fixed arenas
  no normal gameplay state
  no player required unless scenario asks for one
```

## File and Package Plan

Add a new package:

```text
src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/
```

Suggested classes:

- `NpcRuntimeHarnessService`
  - Owns enable/disable state, request polling, active run lifecycle, and shutdown cleanup.
- `NpcRuntimeHarnessConfig`
  - Stores root paths, enable flag, max ticks, max entities, world/instance id, arena origin, and polling interval.
- `NpcRuntimeRequest`
  - Parsed request DTO.
- `NpcRuntimeScenario`
  - Scenario DTO: asset/role, ticks, seed, target setup, blocks/items, Tamework state, expected probes.
- `NpcRuntimeResult`
  - Result DTO written as JSON.
- `NpcRuntimeTraceWriter`
  - Appends JSONL trace records.
- `NpcRuntimeFlatworldManager`
  - Creates/locates/loads the dedicated world or instance and manages chunk/area residency.
- `NpcRuntimeArena`
  - Resets fixed arena geometry and cleans up entities after each run.
- `NpcRuntimeFixtureSpawner`
  - Spawns NPC under test, target dummy, hostile/friendly mobs, items, blocks, leash markers, and optional player anchors.
- `NpcRuntimeObserver`
  - Captures tick-level state from NPCs using existing snapshot/reflection helpers where possible.
- `NpcRuntimeCommand`
  - `/npcruntime status|enable|disable|run|cancel|paths`.
- `NpcRuntimeJson`
  - Minimal JSON serialization/deserialization helper. Prefer existing Hytale/Java JSON utilities if available; otherwise use small explicit serializers to avoid adding a dependency until needed.

Add tests:

```text
src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/
```

Suggested test classes:

- `NpcRuntimeRequestTest`
- `NpcRuntimeTraceWriterTest`
- `NpcRuntimeHarnessConfigTest`
- `NpcRuntimeResultTest`
- `NpcRuntimeHarnessServiceTest`

## Runtime Paths

Use a predictable development-only folder under Hytale UserData:

```text
C:\Users\22ale\AppData\Roaming\Hytale\UserData\NpcRuntimeHarness\
  requests\
  active\
  results\
  traces\
  archive\
```

Request file:

```text
requests\protect_baby_close_target.request.json
```

Result files:

```text
results\protect_baby_close_target.result.json
traces\protect_baby_close_target.trace.jsonl
```

Move processed requests to `archive\` with status metadata so failed runs are inspectable.

## Request Schema v1

Example:

```json
{
  "version": 1,
  "requestId": "protect_baby_close_target",
  "assetId": "Alec_Template_Boar_Family_Adult",
  "roleId": "Alec_Template_Boar_Family_Adult",
  "ticks": 200,
  "seed": 7,
  "world": {
    "instanceId": "npc_runtime_test_flatworld",
    "arena": "default"
  },
  "environment": {
    "timeOfDay": 12000,
    "weather": "clear",
    "light": 15
  },
  "fixtures": {
    "npc": {
      "position": [0, 64, 0],
      "state": "Idle"
    },
    "targets": [
      {
        "slot": "Enemy",
        "kind": "dummy",
        "position": [4, 64, 0],
        "tags": ["hostile"],
        "visible": true
      }
    ],
    "mobs": [],
    "items": [],
    "blocks": []
  },
  "record": {
    "everyTicks": 1,
    "includeSnapshots": true,
    "includeEvents": true
  }
}
```

## Trace Schema v1

Each JSONL line should be independently parseable and stable:

```json
{"version":1,"requestId":"protect_baby_close_target","tick":0,"kind":"run-start","assetId":"Alec_Template_Boar_Family_Adult"}
{"version":1,"requestId":"protect_baby_close_target","tick":1,"kind":"npc-snapshot","npcUuid":"...","role":"...","state":"Idle","position":[0.0,64.0,0.0]}
{"version":1,"requestId":"protect_baby_close_target","tick":4,"kind":"target","slot":"Enemy","present":true,"range":4.0,"visible":true,"tags":["hostile"]}
{"version":1,"requestId":"protect_baby_close_target","tick":5,"kind":"transition","field":"state","from":"Idle","to":"ProtectBaby"}
{"version":1,"requestId":"protect_baby_close_target","tick":6,"kind":"combat","ability":"Charge","eligible":true,"targetSlot":"Enemy","targetRange":4.0}
{"version":1,"requestId":"protect_baby_close_target","tick":200,"kind":"run-end","status":"passed"}
```

Initial trace categories:

- `run-start`
- `arena-reset`
- `fixture-spawn`
- `npc-snapshot`
- `target`
- `transition`
- `timer`
- `alarm`
- `combat`
- `pathing`
- `inventory`
- `flock`
- `tamework`
- `error`
- `run-end`

## Safety Guardrails

- Harness disabled by default unless config/command enables it.
- Refuse to run outside a known dev/test world id.
- Do not execute arbitrary chat/server commands from request JSON.
- Allowlist fixture types, block ids, item ids, entity ids, and max counts.
- Hard cap ticks, entities, arenas, and concurrent runs.
- One active scenario at a time for v1.
- Clean up spawned fixtures after every run, including failure paths.
- Result JSON must include `status`, `startedAt`, `endedAt`, `error`, and cleanup outcome.

## Implementation Tasks

### Task 1: Add Runtime Harness Plan and Configuration Skeleton

Files:

- Create `runtime/NpcRuntimeHarnessConfig.java`
- Create `runtime/NpcRuntimePaths.java`
- Create tests for default paths, disabled-by-default behavior, max tick/entity bounds.

Acceptance:

- Config can resolve UserData harness directories.
- Defaults are safe: disabled, one active run, bounded ticks/entities.
- No Hytale world APIs used yet.

### Task 2: Add Request, Scenario, Result, and Trace DTOs

Files:

- Create `NpcRuntimeRequest`
- Create `NpcRuntimeScenario`
- Create `NpcRuntimeResult`
- Create `NpcRuntimeTraceRecord`
- Create `NpcRuntimeJson`

Acceptance:

- Parse a v1 request JSON.
- Reject missing `requestId`, missing asset/role, unsupported version, excessive ticks/entities.
- Write deterministic result JSON and JSONL traces.
- Unit tests do not require Hytale server runtime.

### Task 3: Add File Queue Poller

Files:

- Create `NpcRuntimeRequestQueue`
- Create `NpcRuntimeHarnessService`

Acceptance:

- Service scans `requests\*.request.json`.
- Atomically moves one request to `active\`.
- Writes result to `results\`.
- Archives request after success/failure.
- Handles malformed JSON with a failed result instead of crashing plugin.

### Task 4: Add Command Surface

Files:

- Create `NpcRuntimeCommand`
- Modify `AlecsNpcDebugInspector.setup()` to register it.

Commands:

```text
/npcruntime status
/npcruntime enable
/npcruntime disable
/npcruntime paths
/npcruntime run <requestId>
/npcruntime cancel
```

Acceptance:

- Commands are admin/dev oriented.
- `status` reports enabled state, active request, queue count, and output paths.
- `run` can enqueue or trigger an existing request file.

### Task 5: Flatworld API Discovery Spike

Purpose:

Find the exact Hytale APIs for:

- Creating or locating a world/instance.
- Keeping an instance/chunks ticking without players.
- Placing blocks.
- Spawning NPC entities by role/template/asset id.
- Spawning non-player target dummies or simple hostile/friendly NPCs.
- Setting time/weather/light if exposed.
- Removing spawned entities safely.

Acceptance:

- Add `docs/runtime-harness-api-notes.md` with confirmed API names and fallback options.
- If chunk/instance force-loading is available, document the API and owner lifecycle.
- If not available, document fallback: server-controlled anchor entity or optional logged-in admin player parked in arena.

### Task 6: Build Flatworld Manager and Arena Reset

Files:

- Create `NpcRuntimeFlatworldManager`
- Create `NpcRuntimeArena`

Acceptance:

- Locate or create `npc_runtime_test_flatworld`.
- Prepare a fixed flat arena.
- Reset arena blocks/entities before each run.
- Keep area loaded if API supports it.
- Emit trace records for load/reset/fallback mode.

### Task 7: Spawn Fixtures

Files:

- Create `NpcRuntimeFixtureSpawner`

Acceptance:

- Spawn NPC under test at requested position.
- Spawn target fixtures with position, tags, visibility intent, and target slot metadata where possible.
- Spawn item/block/mob fixtures.
- Return fixture refs/UUIDs for cleanup and trace recording.
- If a requested fixture type cannot be spawned with known APIs, fail the request with actionable error.

### Task 8: Tick Runner and Observer

Files:

- Create `NpcRuntimeScenarioRunner`
- Create `NpcRuntimeObserver`

Acceptance:

- Run for bounded tick count.
- Record snapshots every `record.everyTicks`.
- Reuse `NpcDebugSnapshotService` and lower-level helpers where practical.
- Record role/state/substate, target slots, timers, alarms, combat fields, pathing, inventory, flock, and Tamework fields.
- Detect transitions by diffing prior tick snapshot fields.

### Task 9: Result Summary and Trace Comparison Contract

Files:

- Extend result JSON output.
- Add a machine-readable summary section.

Acceptance:

Result includes:

```json
{
  "status": "passed",
  "requestId": "...",
  "ticksRun": 200,
  "npcUuid": "...",
  "tracePath": "...",
  "summary": {
    "statesSeen": ["Idle", "ProtectBaby"],
    "actionsInferred": [],
    "combatAbilitiesSeen": ["Charge"],
    "timersSeen": ["Protect_Window"],
    "alarmsSeen": ["ProtectBaby"]
  }
}
```

The Python CLI can consume this without parsing human UI text.

### Task 10: Python CLI Integration in HytaleNpcAssetTools

This task lives in `C:\Users\22ale\AppData\Roaming\Hytale\Modding\HytaleNpcAssetTools`, but should be planned against this harness contract.

Commands:

```powershell
python -m hytale_npc_assets.cli runtime-request write --asset Alec_Template_Boar_Family_Adult --scenario protect-baby-close --out <requests>
python -m hytale_npc_assets.cli runtime-request wait --request-id protect-baby-close --results <results>
python -m hytale_npc_assets.cli runtime-trace compare --asset Alec_Template_Boar_Family_Adult --trace <trace.jsonl>
python -m hytale_npc_assets.cli runtime-run --asset Alec_Template_Boar_Family_Adult --scenario scenarios\protect-baby-close.json
```

Acceptance:

- CLI writes request JSON matching v1 schema.
- CLI waits for result with timeout.
- CLI compares live trace against static `simulate` output and reports divergence.
- CLI can emit regression fixtures from a trace.

### Task 11: Scenario Matrix

Add starter scenarios:

- target absent
- target present close/mid/far
- target hidden
- hostile tag present/missing
- cooldown active/inactive
- leash inside/outside
- item present/absent
- baby/family target present/absent
- Tamework wild/tamed/owner states

Acceptance:

- Scenario files are deterministic and small.
- Each scenario has a stable id, max ticks, fixtures, and expected probes.
- CLI can run a batch by writing multiple request files.

### Task 12: Rebranding Pass

Do after the harness works.

Acceptance:

- README describes the two modes: interactive inspector and runtime harness.
- Manifest description can mention runtime harness if appropriate.
- Keep artifact id/package stable unless intentionally preparing a major release.
- Release notes explain that this is still safe as an inspector mod but now includes optional dev-only automation.

## Verification Plan

Unit tests:

```powershell
.\mvnw.cmd test
```

Local server smoke:

```powershell
.\mvnw.cmd -Prun-server package
```

Manual/runtime smoke:

1. Start local dev server.
2. Confirm `/npcruntime status`.
3. Enable harness.
4. Drop a minimal request JSON into `requests\`.
5. Confirm result and trace files are written.
6. Confirm cleanup removes spawned fixtures.
7. Run Python compare command from `HytaleNpcAssetTools`.

## Open Technical Questions

These must be answered during Task 5:

- Can Hytale keep a world/instance/chunk ticking without players?
- Is there an official chunk ticket or force-load API?
- Can the server create a flatworld instance at runtime, or must it be pre-created?
- What is the correct API for spawning an NPC by role/template/asset id?
- Can target slot state be set directly, or must it be induced through engine actions?
- Can dummy target entities be non-player entities, or do some sensors require real `Player` components?
- What is the safest way to remove all fixtures after failed runs?

## First Milestone

The first useful milestone is deliberately small:

```text
Given a request file for one NPC role:
  harness picks it up
  records status
  loads/anchors test arena if possible
  writes a trace containing run-start, periodic snapshots, and run-end
  cleans up
```

It does not need full fixture spawning or combat tracing to prove the queue, output contract, and lifecycle.

## Definition of Done for Full v1

- AI can write a request file while the server is running.
- No player login is required for scenarios that do not explicitly require a player.
- The harness runs one flatworld scenario autonomously.
- The harness writes stable result JSON and trace JSONL.
- The Python CLI can compare live trace to static simulation.
- Divergences are reported as actionable simulator gaps, asset behavior differences, or runtime-only caveats.
- All generated entities/blocks are cleaned up after every run.
