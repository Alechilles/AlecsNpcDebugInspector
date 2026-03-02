package com.alechilles.alecsnpcdebuginspector.items;

import com.alechilles.alecsnpcdebuginspector.ui.NpcDebugLinkedEntry;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.flock.FlockMembership;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Manages linked-NPC metadata on the debug inspector item.
 */
final class NpcDebugLinkService {
    private static final int MAX_LINKED_NPCS = 50;

    @Nonnull
    ToolResolution ensureToolId(@Nonnull ItemStack stack) {
        String existing = stack.getFromMetadataOrNull(NpcDebugMetadataKeys.TOOL_ID, Codec.STRING);
        if (existing != null && !existing.isBlank()) {
            return new ToolResolution(stack, existing, false);
        }
        String generated = UUID.randomUUID().toString();
        ItemStack updated = stack.withMetadata(NpcDebugMetadataKeys.TOOL_ID, Codec.STRING, generated);
        return new ToolResolution(updated, generated, true);
    }

    @Nonnull
    LinkToggleResult toggleLink(@Nonnull ItemStack stack, @Nonnull UUID npcUuid) {
        Set<UUID> linked = readLinkedNpcSet(stack);
        boolean linkedNow;
        if (linked.contains(npcUuid)) {
            linked.remove(npcUuid);
            linkedNow = false;
        } else {
            if (linked.size() >= MAX_LINKED_NPCS) {
                return new LinkToggleResult(stack, false, false, true, linked.size());
            }
            linked.add(npcUuid);
            linkedNow = true;
        }
        ItemStack updated = writeLinkedNpcSet(stack, linked);
        return new LinkToggleResult(updated, true, linkedNow, false, linked.size());
    }

    @Nonnull
    ItemStack removeLink(@Nonnull ItemStack stack, @Nonnull UUID npcUuid) {
        Set<UUID> linked = readLinkedNpcSet(stack);
        linked.remove(npcUuid);
        return writeLinkedNpcSet(stack, linked);
    }

    @Nonnull
    List<NpcDebugLinkedEntry> buildLinkedEntries(@Nonnull Player player,
                                                 @Nonnull Store<EntityStore> store,
                                                 @Nonnull ItemStack stack) {
        World world = player.getWorld();
        if (world == null) {
            return List.of();
        }
        TransformComponent playerTransform = null;
        Ref<EntityStore> playerRef = player.getReference();
        if (playerRef != null && playerRef.isValid()) {
            playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
        }

        List<NpcDebugLinkedEntry> out = new ArrayList<>();
        for (UUID uuid : readLinkedNpcSet(stack)) {
            Ref<EntityStore> npcRef = world.getEntityRef(uuid);
            if (npcRef == null || !npcRef.isValid()) {
                out.add(new NpcDebugLinkedEntry(
                        uuid,
                        "Unloaded NPC",
                        "<unknown>",
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
                continue;
            }
            NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
            if (npc == null) {
                out.add(new NpcDebugLinkedEntry(
                        uuid,
                        "Unloaded NPC",
                        "<unknown>",
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
                continue;
            }
            out.add(new NpcDebugLinkedEntry(
                    uuid,
                    resolveDisplayName(npcRef, store, npc),
                    resolveRoleId(npc),
                    true,
                    resolveStateName(npc),
                    resolveHealthText(npcRef, store),
                    resolveFlockText(npcRef, store),
                    resolveFlockId(npcRef, store),
                    resolveLocationText(npcRef, store),
                    resolveDistanceText(playerTransform, npcRef, store)
            ));
        }
        return out;
    }

    @Nonnull
    Set<UUID> readLinkedNpcSet(@Nonnull ItemStack stack) {
        String raw = stack.getFromMetadataOrNull(NpcDebugMetadataKeys.LINKED_NPC_UUIDS, Codec.STRING);
        LinkedHashSet<UUID> out = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        String[] tokens = raw.split(",");
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            try {
                out.add(UUID.fromString(token.trim()));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed UUID entries.
            }
        }
        return out;
    }

    @Nonnull
    private ItemStack writeLinkedNpcSet(@Nonnull ItemStack stack, @Nonnull Set<UUID> linkedNpcSet) {
        if (linkedNpcSet.isEmpty()) {
            return stack.withMetadata(NpcDebugMetadataKeys.LINKED_NPC_UUIDS, Codec.STRING, "");
        }
        StringBuilder sb = new StringBuilder();
        for (UUID uuid : linkedNpcSet) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(uuid.toString());
        }
        return stack.withMetadata(NpcDebugMetadataKeys.LINKED_NPC_UUIDS, Codec.STRING, sb.toString());
    }

    @Nonnull
    private String resolveDisplayName(@Nonnull Ref<EntityStore> npcRef,
                                      @Nonnull Store<EntityStore> store,
                                      @Nonnull NPCEntity npc) {
        DisplayNameComponent displayName = store.getComponent(npcRef, DisplayNameComponent.getComponentType());
        if (displayName != null && displayName.getDisplayName() != null) {
            String ansi = displayName.getDisplayName().getAnsiMessage();
            if (ansi != null && !ansi.isBlank()) {
                return ansi;
            }
        }
        String legacy = npc.getLegacyDisplayName();
        if (legacy != null && !legacy.isBlank()) {
            return legacy;
        }
        String roleId = resolveRoleId(npc);
        return roleId.isBlank() ? "NPC" : roleId;
    }

    @Nonnull
    private String resolveRoleId(@Nonnull NPCEntity npc) {
        int roleIndex = npc.getRoleIndex();
        if (roleIndex >= 0 && NPCPlugin.get() != null) {
            String roleId = NPCPlugin.get().getName(roleIndex);
            if (roleId != null && !roleId.isBlank()) {
                return roleId;
            }
        }
        String roleName = npc.getRoleName();
        return roleName != null && !roleName.isBlank() ? roleName : "<unknown>";
    }

    @Nonnull
    private String resolveStateName(@Nonnull NPCEntity npc) {
        if (npc.getRole() == null || npc.getRole().getStateSupport() == null) {
            return "<unknown>";
        }
        String stateName = npc.getRole().getStateSupport().getStateName();
        return stateName != null && !stateName.isBlank() ? stateName : "<unknown>";
    }

    @Nonnull
    private String resolveHealthText(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        EntityStatMap statMap = store.getComponent(npcRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return "n/a";
        }
        int healthIndex = EntityStatType.getAssetMap().getIndex("Health");
        if (healthIndex < 0) {
            return "n/a";
        }
        EntityStatValue value = statMap.get(healthIndex);
        if (value == null) {
            return "n/a";
        }
        int current = Math.max(0, Math.round(value.get()));
        int max = Math.max(1, Math.round(value.getMax()));
        if (current > max) {
            current = max;
        }
        return current + "/" + max;
    }

    @Nonnull
    private String resolveFlockText(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        ComponentType<EntityStore, FlockMembership> flockType = FlockMembership.getComponentType();
        if (flockType == null) {
            return "n/a";
        }
        FlockMembership membership = store.getComponent(npcRef, flockType);
        if (membership == null) {
            return "none";
        }
        return String.valueOf(membership.getMembershipType()).toLowerCase(Locale.ROOT);
    }

    @Nullable
    private String resolveFlockId(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        ComponentType<EntityStore, FlockMembership> flockType = FlockMembership.getComponentType();
        if (flockType == null) {
            return null;
        }
        FlockMembership membership = store.getComponent(npcRef, flockType);
        if (membership == null || membership.getFlockId() == null) {
            return null;
        }
        return String.valueOf(membership.getFlockId());
    }

    @Nullable
    private String resolveLocationText(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        TransformComponent transform = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (transform == null) {
            return null;
        }
        int x = (int) Math.floor(transform.getPosition().x);
        int y = (int) Math.floor(transform.getPosition().y);
        int z = (int) Math.floor(transform.getPosition().z);
        return x + ", " + y + ", " + z;
    }

    @Nullable
    private String resolveDistanceText(@Nullable TransformComponent playerTransform,
                                       @Nonnull Ref<EntityStore> npcRef,
                                       @Nonnull Store<EntityStore> store) {
        if (playerTransform == null) {
            return null;
        }
        TransformComponent npcTransform = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (npcTransform == null) {
            return null;
        }
        double distance = playerTransform.getPosition().distanceTo(npcTransform.getPosition());
        return String.format(Locale.ROOT, "%.1f", distance);
    }

    static final class ToolResolution {
        final ItemStack stack;
        final String toolId;
        final boolean changed;

        ToolResolution(ItemStack stack, String toolId, boolean changed) {
            this.stack = stack;
            this.toolId = toolId;
            this.changed = changed;
        }
    }

    static final class LinkToggleResult {
        final ItemStack stack;
        final boolean toggled;
        final boolean linked;
        final boolean hitMax;
        final int linkedCount;

        LinkToggleResult(ItemStack stack, boolean toggled, boolean linked, boolean hitMax, int linkedCount) {
            this.stack = stack;
            this.toggled = toggled;
            this.linked = linked;
            this.hitMax = hitMax;
            this.linkedCount = linkedCount;
        }
    }
}
