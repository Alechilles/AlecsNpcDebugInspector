package com.alechilles.alecsnpcdebuginspector.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.RoleDebugFlags;
import java.util.EnumSet;
import javax.annotation.Nonnull;

/**
 * Applies and resolves built-in NPC role debug flag state.
 */
public final class NpcDebugRoleDebugFlagService {
    /**
     * Supported preset sets exposed by the debug flags UI.
     */
    public enum Preset {
        DEFAULT,
        ALL,
        NONE
    }

    /**
     * Returns a defensive copy of active debug flags for the given NPC.
     */
    @Nonnull
    public EnumSet<RoleDebugFlags> readFlags(@Nonnull NPCEntity npc) {
        EnumSet<RoleDebugFlags> active = npc.getRoleDebugFlags();
        if (active == null || active.isEmpty()) {
            return EnumSet.noneOf(RoleDebugFlags.class);
        }
        EnumSet<RoleDebugFlags> copy = EnumSet.noneOf(RoleDebugFlags.class);
        copy.addAll(active);
        return copy;
    }

    /**
     * Applies the exact debug flag set to the NPC.
     */
    public void applyFlags(@Nonnull Ref<EntityStore> npcRef,
                           @Nonnull NPCEntity npc,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull EnumSet<RoleDebugFlags> flags) {
        EnumSet<RoleDebugFlags> safeFlags = EnumSet.noneOf(RoleDebugFlags.class);
        safeFlags.addAll(flags);
        store.tryRemoveComponent(npcRef, Nameplate.getComponentType());
        npc.setRoleDebugFlags(safeFlags);
    }

    /**
     * Resolves a preset enum set used by `/npc debug set`.
     */
    @Nonnull
    public EnumSet<RoleDebugFlags> resolvePreset(@Nonnull Preset preset) {
        return switch (preset) {
            case NONE -> EnumSet.noneOf(RoleDebugFlags.class);
            case ALL -> EnumSet.allOf(RoleDebugFlags.class);
            case DEFAULT -> RoleDebugFlags.getPreset("default");
        };
    }
}
