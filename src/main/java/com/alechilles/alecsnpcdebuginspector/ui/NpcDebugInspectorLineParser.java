package com.alechilles.alecsnpcdebuginspector.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Parses inspector details text into structured line entries.
 */
final class NpcDebugInspectorLineParser {
    static final String RECENT_EVENTS_SECTION = "Recent Events";
    static final String EVENTS_LOG_LABEL = "Events Log";
    static final String EVENTS_LOG_PIN_KEY = RECENT_EVENTS_SECTION + "|" + EVENTS_LOG_LABEL;
    private static final String EVENTS_LOG_PIN_TEXT = RECENT_EVENTS_SECTION + " | " + EVENTS_LOG_LABEL;

    private NpcDebugInspectorLineParser() {
    }

    @Nonnull
    static NpcDebugInspectorLine[] parse(@Nullable String details) {
        if (details == null || details.isBlank()) {
            return new NpcDebugInspectorLine[] {
                    new NpcDebugInspectorLine("No inspector lines available.", null, false, "No data")
            };
        }

        String currentSection = "General";
        List<NpcDebugInspectorLine> lines = new ArrayList<>();
        Map<String, Integer> duplicateCounter = new HashMap<>();
        String[] rawLines = details.split("\\R");
        for (String raw : rawLines) {
            if (raw == null) {
                continue;
            }
            String line = raw.stripTrailing();
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("=== ") && line.endsWith(" ===")) {
                currentSection = line.substring(4, Math.max(4, line.length() - 4)).trim();
                String sectionText = currentSection.isBlank() ? "Section" : currentSection;
                lines.add(new NpcDebugInspectorLine("=== " + sectionText + " ===", null, false, sectionText));
                continue;
            }

            String normalized = line;
            if (line.startsWith(">> ")) {
                normalized = line.substring(3);
            } else if (line.startsWith("- ")) {
                normalized = line.substring(2);
            }
            normalized = normalized.trim();

            if (RECENT_EVENTS_SECTION.equalsIgnoreCase(currentSection)) {
                if (normalized.startsWith(EVENTS_LOG_LABEL + ":")) {
                    lines.add(new NpcDebugInspectorLine(line, EVENTS_LOG_PIN_KEY, true, EVENTS_LOG_PIN_TEXT));
                } else {
                    lines.add(new NpcDebugInspectorLine(line, null, false, currentSection));
                }
                continue;
            }

            int valueSeparator = normalized.indexOf(": ");
            if (valueSeparator <= 0) {
                lines.add(new NpcDebugInspectorLine(line, null, false, currentSection));
                continue;
            }

            String label = normalized.substring(0, valueSeparator).trim();
            String value = normalized.substring(valueSeparator + 2).trim();
            String baseKey = currentSection + "|" + label;
            int seen = duplicateCounter.merge(baseKey, 1, Integer::sum);
            String key = seen == 1 ? baseKey : baseKey + "#" + seen;
            String pinnedText = currentSection + " | " + label + ": " + value;
            lines.add(new NpcDebugInspectorLine(line, key, true, pinnedText));
        }

        if (lines.isEmpty()) {
            lines.add(new NpcDebugInspectorLine("No inspector lines available.", null, false, "No data"));
        }
        return lines.toArray(new NpcDebugInspectorLine[0]);
    }
}
