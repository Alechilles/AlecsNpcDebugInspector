package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Describes the harness-owned arena used for one runtime scenario.
 */
public record NpcRuntimeArena(
        @Nonnull String worldId,
        @Nonnull String arenaId,
        long spawnChunkIndex,
        @Nonnull String residencyMode
) {
    @Nonnull
    public static NpcRuntimeArena defaultArena(@Nonnull String worldId, @Nonnull String arenaId, long spawnChunkIndex) {
        return new NpcRuntimeArena(
                worldId,
                arenaId,
                spawnChunkIndex,
                "world-config-canUnloadChunks=false; spawn chunk loaded through World.getChunkAsync"
        );
    }

    @Nonnull
    public Map<String, Object> toMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("worldId", worldId);
        map.put("arenaId", arenaId);
        map.put("spawnChunkIndex", spawnChunkIndex);
        map.put("residencyMode", residencyMode);
        map.put("blockResetMode", "fixture-block-mutations-reset-during-cleanup");
        return map;
    }
}
