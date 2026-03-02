package com.alechilles.alecsnpcdebuginspector.ui;

import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Resolves NPC display names from inspector overview lines using a stable fallback hierarchy.
 */
final class NpcDebugNameHierarchyResolver {
    private NpcDebugNameHierarchyResolver() {
    }

    /**
     * Resolves the preferred NPC name from inspector lines.
     * Fallback order: Display Name -> Entity Name Key -> Role Id -> provided fallback.
     */
    @Nonnull
    static String resolvePreferredName(@Nonnull NpcDebugInspectorLine[] lines, @Nonnull String fallback) {
        String displayName = findOverviewFieldValue(lines, "Display Name");
        String entityNameKey = findOverviewFieldValue(lines, "Entity Name Key");
        String roleId = findOverviewFieldValue(lines, "Role Id");
        return firstMeaningfulName(displayName, entityNameKey, roleId, fallback);
    }

    @Nullable
    private static String findOverviewFieldValue(@Nonnull NpcDebugInspectorLine[] lines, @Nonnull String label) {
        String keyPrefix = "Overview|" + label;
        for (NpcDebugInspectorLine line : lines) {
            if (line.key == null || !line.key.startsWith(keyPrefix)) {
                continue;
            }
            String parsedValue = parseLineValue(line.displayText);
            if (parsedValue != null) {
                return parsedValue;
            }
        }
        return null;
    }

    @Nullable
    private static String parseLineValue(@Nullable String displayText) {
        if (displayText == null) {
            return null;
        }
        String normalized = displayText.strip();
        if (normalized.startsWith(">> ")) {
            normalized = normalized.substring(3);
        } else if (normalized.startsWith("- ")) {
            normalized = normalized.substring(2);
        }
        normalized = normalized.strip();
        if (normalized.isBlank()) {
            return null;
        }
        int separatorIndex = normalized.indexOf(": ");
        if (separatorIndex < 0 || separatorIndex + 2 >= normalized.length()) {
            return normalized;
        }
        return normalized.substring(separatorIndex + 2).trim();
    }

    @Nonnull
    private static String firstMeaningfulName(@Nullable String displayName,
                                              @Nullable String entityNameKey,
                                              @Nullable String roleId,
                                              @Nonnull String fallback) {
        if (isMeaningfulName(displayName)) {
            return displayName.trim();
        }
        if (isMeaningfulName(entityNameKey)) {
            return entityNameKey.trim();
        }
        if (isMeaningfulName(roleId)) {
            return roleId.trim();
        }
        return fallback;
    }

    private static boolean isMeaningfulName(@Nullable String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return !"<none>".equals(lower)
                && !"none".equals(lower)
                && !"n/a".equals(lower)
                && !"null".equals(lower)
                && !"<unknown>".equals(lower)
                && !"unknown".equals(lower);
    }
}
