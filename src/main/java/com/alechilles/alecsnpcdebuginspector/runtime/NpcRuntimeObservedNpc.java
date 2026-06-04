package com.alechilles.alecsnpcdebuginspector.runtime;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Normalized runtime observation derived from the inspector snapshot.
 */
public record NpcRuntimeObservedNpc(
        @Nullable UUID npcUuid,
        @Nonnull String title,
        @Nonnull String subtitle,
        @Nonnull Map<String, Map<String, String>> sections
) {
    @Nonnull
    static NpcRuntimeObservedNpc fromSnapshot(@Nullable UUID npcUuid, @Nonnull NpcDebugSnapshot snapshot) {
        return new NpcRuntimeObservedNpc(
                npcUuid,
                snapshot.title(),
                snapshot.subtitle(),
                parseSections(snapshot.details())
        );
    }

    @Nonnull
    Map<String, Object> stateMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        putSection(map, "identity", "Overview");
        putSection(map, "ai", "AI");
        putSection(map, "lifecycle", "Lifecycle / Persistence");
        return map;
    }

    @Nonnull
    Map<String, Object> tameworkMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : sections.entrySet()) {
            if (entry.getKey().startsWith("Tamework")) {
                map.put(normalizeSectionName(entry.getKey()), entry.getValue());
            }
        }
        return map;
    }

    @Nonnull
    Map<String, String> section(@Nonnull String sectionName) {
        return sections.getOrDefault(sectionName, Map.of());
    }

    @Nonnull
    private static Map<String, Map<String, String>> parseSections(@Nonnull String details) {
        LinkedHashMap<String, Map<String, String>> parsed = new LinkedHashMap<>();
        String currentSection = null;
        LinkedHashMap<String, String> currentFields = new LinkedHashMap<>();
        for (String rawLine : details.split("\\R")) {
            String line = rawLine.trim();
            if (line.startsWith("=== ") && line.endsWith(" ===")) {
                if (currentSection != null) {
                    parsed.put(currentSection, currentFields);
                }
                currentSection = line.substring(4, line.length() - 4).trim();
                currentFields = new LinkedHashMap<>();
                continue;
            }
            if (currentSection == null || line.isBlank() || "<no data>".equals(line)) {
                continue;
            }
            parseFieldLine(line, currentFields);
        }
        if (currentSection != null) {
            parsed.put(currentSection, currentFields);
        }
        return parsed;
    }

    private static void parseFieldLine(@Nonnull String line, @Nonnull LinkedHashMap<String, String> fields) {
        String normalized = line;
        if (normalized.startsWith(">> ")) {
            normalized = normalized.substring(3);
        } else if (normalized.startsWith("- ")) {
            normalized = normalized.substring(2);
        }
        int separator = normalized.indexOf(": ");
        if (separator <= 0) {
            return;
        }
        String label = normalized.substring(0, separator).trim();
        String value = normalized.substring(separator + 2).trim();
        if (!label.isBlank()) {
            fields.put(normalizeFieldName(label), value);
        }
    }

    private void putSection(@Nonnull LinkedHashMap<String, Object> target,
                            @Nonnull String outputName,
                            @Nonnull String sectionName) {
        Map<String, String> values = section(sectionName);
        if (!values.isEmpty()) {
            target.put(outputName, values);
        }
    }

    @Nonnull
    static String normalizeFieldName(@Nonnull String label) {
        return normalizeWords(label, false);
    }

    @Nonnull
    static String normalizeSectionName(@Nonnull String label) {
        return normalizeWords(label, false);
    }

    @Nonnull
    private static String normalizeWords(@Nonnull String text, boolean upperFirst) {
        StringBuilder result = new StringBuilder();
        boolean nextUpper = upperFirst;
        for (String part : text.split("[^A-Za-z0-9]+")) {
            if (part.isBlank()) {
                continue;
            }
            String lower = part.toLowerCase(java.util.Locale.ROOT);
            if (result.isEmpty() && !nextUpper) {
                result.append(lower);
            } else {
                result.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
            }
            nextUpper = true;
        }
        return result.isEmpty() ? "value" : result.toString();
    }

    @Nonnull
    List<String> knownSectionNames() {
        return List.copyOf(sections.keySet());
    }
}
