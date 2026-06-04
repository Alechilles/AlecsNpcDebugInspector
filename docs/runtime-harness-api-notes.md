# NPC Runtime Harness API Notes

Generated while implementing the file-driven runtime harness against:

```text
C:\Users\22ale\AppData\Roaming\Hytale\install\release\package\game\latest\Server\HytaleServer.jar
```

These notes are based on `javap` inspection of public APIs and targeted bytecode checks of built-in Hytale commands. They are the current contract for the next live-runtime implementation slice.

## Confirmed APIs

### Headless Smoke Batches

`HytaleNpcAssetTools` can run repeatable no-login smoke batches against this harness with:

```powershell
python -m hytale_npc_assets.cli runtime-batch run --manifest docs\runtime-smoke-manifest.json --json
```

The starter manifest lives in the tooling repo and points at this harness repo. It currently runs `Mob_Tamework_Example_Simple` in `npc_runtime_test_flatworld`, starts the server when needed, waits for the heartbeat, writes the request, waits for the result, and writes an artifact bundle under `out\runtime-headless-smoke`.

Each per-request artifact bundle is expected to include:

- `request.json`
- `result.json`
- `trace-tail.jsonl`
- `status.json`
- `server-log-tail.txt` when a server log is available
- `bundle.json`

The batch runner intentionally clears previous completed request/result/trace artifacts for the same request id before writing a new request. It does not clear `active\*.request.json`; active-request recovery is handled by the later stale-run recovery phase.

### Stale Active Request Recovery

The harness treats files under `active\*.request.json` as owned by an in-progress server run. On startup and shutdown it scans that directory. Any leftover active request is considered interrupted, receives a failed result with classification `harness-recovered-stale-active-request`, and is moved to `archive`.

Recovery results use error phase `recovery` and include cleanup evidence:

```json
{
  "classification": "harness-recovered-stale-active-request",
  "error": {
    "phase": "recovery",
    "type": "startup"
  },
  "cleanup": {
    "attempted": true,
    "succeeded": true,
    "message": "archived stale active request during startup"
  }
}
```

The heartbeat includes a `lastRecovery` object with the trigger, recovered count, active path, result path, archive path, and request id. External tools should surface this as retry context instead of leaving the queue wedged.

### Semantic Roadmap Gate

Later semantic phases are tracked from the CLI side in:

```text
C:\Users\22ale\AppData\Roaming\Hytale\Modding\HytaleNpcAssetTools\docs\runtime-semantic-roadmap.json
```

Harness work for arena cleanup, fixtures, structured observations, sensors, actions, combat evaluators, Tamework state, and regression promotion should end with no-login `runtime-run --ensure-server` or `runtime-batch run` evidence.

### Headless Harness Heartbeat

Phase 1 of the headless runtime work adds a filesystem heartbeat at:

```text
UserData\NpcRuntimeHarness\status\harness-status.json
```

The heartbeat is written on plugin startup, enable/disable changes, queue/result changes, active request changes, and idle polling. It is intended for external tools such as `HytaleNpcAssetTools` to detect whether the server-side harness is alive without a logged-in player.

The harness can be auto-enabled for development runs with:

```text
-Dalec.npcRuntime.autoEnable=true
```

or:

```text
ALEC_NPC_RUNTIME_AUTO_ENABLE=true
```

The status contract reports separate `serverReady`, `harnessEnabled`, and `worldReady` booleans. It also reports `worldPaused`, `worldReadyReason`, and `worldReadyDetail` so headless tools can distinguish normal startup from a broken runtime world.

Phase 2 of the headless runtime work actively prepares the default flatworld when auto-enable is on. Startup and every enabled poll attempt to load or create `npc_runtime_test_flatworld` through `NpcRuntimeFlatworldManager.ensureWorldReady(defaultWorldId)`. The harness does not run queued requests while `worldReady=false`. If the world reaches a terminal bad state, such as loaded-but-not-ticking or loaded-but-paused, the queued request receives a failed result with classification `harness-world-not-ready` and phase `world`.

Expected readiness reason values:

- `ready`
- `universe-not-available`
- `universe-not-ready`
- `world-not-loaded`
- `world-load-failed`
- `world-not-ticking`
- `world-paused`

### Universe and World Lifecycle

`com.hypixel.hytale.server.core.universe.Universe`

- `Universe.get()`
- `getUniverseReady()`
- `isWorldLoadable(String)`
- `addWorld(String)`
- `addWorld(String, String, String)`
- `makeWorld(String, Path, WorldConfig)`
- `makeWorld(String, Path, WorldConfig, boolean)`
- `loadWorld(String)`
- `getWorld(String)`
- `removeWorld(String)`
- `getWorldsPath()`
- `validateWorldPath(String)`

Implication: the harness can locate or create a dedicated runtime world without executing chat/server commands.

### Flatworld Configuration

`com.hypixel.hytale.server.core.universe.world.WorldConfig`

- `setWorldGenProvider(IWorldGenProvider)`
- `setDefaultSpawnProvider(IWorldGen)`
- `setTicking(boolean)`
- `setGameTimePaused(boolean)`
- `setForcedWeather(String)`
- `setCanUnloadChunks(boolean)`
- `setCanSaveChunks(boolean)`
- `setSaveNewChunks(boolean)`
- `setSpawningNPC(boolean)`
- `setIsAllNPCFrozen(boolean)`
- `setDeleteOnUniverseStart(boolean)`
- `setDeleteOnRemove(boolean)`

`com.hypixel.hytale.server.core.universe.world.worldgen.provider.FlatWorldGenProvider`

- default constructor
- constructor with tint and layers
- `getGenerator()`

Implication: a disposable flat test world should be possible by creating a `WorldConfig`, assigning a `FlatWorldGenProvider`, disabling chunk unload/save behavior as needed, and creating/loading through `Universe`.

Current runtime guardrails for `npc_runtime_test_flatworld`:

- `World.setTicking(true)`
- `World.setPaused(false)`
- `WorldConfig.setTicking(true)`
- `WorldConfig.setGameTimePaused(false)`
- `WorldConfig.setCanUnloadChunks(false)`
- `WorldConfig.setCanSaveChunks(false)`
- `WorldConfig.setSaveNewChunks(false)`
- `WorldConfig.setSpawningNPC(false)`
- `WorldConfig.setIsAllNPCFrozen(false)`
- `WorldConfig.markChanged()` after preparing an existing world
- The harness does not force weather by default. Earlier builds wrote `ForcedWeather: "clear"`, but `clear` is not a valid Weather asset id in the current runtime asset set; startup repairs that stale value out of the dedicated flatworld config before loading it.
- Before spawning the NPC under test, the live runner computes the spawn chunk with `ChunkUtil.indexChunkFromBlock(...)`, calls `World.getChunkAsync(chunkIndex)`, and verifies the chunk is loaded, non-ticking, or in memory. This is the currently confirmed residency mechanism used by no-login smoke runs.

### Arena, Fixture Ownership, and Cleanup

Main endgame Phase 3 adds first-class arena and cleanup evidence for the default runtime arena:

- `NpcRuntimeArena` records the world id, arena id, spawn chunk index, and residency mode.
- `NpcRuntimeFixtureRegistry` records harness-owned fixture ids in deterministic order and rejects duplicate fixture ids before cleanup can become ambiguous.
- `NpcRuntimeCleanupReport` records entity removal attempts/successes/failures, block reset attempts/successes/failures, unresolved fixture ids, and overall success.

Cleanup evidence is embedded under `result.cleanup.report` while preserving the older `cleanup.attempted`, `cleanup.succeeded`, and `cleanup.message` fields. If behavior completes but cleanup fails, the harness returns a failed result with classification `cleanup-failed` instead of a generic runtime error.

Current block reset status: the default smoke arena does not mutate blocks yet, so block reset counts are expected to remain zero. Future fixture phases should register every block mutation before applying it and increment the same cleanup report during reset.

### Fixture Schema and Spawn Support

Main endgame Phase 4 adds a canonical `fixtures.list` schema while preserving the legacy `fixtures.npc` plus `fixtures.targets` request shape. The canonical list supports:

- `npcUnderTest`
- `targetDummy`
- `npc`
- `mob`
- `item`
- `block`
- `playerAnchor`
- `familyMember`
- `flockMember`

NPC-backed fixture kinds (`npcUnderTest`, `targetDummy`, `npc`, `mob`, `familyMember`, and `flockMember`) can currently spawn through `NPCPlugin.spawnNPC(...)` when a `roleId` is present. Spawned fixtures are emitted as `fixture-spawn` trace records with deterministic fixture ids, kind, role id, target slot, NPC UUID, and a structured spawn result. The cleanup report counts each spawned NPC fixture removal.

The schema accepts item, block, and player-anchor fixture declarations, but the harness rejects them before world mutation with classification `unsupported-fixture` until safe item, block, and player-anchor engine APIs are implemented. Direct `entityId` spawning is also rejected with `unsupported-fixture`; use `roleId` for NPC-backed fixtures.

### Structured Observation Records

Main endgame Phase 5 keeps the existing human-readable `npc-snapshot` trace record, then emits normalized JSON records derived from the same live snapshot:

- `npc-state`: identity, AI, and lifecycle fields.
- `targeting`: target slots, sensor scope counts, and resolved target labels currently visible in the inspector snapshot.
- `timers`: timer and cooldown fields.
- `pathing`: path-following, navigation, controller, and movement fields.
- `combat`: attack, damage, target, and combat override fields.
- `inventory`, `alarms`, `flags`, `components`, and `flock`: normalized section maps for those inspector sections.
- `tamework`: all available Tamework sections grouped by normalized section name.
- `npc-transition`: emitted after the first observed snapshot when any normalized field changes.

The structured records are intended for Python-side comparison and triage. Consumers should prefer these records over scraping `npc-snapshot.details`; the text snapshot remains for interactive inspector users and artifact bundles.

### Sensor Evidence Records

Main endgame Phase 6 begins live sensor evidence with explicit `sensor-evidence` trace records derived from normalized targeting, timer, alarm, and flag observations:

- Target slot evidence records `sensorType=TargetSlot`, `sensorId`, `targetSlot`, `matchResult`, and the observed target label.
- Sensor scope evidence records the observed sensor scope key count.
- Timer, alarm, and flag evidence records boolean observed values as `matched` or `not-matched`.
- Fields the current inspector cannot observe directly, such as exact distance, visibility, tags, faction checks, configured thresholds, and cooldown gates, are listed in `unsupportedFields` on the evidence record.

These records make current live evidence machine-readable without pretending that all sensor internals are available yet.

Runtime requests may include sensor assertions under `assertions`. Supported assertion fields are:

- `assertionId`
- `kind: "sensor"`
- `sensorType`
- `sensorId`
- `expectedMatchResult`
- `expectedTargetFixtureId`
- `expectedDistanceBand`
- `expectUnsupported`

Assertions are evaluated after cleanup succeeds. Passing assertions keep the result `passed`; failed assertions return `status=failed` with classification `assertion-failed`; assertions that cannot find matching live evidence return classification `assertion-unknown`. All assertion outcomes are serialized under `result.summary.assertions` and emitted as an `assertions` trace record.

### World Ticking, Chunks, and Entities

`com.hypixel.hytale.server.core.universe.world.World`

- `isTicking()`
- `setTicking(boolean)`
- `isPaused()`
- `setPaused(boolean)`
- `getTick()`
- `getChunkAsync(long)`
- `getNonTickingChunkAsync(long)`
- `getChunkIfLoaded(long)`
- `getChunkIfNonTicking(long)`
- `getChunkIfInMemory(long)`
- `loadChunkIfInMemory(long)`
- `getEntity(UUID)`
- `getEntityRef(UUID)`
- `getEntityStore()`
- `spawnEntity(Entity, Vector3d, Rotation3f)`
- `addEntity(Entity, Vector3d, Rotation3f, AddReason)`
- `execute(Runnable)`

Implication: the harness has world-thread execution and entity access. Chunk residency is still only partially confirmed: chunks can be requested and non-ticking chunks can be accessed, but no explicit long-lived chunk ticket or force-load owner API was found in the inspected public surface.

### NPC Spawning

`com.hypixel.hytale.server.npc.NPCPlugin`

- `NPCPlugin.get()`
- `getIndex(String)`
- `getName(int)`
- `hasRoleName(String)`
- `validateSpawnableRole(String)`
- `forceValidation(int)`
- `testAndValidateRole(BuilderInfo)`
- `tryGetCachedValidRole(int)`
- `spawnNPC(Store<EntityStore>, String roleName, String flockAsset, Vector3dc, Rotation3fc)`
- `spawnNPCWithSpaceValidation(Store<EntityStore>, String roleName, String flockAsset, Vector3dc, Rotation3fc)`
- `spawnEntity(Store<EntityStore>, int roleIndex, Vector3dc, Rotation3fc, Model, TriConsumer)`

Bytecode check: built-in `NPCSpawnCommand` resolves a role argument to `BuilderInfo`, validates the role, resolves its model through `ISpawnableWithModel`, then calls `NPCPlugin.spawnEntity(...)`. The simpler `spawnNPC(...)` helper calls `getIndex(roleName)`, delegates to `spawnEntity(...)`, and optionally spawns flock members.

Implication: the harness should spawn the NPC under test with `spawnNPCWithSpaceValidation(...)` or `spawnNPC(...)` using `request.roleId()` as the role name and `null` for flock unless the request explicitly supports flock setup.

### NPC Observation

Existing repo code already has `NpcDebugSnapshotService.capture(UUID, Ref<EntityStore>, Store<EntityStore>)`.

It reads:

- overview UUID, role, state, health, position, chunk, world
- AI state/substate/current instruction/queued motion
- targeting and sensor support
- pathing
- timers/cooldowns
- lifecycle
- relationships
- combat
- inventory/equipment
- alarms
- flags
- components
- flock
- Tamework sections when available

Implication: the live runner should reuse `NpcDebugSnapshotService` for the first structured observer pass, then add machine-readable fields as needed instead of duplicating reflection-heavy NPC internals.

### Time and Weather

`com.hypixel.hytale.server.core.modules.time.WorldTimeResource`

- `getResourceType()`
- `setGameTime(Instant, World, ComponentAccessor<EntityStore>)`
- `setDayTime(double, World, ComponentAccessor<EntityStore>)`
- `broadcastTimePacket(ComponentAccessor<EntityStore>)`

`WorldConfig`

- `setGameTime(Instant)`
- `setGameTimePaused(boolean)`
- `setForcedWeather(String)`

Implication: time/weather can be configured at world-config level and likely updated live through `WorldTimeResource` once the runner has the world store.

### Blocks

`com.hypixel.hytale.server.core.modules.interaction.BlockPlaceUtils`

- `placeBlock(...)`
- `canPlaceBlock(BlockType, String)`

World/block command classes also exist under:

- `com.hypixel.hytale.server.core.universe.world.commands.block.*`

Implication: direct block mutation is possible, but the safe harness API still needs a concrete minimal block-setting wrapper. The public `placeBlock(...)` signature is interaction-oriented and requires item/container/chunk refs, so it is not yet the clean arena reset path.

## Still Open

- Exact chunk coordinate packing for `World.getChunkAsync(long)` and related chunk APIs.
- Whether `WorldConfig.setCanUnloadChunks(false)` is sufficient to keep the runtime arena loaded without players.
- Whether a long-lived chunk ticket or force-load owner API exists outside the inspected public surface.
- The safest direct block set/reset primitive for arena cleanup.
- The best dummy target entity type for sensors that require real combat, attitude, player, or visibility components.
- Whether target slots should be induced through sensors/actions or can be pre-seeded through role support safely.
- Whether synchronous scenario loops can run on `World.execute(...)` without blocking the world thread, or whether the runner needs a tick-driven state machine.

## Next Implementation Slice

1. Confirm whether `WorldConfig.setCanUnloadChunks(false)` keeps the runtime arena resident without players, or find the internal force-load/ticket API.
2. Add a direct block placement/reset wrapper for repeatable arena layouts.
3. Add explicit request-level time/weather overrides on top of the headless world defaults.
