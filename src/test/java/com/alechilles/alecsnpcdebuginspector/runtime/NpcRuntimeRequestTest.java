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
