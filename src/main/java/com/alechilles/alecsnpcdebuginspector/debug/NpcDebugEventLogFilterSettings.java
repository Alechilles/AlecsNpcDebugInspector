package com.alechilles.alecsnpcdebuginspector.debug;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Stores per-player event log visibility filters for inspector Recent Events.
 */
public final class NpcDebugEventLogFilterSettings {
    private static final EnumSet<NpcDebugEventCategory> DEFAULT_ENABLED = EnumSet.of(
            NpcDebugEventCategory.CORE,
            NpcDebugEventCategory.TARGETING,
            NpcDebugEventCategory.TIMERS,
            NpcDebugEventCategory.ALARMS,
            NpcDebugEventCategory.FLOCK
    );

    private static final Map<UUID, EnumSet<NpcDebugEventCategory>> ENABLED_BY_PLAYER = new ConcurrentHashMap<>();

    private NpcDebugEventLogFilterSettings() {
    }

    @Nonnull
    public static Set<NpcDebugEventCategory> getEnabledCategories(@Nullable UUID playerUuid) {
        if (playerUuid == null) {
            return EnumSet.copyOf(DEFAULT_ENABLED);
        }
        EnumSet<NpcDebugEventCategory> stored = ENABLED_BY_PLAYER.get(playerUuid);
        if (stored == null || stored.isEmpty()) {
            return EnumSet.copyOf(DEFAULT_ENABLED);
        }
        return EnumSet.copyOf(stored);
    }

    public static boolean isEnabled(@Nullable UUID playerUuid, @Nonnull NpcDebugEventCategory category) {
        return getEnabledCategories(playerUuid).contains(category);
    }

    public static void toggle(@Nullable UUID playerUuid, @Nonnull NpcDebugEventCategory category) {
        if (playerUuid == null) {
            return;
        }
        ENABLED_BY_PLAYER.compute(playerUuid, (ignored, current) -> {
            EnumSet<NpcDebugEventCategory> next = current == null || current.isEmpty()
                    ? EnumSet.copyOf(DEFAULT_ENABLED)
                    : EnumSet.copyOf(current);
            if (next.contains(category)) {
                next.remove(category);
            } else {
                next.add(category);
            }
            if (next.isEmpty()) {
                next.add(NpcDebugEventCategory.CORE);
            }
            return next;
        });
    }
}
