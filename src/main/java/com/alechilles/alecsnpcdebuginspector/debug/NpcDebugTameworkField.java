package com.alechilles.alecsnpcdebuginspector.debug;

import javax.annotation.Nonnull;

/**
 * Single Tamework integration line rendered in the inspector.
 */
final class NpcDebugTameworkField {
    private final String key;
    private final String label;
    private final String value;
    private final boolean trackChange;
    private final boolean recordEvent;

    NpcDebugTameworkField(@Nonnull String key,
                          @Nonnull String label,
                          @Nonnull String value,
                          boolean trackChange,
                          boolean recordEvent) {
        this.key = key;
        this.label = label;
        this.value = value;
        this.trackChange = trackChange;
        this.recordEvent = recordEvent;
    }

    @Nonnull
    String key() {
        return key;
    }

    @Nonnull
    String label() {
        return label;
    }

    @Nonnull
    String value() {
        return value;
    }

    boolean trackChange() {
        return trackChange;
    }

    boolean recordEvent() {
        return recordEvent;
    }
}
