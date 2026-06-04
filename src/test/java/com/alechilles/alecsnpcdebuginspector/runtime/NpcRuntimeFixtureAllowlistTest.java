package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeFixtureAllowlistTest {
    @Test
    void acceptsNpcUnderTestAndNpcBackedTargetFixtures() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                """
                        {
                          "version": 1,
                          "requestId": "target_fixture",
                          "assetId": "Mob_Tamework_Example_Simple",
                          "roleId": "Mob_Tamework_Example_Simple",
                          "ticks": 3,
                          "fixtures": {
                            "list": [
                              {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [0, 64, 0], "roleId": "Mob_Tamework_Example_Simple"},
                              {"fixtureId": "target.Enemy", "kind": "targetDummy", "position": [3, 64, 0], "roleId": "Mob_Tamework_Example_Simple", "targetSlot": "Enemy"}
                            ]
                          }
                        }
                        """,
                config
        );

        assertEquals(2, request.fixtures().list().size());
        assertEquals(2, request.fixtures().entityCount());
    }

    @Test
    void rejectsUnsafeBlockMutationAsUnsupportedFixture() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        """
                                {
                                  "version": 1,
                                  "requestId": "block_fixture",
                                  "assetId": "Mob_Tamework_Example_Simple",
                                  "roleId": "Mob_Tamework_Example_Simple",
                                  "ticks": 3,
                                  "fixtures": {
                                    "list": [
                                      {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [0, 64, 0]},
                                      {"fixtureId": "block.cover", "kind": "block", "position": [1, 64, 0], "blockId": "Stone"}
                                    ]
                                  }
                                }
                                """,
                        config
                )
        );

        assertEquals("unsupported-fixture", exception.classification());
        assertTrue(exception.unsupported().toString().contains("safe world mutation is not implemented yet"));
    }

    @Test
    void rejectsDuplicateFixtureIdsBeforeWorldMutation() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        """
                                {
                                  "version": 1,
                                  "requestId": "duplicate_fixture",
                                  "assetId": "Mob_Tamework_Example_Simple",
                                  "roleId": "Mob_Tamework_Example_Simple",
                                  "ticks": 3,
                                  "fixtures": {
                                    "list": [
                                      {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [0, 64, 0]},
                                      {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [1, 64, 0]}
                                    ]
                                  }
                                }
                                """,
                        config
                )
        );

        assertEquals("unsupported-fixture", exception.classification());
        assertTrue(exception.unsupported().toString().contains("duplicate fixture id"));
    }
}
