package com.alechilles.alecsnpcdebuginspector.debug;

import javax.annotation.Nonnull;

/**
 * Immutable snapshot rendered by the NPC debug inspector page.
 */
public final class NpcDebugSnapshot {
    private final String title;
    private final String subtitle;
    private final String details;

    public NpcDebugSnapshot(@Nonnull String title,
                            @Nonnull String subtitle,
                            @Nonnull String details) {
        this.title = title;
        this.subtitle = subtitle;
        this.details = details;
    }

    @Nonnull
    public String title() {
        return title;
    }

    @Nonnull
    public String subtitle() {
        return subtitle;
    }

    @Nonnull
    public String details() {
        return details;
    }
}

