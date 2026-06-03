# NPC Runtime Endgame Phased Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan phase-by-phase. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the full AI-driven NPC runtime testing system described in `docs/npc-runtime-endgame-requirements.md`.

**Architecture:** `alecsnpcdebuginspector` is the live Hytale server-side harness that owns flatworld execution, fixtures, observation, traces, and cleanup. `HytaleNpcAssetTools` is the offline CLI and knowledge layer that generates requests, waits for results, compares live traces with static predictions, builds scenario matrices, and produces actionable reports.

**Tech Stack:** Java/Maven/JUnit 5 for the Hytale mod harness; Python/pytest for `HytaleNpcAssetTools`; JSON request/result contracts; JSONL traces; local Hytale `UserData\NpcRuntimeHarness` file queue.

---

## Planning Notes

- Each phase is intended to be large enough to produce a meaningful working capability, but small enough to run as a focused Codex `/goal`.
- Commit after each phase.
- Do not rename Java packages, Maven coordinates, Python package names, or public commands unless a phase explicitly says so.
- Keep the system CLI-first and AI-agent-first. Do not add an editor UI.
- Preserve all existing unrelated dirty worktree changes.

## Repositories

- Live harness repo: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\alecsnpcdebuginspector`
- Offline CLI repo: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\HytaleNpcAssetTools`

## Phase 1: Runtime Contract Hardening

**/goal prompt:** Fully harden the NPC runtime request, result, trace, queue, and command contracts so malformed requests, unsupported fields, timeouts, cancellation, and cleanup status are always represented as stable machine-readable JSON.

**Primary repo:** `alecsnpcdebuginspector`

**Goal:** Make the existing baseline harness reliable enough that every later live feature can depend on its file contracts and failure behavior.

**Files:**
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeRequest.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeResult.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeTraceRecord.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeJson.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeRequestQueue.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeHarnessService.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeCommand.java`
- Modify: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeRequestTest.java`
- Modify: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeHarnessServiceTest.java`
- Modify: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeTraceWriterTest.java`
- Modify: `docs/npc-runtime-endgame-requirements.md` only if the contract changes need clarification.

**Steps:**
- [ ] Add request fields for `scenario`, `environment`, `fixtures`, `record`, `assertions`, and `limits`, even if later phases initially reject most fixture/assertion types as unsupported.
- [ ] Add explicit validation failures for missing ids, unsupported version, excessive ticks, excessive entities, excessive trace size, non-dev world id, and partially unsupported request sections.
- [ ] Add result fields for `status`, `classification`, `startedAt`, `endedAt`, `ticksRequested`, `ticksRun`, `error`, `unsupported`, `cleanup`, `artifacts`, and `summary`.
- [ ] Make malformed JSON produce a failed result and archived request instead of crashing or leaving the queue wedged.
- [ ] Make `/npcruntime cancel` write a cancellation result for an active or queued request where possible.
- [ ] Make `/npcruntime status` include enabled state, active request id, queued count, last result id/status, and harness root.
- [ ] Add tests for valid request parsing, malformed JSON failure, unsupported version, limit enforcement, deterministic result writing, and archive behavior.
- [ ] Run `.\mvnw.cmd test`.
- [ ] Commit with `Feat: harden NPC runtime contracts`.

**Acceptance:**
- Every request ends in exactly one result JSON unless the server process exits abruptly before the request is claimed.
- Unsupported future-facing fields are reported explicitly, not silently ignored.
- Existing simple live spawn requests still parse and run.

## Phase 2: Tick-Driven Scenario Runner

**/goal prompt:** Replace the one-shot NPC runtime runner with a bounded tick-driven scenario runner that records stable lifecycle and per-tick observation events without blocking the Hytale world thread.

**Primary repo:** `alecsnpcdebuginspector`

**Goal:** Move from spawn-and-snapshot to real multi-tick scenario execution.

**Files:**
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeLiveScenarioRunner.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeScenarioRun.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeTickScheduler.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeObservationCadence.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeTraceWriter.java`
- Create: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeObservationCadenceTest.java`
- Modify: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeHarnessServiceTest.java`

**Steps:**
- [ ] Introduce a run state object with request id, world id, NPC UUIDs, fixtures, current tick, deadline tick, cancellation flag, and cleanup state.
- [ ] Add a scheduler that advances one bounded observation step per world tick or server tick callback instead of sleeping or looping on the world thread.
- [ ] Emit `run-start`, `world-ready`, `arena-reset`, `fixture-spawn`, `tick-start`, `npc-snapshot`, `tick-end`, `cleanup`, and `run-end` events.
- [ ] Honor `record.everyTicks`, `record.includeSnapshots`, `record.includeEvents`, and max trace byte limits.
- [ ] Add timeout handling that writes a failed result with classification `harness-timeout`.
- [ ] Add cancellation handling that writes a canceled result with cleanup status.
- [ ] Add unit tests for cadence, timeout summary, cancellation summary, and trace event ordering.
- [ ] Run `.\mvnw.cmd test`.
- [ ] Run one live smoke request for `Mob_Tamework_Example_Simple` and verify the trace includes multiple tick events.
- [ ] Commit with `Feat: add tick-driven NPC runtime runner`.

**Acceptance:**
- A request with `ticks > 1` produces a multi-tick trace.
- The runner does not monopolize the world thread.
- Cancellation and timeout produce usable result files and cleanup evidence.

## Phase 3: Arena Residency, Reset, and Cleanup Reliability

**/goal prompt:** Make the NPC runtime flatworld arena repeatable by implementing documented chunk residency, arena reset, fixture ownership tracking, and robust cleanup after success, failure, cancellation, and shutdown.

**Primary repo:** `alecsnpcdebuginspector`

**Goal:** Ensure repeated playerless runs do not accumulate entities, dirty blocks, or stale runtime state.

**Files:**
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeFlatworldManager.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeArena.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeFixtureRegistry.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeCleanupReport.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeLiveScenarioRunner.java`
- Modify: `docs/runtime-harness-api-notes.md`
- Create: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeFixtureRegistryTest.java`
- Create: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeCleanupReportTest.java`

**Steps:**
- [ ] Document the confirmed chunk residency behavior in `docs/runtime-harness-api-notes.md`, including whether `WorldConfig.setCanUnloadChunks(false)` is sufficient.
- [ ] Add a fixture registry that records every spawned entity UUID, block mutation, and harness-owned marker with deterministic fixture ids.
- [ ] Add cleanup reporting for entity removal attempted/succeeded/failed, block reset attempted/succeeded/failed, and unresolved fixture ids.
- [ ] Add arena reset events before run start and cleanup events after run end.
- [ ] Add shutdown cleanup for active run state.
- [ ] Add explicit result classification `cleanup-failed` when the scenario behavior passes but cleanup leaves residue.
- [ ] Add tests for fixture registry ownership, duplicate fixture id rejection, cleanup report serialization, and cleanup failure result classification.
- [ ] Run `.\mvnw.cmd test`.
- [ ] Run two identical live smoke requests back-to-back and verify no stale spawned NPC is observed from the first run.
- [ ] Commit with `Feat: make NPC runtime arena repeatable`.

**Acceptance:**
- Repeated runs are safe against the same arena.
- Cleanup failures are visible and classified.
- The docs state the actual residency mechanism or fallback.

## Phase 4: Fixture Schema and Fixture Spawning

**/goal prompt:** Implement the NPC runtime fixture system for NPCs, target dummies, mobs, items, blocks, flock/family members, and player anchors with strict allowlists and actionable unsupported-fixture errors.

**Primary repo:** `alecsnpcdebuginspector`

**Goal:** Give scenarios enough controlled world state to exercise real NPC behavior.

**Files:**
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeRequest.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeFixtureSpawner.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeFixtureSpec.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeFixtureKind.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeFixtureSpawnResult.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeFixtureAllowlist.java`
- Modify: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeRequestTest.java`
- Create: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeFixtureSpecTest.java`
- Create: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeFixtureAllowlistTest.java`

**Steps:**
- [ ] Define fixture specs for `npcUnderTest`, `targetDummy`, `npc`, `mob`, `item`, `block`, `playerAnchor`, `familyMember`, and `flockMember`.
- [ ] Parse fixture positions, rotations, tags, role ids, entity ids, block ids, item ids, target slot intent, visibility intent, and faction/attitude hints.
- [ ] Enforce max fixture count and allowlisted fixture ids before mutating the world.
- [ ] Spawn fixtures with deterministic fixture ids and record spawn results in the trace.
- [ ] Return `unsupported-fixture` failures for fixture kinds or fields that cannot yet be implemented safely.
- [ ] Add tests for fixture parsing, allowlist enforcement, unsupported fields, deterministic fixture ids, and spawn result serialization.
- [ ] Run `.\mvnw.cmd test`.
- [ ] Live-test at least one NPC-under-test plus one target fixture, even if the first target implementation is another NPC role rather than a perfect dummy.
- [ ] Commit with `Feat: add NPC runtime fixtures`.

**Acceptance:**
- Scenario JSON can express the world setup needed by later sensor/action tests.
- The harness fails early and clearly for unsupported fixture semantics.
- All spawned fixtures are registered for cleanup.

## Phase 5: Structured Observation Model

**/goal prompt:** Convert NPC runtime observations from mostly snapshot text into normalized JSON trace records for identity, AI control flow, targeting, timers, alarms, pathing, inventory, flock, components, and Tamework state.

**Primary repo:** `alecsnpcdebuginspector`

**Goal:** Make live traces directly comparable by Python code without scraping human debug strings.

**Files:**
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/debug/NpcDebugSnapshot.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/debug/NpcDebugSnapshotService.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeObserver.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeObservedNpc.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeObservationDiff.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeLiveScenarioRunner.java`
- Create: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeObservationDiffTest.java`

**Steps:**
- [ ] Add structured fields to `NpcDebugSnapshot` where current snapshot data only exists as formatted lines.
- [ ] Add observer records for identity, state/substate/current instruction, target slots, timers, alarms, pathing, combat summary, inventory/equipment, flock, components, and Tamework sections.
- [ ] Add diffing that emits transition records only when observed fields change.
- [ ] Keep human-readable snapshot output intact for interactive inspector users.
- [ ] Add tests for observation diffing, null/unknown field serialization, and transition record generation.
- [ ] Run `.\mvnw.cmd test`.
- [ ] Run a live smoke and verify the trace contains structured `npc-state`, `targeting`, `timers`, `pathing`, and `tamework` records when available.
- [ ] Commit with `Feat: add structured NPC runtime observations`.

**Acceptance:**
- The trace contains structured observation records for all currently inspectable sections.
- Unknown or inaccessible data is represented explicitly as `unknown` or `unsupported`, not omitted when the distinction matters.

## Phase 6: Sensor Semantics Coverage

**/goal prompt:** Fully support NPC sensor semantics in the runtime testing system by recording live sensor evidence, adding sensor assertions, and teaching the Python CLI to compare live sensor outcomes with static simulation.

**Primary repos:** `alecsnpcdebuginspector` and `HytaleNpcAssetTools`

**Goal:** Close the live-vs-static loop for sensor behavior.

**Files in `alecsnpcdebuginspector`:**
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeSensorObserver.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeAssertion.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeAssertionResult.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeObserver.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeResult.java`
- Create: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeAssertionTest.java`

**Files in `HytaleNpcAssetTools`:**
- Modify: `src/hytale_npc_assets/runtime_semantics.py`
- Modify: `src/hytale_npc_assets/runtime_sim.py`
- Modify: `src/hytale_npc_assets/trace.py`
- Modify: `src/hytale_npc_assets/cli.py`
- Modify: `tests/test_runtime_semantics.py`
- Modify: `tests/test_runtime_sim.py`
- Modify: `tests/test_trace.py`
- Modify: `tests/test_cli.py`

**Steps:**
- [ ] Enumerate supported sensor categories from descriptor/schema corpus and current static simulator coverage.
- [ ] Add live trace records for sensor id/type, input fixture/target, match result, distance, visibility, tags/factions, threshold values, cooldown gates, and unsupported fields.
- [ ] Add request assertions for sensor expected result, expected target fixture id, expected distance band, and expected unknown/unsupported status.
- [ ] Add Java tests for assertion pass/fail/unknown output.
- [ ] Add Python trace parsing for sensor records.
- [ ] Add Python comparison output that classifies sensor mismatches as asset bug, simulator gap, harness limitation, runtime-only behavior, or tolerance issue.
- [ ] Add scenario fixtures that cover absent target, close/mid/far target, hidden target, hostile/friendly tags, and cooldown gates.
- [ ] Run `.\mvnw.cmd test` in `alecsnpcdebuginspector`.
- [ ] Run `pytest` in `HytaleNpcAssetTools`.
- [ ] Commit the Java repo with `Feat: observe NPC runtime sensors`.
- [ ] Commit the Python repo with `Feat: compare NPC runtime sensor traces`.

**Acceptance:**
- Every sensor semantic known to the static tool has either live observation support or an explicit unsupported reason.
- Python comparison can explain sensor divergences without manual trace reading.

## Phase 7: Action and Combat Evaluator Coverage

**/goal prompt:** Fully support NPC action and combat action evaluator semantics by recording live action eligibility, action selection, action lifecycle, combat evaluator decisions, and Python static-vs-live comparisons.

**Primary repos:** `alecsnpcdebuginspector` and `HytaleNpcAssetTools`

**Goal:** Validate the most behavior-critical part of NPC assets: what the NPC actually chooses to do.

**Files in `alecsnpcdebuginspector`:**
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeActionObserver.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeCombatEvaluatorObserver.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeObserver.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeAssertion.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeResult.java`
- Create: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeActionAssertionTest.java`

**Files in `HytaleNpcAssetTools`:**
- Modify: `src/hytale_npc_assets/actions.py`
- Modify: `src/hytale_npc_assets/combat_runtime.py`
- Modify: `src/hytale_npc_assets/runtime_sim.py`
- Modify: `src/hytale_npc_assets/trace.py`
- Modify: `src/hytale_npc_assets/cli.py`
- Modify: `tests/test_combat_runtime.py`
- Modify: `tests/test_runtime_sim.py`
- Modify: `tests/test_trace.py`
- Modify: `tests/test_cli.py`

**Steps:**
- [ ] Add live action trace records for candidate action, selected action, target, preconditions, start tick, end tick, success/failure/cancel reason, cooldown, and movement/pathing side effects.
- [ ] Add live combat evaluator trace records for evaluator id/type, candidate combat action, target, range, line-of-sight, cooldown, resource/ammo gates, eligibility result, rejection reason, and selected action.
- [ ] Add action assertions for expected action attempt, expected action completion, expected rejection, expected cooldown, expected target, and expected tick window.
- [ ] Add combat evaluator assertions for expected eligibility and expected selected combat action.
- [ ] Add Java tests for action/evaluator assertion pass, fail, unknown, and unsupported output.
- [ ] Add Python trace parsing and comparison for action and combat evaluator records.
- [ ] Add static-vs-live divergence classification for action selection and combat eligibility.
- [ ] Run `.\mvnw.cmd test` in `alecsnpcdebuginspector`.
- [ ] Run `pytest` in `HytaleNpcAssetTools`.
- [ ] Commit the Java repo with `Feat: observe NPC runtime actions`.
- [ ] Commit the Python repo with `Feat: compare NPC runtime action traces`.

**Acceptance:**
- Combat action evaluator behavior is no longer a major unknown.
- Action traces explain both selected behavior and rejected candidates when exposed by the engine.

## Phase 8: Runtime CLI Request, Wait, and Compare Workflow

**/goal prompt:** Implement the HytaleNpcAssetTools runtime CLI workflow so an AI agent can write request files, wait for live harness results, summarize results, compare traces, and rerun failures from the command line.

**Primary repo:** `HytaleNpcAssetTools`

**Goal:** Make the live harness usable without hand-writing JSON or browsing output folders.

**Files:**
- Create: `src/hytale_npc_assets/runtime_request.py`
- Create: `src/hytale_npc_assets/runtime_result.py`
- Create: `src/hytale_npc_assets/runtime_compare.py`
- Create: `src/hytale_npc_assets/runtime_batch.py`
- Modify: `src/hytale_npc_assets/trace.py`
- Modify: `src/hytale_npc_assets/cli.py`
- Create: `tests/test_runtime_request.py`
- Create: `tests/test_runtime_result.py`
- Create: `tests/test_runtime_compare.py`
- Create: `tests/test_runtime_batch.py`
- Modify: `tests/test_cli.py`

**Steps:**
- [ ] Add `runtime-request write` to generate v1 request JSON from asset id, role id, scenario file, tick count, harness root, and output path.
- [ ] Add `runtime-request wait` to wait for result JSON with timeout and clear exit codes.
- [ ] Add `runtime-result summarize` for compact human and JSON summaries.
- [ ] Add `runtime-trace compare` to compare live trace records against static simulator output.
- [ ] Add `runtime-run` as a convenience command that writes, waits, summarizes, and optionally compares.
- [ ] Add `runtime-batch rerun-failed` to rerun only failures from a previous batch result.
- [ ] Add tests for request JSON generation, timeout handling, exit codes, compact summaries, JSON summaries, and compare classifications.
- [ ] Run `pytest`.
- [ ] Commit with `Feat: add NPC runtime CLI workflow`.

**Acceptance:**
- An AI agent can run the live harness loop from Python commands without manually preparing request/result paths.
- CLI output is compact by default and machine-readable on request.

## Phase 9: Descriptor Corpus and Scenario Generation

**/goal prompt:** Build descriptor-aware scenario generation in HytaleNpcAssetTools using mod assets, npc_descriptors.json, schema generator output, known runtime contracts, and descriptor drift snapshots.

**Primary repo:** `HytaleNpcAssetTools`

**Goal:** Let the offline tool generate useful runtime scenarios from the actual descriptors and mods installed in the current world.

**Files:**
- Modify: `src/hytale_npc_assets/descriptor_context.py`
- Modify: `src/hytale_npc_assets/descriptor_discovery.py`
- Modify: `src/hytale_npc_assets/schema_dump_discovery.py`
- Modify: `src/hytale_npc_assets/schema_dump_validation.py`
- Create: `src/hytale_npc_assets/corpus_snapshot.py`
- Create: `src/hytale_npc_assets/scenario_generation.py`
- Modify: `src/hytale_npc_assets/cli.py`
- Modify: `tests/test_descriptor_context.py`
- Modify: `tests/test_schema_dump_discovery.py`
- Create: `tests/test_corpus_snapshot.py`
- Create: `tests/test_scenario_generation.py`
- Modify: `tests/test_cli.py`

**Steps:**
- [ ] Add corpus snapshot metadata for asset roots, descriptor files, schema dump roots, mod list, generated time, and content hashes.
- [ ] Add drift detection that reports descriptor/schema changes between snapshots.
- [ ] Add scenario generation for target absent, close/mid/far target, hidden target, hostile/friendly tags, cooldown gates, leash bounds, item present/absent, family member present/absent, and flock member present/absent.
- [ ] Use descriptor/schema data to avoid generating fixture or assertion fields that the current corpus cannot support.
- [ ] Add CLI commands for `corpus snapshot`, `corpus diff`, and `scenario generate`.
- [ ] Add tests for snapshot hashing, drift summaries, scenario ids, generated request compatibility, and descriptor-aware omissions.
- [ ] Run `pytest`.
- [ ] Commit with `Feat: generate runtime scenarios from descriptors`.

**Acceptance:**
- Scenario generation reacts to the installed mod set and descriptor corpus.
- Descriptor/schema drift is visible before comparing behavior.

## Phase 10: Tamework Runtime Fixtures and Assertions

**/goal prompt:** Add Tamework-specific runtime fixture setup, observation, assertions, and scenario generation for tame state, owner, needs, happiness, hunger, commands, behavior profiles, and config diagnostics.

**Primary repos:** `alecsnpcdebuginspector` and `HytaleNpcAssetTools`

**Goal:** Make the test system valuable for Tamework-enabled NPCs, not only base Hytale behavior.

**Files in `alecsnpcdebuginspector`:**
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/debug/NpcDebugTameworkIntegration.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/debug/NpcDebugTameworkApiIntegration.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeTameworkFixture.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeTameworkObserver.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeFixtureSpawner.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeObserver.java`
- Create: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeTameworkFixtureTest.java`

**Files in `HytaleNpcAssetTools`:**
- Modify: `src/hytale_npc_assets/runtime_semantics.py`
- Modify: `src/hytale_npc_assets/scenario_generation.py`
- Modify: `src/hytale_npc_assets/runtime_compare.py`
- Modify: `tests/test_runtime_semantics.py`
- Modify: `tests/test_scenario_generation.py`
- Modify: `tests/test_runtime_compare.py`

**Steps:**
- [ ] Add request fields for Tamework tame status, owner anchor, command state, needs/happiness/hunger overrides, and expected profile/config diagnostics.
- [ ] Implement Tamework fixture application through available API integration only when the Tamework plugin is present.
- [ ] Return `unsupported-mod-state` when Tamework is absent or a requested state cannot be applied safely.
- [ ] Add structured Tamework trace records that include data path, config source, profile, tame state, owner, needs, happiness, hunger, and command state when available.
- [ ] Add Java assertions for expected Tamework state.
- [ ] Add Python scenario generation for wild/tamed/owned/commanded Tamework cases.
- [ ] Add Python comparison of Tamework trace records against expected static/config-derived state.
- [ ] Run `.\mvnw.cmd test` in `alecsnpcdebuginspector`.
- [ ] Run `pytest` in `HytaleNpcAssetTools`.
- [ ] Live-test one Tamework-enabled request in the local Update5Test environment.
- [ ] Commit the Java repo with `Feat: add Tamework runtime fixtures`.
- [ ] Commit the Python repo with `Feat: compare Tamework runtime traces`.

**Acceptance:**
- Tamework-specific behavior can be tested without relying on manual in-game setup.
- Absence or incompatibility of Tamework is explicit and non-fatal.

## Phase 11: Batch Regression and Failure Triage

**/goal prompt:** Build the runtime batch regression system that runs scenario matrices, stores compact artifacts, reruns failures, classifies divergences, and emits minimal reproduction bundles for NPC behavior failures.

**Primary repo:** `HytaleNpcAssetTools`

**Goal:** Turn individual live tests into a repeatable regression workflow.

**Files:**
- Modify: `src/hytale_npc_assets/runtime_batch.py`
- Modify: `src/hytale_npc_assets/runtime_compare.py`
- Create: `src/hytale_npc_assets/runtime_report.py`
- Create: `src/hytale_npc_assets/regression_fixtures.py`
- Modify: `src/hytale_npc_assets/cli.py`
- Create: `tests/test_runtime_report.py`
- Create: `tests/test_regression_fixtures.py`
- Modify: `tests/test_runtime_batch.py`
- Modify: `tests/test_runtime_compare.py`
- Modify: `tests/test_cli.py`

**Steps:**
- [ ] Add batch manifests with deterministic scenario order, harness root, timeout, compare mode, and artifact output directory.
- [ ] Add resumable batch state so interrupted runs can continue without repeating passed scenarios.
- [ ] Add failure rerun support with configurable retry count and nondeterminism notes.
- [ ] Add minimal reproduction bundles containing request JSON, result JSON, relevant trace slice, asset paths, descriptor/schema snapshot id, and comparison summary.
- [ ] Add report output in compact text and JSON.
- [ ] Add commands for `runtime-batch run`, `runtime-batch status`, `runtime-batch rerun-failed`, and `runtime-trace promote-regression`.
- [ ] Add tests for deterministic ordering, resume behavior, failure bundle contents, rerun filtering, and JSON report shape.
- [ ] Run `pytest`.
- [ ] Commit with `Feat: add NPC runtime regression batches`.

**Acceptance:**
- A full scenario matrix can run without manual supervision.
- Failures come with enough artifact context for an AI agent to decide the next edit.

## Phase 12: Autonomous Local Loop and Documentation

**/goal prompt:** Finish the NPC runtime endgame v1 by documenting and validating the autonomous local workflow from asset edit to request generation, live run, trace comparison, failure classification, and regression promotion.

**Primary repos:** `alecsnpcdebuginspector` and `HytaleNpcAssetTools`

**Goal:** Make the whole system usable as an end-to-end AI-driven NPC development tool.

**Files in `alecsnpcdebuginspector`:**
- Modify: `README.md`
- Modify: `docs/npc-runtime-endgame-requirements.md`
- Create: `docs/npc-runtime-harness-usage.md`
- Modify: `docs/runtime-harness-api-notes.md`
- Modify: `src/main/resources/manifest.json` only if the existing description should mention optional runtime automation.

**Files in `HytaleNpcAssetTools`:**
- Modify: `README.md`
- Create: `docs/runtime-testing-workflow.md`
- Create: `docs/runtime-trace-contract.md`
- Create: `docs/runtime-scenario-examples.md`
- Modify: `tests/test_cli.py`

**Steps:**
- [ ] Document the harness setup, `/npcruntime` commands, harness paths, request/result/trace contract, safety model, and cleanup behavior.
- [ ] Document the CLI workflow for request write, wait, run, compare, batch, rerun failed, and promote regression.
- [ ] Add at least one base NPC example and one Tamework-enabled example.
- [ ] Add a final requirements coverage checklist that maps each `npc-runtime-endgame-requirements.md` section to the implemented phase.
- [ ] Run `.\mvnw.cmd test` in `alecsnpcdebuginspector`.
- [ ] Run `pytest` in `HytaleNpcAssetTools`.
- [ ] Run one live end-to-end smoke if the Hytale server is available: generate request with Python, execute through `/npcruntime` or file queue, wait for result, compare trace, and promote a regression fixture.
- [ ] Commit the Java repo with `Docs: document NPC runtime harness workflow`.
- [ ] Commit the Python repo with `Docs: document NPC runtime CLI workflow`.

**Acceptance:**
- A new Codex agent can follow the docs to run the full local workflow.
- The requirements document has no major uncovered v1 sections.
- The system remains CLI-first and does not require an editor UI.

## Suggested Execution Order

1. Phase 1: Runtime Contract Hardening
2. Phase 2: Tick-Driven Scenario Runner
3. Phase 3: Arena Residency, Reset, and Cleanup Reliability
4. Phase 4: Fixture Schema and Fixture Spawning
5. Phase 5: Structured Observation Model
6. Phase 6: Sensor Semantics Coverage
7. Phase 7: Action and Combat Evaluator Coverage
8. Phase 8: Runtime CLI Request, Wait, and Compare Workflow
9. Phase 9: Descriptor Corpus and Scenario Generation
10. Phase 10: Tamework Runtime Fixtures and Assertions
11. Phase 11: Batch Regression and Failure Triage
12. Phase 12: Autonomous Local Loop and Documentation

## Verification Commands

Run in `C:\Users\22ale\AppData\Roaming\Hytale\Modding\alecsnpcdebuginspector`:

```powershell
.\mvnw.cmd test
```

Run in `C:\Users\22ale\AppData\Roaming\Hytale\Modding\HytaleNpcAssetTools`:

```powershell
pytest
```

When live verification is required and the local Hytale server is running:

```powershell
hytale-npc-assets runtime-run --asset Mob_Tamework_Example_Simple --role Mob_Tamework_Example_Simple --ticks 40 --harness-root "$env:APPDATA\Hytale\UserData\NpcRuntimeHarness"
```

If the CLI entrypoint is unavailable in the current shell, use:

```powershell
python -m hytale_npc_assets.cli runtime-run --asset Mob_Tamework_Example_Simple --role Mob_Tamework_Example_Simple --ticks 40 --harness-root "$env:APPDATA\Hytale\UserData\NpcRuntimeHarness"
```

## Coverage Check

- Goals, non-goals, and use cases: covered by Phases 1, 8, 11, and 12.
- Scenario requirements: covered by Phases 1, 4, 8, and 9.
- Runtime harness requirements: covered by Phases 1, 2, 3, and 4.
- Observation requirements: covered by Phases 5, 6, 7, and 10.
- Assertion and comparison requirements: covered by Phases 6, 7, 8, 10, and 11.
- CLI requirements: covered by Phases 8, 9, 11, and 12.
- Knowledge corpus requirements: covered by Phase 9.
- Safety requirements: covered by Phases 1, 3, 4, and 12.
- Performance requirements: covered by Phases 2, 5, and 11.
- Reporting requirements: covered by Phases 1, 8, and 11.
- Integration workflow requirements: covered by Phases 8, 11, and 12.
- Full v1 definition of done: covered by Phase 12 after all earlier phases land.
