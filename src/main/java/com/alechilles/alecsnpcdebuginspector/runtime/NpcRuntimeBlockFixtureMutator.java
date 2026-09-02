package com.alechilles.alecsnpcdebuginspector.runtime;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Applies harness-owned block fixtures and keeps enough state to reset them during cleanup.
 */
final class NpcRuntimeBlockFixtureMutator {
    @Nonnull
    PreparedMutation prepare(@Nonnull World world,
                             @Nonnull String requestId,
                             int tick,
                             @Nonnull NpcRuntimeFixtureSpec fixture) {
        if (fixture.kind() != NpcRuntimeFixtureKind.BLOCK) {
            throw new IllegalArgumentException("fixture is not a block fixture: " + fixture.fixtureId());
        }
        if (fixture.blockId() == null || fixture.blockId().isBlank()) {
            throw new IllegalArgumentException("block fixture requires blockId");
        }
        int worldX = blockCoordinate(fixture.position(), 0, "position");
        int worldY = blockCoordinate(fixture.position(), 1, "position");
        int worldZ = blockCoordinate(fixture.position(), 2, "position");
        int localX = localBlockCoordinate(worldX);
        int localZ = localBlockCoordinate(worldZ);
        long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
        String stateKey = stateKey(fixture.state());
        String appliedBlockId = appliedBlockId(fixture.blockId(), stateKey);

        var chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) {
            chunk = world.getChunkIfNonTicking(chunkIndex);
        }
        if (chunk == null) {
            chunk = world.getChunkIfInMemory(chunkIndex);
        }
        if (chunk == null) {
            throw new IllegalStateException("block fixture chunk is not resident: " + chunkIndex);
        }

        int previousBlockId = chunk.getBlock(localX, worldY, localZ);
        return new PreparedMutation(
                requestId,
                tick,
                fixture,
                worldX,
                worldY,
                worldZ,
                localX,
                localZ,
                chunkIndex,
                previousBlockId,
                appliedBlockId,
                stateKey
        );
    }

    boolean reset(@Nonnull World world, @Nonnull AppliedMutation mutation) {
        var chunk = world.getChunkIfLoaded(mutation.chunkIndex());
        if (chunk == null) {
            chunk = world.getChunkIfNonTicking(mutation.chunkIndex());
        }
        if (chunk == null) {
            chunk = world.getChunkIfInMemory(mutation.chunkIndex());
        }
        return chunk != null && chunk.setBlock(mutation.localX(), mutation.worldY(), mutation.localZ(), mutation.previousBlockId());
    }

    static int localBlockCoordinate(int worldCoordinate) {
        return Math.floorMod(worldCoordinate, 32);
    }

    private static int blockCoordinate(@Nonnull List<Object> position, int index, @Nonnull String fieldName) {
        if (position.size() != 3) {
            throw new IllegalArgumentException(fieldName + " must contain exactly 3 coordinates");
        }
        Object value = position.get(index);
        if (value instanceof Number number) {
            return (int) Math.floor(number.doubleValue());
        }
        throw new IllegalArgumentException(fieldName + " must contain numeric coordinates");
    }

    record PreparedMutation(@Nonnull String requestId,
                            int tick,
                            @Nonnull NpcRuntimeFixtureSpec fixture,
                            int worldX,
                            int worldY,
                            int worldZ,
                            int localX,
                            int localZ,
                            long chunkIndex,
                            int previousBlockId,
                            @Nonnull String appliedBlockId,
                            @Nullable String stateKey) {
        @Nonnull
        NpcRuntimeTraceRecord beforeApplyRecord() {
            return baseRecord("before-apply", "pending", null)
                    .with("previousBlockId", previousBlockId);
        }

        @Nonnull
        AppliedMutation apply(@Nonnull World world) {
            var chunk = world.getChunkIfLoaded(chunkIndex);
            if (chunk == null) {
                chunk = world.getChunkIfNonTicking(chunkIndex);
            }
            if (chunk == null) {
                chunk = world.getChunkIfInMemory(chunkIndex);
            }
            if (chunk == null) {
                throw new IllegalStateException("block fixture chunk became unavailable: " + chunkIndex);
            }
            boolean applied = chunk.setBlock(localX, worldY, localZ, appliedBlockId);
            if (!applied) {
                throw new IllegalStateException("block fixture setBlock returned false");
            }
            return new AppliedMutation(
                    requestId,
                    tick,
                    fixture.fixtureId(),
                    fixture.blockId(),
                    appliedBlockId,
                    fixture.state(),
                    stateKey,
                    worldX,
                    worldY,
                    worldZ,
                    localX,
                    localZ,
                    chunkIndex,
                    previousBlockId
            );
        }

        @Nonnull
        private NpcRuntimeTraceRecord baseRecord(@Nonnull String phase,
                                                 @Nonnull String status,
                                                 @Nullable String error) {
            NpcRuntimeTraceRecord record = NpcRuntimeTraceRecord.of(requestId, tick, "block-fixture-mutation")
                    .with("fixtureId", fixture.fixtureId())
                    .with("phase", phase)
                    .with("status", status)
                    .with("blockId", fixture.blockId())
                    .with("appliedBlockId", appliedBlockId)
                    .with("state", fixture.state())
                    .with("stateKey", stateKey)
                    .with("position", fixture.position())
                    .with("worldPosition", List.of(worldX, worldY, worldZ))
                    .with("localPosition", List.of(localX, worldY, localZ))
                    .with("chunkIndex", chunkIndex)
                    .with("unsupportedFields", List.of());
            if (error != null) {
                record.with("error", error);
            }
            return record;
        }
    }

    record AppliedMutation(@Nonnull String requestId,
                           int tick,
                           @Nonnull String fixtureId,
                           @Nonnull String blockId,
                           @Nonnull String appliedBlockId,
                           @Nullable Object state,
                           @Nullable String stateKey,
                           int worldX,
                           int worldY,
                           int worldZ,
                           int localX,
                           int localZ,
                           long chunkIndex,
                           int previousBlockId) {
        @Nonnull
        NpcRuntimeTraceRecord appliedRecord() {
            return NpcRuntimeTraceRecord.of(requestId, tick, "block-fixture-mutation")
                    .with("fixtureId", fixtureId)
                    .with("phase", "after-apply")
                    .with("status", "applied")
                    .with("blockId", blockId)
                    .with("appliedBlockId", appliedBlockId)
                    .with("state", state)
                    .with("stateKey", stateKey)
                    .with("worldPosition", List.of(worldX, worldY, worldZ))
                    .with("localPosition", List.of(localX, worldY, localZ))
                    .with("chunkIndex", chunkIndex)
                    .with("previousBlockId", previousBlockId)
                    .with("unsupportedFields", List.of());
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("fixtureId", fixtureId);
            map.put("blockId", blockId);
            map.put("appliedBlockId", appliedBlockId);
            if (state != null) {
                map.put("state", state);
            }
            if (stateKey != null) {
                map.put("stateKey", stateKey);
            }
            map.put("worldPosition", List.of(worldX, worldY, worldZ));
            map.put("localPosition", List.of(localX, worldY, localZ));
            map.put("chunkIndex", chunkIndex);
            map.put("previousBlockId", previousBlockId);
            return map;
        }
    }

    @Nullable
    private static String stateKey(@Nullable Object state) {
        if (state == null) {
            return null;
        }
        if (state instanceof String text) {
            if (text.isBlank()) {
                throw new IllegalArgumentException("block fixture state must not be blank");
            }
            return text;
        }
        if (state instanceof Map<?, ?> map) {
            for (String key : List.of("stateKey", "key", "state", "id")) {
                Object value = map.get(key);
                if (value instanceof String text && !text.isBlank()) {
                    return text;
                }
            }
            if (map.size() == 1) {
                Object value = map.values().iterator().next();
                if (value instanceof String text && !text.isBlank()) {
                    return text;
                }
            }
            throw new IllegalArgumentException("block fixture state object must provide stateKey, key, state, id, or one string value");
        }
        throw new IllegalArgumentException("block fixture state must be a string or object");
    }

    @Nonnull
    private static String appliedBlockId(@Nonnull String blockId, @Nullable String stateKey) {
        if (stateKey == null) {
            return blockId;
        }
        BlockType blockType = BlockType.fromString(blockId);
        String resolved = blockType.getBlockKeyForState(stateKey);
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalArgumentException("block fixture state key is not valid for blockId " + blockId + ": " + stateKey);
        }
        return resolved;
    }
}
