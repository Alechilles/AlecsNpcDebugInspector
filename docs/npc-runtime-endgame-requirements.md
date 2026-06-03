# NPC Runtime Endgame Requirements

## Purpose

Build a complete, AI-driven NPC testing system that lets an agent or modder validate Hytale NPC assets against live engine behavior, not only static JSON interpretation.

The endgame system has two cooperating halves:

- `alecsnpcdebuginspector`: the live server-side runtime harness. It creates or loads controlled test worlds, spawns fixtures, observes NPC behavior, and writes ground-truth traces.
- `HytaleNpcAssetTools`: the offline asset analysis and CLI layer. It indexes descriptors/schemas/assets, predicts behavior, writes runtime requests, waits for results, and compares static expectations against live traces.

The primary user is an AI agent doing asset development. The system must therefore favor stable machine-readable contracts, deterministic scenarios, actionable failure reports, and safe autonomous operation over interactive UI.

## Current Baseline

The harness already proves the first narrow live loop:

- `/npcruntime` exposes status, enable/disable, paths, and request execution.
- Requests are file-driven under `UserData\NpcRuntimeHarness`.
- A dedicated `npc_runtime_test_flatworld` can be created or loaded.
- A scenario can spawn one NPC by role, capture a live `NpcDebugSnapshotService` snapshot, write result JSON, write trace JSONL, and clean up the spawned NPC.
- The runner executes on the target world thread.
- A live no-player Tamework smoke run proved that `Mob_Tamework_Example_Simple` could spawn, snapshot, report Tamework diagnostics/config, and clean up.

Everything below describes the full target, not the current implementation.

## Goals

- Run NPC behavior tests autonomously while the Hytale server is running.
- Support no-player tests by default, with explicit player-required scenarios only when the behavior truly depends on player components or login state.
- Use a dedicated flatworld or equivalent isolated test instance so normal saves are not mutated.
- Exercise sensors, state transitions, actions, combat action evaluators, target selection, timers, pathing, inventory/equipment, flock behavior, and mod-specific state such as Tamework.
- Produce structured live traces that the offline CLI can compare against static simulation.
- Turn every failure into an actionable classification: asset bug, simulator gap, harness limitation, runtime-only behavior, or nondeterminism/timing tolerance issue.
- Make it practical for an AI agent to generate, run, inspect, and refine NPC behavior assets with minimal human intervention.

## Non-Goals

- No editor UI, tree browser, diff viewer, or guided visual authoring workflow for this phase.
- No arbitrary command execution from request files.
- No use of normal gameplay worlds as test arenas.
- No attempt to perfectly emulate Hytale offline when live harness evidence is available.
- No broad rebrand or package rename until the runtime harness contract is stable.

## Core Use Cases

- An AI agent writes or modifies an NPC asset, generates scenarios, runs them live, and uses the result to revise JSON.
- A modder validates that a role still behaves correctly after changing sensors, actions, descriptors, or Tamework config.
- A regression suite reruns known NPC scenarios after Hytale updates, mod dependency updates, or schema descriptor changes.
- The offline simulator detects a predicted behavior, then asks the runtime harness to verify whether Hytale actually behaves that way.
- A failure trace can be replayed or reduced into a minimal reproduction request.

## Scenario Requirements

Each scenario must be a deterministic, versioned JSON request with:

- Stable `requestId`.
- Asset and role identifiers.
- Max tick count and random seed where supported.
- Target world/arena selection.
- Environment setup: time, weather, lighting, difficulty-like flags if exposed, and world ticking policy.
- Fixture definitions for NPCs, mobs, target dummies, player anchors, items, blocks, beacons/spawners, flock members, family members, and mod-specific state.
- Observation policy: tick cadence, event categories, snapshot depth, trace size limits, and whether to include verbose debug sections.
- Assertion policy: expected states, target slots, action attempts, action success/failure, combat evaluator decisions, positions/ranges, timers, alarms, events, and final cleanup state.

Fixture identifiers must be deterministic inside the trace so the offline CLI can correlate `npcUnderTest`, `target_1`, `owner_anchor`, `baby_1`, and similar entities across events.

## Runtime Harness Requirements

The server-side harness must:

- Stay disabled by default unless explicitly enabled by command or dev config.
- Process one active run at a time for v1.
- Keep request, active, result, archive, and trace directories under the harness root.
- Atomically claim request files so partially-written files are not executed.
- Create, load, or validate the dedicated flatworld test instance.
- Keep the arena loaded and ticking without a player when the engine allows it.
- Fall back to a documented anchor strategy if chunk residency cannot be guaranteed through official APIs.
- Reset the arena before each run.
- Execute all world/entity mutation on the correct world thread.
- Spawn all requested fixtures or fail early with a machine-readable unsupported-fixture error.
- Run bounded tick loops without blocking the world thread indefinitely.
- Capture observations at the requested cadence.
- Clean up spawned entities and harness-owned blocks after success, failure, timeout, cancellation, or plugin shutdown.
- Write result JSON even for malformed requests, unsupported scenarios, runtime exceptions, and cleanup failures.

The harness must preserve enough failure evidence that an AI agent can continue without guessing.

## Observation Requirements

The trace must expose structured, machine-readable records for:

- Run lifecycle: request accepted, world ready, arena reset, fixtures spawned, tick start/end, timeout, cancellation, cleanup, run end.
- NPC identity: UUID, role, model, world, position, rotation, health, alive/dead state, chunk, and spawn metadata.
- AI control flow: state, substate, current instruction, queued instruction/motion, behavior tree or equivalent step names when visible.
- Sensor evaluation: sensor id/type, input target, matched entities, boolean result, numeric distance/range thresholds, visibility/line-of-sight, tags/factions, cooldown gates, and unknown/unsupported fields.
- Targeting: target slots, target UUIDs, acquisition/loss reason, distance, visibility, attitude, faction/team relationship, and priority decisions when observable.
- Actions: selected action, action preconditions, start tick, end tick, success/failure/cancel reason, cooldown application, motion/pathing side effects, and target used.
- Combat action evaluators: evaluator id/type, candidate action, target, range, line-of-sight, cooldown, resource/ammo/state gates, eligibility result, rejection reason where available, and chosen action.
- Timers and alarms: timer name/id, start, reset, expiry, remaining ticks, alarm fired, alarm consumed.
- Pathing and motion: path requested, path found/failed, destination, current velocity, stuck status, leash bounds, flee/chase/approach intent, and teleport/warp if used.
- Inventory and equipment: held item, equipped gear, consumed items, dropped items, ammo/resource state where exposed.
- Flock/family/social state: flock id, leader/follower status, nearby family members, baby/adult relationships, owner/companion relationships, protect/flee/social triggers.
- Tamework state: tame status, owner, needs, happiness, hunger, commands, behavior profile, config source, SQLite/data path, and diagnostics availability.
- Errors: exception class, message, phase, fixture id, cleanup status, and relevant stack summary.

Human-readable snapshot text may remain useful, but every field needed for comparison must exist as structured JSON.

## Assertion and Comparison Requirements

The offline CLI must be able to compare static predictions and live traces across:

- States and transitions.
- Sensor outcomes.
- Target acquisition and target slot contents.
- Action attempts and action completions.
- Combat evaluator eligibility and selected combat actions.
- Timer/alarm behavior.
- Pathing movement and distance bands.
- Fixture interaction, including damage, death, item pickup, block interaction, and flock/social response.
- Tamework-specific state transitions and command behavior.

The comparator must support:

- Tick windows and tolerance ranges.
- Optional assertions for fields that are not exposed in a given Hytale build.
- Classification of differences as asset bug, static simulator gap, runtime-only behavior, harness limitation, or nondeterministic result.
- Minimal reproduction output: the request file, relevant trace slice, asset paths, descriptor/schema sources, and a concise failure summary.

## CLI Requirements

`HytaleNpcAssetTools` must provide commands to:

- Write a runtime request from an asset, scenario template, or explicit fixture JSON.
- Wait for a result with timeout and clear exit codes.
- Print concise result summaries for AI consumption.
- Compare a live trace against static simulation.
- Batch-run a scenario matrix.
- Generate scenario templates from an asset's sensors/actions/descriptors.
- Promote a live trace into a regression fixture.
- Re-run only failing scenarios from a previous batch.
- Show harness paths and request/result locations.

CLI output must be intentionally compact by default, with machine-readable JSON output available for agent workflows.

## Knowledge Corpus Requirements

The offline layer must ingest and version:

- Mod asset JSON files.
- `npc_descriptors.json` generated by `/npc descriptors`.
- Full schema generator output when available.
- Mod-specific descriptor sets, including Tamework descriptors when installed.
- Known Hytale runtime API notes and harness contracts.
- Curated behavior examples and regression traces.

Descriptor ingestion must treat each world/mod set as a snapshot. If installed mods change, the tool must be able to refresh the corpus and identify descriptor/schema drift.

## Safety Requirements

The system must:

- Never execute arbitrary server/chat commands from request JSON.
- Only mutate harness-owned worlds/arenas.
- Refuse or warn on non-dev world targets.
- Enforce max ticks, max entities, max blocks, max trace bytes, and max concurrent runs.
- Use fixture allowlists and explicit unsupported-fixture errors.
- Never persist secrets or personal account data into traces.
- Clean up harness-owned entities and blocks after every run.
- Include cleanup status in the result.
- Make cancellation and shutdown cleanup best-effort and observable.

## Performance Requirements

- Default traces must be small enough for AI agents to ingest directly.
- Verbose trace sections must be opt-in.
- Batch runs must support deterministic ordering and resumable failure handling.
- The harness must avoid unbounded reflection or per-tick full object dumps.
- Long traces must be chunked, summarized, or sampled while preserving assertion-relevant events.

## Reporting Requirements

Every result must include:

- `requestId`, status, start/end time, ticks requested, ticks run, and scenario metadata.
- Paths to result and trace artifacts.
- Spawned fixture summary.
- Cleanup summary.
- States/actions/sensors/combat evaluators/timers/alarms observed.
- Assertion pass/fail details if assertions were requested.
- Failure classification and next diagnostic step when possible.

The human summary should be short. The machine JSON should contain the complete structured evidence.

## Integration Workflow Requirements

The intended autonomous workflow is:

1. AI agent edits or generates NPC JSON.
2. CLI indexes assets/descriptors/schemas.
3. CLI generates or selects scenarios.
4. CLI writes request files into the harness queue.
5. Running Hytale server executes requests in the dedicated flatworld.
6. Harness writes result JSON and trace JSONL.
7. CLI waits, compares, and classifies divergences.
8. AI agent applies asset or simulator fixes.
9. Passing traces can be promoted into regression fixtures.

This workflow must not require the player to be logged in for non-player scenarios.

## Milestones

1. Baseline live spawn and snapshot: done.
2. Single-NPC multi-tick observation with stable lifecycle events.
3. Target, item, block, mob, family, flock, and player-anchor fixture spawning.
4. Full sensor semantic observation and assertion coverage.
5. Full action and combat action evaluator observation and assertion coverage.
6. Tamework state fixture setup and Tamework-specific assertions.
7. Static simulation versus live trace comparison with useful classifications.
8. Batch regression suite with scenario matrices.
9. Autonomous local loop suitable for AI-driven development.

## Definition of Done for Full v1

Full v1 is complete when:

- An AI agent can generate a request for a real NPC asset, run it on a live local server, wait for the result, compare it to static expectations, and use the output to make a concrete asset change.
- The harness can run playerless scenarios in a dedicated flatworld without mutating normal saves.
- The trace includes enough structured data to validate sensors, actions, combat evaluators, target selection, timers, movement, and cleanup.
- Unsupported semantics are explicit in the result, not silently ignored.
- At least one meaningful regression matrix exists for base NPC behavior and one for Tamework-enabled behavior.
- Cleanup is reliable enough that repeated batch runs do not accumulate test entities or dirty arena state.
- Failure reports identify whether the next fix belongs in the asset, the offline simulator, the runtime harness, or the scenario request.
