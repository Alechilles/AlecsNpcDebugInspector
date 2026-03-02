package com.alechilles.alecsnpcdebuginspector.ui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Parsed line model for NPC inspector output.
 */
final class NpcDebugInspectorLine {
    final String displayText;
    @Nullable
    final String key;
    final boolean pinnable;
    final String pinnedText;

    NpcDebugInspectorLine(@Nonnull String displayText,
                          @Nullable String key,
                          boolean pinnable,
                          @Nonnull String pinnedText) {
        this.displayText = displayText;
        this.key = key;
        this.pinnable = pinnable;
        this.pinnedText = pinnedText;
    }
}
