package com.alechilles.alecsnpcdebuginspector.runtime;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
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

    @Nonnull
    public SpawnedItem spawnItemFixture(@Nonnull World world, @Nonnull NpcRuntimeFixtureSpec fixture) {
        if (fixture.kind() != NpcRuntimeFixtureKind.ITEM) {
            throw new IllegalArgumentException("Fixture kind is not an item fixture: " + fixture.kind().jsonName());
        }
        if (fixture.itemId() == null || fixture.itemId().isBlank()) {
            throw new IllegalArgumentException("Fixture " + fixture.fixtureId() + " requires an itemId");
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        Vector3d position = toVector3d(fixture.position(), "fixtures.list[" + fixture.fixtureId() + "].position");
        Holder<EntityStore> holder = ItemComponent.generateItemDrop(
                store,
                new ItemStack(fixture.itemId(), 1),
                position,
                Rotation3f.IDENTITY,
                0.0f,
                0.0f,
                0.0f
        );
        if (holder == null) {
            throw new IllegalStateException("Hytale returned no item drop for item " + fixture.itemId());
        }
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        if (ref == null || !ref.isValid()) {
            throw new IllegalStateException("Hytale returned no item ref for item " + fixture.itemId());
        }
        return new SpawnedItem(fixture.fixtureId(), fixture.itemId(), ref);
    }

    public boolean cleanup(@Nonnull Store<EntityStore> store, @Nullable SpawnedNpc npc) {
        if (npc == null || npc.ref() == null || !npc.ref().isValid()) {
            return true;
        }
        store.removeEntity(npc.ref(), RemoveReason.REMOVE);
        return true;
    }

    public boolean cleanup(@Nonnull Store<EntityStore> store, @Nullable SpawnedItem item) {
        if (item == null || item.ref() == null || !item.ref().isValid()) {
            return true;
        }
        store.removeEntity(item.ref(), RemoveReason.REMOVE);
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

    public record SpawnedItem(@Nonnull String fixtureId,
                              @Nonnull String itemId,
                              @Nonnull Ref<EntityStore> ref) {
        @Nonnull
        public NpcRuntimeFixtureSpawnResult toSpawnResult() {
            return new NpcRuntimeFixtureSpawnResult(fixtureId, NpcRuntimeFixtureKind.ITEM, true, null, null, null, "spawned item drop");
        }
    }
}
