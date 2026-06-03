# NPC Runtime Headless-First Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan phase-by-phase. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorder the NPC runtime roadmap so Codex can start a local Hytale server, load the dedicated test instance, run NPC runtime requests, wait for results, and inspect artifacts without the user logging into a server.

**Architecture:** Keep `alecsnpcdebuginspector` as the live in-server runtime harness and add a headless status/heartbeat contract that external tools can poll. Add `HytaleNpcAssetTools` commands that manage the local server process, write request files, wait for harness readiness/results, and produce compact summaries. Deeper fixture, sensor, action, and combat semantics remain important, but they move behind the end-to-end autonomous execution loop.

**Tech Stack:** Java/Maven/JUnit 5 for the Hytale mod harness; Python/pytest for `HytaleNpcAssetTools`; JSON request/result/status contracts; JSONL traces; local Hytale `UserData\NpcRuntimeHarness` file queue; PowerShell-compatible process launch on Windows.

---

## Planning Notes

- This plan supersedes the execution order in `docs/superpowers/plans/2026-06-03-npc-runtime-endgame-phased-plan.md`; it does not delete those semantic phases.
- Completed work remains completed: hardened request/result contracts and the tick-driven runner are already baseline capabilities.
- Prioritize commands that let an AI agent operate end-to-end:
  - build or install the harness,
  - start the server,
  - wait for harness readiness,
  - ensure the flatworld is loaded/ticking,
  - enqueue a request,
  - wait for result,
  - summarize trace/result evidence,
  - stop or leave the server running intentionally.
- Keep the harness safe: no arbitrary server command execution from request JSON, no mutation of normal worlds, bounded runs, one active run for v1.
- Commit after each phase in the repo touched by that phase. Preserve unrelated dirty files.

## Repositories

- Live harness repo: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\alecsnpcdebuginspector`
- Offline CLI repo: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\HytaleNpcAssetTools`

## Target Headless Workflow

The first target workflow should be:

```powershell
cd "C:\Users\22ale\AppData\Roaming\Hytale\Modding\HytaleNpcAssetTools"
python -m hytale_npc_assets.cli runtime-run `
  --asset Mob_Tamework_Example_Simple `
  --role Mob_Tamework_Example_Simple `
  --ticks 20 `
  --ensure-server `
  --server-repo "C:\Users\22ale\AppData\Roaming\Hytale\Modding\alecsnpcdebuginspector" `
  --harness-root "$env:APPDATA\Hytale\UserData\NpcRuntimeHarness" `
  --world npc_runtime_test_flatworld `
  --json
```

Expected behavior:

1. If the server is not running, the CLI starts it.
2. The CLI waits for `status\harness-status.json` to report `serverReady=true`, `harnessEnabled=true`, and `worldReady=true`.
3. The CLI writes a request JSON.
4. The harness claims the request, runs it in the dedicated flatworld, writes result and trace artifacts, and archives the request.
5. The CLI exits with `0` for pass, non-zero for failure/timeout/unsupported semantics.
6. Codex can read the compact JSON summary and decide the next implementation or asset edit without asking the user to log in.

## Headless Contract

Add this stable status file under the harness root:

```text
C:\Users\22ale\AppData\Roaming\Hytale\UserData\NpcRuntimeHarness\status\harness-status.json
```

Minimum shape:

```json
{
  "version": 1,
  "serverReady": true,
  "harnessEnabled": true,
  "worldReady": true,
  "worldId": "npc_runtime_test_flatworld",
  "worldTicking": true,
  "playerCount": 0,
  "activeRequestId": null,
  "queuedCount": 0,
  "lastResult": {
    "requestId": "tamework_example_simple_tickdriven_v2",
    "status": "passed",
    "classification": "passed"
  },
  "paths": {
    "root": "C:\\Users\\22ale\\AppData\\Roaming\\Hytale\\UserData\\NpcRuntimeHarness",
    "requests": "C:\\Users\\22ale\\AppData\\Roaming\\Hytale\\UserData\\NpcRuntimeHarness\\requests",
    "results": "C:\\Users\\22ale\\AppData\\Roaming\\Hytale\\UserData\\NpcRuntimeHarness\\results",
    "traces": "C:\\Users\\22ale\\AppData\\Roaming\\Hytale\\UserData\\NpcRuntimeHarness\\traces",
    "archive": "C:\\Users\\22ale\\AppData\\Roaming\\Hytale\\UserData\\NpcRuntimeHarness\\archive"
  },
  "updatedAt": "2026-06-03T23:38:29.358562Z"
}
```

The CLI should treat missing or stale status as "server not ready" and should surface the newest server log lines as the next diagnostic step.

---

## Phase 1: Harness Auto-Enable and Readiness Status

**/goal prompt:** Make the NPC runtime harness usable without an in-game `/npcruntime enable` command by adding a dev-only auto-enable path and a stable `harness-status.json` heartbeat that reports server, harness, queue, and flatworld readiness.

**Primary repo:** `alecsnpcdebuginspector`

**Goal:** A headless process can detect whether the server-side harness is alive and ready to accept requests without any logged-in player.

**Files:**
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeHarnessConfig.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeHarnessService.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimePaths.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeHarnessStatus.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeHarnessStatusWriter.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeCommand.java`
- Create: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeHarnessStatusTest.java`
- Modify: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeHarnessConfigTest.java`
- Modify: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeHarnessServiceTest.java`
- Modify: `docs/runtime-harness-api-notes.md`

**Steps:**
- [ ] Add config fields for `autoEnable`, `statusWriteIntervalMillis`, `statusStaleAfterMillis`, and `defaultWorldId`.
- [ ] Read `autoEnable` from a dev-only system property such as `-Dalec.npcRuntime.autoEnable=true` and an optional environment variable such as `ALEC_NPC_RUNTIME_AUTO_ENABLE=true`.
- [ ] Add a `status` directory to `NpcRuntimePaths`.
- [ ] Create `NpcRuntimeHarnessStatus` with the exact fields from the headless contract above.
- [ ] Create `NpcRuntimeHarnessStatusWriter` that writes to a temporary file and atomically replaces `status\harness-status.json`.
- [ ] Update the service loop so it writes status on startup, after enable/disable, after queue changes, after active run changes, after result writing, and during idle polling.
- [ ] Make `/npcruntime status` include the status file path and whether the latest status was written successfully.
- [ ] Add tests for default disabled behavior, auto-enable behavior, status JSON shape, atomic write behavior, and last-result reporting.
- [ ] Run `.\mvnw.cmd test`.
- [ ] Commit with `Feat: add NPC runtime headless readiness status`.

**Acceptance:**
- Starting the server with auto-enable writes `harness-status.json` without player login.
- The status file distinguishes `serverReady`, `harnessEnabled`, and `worldReady`.
- The status file includes queue count, active request id, last result, paths, and timestamp.

---

## Phase 2: Boot-Time Flatworld Readiness

**/goal prompt:** Make the NPC runtime harness create or load the dedicated flatworld during headless startup and report whether the world is ticking without requiring a player to join.

**Primary repo:** `alecsnpcdebuginspector`

**Goal:** Server startup plus harness auto-enable should be enough to prepare `npc_runtime_test_flatworld` for requests.

**Files:**
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeFlatworldManager.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeHarnessService.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeHarnessStatus.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeLiveScenarioRunner.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeWorldReadiness.java`
- Create: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeWorldReadinessTest.java`
- Modify: `docs/runtime-harness-api-notes.md`

**Steps:**
- [ ] Add a service startup step that calls `NpcRuntimeFlatworldManager.ensureWorld(defaultWorldId)` when auto-enable is true.
- [ ] Set world config guardrails for the runtime world: ticking enabled, game time unpaused unless the request overrides it, chunk saving disabled where safe, normal NPC spawning controlled by the harness.
- [ ] Report `worldReady=false` with a machine-readable reason when Universe is not ready, the world cannot be loaded, the world is paused, or the world is not ticking.
- [ ] Keep request execution blocked until `worldReady=true`, returning `harness-world-not-ready` if a request cannot be run before its timeout/deadline.
- [ ] Add a `world-ready` status transition in `harness-status.json`.
- [ ] Add tests around `NpcRuntimeWorldReadiness` serialization and status reason mapping.
- [ ] Run `.\mvnw.cmd test`.
- [ ] Live smoke: start the server without logging in and verify `harness-status.json` reports `worldReady=true` and `playerCount=0`.
- [ ] Commit with `Feat: prepare NPC runtime flatworld on startup`.

**Acceptance:**
- The agent can start the server and see the test flatworld become ready from the filesystem alone.
- Request processing does not depend on a player issuing `/npcruntime`.
- If the flatworld cannot be prepared, the failure is visible in status and result JSON.

---

## Phase 3: Local Server Process Manager CLI

**/goal prompt:** Add HytaleNpcAssetTools commands to start, stop, and inspect the local Hytale server process for the NPC runtime harness, using the Alec NPC Inspector repo's Maven run-server profile and filesystem readiness status.

**Primary repo:** `HytaleNpcAssetTools`

**Goal:** Codex can launch and monitor the server from CLI without using the Hytale client or in-game commands.

**Files:**
- Create: `src/hytale_npc_assets/runtime_server.py`
- Create: `src/hytale_npc_assets/runtime_harness_status.py`
- Modify: `src/hytale_npc_assets/cli.py`
- Create: `tests/test_runtime_server.py`
- Create: `tests/test_runtime_harness_status.py`
- Modify: `tests/test_cli.py`
- Modify: `README.md`

**Steps:**
- [ ] Implement `RuntimeServerConfig` with `server_repo`, `harness_root`, `log_dir`, `pid_file`, `startup_timeout_seconds`, and `auto_enable`.
- [ ] Implement `runtime-server status` to read PID, process liveness, newest log path, and `harness-status.json`.
- [ ] Implement `runtime-server start` using `subprocess.Popen` with `.\mvnw.cmd -Prun-server -Dalec.npcRuntime.autoEnable=true package` from the `alecsnpcdebuginspector` repo.
- [ ] Write stdout/stderr to `UserData\NpcRuntimeHarness\logs\server-<timestamp>.log`.
- [ ] Write a PID file under `UserData\NpcRuntimeHarness\server\server.pid`.
- [ ] Implement `runtime-server wait-ready` that waits until `harness-status.json` has `serverReady=true`, `harnessEnabled=true`, and `worldReady=true`.
- [ ] Implement `runtime-server stop` that terminates only the PID recorded in the harness PID file, then waits for process exit.
- [ ] Add stale PID detection so a previous dead process does not block startup.
- [ ] Add tests for command construction, PID file parsing, stale PID behavior, status parsing, wait-ready timeout, and log path reporting.
- [ ] Run `pytest`.
- [ ] Commit with `Feat: add NPC runtime server manager CLI`.

**Acceptance:**
- `runtime-server start` can launch the local server process headlessly.
- `runtime-server wait-ready` provides a clear timeout and points to the server log when readiness fails.
- `runtime-server stop` only stops the process that the CLI started.

---

## Phase 4: Runtime Request, Wait, and One-Command Run CLI

**/goal prompt:** Add HytaleNpcAssetTools runtime request commands that write NPC runtime requests, wait for results, summarize artifacts, and optionally ensure the local server is running first.

**Primary repo:** `HytaleNpcAssetTools`

**Goal:** Codex can run a live NPC runtime test from one command and get a compact pass/fail summary.

**Files:**
- Create: `src/hytale_npc_assets/runtime_request.py`
- Create: `src/hytale_npc_assets/runtime_result.py`
- Create: `src/hytale_npc_assets/runtime_trace_contract.py`
- Modify: `src/hytale_npc_assets/runtime_server.py`
- Modify: `src/hytale_npc_assets/cli.py`
- Create: `tests/test_runtime_request.py`
- Create: `tests/test_runtime_result.py`
- Create: `tests/test_runtime_trace_contract.py`
- Modify: `tests/test_cli.py`
- Modify: `README.md`

**Steps:**
- [ ] Implement `runtime-request write --asset --role --ticks --harness-root --request-id`.
- [ ] Write request files atomically into `requests\<requestId>.request.json`.
- [ ] Implement `runtime-request wait --request-id --harness-root --timeout-seconds`.
- [ ] Implement `runtime-result summarize --result <path> --trace <path> --json`.
- [ ] Implement `runtime-run` that runs `write`, `wait`, and `summarize` in one command.
- [ ] Add `runtime-run --ensure-server --server-repo <path>` that starts the server if needed and waits for harness readiness before writing the request.
- [ ] Add exit codes: `0` passed, `1` assertion/runtime failure, `2` unsupported request, `3` timeout, `4` server/harness unavailable, `5` malformed local inputs.
- [ ] Add compact JSON output with `requestId`, `status`, `classification`, `ticksRun`, `resultPath`, `tracePath`, and `nextDiagnosticStep`.
- [ ] Add tests for request shape, atomic writes, wait success, wait timeout, result summary, exit codes, and `--ensure-server` delegation.
- [ ] Run `pytest`.
- [ ] Live smoke: run `runtime-run --ensure-server` for `Mob_Tamework_Example_Simple` and verify it returns a passed summary without logging into the server.
- [ ] Commit with `Feat: add one-command NPC runtime run CLI`.

**Acceptance:**
- Codex can run one live NPC test end-to-end with no manual request-file preparation.
- The command can start the server if it is not already running.
- The command output is small enough to paste directly into an AI context.

---

## Phase 5: Headless Smoke Suite and Artifact Bundles

**/goal prompt:** Add a small headless NPC runtime smoke suite that runs multiple requests against the flatworld, captures compact artifacts, and proves the loop is repeatable without player login.

**Primary repos:** `alecsnpcdebuginspector` and `HytaleNpcAssetTools`

**Goal:** Convert the one-command smoke into a repeatable agent confidence check.

**Files in `HytaleNpcAssetTools`:**
- Create: `src/hytale_npc_assets/runtime_batch.py`
- Create: `src/hytale_npc_assets/runtime_artifacts.py`
- Modify: `src/hytale_npc_assets/cli.py`
- Create: `tests/test_runtime_batch.py`
- Create: `tests/test_runtime_artifacts.py`
- Modify: `tests/test_cli.py`
- Create: `docs/runtime-headless-workflow.md`

**Files in `alecsnpcdebuginspector`:**
- Modify: `docs/runtime-harness-api-notes.md`
- Create: `docs/npc-runtime-headless-smoke.md`

**Steps:**
- [ ] Implement a batch manifest format with `id`, `harnessRoot`, `serverRepo`, `ensureServer`, `requests`, and `artifactDir`.
- [ ] Implement `runtime-batch run --manifest <path>`.
- [ ] Add deterministic request ordering and per-request timeout handling.
- [ ] Write a batch summary JSON with pass/fail counts, per-request classifications, result paths, trace paths, and log path.
- [ ] Add artifact bundle generation containing request JSON, result JSON, trace tail, status snapshot, and server log tail.
- [ ] Add a starter smoke manifest with `Mob_Tamework_Example_Simple` and one base-game NPC role if a stable role is available in the local descriptor corpus.
- [ ] Add tests for deterministic order, partial failure summaries, timeout summaries, and artifact bundle contents.
- [ ] Run `pytest` in `HytaleNpcAssetTools`.
- [ ] Run `.\mvnw.cmd test` in `alecsnpcdebuginspector`.
- [ ] Live smoke: run the batch twice in a row with no player logged in and verify both runs pass or fail with stable classifications and cleanup evidence.
- [ ] Commit Java docs with `Docs: document NPC runtime headless smoke`.
- [ ] Commit Python changes with `Feat: add NPC runtime headless smoke batches`.

**Acceptance:**
- The agent has a single repeatable command to validate that the server, harness, flatworld, queue, result, trace, and cleanup loop still work.
- Failures produce an artifact bundle that points to the next diagnostic step.

---

## Phase 6: Crash Recovery, Shutdown Cleanup, and Stale Run Handling

**/goal prompt:** Harden the NPC runtime harness and CLI against interrupted runs by recovering stale active requests, cleaning up harness-owned fixtures on startup/shutdown, and preserving enough logs/artifacts for autonomous diagnosis.

**Primary repos:** `alecsnpcdebuginspector` and `HytaleNpcAssetTools`

**Goal:** Make autonomous runs safe enough that Codex can retry after failures without manual folder cleanup.

**Files in `alecsnpcdebuginspector`:**
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeHarnessService.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeRequestQueue.java`
- Modify: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeScenarioRun.java`
- Create: `src/main/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeRecoveryReport.java`
- Create: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeRecoveryReportTest.java`
- Modify: `src/test/java/com/alechilles/alecsnpcdebuginspector/runtime/NpcRuntimeHarnessServiceTest.java`

**Files in `HytaleNpcAssetTools`:**
- Modify: `src/hytale_npc_assets/runtime_server.py`
- Modify: `src/hytale_npc_assets/runtime_result.py`
- Modify: `src/hytale_npc_assets/runtime_artifacts.py`
- Modify: `tests/test_runtime_server.py`
- Modify: `tests/test_runtime_result.py`

**Steps:**
- [ ] On harness startup, scan `active\*.request.json` and write a failed result with classification `harness-recovered-stale-active-request`.
- [ ] On harness shutdown, cancel the active run if possible and write cleanup evidence.
- [ ] Add fixture ownership markers that can be used for best-effort cleanup on startup after a crash.
- [ ] Add recovery status fields to `harness-status.json`.
- [ ] Make the CLI detect stale active requests and show the recovery result path.
- [ ] Include server log tail and status snapshots in timeout diagnostics.
- [ ] Add tests for stale active request recovery, shutdown cancellation result shape, recovery report serialization, and CLI timeout diagnostics.
- [ ] Run `.\mvnw.cmd test` in `alecsnpcdebuginspector`.
- [ ] Run `pytest` in `HytaleNpcAssetTools`.
- [ ] Live smoke: kill a CLI-started server during an active long request, restart with `runtime-server start`, and verify the stale run is classified instead of wedging the queue.
- [ ] Commit Java changes with `Feat: recover NPC runtime stale runs`.
- [ ] Commit Python changes with `Feat: surface NPC runtime recovery diagnostics`.

**Acceptance:**
- A failed autonomous run does not require the user to manually delete active/request files.
- The next agent can tell whether it should retry, inspect logs, or fix code/assets.

---

## Phase 7: Resume Semantic Coverage Roadmap

**/goal prompt:** Resume the NPC runtime semantic coverage roadmap now that the system can be operated headlessly end-to-end, starting with fixtures and structured observation before sensors/actions/combat assertions.

**Primary repos:** `alecsnpcdebuginspector` and `HytaleNpcAssetTools`

**Goal:** Reattach the original endgame semantic work to the new headless execution foundation.

**Execution Order After Headless Foundation:**

1. Arena residency, reset, and cleanup reliability from the old Phase 3, but now verified through `runtime-batch run`.
2. Fixture schema and fixture spawning from the old Phase 4.
3. Structured observation model from the old Phase 5.
4. Sensor semantics coverage from the old Phase 6.
5. Action and combat evaluator coverage from the old Phase 7.
6. Descriptor-aware scenario generation from the old Phase 9.
7. Tamework runtime fixtures and assertions from the old Phase 10.
8. Regression suites, comparison classifications, and trace promotion from the old Phases 8 and 11.
9. Final documentation from the old Phase 12, updated around headless-first workflows.

**Acceptance:**
- Every later semantic phase is verified with a no-login `runtime-run` or `runtime-batch run` command.
- Manual in-game setup is reserved only for scenarios that explicitly require a real player component.

---

## New Suggested Execution Order

1. Completed: runtime contract hardening.
2. Completed: tick-driven scenario runner.
3. Phase 1: Harness Auto-Enable and Readiness Status.
4. Phase 2: Boot-Time Flatworld Readiness.
5. Phase 3: Local Server Process Manager CLI.
6. Phase 4: Runtime Request, Wait, and One-Command Run CLI.
7. Phase 5: Headless Smoke Suite and Artifact Bundles.
8. Phase 6: Crash Recovery, Shutdown Cleanup, and Stale Run Handling.
9. Phase 7: Resume Semantic Coverage Roadmap.

## Verification Commands

Run in `C:\Users\22ale\AppData\Roaming\Hytale\Modding\alecsnpcdebuginspector`:

```powershell
.\mvnw.cmd test
```

Run in `C:\Users\22ale\AppData\Roaming\Hytale\Modding\HytaleNpcAssetTools`:

```powershell
pytest
```

Headless live smoke after Phase 4:

```powershell
cd "C:\Users\22ale\AppData\Roaming\Hytale\Modding\HytaleNpcAssetTools"
python -m hytale_npc_assets.cli runtime-run `
  --asset Mob_Tamework_Example_Simple `
  --role Mob_Tamework_Example_Simple `
  --ticks 20 `
  --ensure-server `
  --server-repo "C:\Users\22ale\AppData\Roaming\Hytale\Modding\alecsnpcdebuginspector" `
  --harness-root "$env:APPDATA\Hytale\UserData\NpcRuntimeHarness" `
  --json
```

## Coverage Check

- No-login operation: covered by Phases 1, 2, 3, and 4.
- Autonomous server lifecycle: covered by Phases 3, 5, and 6.
- Dedicated flatworld readiness: covered by Phase 2.
- Request/result/trace loop: already started by completed baseline, made agent-friendly by Phase 4.
- Repeatable smoke testing: covered by Phase 5.
- Failure recovery: covered by Phase 6.
- Original sensor/action/combat/Tamework goals: preserved in Phase 7, but now verified through the headless commands.
