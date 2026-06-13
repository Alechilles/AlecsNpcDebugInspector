package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeRequestTest {
    @Test
    void parsesValidV1Request() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));
        String json = """
                {
                  "version": 1,
                  "requestId": "protect_baby_close_target",
                  "scenario": {"id": "protect-baby-close", "description": "close hostile target"},
                  "assetId": "Alec_Template_Boar_Family_Adult",
                  "roleId": "Alec_Template_Boar_Family_Adult",
                  "ticks": 200,
                  "seed": 7,
                  "world": {"instanceId": "npc_runtime_test_flatworld", "arena": "default"},
                  "environment": {"timeOfDay": 12000, "weather": "clear", "light": 15},
                  "fixtures": {
                    "npc": {"position": [1, 64, -2], "state": "Idle"},
                    "targets": [
                      {"slot": "Enemy", "kind": "dummy", "position": [4, 64, 0], "tags": ["hostile"], "visible": true}
                    ]
                  },
                  "record": {"everyTicks": 2, "includeSnapshots": true, "includeEvents": true},
                  "assertions": [],
                  "limits": {"maxEntities": 4, "maxTraceBytes": 65536}
                }
                """;

        NpcRuntimeRequest request = NpcRuntimeRequest.parse(json, config);

        assertEquals(1, request.version());
        assertEquals("protect_baby_close_target", request.requestId());
        assertEquals("Alec_Template_Boar_Family_Adult", request.assetId());
        assertEquals("Alec_Template_Boar_Family_Adult", request.roleId());
        assertEquals("protect-baby-close", request.scenario().id());
        assertEquals(200, request.ticks());
        assertEquals(7L, request.seed());
        assertEquals("default", request.world().arena());
        assertEquals(12000, request.environment().timeOfDay());
        assertEquals("clear", request.environment().weather());
        assertEquals(null, request.environment().pauseTime());
        assertTrue(request.environment().effectivePauseTime());
        assertEquals(3, request.fixtures().npc().position().size());
        assertEquals("Idle", request.fixtures().npc().state());
        assertEquals(1, request.fixtures().targets().size());
        assertEquals("Enemy", request.fixtures().targets().getFirst().slot());
        assertEquals(2, request.record().everyTicks());
        assertEquals(65536, request.limits().maxTraceBytes());
    }

    @Test
    void rejectsUnsafeOrMalformedRequests() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        assertThrows(IllegalArgumentException.class, () -> NpcRuntimeRequest.parse("{\"version\":2}", config));
        assertThrows(IllegalArgumentException.class, () -> NpcRuntimeRequest.parse(
                "{\"version\":1,\"requestId\":\"bad id\",\"assetId\":\"A\",\"roleId\":\"R\",\"ticks\":1}",
                config
        ));
        assertThrows(IllegalArgumentException.class, () -> NpcRuntimeRequest.parse(
                "{\"version\":1,\"requestId\":\"ok\",\"assetId\":\"A\",\"roleId\":\"R\",\"ticks\":99999}",
                config
        ));
    }

    @Test
    void rejectsNonDevWorldAndExcessiveEntityOrTraceLimits() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        assertThrows(NpcRuntimeRequest.ValidationException.class, () -> NpcRuntimeRequest.parse(
                "{\"version\":1,\"requestId\":\"wrong_world\",\"assetId\":\"A\",\"roleId\":\"R\",\"ticks\":1,"
                        + "\"world\":{\"instanceId\":\"default\"}}",
                config
        ));
        assertThrows(NpcRuntimeRequest.ValidationException.class, () -> NpcRuntimeRequest.parse(
                "{\"version\":1,\"requestId\":\"too_many_entities\",\"assetId\":\"A\",\"roleId\":\"R\",\"ticks\":1,"
                        + "\"limits\":{\"maxEntities\":999}}",
                config
        ));
        assertThrows(NpcRuntimeRequest.ValidationException.class, () -> NpcRuntimeRequest.parse(
                "{\"version\":1,\"requestId\":\"too_much_trace\",\"assetId\":\"A\",\"roleId\":\"R\",\"ticks\":1,"
                        + "\"limits\":{\"maxTraceBytes\":999999999}}",
                config
        ));
    }

    @Test
    void parsesSensorAssertionsForRuntimeEvaluation() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                "{\"version\":1,\"requestId\":\"assert_sensor\",\"assetId\":\"A\",\"roleId\":\"R\",\"ticks\":1,"
                        + "\"assertions\":[{\"kind\":\"sensor\",\"sensorType\":\"TargetSlot\",\"sensorId\":\"targetEnemy\","
                        + "\"expectedMatchResult\":\"matched\",\"expectUnsupported\":false}]}",
                config
        );

        assertEquals(1, request.assertions().size());
        assertEquals("TargetSlot", request.assertions().getFirst().fields().get("sensorType"));
    }

    @Test
    void parsesTimingRecordProfileAndAssertionWindows() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest request = NpcRuntimeRequest.parse("""
                {
                  "version": 1,
                  "requestId": "guard_long",
                  "assetId": "Territorial_Guard_Boar",
                  "roleId": "Territorial_Guard_Boar",
                  "ticks": 300,
                  "timing": {"warmupTicks": 60, "stopWhenAssertionsResolved": true},
                  "world": {"instanceId": "npc_runtime_test_flatworld"},
                  "fixtures": {"npc": {"position": [0, 64, 0]}},
                  "record": {"profile": "standard", "everyTicks": 3, "includeSnapshots": true, "includeEvents": false},
                  "assertions": [
                    {
                      "assertionId": "eventual-target",
                      "kind": "sensor",
                      "window": {"mode": "eventually", "startTick": 60, "endTick": 300}
                    }
                  ]
                }
                """, config);

        assertEquals(60, request.timing().warmupTicks());
        assertTrue(request.timing().stopWhenAssertionsResolved());
        assertEquals("standard", request.record().profile());
        assertEquals(3, request.record().everyTicks());
        assertFalse(request.record().includeEvents());
        assertEquals("eventually", request.assertions().getFirst().window().mode());
    }

    @Test
    void parsesRecordFixtureCorrelationFields() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest request = NpcRuntimeRequest.parse("""
                {
                  "version": 1,
                  "requestId": "record_fixture_ids",
                  "assetId": "A",
                  "roleId": "R",
                  "ticks": 120,
                  "fixtures": {
                    "list": [
                      {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "roleId": "R"},
                      {"fixtureId": "target.Enemy", "kind": "targetDummy", "roleId": "R", "targetSlot": "Enemy"}
                    ]
                  },
                  "record": {
                    "profile": "minimal",
                    "everyTicks": 5,
                    "includeSnapshots": true,
                    "includeEvents": false,
                    "fixtureScope": "allFixtures",
                    "fixtureIds": ["npcUnderTest", "target.Enemy"]
                  }
                }
                """, config);

        assertEquals("minimal", request.record().profile());
        assertEquals(5, request.record().everyTicks());
        assertEquals("allFixtures", request.record().fixtureScope());
        assertEquals("target.Enemy", request.record().fixtureIds().get(1));
        assertTrue(request.toJson().contains("\"fixtureScope\":\"allFixtures\""));
        assertTrue(request.toJson().contains("\"fixtureIds\":[\"npcUnderTest\",\"target.Enemy\"]"));
    }

    @Test
    void rejectsMalformedRecordFixtureCorrelationFields() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException scopeException = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        "{\"version\":1,\"requestId\":\"bad_scope\",\"assetId\":\"A\",\"roleId\":\"R\",\"ticks\":1,"
                                + "\"record\":{\"fixtureScope\":\"nearbyOnly\"}}",
                        config
                )
        );
        assertEquals("invalid-request", scopeException.classification());
        assertEquals("unsupported record fixtureScope nearbyOnly", scopeException.getMessage());

        NpcRuntimeRequest.ValidationException idsException = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        "{\"version\":1,\"requestId\":\"bad_fixture_ids\",\"assetId\":\"A\",\"roleId\":\"R\",\"ticks\":1,"
                                + "\"record\":{\"fixtureIds\":[\"npcUnderTest\",\"bad id\"]}}",
                        config
                )
        );
        assertEquals("invalid-request", idsException.classification());
        assertEquals("record.fixtureIds must be an array of safe fixture ids", idsException.getMessage());
    }
    @Test
    void parsesTameworkFixtureMutationsAndEngineHooks() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                """
                {
                  "version": 1,
                  "requestId": "tamework_mutation_contract",
                  "assetId": "AlecNpcTest:Boar",
                  "roleId": "Default",
                  "ticks": 20,
                  "fixtures": {
                    "list": [
                      {
                        "fixtureId": "npc_under_test",
                        "kind": "npcUnderTest",
                        "roleId": "Default",
                        "asset": "AlecNpcTest:Boar",
                        "tamework": {
                          "tamed": true,
                          "owner": {"type": "syntheticPlayer", "id": "owner_a"},
                          "needs": {"hunger": 80, "thirst": 60},
                          "effects": ["tamework:well_fed"],
                          "command": "follow",
                          "lifeStage": "adult"
                        }
                      }
                    ]
                  },
                  "engineHooks": {
                    "targetSelection": true,
                    "pathing": true,
                    "combatEligibility": true,
                    "instructionLifecycle": true
                  }
                }
                """,
                config
        );

        NpcRuntimeFixtureSpec.TameworkMutation tamework = request.fixtures().list().getFirst().tamework();
        assertEquals(true, tamework.tamed());
        assertEquals("syntheticPlayer", tamework.owner().get("type"));
        assertEquals("owner_a", tamework.owner().get("id"));
        assertEquals(80, tamework.needs().get("hunger"));
        assertEquals("tamework:well_fed", tamework.effects().getFirst());
        assertEquals("follow", tamework.commandState());
        assertEquals("adult", tamework.lifeStage());
        assertTrue(request.engineHooks().targetSelection());
        assertTrue(request.engineHooks().pathing());
        assertTrue(request.engineHooks().combatEligibility());
        assertTrue(request.engineHooks().instructionLifecycle());
        assertTrue(request.toJson().contains("\"commandState\":\"follow\""));
        assertTrue(request.toJson().contains("\"engineHooks\""));
    }

    @Test
    void rejectsUnknownEngineHooks() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        "{\"version\":1,\"requestId\":\"bad_hook\",\"assetId\":\"A\",\"roleId\":\"R\",\"ticks\":1,"
                                + "\"engineHooks\":{\"targetSelection\":true,\"weatherControl\":true}}",
                        config
                )
        );

        assertEquals("unsupported-request", exception.classification());
        assertEquals("engineHooks.weatherControl", exception.unsupported().getFirst().path());
    }

    @Test
    void rejectsUnknownFieldsInsideKnownSections() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        "{\"version\":1,\"requestId\":\"unknown_field\",\"assetId\":\"A\",\"roleId\":\"R\",\"ticks\":1,"
                                + "\"environment\":{\"difficulty\":\"hard\"}}",
                        config
                )
        );

        assertEquals("unsupported-request", exception.classification());
        assertEquals("environment.difficulty", exception.unsupported().getFirst().path());
        assertEquals("field is not supported by the current runtime contract", exception.unsupported().getFirst().reason());
    }

    @Test
    void rejectsPreseedTargetSlotsAsUnsafeHeadlessMutation() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        """
                        {
                          "version": 1,
                          "requestId": "preseed_target_slots",
                          "assetId": "A",
                          "roleId": "R",
                          "ticks": 120,
                          "preseedTargetSlots": [
                            {"slot": "Enemy", "fixtureId": "target.Enemy", "mode": "beforeRun"}
                          ],
                          "fixtures": {
                            "list": [
                              {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "roleId": "R"},
                              {"fixtureId": "target.Enemy", "kind": "targetDummy", "roleId": "R", "targetSlot": "Enemy"}
                            ]
                          }
                        }
                        """,
                        config
                )
        );

        assertEquals("unsupported-request", exception.classification());
        assertEquals("preseedTargetSlots", exception.unsupported().getFirst().path());
        assertEquals("direct target-slot preseeding is not implemented safely; use fixture-driven sensor induction", exception.unsupported().getFirst().reason());
    }

    @Test
    void parsesPauseTimeAndRejectsUnsafeWeatherIds() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                """
                {
                  "version": 1,
                  "requestId": "paused_weather",
                  "assetId": "A",
                  "roleId": "R",
                  "ticks": 20,
                  "environment": {
                    "timeOfDay": 6000,
                    "weather": "hytale:clear",
                    "pauseTime": true
                  }
                }
                """,
                config
        );

        assertEquals(6000, request.environment().timeOfDay());
        assertEquals("hytale:clear", request.environment().weather());
        assertEquals(true, request.environment().pauseTime());
        assertTrue(request.toJson().contains("\"pauseTime\":true"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        "{\"version\":1,\"requestId\":\"bad_weather\",\"assetId\":\"A\",\"roleId\":\"R\",\"ticks\":20,"
                                + "\"environment\":{\"weather\":\"bad weather!\"}}",
                        config
                )
        );
        assertEquals("invalid-request", exception.classification());
        assertEquals("environment.weather must be a safe asset id or keyword", exception.getMessage());
    }

    @Test
    void rejectsDuplicateFixtureIdsWithExactPath() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        """
                        {
                          "version": 1,
                          "requestId": "duplicate_fixture",
                          "assetId": "A",
                          "roleId": "R",
                          "ticks": 1,
                          "fixtures": {
                            "list": [
                              {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "roleId": "R"},
                              {"fixtureId": "target.Enemy", "kind": "targetDummy", "roleId": "R"},
                              {"fixtureId": "target.Enemy", "kind": "targetDummy", "roleId": "R"}
                            ]
                          }
                        }
                        """,
                        config
                )
        );

        assertEquals("unsupported-fixture", exception.classification());
        assertEquals("fixtures.list[2].fixtureId", exception.unsupported().getFirst().path());
        assertEquals("duplicate fixture id: target.Enemy", exception.unsupported().getFirst().reason());
    }

    @Test
    void rejectsMissingRoleIdOnExplicitEntityLikeFixtures() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        """
                        {
                          "version": 1,
                          "requestId": "missing_fixture_role",
                          "assetId": "A",
                          "roleId": "R",
                          "ticks": 1,
                          "fixtures": {
                            "list": [
                              {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "roleId": "R"},
                              {"fixtureId": "npc.helper", "kind": "npc"}
                            ]
                          }
                        }
                        """,
                        config
                )
        );

        assertEquals("unsupported-fixture", exception.classification());
        assertEquals("fixtures.list[1].roleId", exception.unsupported().getFirst().path());
        assertEquals("entity-like fixtures require a roleId", exception.unsupported().getFirst().reason());
    }

    @Test
    void rejectsUnsupportedFixtureFieldsWithIndexedPath() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        """
                        {
                          "version": 1,
                          "requestId": "unsupported_fixture_field",
                          "assetId": "A",
                          "roleId": "R",
                          "ticks": 1,
                          "fixtures": {
                            "list": [
                              {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "roleId": "R"},
                              {"fixtureId": "target.Enemy", "kind": "targetDummy", "roleId": "R", "brainOverride": "Aggressive"}
                            ]
                          }
                        }
                        """,
                        config
                )
        );

        assertEquals("unsupported-fixture", exception.classification());
        assertEquals("fixtures.list[1].brainOverride", exception.unsupported().getFirst().path());
        assertEquals("field is not supported by the fixture schema", exception.unsupported().getFirst().reason());
    }

    @Test
    void rejectsItemFixtureWorldMutationButAcceptsBlockFixtures() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        """
                        {
                          "version": 1,
                          "requestId": "unsupported_item",
                          "assetId": "A",
                          "roleId": "R",
                          "ticks": 1,
                          "fixtures": {
                            "list": [
                              {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "roleId": "R"},
                              {"fixtureId": "item.food", "kind": "item", "itemId": "hytale:apple"},
                              {"fixtureId": "block.wall", "kind": "block", "blockId": "hytale:stone"}
                            ]
                          }
                        }
                        """,
                        config
                )
        );

        assertEquals("unsupported-fixture", exception.classification());
        assertEquals(1, exception.unsupported().size());
        assertEquals("fixtures.list[1].kind", exception.unsupported().getFirst().path());
        assertEquals("item fixture spawning is not implemented; safe item spawn/drop API is unconfirmed", exception.unsupported().getFirst().reason());

        NpcRuntimeRequest blockRequest = NpcRuntimeRequest.parse(
                """
                {
                  "version": 1,
                  "requestId": "supported_block",
                  "assetId": "A",
                  "roleId": "R",
                  "ticks": 1,
                  "fixtures": {
                    "list": [
                      {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "roleId": "R"},
                      {"fixtureId": "block.wall", "kind": "block", "blockId": "hytale:stone"}
                    ]
                  }
                }
                """,
                config
        );
        assertEquals(NpcRuntimeFixtureKind.BLOCK, blockRequest.fixtures().list().get(1).kind());
    }

    @Test
    void rejectsMultipleNpcUnderTestFixtures() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        """
                        {
                          "version": 1,
                          "requestId": "two_npcs",
                          "assetId": "A",
                          "roleId": "R",
                          "ticks": 1,
                          "fixtures": {
                            "list": [
                              {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "roleId": "R"},
                              {"fixtureId": "npc_under_test", "kind": "npcUnderTest", "roleId": "R"}
                            ]
                          }
                        }
                        """,
                        config
                )
        );

        assertEquals("unsupported-fixture", exception.classification());
        assertEquals("fixtures.list", exception.unsupported().getFirst().path());
        assertEquals("exactly one npcUnderTest fixture is required", exception.unsupported().getFirst().reason());
    }

    @Test
    void rejectsMissingFixtureRelationshipTargets() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        """
                        {
                          "version": 1,
                          "requestId": "missing_leader",
                          "assetId": "A",
                          "roleId": "R",
                          "ticks": 1,
                          "fixtures": {
                            "list": [
                              {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "roleId": "R"},
                              {"fixtureId": "flock.child", "kind": "flockMember", "roleId": "R", "leaderFixtureId": "flock.missing"}
                            ]
                          }
                        }
                        """,
                        config
                )
        );

        assertEquals("unsupported-fixture", exception.classification());
        assertEquals("fixtures.list[1].leaderFixtureId", exception.unsupported().getFirst().path());
        assertEquals("referenced fixture id does not exist: flock.missing", exception.unsupported().getFirst().reason());
    }

    @Test
    void parsesMultiNpcContractAndPreservesDefaults() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest defaultRequest = NpcRuntimeRequest.parse(
                "{\"version\":1,\"requestId\":\"single_default\",\"assetId\":\"A\",\"roleId\":\"R\",\"ticks\":120}",
                config
        );
        NpcRuntimeRequest linkedRequest = NpcRuntimeRequest.parse(
                """
                {
                  "version": 1,
                  "requestId": "linked_family",
                  "assetId": "A",
                  "roleId": "R",
                  "ticks": 240,
                  "timing": {"warmupTicks": 30},
                  "multiNpc": {
                    "mode": "linked",
                    "deliveryWindowTicks": 120,
                    "maxFixtureCount": 6
                  },
                  "fixtures": {
                    "list": [
                      {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "roleId": "R"},
                      {"fixtureId": "family.child", "kind": "familyMember", "roleId": "R", "parentFixtureId": "npcUnderTest"}
                    ]
                  }
                }
                """,
                config
        );

        assertEquals("single", defaultRequest.multiNpc().mode());
        assertEquals(90, defaultRequest.multiNpc().deliveryWindowTicks());
        assertEquals(64, defaultRequest.multiNpc().maxFixtureCount());
        assertEquals("linked", linkedRequest.multiNpc().mode());
        assertEquals(120, linkedRequest.multiNpc().deliveryWindowTicks());
        assertEquals(6, linkedRequest.multiNpc().maxFixtureCount());
        assertTrue(linkedRequest.toJson().contains("\"multiNpc\":{\"mode\":\"linked\",\"deliveryWindowTicks\":120,\"maxFixtureCount\":6}"));
    }

    @Test
    void parsesRequestLevelMultiNpcContractAliases() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                """
                {
                  "version": 1,
                  "requestId": "root_level_multi_npc",
                  "assetId": "A",
                  "roleId": "R",
                  "ticks": 180,
                  "timing": {"warmupTicks": 30},
                  "multiNpcMode": "linked",
                  "deliveryWindowTicks": 120,
                  "maxFixtureCount": 6,
                  "fixtures": {
                    "list": [
                      {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "roleId": "R"},
                      {"fixtureId": "message.threat", "kind": "message", "senderFixtureId": "npcUnderTest", "receiverFixtureId": "npcUnderTest"}
                    ]
                  }
                }
                """,
                config
        );

        assertEquals("linked", request.multiNpc().mode());
        assertEquals(120, request.multiNpc().deliveryWindowTicks());
        assertEquals(6, request.multiNpc().maxFixtureCount());
        assertTrue(request.toJson().contains("\"multiNpcMode\":\"linked\""));
        assertTrue(request.toJson().contains("\"deliveryWindowTicks\":120"));
        assertTrue(request.toJson().contains("\"maxFixtureCount\":6"));
    }

    @Test
    void rejectsConflictingRequestLevelAndNestedMultiNpcContract() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        """
                        {
                          "version": 1,
                          "requestId": "conflicting_multi_npc",
                          "assetId": "A",
                          "roleId": "R",
                          "ticks": 180,
                          "multiNpcMode": "linked",
                          "multiNpc": {"mode": "single"}
                        }
                        """,
                        config
                )
        );

        assertEquals("invalid-request", exception.classification());
        assertEquals("multiNpcMode conflicts with multiNpc.mode", exception.getMessage());
    }

    @Test
    void rejectsMultiNpcDeliveryWindowLongerThanScenarioWindow() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        """
                        {
                          "version": 1,
                          "requestId": "too_short_for_delivery",
                          "assetId": "A",
                          "roleId": "R",
                          "ticks": 80,
                          "timing": {"warmupTicks": 30},
                          "multiNpc": {"mode": "linked", "deliveryWindowTicks": 90}
                        }
                        """,
                        config
                )
        );

        assertEquals("invalid-request", exception.classification());
        assertEquals("multiNpc.deliveryWindowTicks requires ticks to be at least warmupTicks + deliveryWindowTicks", exception.getMessage());
    }

    @Test
    void rejectsSwarmFixtureCountOverMultiNpcGuard() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        """
                        {
                          "version": 1,
                          "requestId": "swarm_too_large",
                          "assetId": "A",
                          "roleId": "R",
                          "ticks": 120,
                          "multiNpc": {"mode": "swarm", "maxFixtureCount": 2},
                          "fixtures": {
                            "list": [
                              {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "roleId": "R"},
                              {"fixtureId": "flock.one", "kind": "flockMember", "roleId": "R", "leaderFixtureId": "npcUnderTest"},
                              {"fixtureId": "flock.two", "kind": "flockMember", "roleId": "R", "leaderFixtureId": "npcUnderTest"}
                            ]
                          }
                        }
                        """,
                        config
                )
        );

        assertEquals("invalid-request", exception.classification());
        assertEquals("multiNpc.maxFixtureCount exceeded by fixture list", exception.getMessage());
    }

    @Test
    void rejectsRequiresPlayerInHeadlessRuntimeMode() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest.ValidationException exception = assertThrows(
                NpcRuntimeRequest.ValidationException.class,
                () -> NpcRuntimeRequest.parse(
                        """
                        {
                          "version": 1,
                          "requestId": "logged_in_player_required",
                          "assetId": "A",
                          "roleId": "R",
                          "ticks": 120,
                          "requiresPlayer": true
                        }
                        """,
                        config
                )
        );

        assertEquals("unsupported-request", exception.classification());
        assertEquals("requiresPlayer", exception.unsupported().getFirst().path());
        assertEquals("logged-in player runtime mode is not implemented for headless batch runs", exception.unsupported().getFirst().reason());
    }

    @Test
    void parsesRequiresPlayerFalseAsHeadlessCompatible() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                """
                {
                  "version": 1,
                  "requestId": "headless_anchor_compatible",
                  "assetId": "A",
                  "roleId": "R",
                  "ticks": 120,
                  "requiresPlayer": false
                }
                """,
                config
        );

        assertFalse(request.requiresPlayer());
        assertFalse(request.toJson().contains("requiresPlayer"));
    }

    @Test
    void serializesDeterministicJsonForCliRoundTrip() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));
        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                "{\"version\":1,\"requestId\":\"simple\",\"assetId\":\"Asset\",\"roleId\":\"Role\",\"ticks\":5}",
                config
        );

        String json = request.toJson();

        assertTrue(json.contains("\"requestId\":\"simple\""));
        assertTrue(json.contains("\"ticks\":5"));
        assertTrue(json.contains("\"npc\":{\"position\":[0,64,0]}"));
        assertTrue(json.contains("\"scenario\":{\"id\":\"simple\"}"));
        assertTrue(json.contains("\"environment\":{}"));
        assertTrue(json.contains("\"multiNpc\":{\"mode\":\"single\",\"deliveryWindowTicks\":90,\"maxFixtureCount\":64}"));
        assertTrue(json.contains("\"assertions\":[]"));
        assertTrue(json.contains("\"limits\":{\"maxEntities\":64,\"maxTraceBytes\":8388608}"));
    }

    @Test
    void preservesSimpleRequestCompatibility() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                "{\"version\":1,\"requestId\":\"old_shape\",\"assetId\":\"Asset\",\"roleId\":\"Role\",\"ticks\":5}",
                config
        );

        Map<String, Object> map = request.toMap();
        assertEquals("old_shape", request.scenario().id());
        assertEquals(Map.of(), map.get("environment"));
        assertEquals(0, request.assertions().size());
    }
}
