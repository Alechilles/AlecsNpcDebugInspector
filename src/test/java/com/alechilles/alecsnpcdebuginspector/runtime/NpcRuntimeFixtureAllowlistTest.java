package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Path;
import java.util.List;
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
    void acceptsBlockFixtureWhenBlockIdIsPresent() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                """
                        {
                          "version": 1,
                          "requestId": "block_fixture",
                          "assetId": "Mob_Tamework_Example_Simple",
                          "roleId": "Mob_Tamework_Example_Simple",
                          "ticks": 3,
                          "fixtures": {
                            "list": [
                              {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [0, 64, 0], "roleId": "Mob_Tamework_Example_Simple"},
                              {"fixtureId": "block.cover", "kind": "block", "position": [1, 64, 0], "blockId": "Stone", "state": {"variant": "solid"}}
                            ]
                          }
                        }
                        """,
                config
        );

        assertEquals(2, request.fixtures().list().size());
        assertEquals(1, request.fixtures().entityCount());
        assertEquals(NpcRuntimeFixtureKind.BLOCK, request.fixtures().list().get(1).kind());
        assertEquals("Stone", request.fixtures().list().get(1).blockId());
        assertTrue(request.toJson().contains("\"state\""));
    }

    @Test
    void rejectsBlockFixtureWithoutBlockIdWithExactPath() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        """
                                {
                                  "version": 1,
                                  "requestId": "block_fixture_missing_id",
                                  "assetId": "Mob_Tamework_Example_Simple",
                                  "roleId": "Mob_Tamework_Example_Simple",
                                  "ticks": 3,
                                  "fixtures": {
                                    "list": [
                                      {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [0, 64, 0], "roleId": "Mob_Tamework_Example_Simple"},
                                      {"fixtureId": "block.cover", "kind": "block", "position": [1, 64, 0]}
                                    ]
                                  }
                                }
                                """,
                        config
                )
        );

        assertEquals("unsupported-fixture", exception.classification());
        assertEquals("fixtures.list[1].blockId", exception.unsupported().getFirst().path());
        assertEquals("block fixtures require blockId", exception.unsupported().getFirst().reason());
    }

    @Test
    void rejectsUnsafeItemMutationAsUnsupportedFixtureWithExactPath() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        """
                                {
                                  "version": 1,
                                  "requestId": "item_fixture",
                                  "assetId": "Mob_Tamework_Example_Simple",
                                  "roleId": "Mob_Tamework_Example_Simple",
                                  "ticks": 3,
                                  "fixtures": {
                                    "list": [
                                      {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [0, 64, 0], "roleId": "Mob_Tamework_Example_Simple"},
                                      {"fixtureId": "item.food", "kind": "item", "position": [1, 64, 0], "itemId": "hytale:apple"}
                                    ]
                                  }
                                }
                                """,
                        config
                )
        );

        assertEquals("unsupported-fixture", exception.classification());
        assertEquals("fixtures.list[1].kind", exception.unsupported().getFirst().path());
        assertEquals(
                "item fixture spawning is not implemented; safe item spawn/drop API is unconfirmed",
                exception.unsupported().getFirst().reason()
        );
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
                              {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [0, 64, 0], "roleId": "Mob_Tamework_Example_Simple"},
                              {"fixtureId": "target.Enemy", "kind": "targetDummy", "position": [3, 64, 0], "roleId": "Mob_Tamework_Example_Simple", "targetSlot": "Enemy"},
                              {"fixtureId": "flock.follower_one", "kind": "flockMember", "position": [-2, 64, 0], "roleId": "Mob_Tamework_Example_Simple", "leaderFixtureId": "npcUnderTest"},
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
    void acceptsFlockFixtureWithLeaderAndTwoFollowers() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                """
                        {
                          "version": 1,
                          "requestId": "three_member_flock",
                          "assetId": "Mob_Tamework_Example_Simple",
                          "roleId": "Mob_Tamework_Example_Simple",
                          "ticks": 180,
                          "fixtures": {
                            "list": [
                              {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [0, 64, 0], "roleId": "Mob_Tamework_Example_Simple"},
                              {"fixtureId": "flock.follower_one", "kind": "flockMember", "position": [-2, 64, 1], "roleId": "Mob_Tamework_Example_Simple", "flockId": "generated.flock", "flockRole": "follower", "leaderFixtureId": "npcUnderTest"},
                              {"fixtureId": "flock.follower_two", "kind": "flockMember", "position": [-4, 64, -1], "roleId": "Mob_Tamework_Example_Simple", "flockId": "generated.flock", "flockRole": "follower", "leaderFixtureId": "npcUnderTest"}
                            ]
                          },
                          "limits": {"maxEntities": 4}
                        }
                        """,
                config
        );

        assertEquals(3, request.fixtures().list().size());
        assertEquals("npcUnderTest", request.fixtures().list().get(1).leaderFixtureId());
        assertEquals("npcUnderTest", request.fixtures().list().get(2).leaderFixtureId());
    }

    @Test
    void acceptsSplitDistanceFlockFixtureSetup() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                """
                        {
                          "version": 1,
                          "requestId": "split_distance_flock",
                          "assetId": "Mob_Tamework_Example_Simple",
                          "roleId": "Mob_Tamework_Example_Simple",
                          "ticks": 600,
                          "fixtures": {
                            "list": [
                              {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [0, 64, 0], "roleId": "Mob_Tamework_Example_Simple"},
                              {"fixtureId": "flock.near", "kind": "flockMember", "position": [-2, 64, 1], "roleId": "Mob_Tamework_Example_Simple", "flockId": "generated.flock", "flockRole": "follower", "leaderFixtureId": "npcUnderTest"},
                              {"fixtureId": "flock.distant", "kind": "flockMember", "position": [-16, 64, -1], "roleId": "Mob_Tamework_Example_Simple", "flockId": "generated.flock", "flockRole": "follower", "leaderFixtureId": "npcUnderTest"}
                            ]
                          },
                          "limits": {"maxEntities": 4}
                        }
                        """,
                config
        );

        assertEquals(List.of(-2.0, 64.0, 1.0), request.fixtures().list().get(1).position());
        assertEquals(List.of(-16.0, 64.0, -1.0), request.fixtures().list().get(2).position());
    }

    @Test
    void acceptsFamilyFixtureWithParentAndChild() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                """
                        {
                          "version": 1,
                          "requestId": "adult_child_family",
                          "assetId": "Mob_Tamework_Example_Simple",
                          "roleId": "Mob_Tamework_Example_Simple",
                          "ticks": 180,
                          "fixtures": {
                            "list": [
                              {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [0, 64, 0], "roleId": "Mob_Tamework_Example_Simple", "familyId": "generated.family", "familyRole": "adult"},
                              {"fixtureId": "family.child", "kind": "familyMember", "position": [-2, 64, 0], "roleId": "Mob_Tamework_Example_Simple", "familyId": "generated.family", "familyRole": "child", "parentFixtureId": "npcUnderTest"}
                            ]
                          },
                          "limits": {"maxEntities": 3}
                        }
                        """,
                config
        );

        assertEquals(2, request.fixtures().list().size());
        assertEquals("npcUnderTest", request.fixtures().list().get(1).parentFixtureId());
        assertEquals("child", request.fixtures().list().get(1).familyRole());
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
                              {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [0, 64, 0], "roleId": "Mob_Tamework_Example_Simple"},
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
                                      {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [0, 64, 0], "roleId": "Mob_Tamework_Example_Simple"},
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
    void rejectsForwardFlockAndFamilyRelationshipReferences() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        """
                                {
                                  "version": 1,
                                  "requestId": "forward_refs",
                                  "assetId": "Mob_Tamework_Example_Simple",
                                  "roleId": "Mob_Tamework_Example_Simple",
                                  "ticks": 120,
                                  "fixtures": {
                                    "list": [
                                      {"fixtureId": "flock.follower", "kind": "flockMember", "position": [-2, 64, 0], "roleId": "Mob_Tamework_Example_Simple", "leaderFixtureId": "npcUnderTest"},
                                      {"fixtureId": "family.child", "kind": "familyMember", "position": [-3, 64, 0], "roleId": "Mob_Tamework_Example_Simple", "parentFixtureId": "npcUnderTest"},
                                      {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [0, 64, 0], "roleId": "Mob_Tamework_Example_Simple"}
                                    ]
                                  }
                                }
                                """,
                        config
                )
        );

        assertEquals("unsupported-fixture", exception.classification());
        assertEquals("fixtures.list[0].leaderFixtureId", exception.unsupported().getFirst().path());
        assertEquals(
                "referenced fixture id must be declared before this fixture: npcUnderTest",
                exception.unsupported().getFirst().reason()
        );
        assertEquals("fixtures.list[1].parentFixtureId", exception.unsupported().get(1).path());
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
                                      {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [0, 64, 0], "roleId": "Mob_Tamework_Example_Simple"},
                                      {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [1, 64, 0], "roleId": "Mob_Tamework_Example_Simple"}
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
