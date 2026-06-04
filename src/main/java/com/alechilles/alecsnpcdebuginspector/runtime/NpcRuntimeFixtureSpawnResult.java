package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Public trace representation for a spawned fixture.
 */
public record NpcRuntimeFixtureSpawnResult(
        @Nonnull String fixtureId,
        @Nonnull NpcRuntimeFixtureKind kind,
        boolean spawned,
        @Nullable UUID entityUuid,
        @Nullable String roleId,
        @Nullable String targetSlot,
        @Nonnull String message
) {
    @Nonnull
    static NpcRuntimeFixtureSpawnResult spawned(@Nonnull NpcRuntimeFixtureSpec spec, @Nullable UUID entityUuid) {
        return new NpcRuntimeFixtureSpawnResult(
                spec.fixtureId(),
                spec.kind(),
                true,
                entityUuid,
                spec.roleId(),
                spec.targetSlot(),
                "spawned"
        );
    }

    @Nonnull
    Map<String, Object> toMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("fixtureId", fixtureId);
        map.put("kind", kind.jsonName());
        map.put("spawned", spawned);
        if (entityUuid != null) {
            map.put("entityUuid", entityUuid.toString());
        }
        if (roleId != null) {
            map.put("roleId", roleId);
        }
        if (targetSlot != null) {
            map.put("targetSlot", targetSlot);
        }
        map.put("message", message);
        return map;
    }
}
