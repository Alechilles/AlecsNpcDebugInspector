# NPC Runtime Semantic Roadmap

The live harness should treat the headless file-queue workflow as the required verification path for later semantic work. The source-of-truth roadmap for agent execution is:

```text
C:\Users\22ale\AppData\Roaming\Hytale\Modding\HytaleNpcAssetTools\docs\runtime-semantic-roadmap.json
```

Future harness phases should add or update a matching `runtime-batch` manifest in `HytaleNpcAssetTools` and prove the phase through a no-login run against `npc_runtime_test_flatworld`.

## Required Gate

Each semantic phase should provide:

- structured trace records or explicit unsupported classifications
- result JSON with stable pass/fail/unsupported classifications
- artifact bundles containing request, result, status, trace tail when available, and server log tail when available
- cleanup or recovery evidence for interrupted runs
- a no-login `runtime-run --ensure-server` or `runtime-batch run` command as acceptance evidence

Manual in-game setup is only acceptable when a scenario explicitly needs a real player component. Sensor, action, combat, descriptor, Tamework, and regression phases should otherwise be fully automatable from the CLI.
