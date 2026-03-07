package com.alechilles.alecsnpcdebuginspector.debug;

import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Event categories used by the inspector Recent Events log.
 */
public enum NpcDebugEventCategory {
    CORE("core", "Core"),
    TARGETING("targeting", "Targeting"),
    TIMERS("timers", "Timers"),
    ALARMS("alarms", "Alarms"),
    NEEDS("needs", "Needs"),
    FLOCK("flock", "Flock");

    private final String id;
    private final String label;

    NpcDebugEventCategory(@Nonnull String id, @Nonnull String label) {
        this.id = id;
        this.label = label;
    }

    @Nonnull
    public String id() {
        return id;
    }

    @Nonnull
    public String label() {
        return label;
    }

    @Nullable
    public static NpcDebugEventCategory fromId(@Nullable String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        String normalized = rawId.trim().toLowerCase(Locale.ROOT);
        for (NpcDebugEventCategory category : values()) {
            if (category.id.equals(normalized)) {
                return category;
            }
        }
        return null;
    }
}
