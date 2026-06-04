package com.alechilles.alecsnpcdebuginspector.runtime;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Spawns and cleans up runtime-harness fixtures through Hytale's NPC APIs.
 */
public final class NpcRuntimeFixtureSpawner {
    @Nonnull
    public SpawnedNpc spawnNpcUnderTest(@Nonnull World world, @Nonnull NpcRuntimeRequest request) {
        return spawnFixture(world, request.fixtures().npcUnderTest());
    }

    @Nonnull
    public SpawnedNpc spawnFixture(@Nonnull World world, @Nonnull NpcRuntimeFixtureSpec fixture) {
        if (!fixture.kind().entityLike()) {
            throw new IllegalArgumentException("Fixture kind is not an entity fixture: " + fixture.kind().jsonName());
        }
        if (fixture.roleId() == null || fixture.roleId().isBlank()) {
            throw new IllegalArgumentException("Fixture " + fixture.fixtureId() + " requires a roleId");
        }
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            throw new IllegalStateException("NPCPlugin is not available yet");
        }
        if (!npcPlugin.hasRoleName(fixture.roleId())) {
            throw new IllegalArgumentException("Unknown NPC role: " + fixture.roleId());
        }

        Store<EntityStore> store = world.getEntityStore().getStore();
        Vector3d position = toVector3d(fixture.position(), "fixtures.list[" + fixture.fixtureId() + "].position");
        Pair<Ref<EntityStore>, INonPlayerCharacter> spawned = npcPlugin.spawnNPC(
                store,
                fixture.roleId(),
                null,
                position,
                Rotation3f.IDENTITY
        );
        if (spawned == null || spawned.first() == null || spawned.second() == null) {
            throw new IllegalStateException("Hytale returned no NPC for role " + fixture.roleId());
        }
        if (!(spawned.second() instanceof NPCEntity npcEntity)) {
            throw new IllegalStateException("Spawned non-NPC entity for role " + fixture.roleId());
        }
        return new SpawnedNpc(fixture.fixtureId(), fixture.kind(), fixture.roleId(), fixture.targetSlot(), spawned.first(), npcEntity);
    }

    public boolean cleanup(@Nonnull Store<EntityStore> store, @Nullable SpawnedNpc npc) {
        if (npc == null || npc.ref() == null || !npc.ref().isValid()) {
            return true;
        }
        store.removeEntity(npc.ref(), RemoveReason.REMOVE);
        return true;
    }

    @Nonnull
    private static Vector3d toVector3d(@Nonnull List<Object> values, @Nonnull String fieldName) {
        if (values.size() != 3) {
            throw new IllegalArgumentException(fieldName + " must contain exactly 3 numbers");
        }
        return new Vector3d(number(values.get(0), fieldName), number(values.get(1), fieldName), number(values.get(2), fieldName));
    }

    private static double number(@Nullable Object value, @Nonnull String fieldName) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException(fieldName + " must contain only numbers");
    }

    public record SpawnedNpc(@Nonnull String fixtureId,
                             @Nonnull NpcRuntimeFixtureKind kind,
                             @Nonnull String roleId,
                             @Nullable String targetSlot,
                             @Nonnull Ref<EntityStore> ref,
                             @Nonnull NPCEntity npc) {
        @Nullable
        public UUID uuid() {
            return npc.getUuid();
        }

        @Nonnull
        public NpcRuntimeFixtureSpawnResult toSpawnResult() {
            return new NpcRuntimeFixtureSpawnResult(fixtureId, kind, true, uuid(), roleId, targetSlot, "spawned");
        }
    }
}
