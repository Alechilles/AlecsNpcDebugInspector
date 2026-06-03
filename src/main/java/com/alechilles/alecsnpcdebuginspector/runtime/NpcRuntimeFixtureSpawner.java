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
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            throw new IllegalStateException("NPCPlugin is not available yet");
        }
        if (!npcPlugin.hasRoleName(request.roleId())) {
            throw new IllegalArgumentException("Unknown NPC role: " + request.roleId());
        }

        Store<EntityStore> store = world.getEntityStore().getStore();
        Vector3d position = toVector3d(request.fixtures().npc().position(), "fixtures.npc.position");
        Pair<Ref<EntityStore>, INonPlayerCharacter> spawned = npcPlugin.spawnNPC(
                store,
                request.roleId(),
                null,
                position,
                Rotation3f.IDENTITY
        );
        if (spawned == null || spawned.first() == null || spawned.second() == null) {
            throw new IllegalStateException("Hytale returned no NPC for role " + request.roleId());
        }
        if (!(spawned.second() instanceof NPCEntity npcEntity)) {
            throw new IllegalStateException("Spawned non-NPC entity for role " + request.roleId());
        }
        return new SpawnedNpc(spawned.first(), npcEntity);
    }

    public void cleanup(@Nonnull Store<EntityStore> store, @Nullable SpawnedNpc npc) {
        if (npc == null || npc.ref() == null || !npc.ref().isValid()) {
            return;
        }
        store.removeEntity(npc.ref(), RemoveReason.REMOVE);
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

    public record SpawnedNpc(@Nonnull Ref<EntityStore> ref, @Nonnull NPCEntity npc) {
        @Nullable
        public UUID uuid() {
            return npc.getUuid();
        }
    }
}
