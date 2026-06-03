# NPC Runtime Harness API Notes

Generated while implementing the file-driven runtime harness against:

```text
C:\Users\22ale\AppData\Roaming\Hytale\install\release\package\game\latest\Server\HytaleServer.jar
```

These notes are based on `javap` inspection of public APIs and targeted bytecode checks of built-in Hytale commands. They are the current contract for the next live-runtime implementation slice.

## Confirmed APIs

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

1. Add `NpcRuntimeFlatworldManager` around `Universe` and `WorldConfig`.
2. Add a live runner that:
   - loads/creates `npc_runtime_test_flatworld`,
   - sets ticking/unload/save guardrails,
   - spawns the NPC under test through `NPCPlugin.spawnNPCWithSpaceValidation(...)`,
   - writes `run-start`, `world-ready`, `fixture-spawn`, `npc-snapshot`, and `run-end` records,
   - removes spawned entities in `finally`.
3. Keep the runner one-shot at first. Add multi-tick observation only after the cleanup path is proven.
