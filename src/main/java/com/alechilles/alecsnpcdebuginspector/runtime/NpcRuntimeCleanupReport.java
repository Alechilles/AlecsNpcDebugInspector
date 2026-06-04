package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Structured cleanup evidence for harness-owned arena fixtures.
 */
public final class NpcRuntimeCleanupReport {
    private final int entityRemovalAttempted;
    private final int entityRemovalSucceeded;
    private final int entityRemovalFailed;
    private final int blockResetAttempted;
    private final int blockResetSucceeded;
    private final int blockResetFailed;
    private final List<String> unresolvedFixtureIds;

    public NpcRuntimeCleanupReport(int entityRemovalAttempted,
                                   int entityRemovalSucceeded,
                                   int entityRemovalFailed,
                                   int blockResetAttempted,
                                   int blockResetSucceeded,
                                   int blockResetFailed,
                                   @Nonnull List<String> unresolvedFixtureIds) {
        this.entityRemovalAttempted = Math.max(0, entityRemovalAttempted);
        this.entityRemovalSucceeded = Math.max(0, entityRemovalSucceeded);
        this.entityRemovalFailed = Math.max(0, entityRemovalFailed);
        this.blockResetAttempted = Math.max(0, blockResetAttempted);
        this.blockResetSucceeded = Math.max(0, blockResetSucceeded);
        this.blockResetFailed = Math.max(0, blockResetFailed);
        this.unresolvedFixtureIds = List.copyOf(unresolvedFixtureIds);
    }

    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    public boolean succeeded() {
        return entityRemovalFailed == 0 && blockResetFailed == 0 && unresolvedFixtureIds.isEmpty();
    }

    @Nonnull
    public String message() {
        if (succeeded()) {
            return "removed entities=" + entityRemovalSucceeded + ", reset blocks=" + blockResetSucceeded;
        }
        return "cleanup failed: entityFailures=" + entityRemovalFailed
                + ", blockFailures=" + blockResetFailed
                + ", unresolvedFixtures=" + unresolvedFixtureIds.size();
    }

    @Nonnull
    public Map<String, Object> toMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("entityRemovalAttempted", entityRemovalAttempted);
        map.put("entityRemovalSucceeded", entityRemovalSucceeded);
        map.put("entityRemovalFailed", entityRemovalFailed);
        map.put("blockResetAttempted", blockResetAttempted);
        map.put("blockResetSucceeded", blockResetSucceeded);
        map.put("blockResetFailed", blockResetFailed);
        map.put("unresolvedFixtureIds", unresolvedFixtureIds);
        map.put("succeeded", succeeded());
        return map;
    }

    public static final class Builder {
        private int entityRemovalAttempted;
        private int entityRemovalSucceeded;
        private int entityRemovalFailed;
        private int blockResetAttempted;
        private int blockResetSucceeded;
        private int blockResetFailed;
        private final ArrayList<String> unresolvedFixtureIds = new ArrayList<>();

        @Nonnull
        public Builder entityRemoval(boolean succeeded) {
            entityRemovalAttempted++;
            if (succeeded) {
                entityRemovalSucceeded++;
            } else {
                entityRemovalFailed++;
            }
            return this;
        }

        @Nonnull
        public Builder blockReset(boolean succeeded) {
            blockResetAttempted++;
            if (succeeded) {
                blockResetSucceeded++;
            } else {
                blockResetFailed++;
            }
            return this;
        }

        @Nonnull
        public Builder unresolvedFixture(@Nonnull String fixtureId) {
            if (!unresolvedFixtureIds.contains(fixtureId)) {
                unresolvedFixtureIds.add(fixtureId);
            }
            return this;
        }

        @Nonnull
        public NpcRuntimeCleanupReport build() {
            return new NpcRuntimeCleanupReport(
                    entityRemovalAttempted,
                    entityRemovalSucceeded,
                    entityRemovalFailed,
                    blockResetAttempted,
                    blockResetSucceeded,
                    blockResetFailed,
                    unresolvedFixtureIds
            );
        }
    }
}
