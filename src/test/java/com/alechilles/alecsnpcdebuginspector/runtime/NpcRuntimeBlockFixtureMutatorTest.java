package com.alechilles.alecsnpcdebuginspector.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NpcRuntimeBlockFixtureMutatorTest {
    @Test
    void convertsWorldCoordinatesToChunkLocalCoordinates() {
        assertEquals(0, NpcRuntimeBlockFixtureMutator.localBlockCoordinate(0));
        assertEquals(31, NpcRuntimeBlockFixtureMutator.localBlockCoordinate(-1));
        assertEquals(1, NpcRuntimeBlockFixtureMutator.localBlockCoordinate(33));
        assertEquals(30, NpcRuntimeBlockFixtureMutator.localBlockCoordinate(-34));
    }

    @Test
    void appliedMutationRecordContainsResetEvidenceAndAppliedStateBlock() {
        NpcRuntimeBlockFixtureMutator.AppliedMutation mutation = new NpcRuntimeBlockFixtureMutator.AppliedMutation(
                "block_request",
                0,
                "block.cover",
                "hytale:stone",
                "hytale:stone_solid",
                java.util.Map.of("variant", "solid"),
                "solid",
                -1,
                64,
                33,
                31,
                1,
                12345L,
                7
        );

        NpcRuntimeTraceRecord record = mutation.appliedRecord();

        assertEquals("block-fixture-mutation", record.fields().get("kind"));
        assertEquals("after-apply", record.fields().get("phase"));
        assertEquals("applied", record.fields().get("status"));
        assertEquals(List.of(31, 64, 1), record.fields().get("localPosition"));
        assertEquals(7, record.fields().get("previousBlockId"));
        assertEquals("hytale:stone_solid", record.fields().get("appliedBlockId"));
        assertEquals("solid", record.fields().get("stateKey"));
        assertFalse(record.fields().get("unsupportedFields").toString().contains("blockStateMutation"));
        assertEquals(7, mutation.toMap().get("previousBlockId"));
        assertEquals("hytale:stone_solid", mutation.toMap().get("appliedBlockId"));
    }
}
