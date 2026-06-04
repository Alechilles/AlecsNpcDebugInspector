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
    void acceptsDeclarativeMessageAndBeaconFixturesWithoutEntityMutation() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                """
                        {
                          "version": 1,
                          "requestId": "declarative_signals",
                          "assetId": "Mob_Tamework_Example_Simple",
                          "roleId": "Mob_Tamework_Example_Simple",
                          "ticks": 120,
                          "fixtures": {
                            "list": [
                              {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [0, 64, 0]},
                              {"fixtureId": "target.Enemy", "kind": "targetDummy", "position": [3, 64, 0], "targetSlot": "Enemy"},
                              {"fixtureId": "flock.follower_one", "kind": "flockMember", "position": [-2, 64, 0], "leaderFixtureId": "npcUnderTest"},
                              {"fixtureId": "message.threat", "kind": "message", "messageType": "threat.broadcast", "senderFixtureId": "npcUnderTest", "receiverFixtureId": "flock.follower_one", "targetFixtureId": "target.Enemy"},
                              {"fixtureId": "beacon.threat", "kind": "beacon", "beaconType": "threat", "sourceFixtureId": "npcUnderTest", "targetFixtureId": "target.Enemy", "requiredConsumerFixtureIds": ["flock.follower_one"]}
                            ]
                          },
                          "limits": {"maxEntities": 4}
                        }
                        """,
                config
        );

        assertEquals(5, request.fixtures().list().size());
        assertEquals(3, request.fixtures().entityCount());
    }

    @Test
    void acceptsPlayerAnchorAsHeadlessDeclarativeFixture() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                """
                        {
                          "version": 1,
                          "requestId": "player_anchor_declaration",
                          "assetId": "Mob_Tamework_Example_Simple",
                          "roleId": "Mob_Tamework_Example_Simple",
                          "ticks": 120,
                          "fixtures": {
                            "list": [
                              {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [0, 64, 0]},
                              {"fixtureId": "playerAnchor", "kind": "playerAnchor", "position": [2, 64, 0], "tags": ["owner", "nearby"]}
                            ]
                          }
                        }
                        """,
                config
        );

        assertEquals(2, request.fixtures().list().size());
        assertEquals(1, request.fixtures().entityCount());
        assertEquals(NpcRuntimeFixtureKind.PLAYER_ANCHOR, request.fixtures().list().get(1).kind());
        assertTrue(request.toJson().contains("\"kind\":\"playerAnchor\""));
    }

    @Test
    void rejectsMissingDeclarativeMessageOrBeaconReferences() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        """
                                {
                                  "version": 1,
                                  "requestId": "bad_signal_refs",
                                  "assetId": "Mob_Tamework_Example_Simple",
                                  "roleId": "Mob_Tamework_Example_Simple",
                                  "ticks": 120,
                                  "fixtures": {
                                    "list": [
                                      {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [0, 64, 0]},
                                      {"fixtureId": "message.threat", "kind": "message", "messageType": "threat.broadcast", "senderFixtureId": "npcUnderTest", "receiverFixtureId": "flock.missing"},
                                      {"fixtureId": "beacon.threat", "kind": "beacon", "beaconType": "threat", "sourceFixtureId": "npcUnderTest", "requiredConsumerFixtureIds": ["flock.missing"]}
                                    ]
                                  }
                                }
                                """,
                        config
                )
        );

        assertEquals("unsupported-fixture", exception.classification());
        assertTrue(exception.unsupported().toString().contains("fixtures.list[1].receiverFixtureId"));
        assertTrue(exception.unsupported().toString().contains("fixtures.list[2].requiredConsumerFixtureIds[0]"));
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
