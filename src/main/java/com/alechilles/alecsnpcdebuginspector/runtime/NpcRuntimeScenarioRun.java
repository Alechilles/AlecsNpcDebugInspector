package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Mutable state for one bounded runtime scenario execution.
 */
public final class NpcRuntimeScenarioRun {
    private final String requestId;
    private final String worldId;
    private final int deadlineTick;
    private final LinkedHashMap<String, UUID> npcUuids = new LinkedHashMap<>();
    private final List<String> fixtures = new ArrayList<>();
    private int currentTick = -1;
    private boolean canceled;
    private String cancelReason;
    private boolean cleanupAttempted;
    private boolean cleanupSucceeded = true;
    private String cleanupMessage = "not started";

    private NpcRuntimeScenarioRun(@Nonnull String requestId, @Nonnull String worldId, int deadlineTick) {
        this.requestId = requestId;
        this.worldId = worldId;
        this.deadlineTick = Math.max(0, deadlineTick);
    }

    @Nonnull
    public static NpcRuntimeScenarioRun start(@Nonnull String requestId, @Nonnull String worldId, int deadlineTick) {
        return new NpcRuntimeScenarioRun(requestId, worldId, deadlineTick);
    }

    @Nonnull
    public String requestId() {
        return requestId;
    }

    @Nonnull
    public String worldId() {
        return worldId;
    }

    public int deadlineTick() {
        return deadlineTick;
    }

    public int currentTick() {
        return currentTick;
    }

    void setCurrentTick(int currentTick) {
        this.currentTick = currentTick;
    }

    public boolean canceled() {
        return canceled;
    }

    @Nullable
    public String cancelReason() {
        return cancelReason;
    }

    public void cancel(@Nonnull String reason) {
        this.canceled = true;
        this.cancelReason = reason;
    }

    public void addNpc(@Nonnull String fixtureId, @Nonnull UUID uuid) {
        npcUuids.put(fixtureId, uuid);
    }

    @Nonnull
    public Map<String, UUID> npcUuids() {
        return Map.copyOf(npcUuids);
    }

    public void addFixture(@Nonnull String fixtureId) {
        if (!fixtures.contains(fixtureId)) {
            fixtures.add(fixtureId);
        }
    }

    @Nonnull
    public List<String> fixtures() {
        return List.copyOf(fixtures);
    }

    public void markCleanup(boolean succeeded, @Nonnull String message) {
        cleanupAttempted = true;
        cleanupSucceeded = succeeded;
        cleanupMessage = message;
    }

    public boolean cleanupAttempted() {
        return cleanupAttempted;
    }

    public boolean cleanupSucceeded() {
        return cleanupSucceeded;
    }

    @Nonnull
    public String cleanupMessage() {
        return cleanupMessage;
    }
}
