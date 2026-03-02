package com.alechilles.alecsnpcdebuginspector.ui;

import com.hypixel.hytale.server.npc.role.RoleDebugFlags;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Static catalog for built-in NPC role debug flags.
 */
public final class NpcDebugRoleDebugFlagCatalog {
    private static final String CATEGORY_VISUALIZATION = "Visualization";
    private static final String CATEGORY_DISPLAY = "Display";
    private static final String CATEGORY_TRACE = "Tracing";
    private static final String CATEGORY_MOVEMENT = "Movement / Pathing";
    private static final String CATEGORY_OTHER = "Other";
    private static final String[] CATEGORY_ORDER = {
            CATEGORY_VISUALIZATION,
            CATEGORY_DISPLAY,
            CATEGORY_TRACE,
            CATEGORY_MOVEMENT,
            CATEGORY_OTHER
    };
    private static final LinkedHashMap<String, List<FlagDescriptor>> DESCRIPTORS_BY_CATEGORY = buildDescriptorsByCategory();

    private NpcDebugRoleDebugFlagCatalog() {
    }

    /**
     * Returns all debug flags grouped by UI category in a stable order.
     */
    @Nonnull
    public static LinkedHashMap<String, List<FlagDescriptor>> groupedDescriptors() {
        LinkedHashMap<String, List<FlagDescriptor>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<FlagDescriptor>> entry : DESCRIPTORS_BY_CATEGORY.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return copy;
    }

    @Nonnull
    private static LinkedHashMap<String, List<FlagDescriptor>> buildDescriptorsByCategory() {
        LinkedHashMap<String, List<FlagDescriptor>> rows = new LinkedHashMap<>();
        for (String category : CATEGORY_ORDER) {
            rows.put(category, new ArrayList<>());
        }
        for (RoleDebugFlags flag : RoleDebugFlags.values()) {
            String category = resolveCategory(flag);
            String label = humanizeFlagName(flag.name());
            String description = safeDescription(flag);
            rows.computeIfAbsent(category, ignored -> new ArrayList<>())
                    .add(new FlagDescriptor(flag, label, description));
        }
        return rows;
    }

    @Nonnull
    private static String resolveCategory(@Nonnull RoleDebugFlags flag) {
        String name = flag.name();
        if (name.startsWith("Vis")) {
            return CATEGORY_VISUALIZATION;
        }
        if (name.startsWith("Display")) {
            return CATEGORY_DISPLAY;
        }
        if (name.startsWith("Trace") || "Flock".equals(name) || "FlockDamage".equals(name) || "BeaconMessages".equals(name)) {
            return CATEGORY_TRACE;
        }
        if (name.startsWith("MotionController")
                || name.startsWith("Validate")
                || "Collisions".equals(name)
                || "BlockCollisions".equals(name)
                || "ProbeBlockCollisions".equals(name)
                || "SteeringRole".equals(name)
                || "Overlaps".equals(name)
                || "Pathfinder".equals(name)) {
            return CATEGORY_MOVEMENT;
        }
        return CATEGORY_OTHER;
    }

    @Nonnull
    private static String safeDescription(@Nonnull RoleDebugFlags flag) {
        String description = flag.get();
        if (description == null || description.isBlank()) {
            return flag.name();
        }
        return description.trim();
    }

    @Nonnull
    private static String humanizeFlagName(@Nonnull String rawName) {
        if (rawName.isBlank()) {
            return rawName;
        }
        String spaced = rawName
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
        String[] words = spaced.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            if (word.length() == 1) {
                out.append(word.toUpperCase(Locale.ROOT));
                continue;
            }
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
            out.append(word.substring(1));
        }
        return out.length() > 0 ? out.toString() : rawName;
    }

    /**
     * UI metadata for one built-in debug flag.
     */
    public record FlagDescriptor(@Nonnull RoleDebugFlags flag,
                                 @Nonnull String label,
                                 @Nonnull String description) {
    }
}
