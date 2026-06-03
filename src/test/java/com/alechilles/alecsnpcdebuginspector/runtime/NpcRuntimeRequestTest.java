package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                  "assetId": "Alec_Template_Boar_Family_Adult",
                  "roleId": "Alec_Template_Boar_Family_Adult",
                  "ticks": 200,
                  "seed": 7,
                  "world": {"instanceId": "npc_runtime_test_flatworld", "arena": "default"},
                  "fixtures": {
                    "targets": [
                      {"slot": "Enemy", "kind": "dummy", "position": [4, 64, 0], "tags": ["hostile"], "visible": true}
                    ]
                  },
                  "record": {"everyTicks": 2, "includeSnapshots": true, "includeEvents": true}
                }
                """;

        NpcRuntimeRequest request = NpcRuntimeRequest.parse(json, config);

        assertEquals(1, request.version());
        assertEquals("protect_baby_close_target", request.requestId());
        assertEquals("Alec_Template_Boar_Family_Adult", request.assetId());
        assertEquals("Alec_Template_Boar_Family_Adult", request.roleId());
        assertEquals(200, request.ticks());
        assertEquals(7L, request.seed());
        assertEquals("default", request.world().arena());
        assertEquals(1, request.fixtures().targets().size());
        assertEquals("Enemy", request.fixtures().targets().getFirst().slot());
        assertEquals(2, request.record().everyTicks());
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
    void serializesDeterministicJsonForCliRoundTrip() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));
        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                "{\"version\":1,\"requestId\":\"simple\",\"assetId\":\"Asset\",\"roleId\":\"Role\",\"ticks\":5}",
                config
        );

        String json = request.toJson();

        assertTrue(json.contains("\"requestId\":\"simple\""));
        assertTrue(json.contains("\"ticks\":5"));
    }
}
