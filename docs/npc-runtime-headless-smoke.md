# NPC Runtime Headless Smoke

This repo provides the live server-side harness. The repeatable smoke command lives in `HytaleNpcAssetTools`:

```powershell
cd "C:\Users\22ale\AppData\Roaming\Hytale\Modding\HytaleNpcAssetTools"
hytale-assets runtime-batch run --manifest docs\runtime-smoke-manifest.json --json
```

The manifest starts the local server through this repo when needed, waits for `UserData\NpcRuntimeHarness\status\harness-status.json`, runs the configured requests in `npc_runtime_test_flatworld`, and writes artifacts to `out\runtime-headless-smoke`.

## Artifacts

Each request bundle contains the archived request, result JSON, trace tail, status snapshot, and server log tail when available. The batch also writes `<batch-id>.summary.json` with pass/fail counts and per-request classifications.

## Repeatability Contract

The CLI removes previous completed artifacts for a reused request id before enqueuing a new request. It preserves `active` requests so a crashed or interrupted run can be diagnosed by the stale-run recovery phase instead of being silently discarded.

## Interpreting Results

A passed result proves the server, auto-enable path, flatworld readiness, file queue, result writer, trace writer, and artifact capture loop are operational. Trace warnings still matter: `NPC unavailable in snapshots` means the run completed but the observation window is not yet reliable enough for deeper behavior assertions.
