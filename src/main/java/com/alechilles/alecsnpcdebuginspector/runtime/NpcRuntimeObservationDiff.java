package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Field-level changes between two normalized observations.
 */
public record NpcRuntimeObservationDiff(@Nonnull List<Change> changes) {
    @Nonnull
    static NpcRuntimeObservationDiff between(@Nullable NpcRuntimeObservedNpc previous,
                                             @Nonnull NpcRuntimeObservedNpc current) {
        if (previous == null) {
            return new NpcRuntimeObservationDiff(List.of());
        }
        ArrayList<Change> changes = new ArrayList<>();
        Set<String> sections = new LinkedHashSet<>();
        sections.addAll(previous.sections().keySet());
        sections.addAll(current.sections().keySet());
        for (String section : sections) {
            Map<String, String> beforeSection = previous.section(section);
            Map<String, String> afterSection = current.section(section);
            Set<String> fields = new LinkedHashSet<>();
            fields.addAll(beforeSection.keySet());
            fields.addAll(afterSection.keySet());
            for (String field : fields) {
                String before = beforeSection.get(field);
                String after = afterSection.get(field);
                if (before == null ? after != null : !before.equals(after)) {
                    changes.add(new Change(section, field, before, after));
                }
            }
        }
        return new NpcRuntimeObservationDiff(List.copyOf(changes));
    }

    public boolean changed() {
        return !changes.isEmpty();
    }

    public record Change(@Nonnull String section,
                         @Nonnull String field,
                         @Nullable String before,
                         @Nullable String after) {
    }
}
