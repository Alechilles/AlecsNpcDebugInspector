package com.alechilles.alecsnpcdebuginspector.ui;

import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * View model for one linked NPC card in the inspector roster page.
 */
public final class NpcDebugLinkedEntry {
    private final UUID npcUuid;
    private final String displayName;
    private final String roleId;
    private final boolean loaded;
    private final String stateName;
    private final String healthText;
    private final String flockText;

    public NpcDebugLinkedEntry(@Nonnull UUID npcUuid,
                               @Nonnull String displayName,
                               @Nonnull String roleId,
                               boolean loaded,
                               @Nullable String stateName,
                               @Nullable String healthText,
                               @Nullable String flockText) {
        this.npcUuid = npcUuid;
        this.displayName = displayName;
        this.roleId = roleId;
        this.loaded = loaded;
        this.stateName = stateName;
        this.healthText = healthText;
        this.flockText = flockText;
    }

    @Nonnull
    public UUID npcUuid() {
        return npcUuid;
    }

    @Nonnull
    public String displayName() {
        return displayName;
    }

    @Nonnull
    public String roleId() {
        return roleId;
    }

    public boolean loaded() {
        return loaded;
    }

    @Nullable
    public String stateName() {
        return stateName;
    }

    @Nullable
    public String healthText() {
        return healthText;
    }

    @Nullable
    public String flockText() {
        return flockText;
    }
}

