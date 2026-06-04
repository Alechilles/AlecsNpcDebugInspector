package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Owns the deterministic list of fixtures created by one runtime scenario.
 */
public final class NpcRuntimeFixtureRegistry {
    private final LinkedHashMap<String, FixtureRecord> fixtures = new LinkedHashMap<>();
    private final LinkedHashMap<String, BlockMutation> blockMutations = new LinkedHashMap<>();
    private final List<FixtureLink> fixtureLinks = new java.util.ArrayList<>();

    @Nonnull
    public FixtureRecord recordEntity(@Nonnull String fixtureId, @Nonnull String kind, @Nullable UUID uuid) {
        rejectDuplicate(fixtureId);
        FixtureRecord record = new FixtureRecord(fixtureId, kind, uuid);
        fixtures.put(fixtureId, record);
        return record;
    }

    @Nonnull
    public BlockMutation recordBlockMutation(@Nonnull String fixtureId, @Nonnull String position, @Nonnull String originalBlockId) {
        rejectDuplicate(fixtureId);
        BlockMutation mutation = new BlockMutation(fixtureId, position, originalBlockId);
        blockMutations.put(fixtureId, mutation);
        return mutation;
    }

    @Nonnull
    public FixtureLink recordFixtureLink(@Nonnull String fixtureId,
                                         @Nonnull String relationship,
                                         @Nonnull String targetFixtureId) {
        FixtureLink link = new FixtureLink(fixtureId, relationship, targetFixtureId);
        fixtureLinks.add(link);
        return link;
    }

    @Nonnull
    public List<FixtureRecord> fixtures() {
        return List.copyOf(fixtures.values());
    }

    @Nonnull
    public List<BlockMutation> blockMutations() {
        return List.copyOf(blockMutations.values());
    }

    @Nonnull
    public List<FixtureLink> fixtureLinks() {
        return List.copyOf(fixtureLinks);
    }

    public boolean contains(@Nonnull String fixtureId) {
        return fixtures.containsKey(fixtureId) || blockMutations.containsKey(fixtureId);
    }

    private void rejectDuplicate(@Nonnull String fixtureId) {
        if (contains(fixtureId)) {
            throw new IllegalArgumentException("Duplicate runtime fixture id: " + fixtureId);
        }
    }

    public record FixtureRecord(@Nonnull String fixtureId, @Nonnull String kind, @Nullable UUID uuid) {
        @Nonnull
        public Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("fixtureId", fixtureId);
            map.put("kind", kind);
            map.put("uuid", uuid != null ? uuid.toString() : null);
            return map;
        }
    }

    public record BlockMutation(@Nonnull String fixtureId, @Nonnull String position, @Nonnull String originalBlockId) {
        @Nonnull
        public Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("fixtureId", fixtureId);
            map.put("position", position);
            map.put("originalBlockId", originalBlockId);
            return map;
        }
    }

    public record FixtureLink(@Nonnull String fixtureId,
                              @Nonnull String relationship,
                              @Nonnull String targetFixtureId) {
        @Nonnull
        public Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("fixtureId", fixtureId);
            map.put("relationship", relationship);
            map.put("targetFixtureId", targetFixtureId);
            return map;
        }
    }
}
