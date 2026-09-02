package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeHarnessServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void processesOneQueuedRequestIntoResultTraceAndArchive() throws Exception {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(tempDir);
        NpcRuntimeHarnessService service = new NpcRuntimeHarnessService(config, request -> NpcRuntimeResult.passed(
                request,
                0,
                config.paths().traces().resolve(request.requestId() + ".trace.jsonl"),
                NpcRuntimeResult.Summary.empty().withState("Idle")
        ));
        service.initializeDirectories();
        Files.writeString(
                config.paths().requests().resolve("simple.request.json"),
                "{\"version\":1,\"requestId\":\"simple\",\"assetId\":\"Asset\",\"roleId\":\"Role\",\"ticks\":5}"
        );

        NpcRuntimeHarnessService.ProcessOutcome outcome = service.processNextQueuedRequest();

        assertTrue(outcome.processed());
        assertEquals("simple", outcome.requestId());
        Path resultPath = config.paths().results().resolve("simple.result.json");
        assertTrue(Files.exists(resultPath));
        assertTrue(Files.exists(config.paths().traces().resolve("simple.trace.jsonl")));
        assertTrue(Files.exists(config.paths().archive().resolve("simple.request.json")));
        assertFalse(Files.exists(config.paths().active().resolve("simple.request.json")));

        Map<String, Object> result = NpcRuntimeJson.parseObject(Files.readString(resultPath));
        assertEquals("passed", result.get("status"));
        assertEquals("passed", result.get("classification"));
        assertEquals(5, ((Number) result.get("ticksRequested")).intValue());
        assertEquals(0, ((Number) result.get("ticksRun")).intValue());
        assertTrue(result.containsKey("cleanup"));
        assertTrue(result.containsKey("artifacts"));
        assertTrue(result.containsKey("summary"));

        Map<String, Object> status = NpcRuntimeJson.parseObject(Files.readString(config.paths().statusFile()));
        assertEquals("simple", ((Map<?, ?>) status.get("lastResult")).get("requestId"));
        assertEquals("passed", ((Map<?, ?>) status.get("lastResult")).get("status"));
        assertEquals("passed", ((Map<?, ?>) status.get("lastResult")).get("classification"));
        assertEquals(0, ((Number) status.get("queuedCount")).intValue());
    }

    @Test
    void malformedRequestWritesFailedResultAndArchivesInput() throws Exception {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(tempDir);
        NpcRuntimeHarnessService service = new NpcRuntimeHarnessService(config);
        service.initializeDirectories();
        Files.writeString(config.paths().requests().resolve("bad.request.json"), "{\"version\":1");

        NpcRuntimeHarnessService.ProcessOutcome outcome = service.processNextQueuedRequest();

        assertTrue(outcome.processed());
        assertEquals("bad", outcome.requestId());
        Map<String, Object> result = NpcRuntimeJson.parseObject(Files.readString(config.paths().results().resolve("bad.result.json")));
        assertEquals("failed", result.get("status"));
        assertEquals("invalid-request", result.get("classification"));
        assertEquals("bad", result.get("requestId"));
        assertEquals(0, ((Number) result.get("ticksRequested")).intValue());
        assertEquals(0, ((Number) result.get("ticksRun")).intValue());
        assertTrue(result.get("error").toString().contains("Expected"));
        assertTrue(Files.exists(config.paths().archive().resolve("bad.request.json")));
    }

    @Test
    void sensorAssertionRequestReachesRunner() throws Exception {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(tempDir);
        NpcRuntimeHarnessService service = new NpcRuntimeHarnessService(config, request -> {
            assertEquals(1, request.assertions().size());
            return NpcRuntimeResult.passed(
                    request,
                    0,
                    config.paths().traces().resolve(request.requestId() + ".trace.jsonl"),
                    NpcRuntimeResult.Summary.empty()
            );
        });
        service.initializeDirectories();
        Files.writeString(
                config.paths().requests().resolve("assert_sensor.request.json"),
                "{\"version\":1,\"requestId\":\"assert_sensor\",\"assetId\":\"A\",\"roleId\":\"R\",\"ticks\":1,"
                        + "\"assertions\":[{\"kind\":\"sensor\",\"sensorType\":\"TargetSlot\",\"expectedMatchResult\":\"matched\"}]}"
        );

        NpcRuntimeHarnessService.ProcessOutcome outcome = service.processNextQueuedRequest();

        assertTrue(outcome.processed());
        Map<String, Object> result = NpcRuntimeJson.parseObject(Files.readString(config.paths().results().resolve("assert_sensor.result.json")));
        assertEquals("passed", result.get("status"));
        assertEquals("passed", result.get("classification"));
        assertTrue(Files.exists(config.paths().archive().resolve("assert_sensor.request.json")));
    }

    @Test
    void unsupportedFixtureWritesUnsupportedFixtureDetails() throws Exception {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(tempDir);
        NpcRuntimeHarnessService service = new NpcRuntimeHarnessService(config, request -> {
            throw new AssertionError("runner should not execute for unsupported fixture requests");
        });
        service.initializeDirectories();
        Files.writeString(
                config.paths().requests().resolve("unsupported_fixture.request.json"),
                """
                        {
                          "version": 1,
                          "requestId": "unsupported_fixture",
                          "assetId": "A",
                          "roleId": "R",
                          "ticks": 1,
                          "fixtures": {
                            "list": [
                              {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [0, 64, 0], "roleId": "Role"},
                              {"fixtureId": "entity.food", "kind": "entity", "position": [1, 64, 0], "entityId": "Apple"}
                            ]
                          }
                        }
                        """
        );

        NpcRuntimeHarnessService.ProcessOutcome outcome = service.processNextQueuedRequest();

        assertTrue(outcome.processed());
        Map<String, Object> result = NpcRuntimeJson.parseObject(Files.readString(config.paths().results().resolve("unsupported_fixture.result.json")));
        assertEquals("failed", result.get("status"));
        assertEquals("unsupported-fixture", result.get("classification"));
        assertTrue(result.get("unsupported").toString().contains("fixtures.list[1].kind"));
        assertTrue(Files.exists(config.paths().archive().resolve("unsupported_fixture.request.json")));
    }

    @Test
    void linkedChildFixtureSpawnFailureKeepsFixtureAndRoleContext() throws Exception {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(tempDir);
        NpcRuntimeHarnessService service = new NpcRuntimeHarnessService(config, request -> {
            NpcRuntimeFixtureSpec child = request.fixtures().list().stream()
                    .filter(fixture -> "family.child".equals(fixture.fixtureId()))
                    .findFirst()
                    .orElseThrow();
            return NpcRuntimeResult.fixtureSpawnFailed(
                    request,
                    0,
                    config.paths().traces().resolve(request.requestId() + ".trace.jsonl"),
                    NpcRuntimeFixtureSpawnResult.failed(child, "Unknown NPC role: MissingChildRole"),
                    NpcRuntimeCleanupReport.builder().build()
            );
        });
        service.initializeDirectories();
        Files.writeString(
                config.paths().requests().resolve("missing_child_role.request.json"),
                """
                        {
                          "version": 1,
                          "requestId": "missing_child_role",
                          "assetId": "AdultRole",
                          "roleId": "AdultRole",
                          "ticks": 120,
                          "fixtures": {
                            "list": [
                              {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "position": [0, 64, 0], "roleId": "AdultRole"},
                              {"fixtureId": "family.child", "kind": "familyMember", "position": [-2, 64, 0], "roleId": "MissingChildRole", "familyId": "generated.family", "familyRole": "child", "parentFixtureId": "npcUnderTest"}
                            ]
                          }
                        }
                        """
        );

        NpcRuntimeHarnessService.ProcessOutcome outcome = service.processNextQueuedRequest();

        assertTrue(outcome.processed());
        Map<String, Object> result = NpcRuntimeJson.parseObject(Files.readString(config.paths().results().resolve("missing_child_role.result.json")));
        assertEquals("failed", result.get("status"));
        assertEquals("fixture-spawn-failed", result.get("classification"));
        assertTrue(result.get("error").toString().contains("fixtureId=family.child"));
        assertTrue(result.get("error").toString().contains("roleId=MissingChildRole"));
        assertTrue(result.get("summary").toString().contains("family.child"));
        assertTrue(Files.exists(config.paths().archive().resolve("missing_child_role.request.json")));
    }

    @Test
    void runnerTimeoutWritesHarnessTimeoutResult() throws Exception {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(tempDir);
        NpcRuntimeHarnessService service = new NpcRuntimeHarnessService(config, request -> {
            throw new TimeoutException("world thread timed out");
        });
        service.initializeDirectories();
        Files.writeString(
                config.paths().requests().resolve("timeout.request.json"),
                "{\"version\":1,\"requestId\":\"timeout\",\"assetId\":\"Asset\",\"roleId\":\"Role\",\"ticks\":5}"
        );

        NpcRuntimeHarnessService.ProcessOutcome outcome = service.processNextQueuedRequest();

        assertTrue(outcome.processed());
        Map<String, Object> result = NpcRuntimeJson.parseObject(Files.readString(config.paths().results().resolve("timeout.result.json")));
        assertEquals("failed", result.get("status"));
        assertEquals("harness-timeout", result.get("classification"));
        assertEquals(5, ((Number) result.get("ticksRequested")).intValue());
        assertTrue(result.get("error").toString().contains("world thread timed out"));
        assertTrue(result.get("cleanup").toString().contains("not started"));
        assertTrue(Files.exists(config.paths().archive().resolve("timeout.request.json")));
    }

    @Test
    void statusIncludesRootAndLastResult() throws Exception {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(tempDir);
        NpcRuntimeHarnessService service = new NpcRuntimeHarnessService(config, request -> NpcRuntimeResult.passed(
                request,
                0,
                config.paths().traces().resolve(request.requestId() + ".trace.jsonl"),
                NpcRuntimeResult.Summary.empty()
        ));
        service.initializeDirectories();
        Files.writeString(
                config.paths().requests().resolve("simple.request.json"),
                "{\"version\":1,\"requestId\":\"simple\",\"assetId\":\"Asset\",\"roleId\":\"Role\",\"ticks\":5}"
        );
        service.processNextQueuedRequest();

        String status = service.statusText();

        assertTrue(status.contains("enabled=false"));
        assertTrue(status.contains("active=<none>"));
        assertTrue(status.contains("queued=0"));
        assertTrue(status.contains("last=simple:passed"));
        assertTrue(status.contains("root=" + config.paths().root()));
        assertTrue(status.contains("statusFile=" + config.paths().statusFile()));
        assertTrue(status.contains("statusWrite=ok"));
    }

    @Test
    void startWritesStatusHeartbeatWithoutPlayerCommand() throws Exception {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(tempDir);
        NpcRuntimeHarnessService service = new NpcRuntimeHarnessService(
                config,
                request -> NpcRuntimeResult.passed(
                        request,
                        0,
                        config.paths().traces().resolve(request.requestId() + ".trace.jsonl"),
                        NpcRuntimeResult.Summary.empty()
                ),
                new NpcRuntimeHarnessStatusWriter(config.paths()),
                () -> NpcRuntimeWorldReadiness.notReady(
                        config.defaultWorldId(),
                        NpcRuntimeWorldReadiness.WORLD_NOT_LOADED,
                        "World readiness test fixture"
                ),
                () -> NpcRuntimeWorldReadiness.notReady(
                        config.defaultWorldId(),
                        NpcRuntimeWorldReadiness.WORLD_NOT_LOADED,
                        "World readiness test fixture"
                )
        );

        service.start();
        try {
            assertTrue(Files.exists(config.paths().statusFile()));
            Map<String, Object> status = NpcRuntimeJson.parseObject(Files.readString(config.paths().statusFile()));
            assertEquals(false, status.get("harnessEnabled"));
            assertEquals(false, status.get("worldReady"));
            assertEquals("npc_runtime_test_flatworld", status.get("worldId"));
            assertEquals("world-not-loaded", status.get("worldReadyReason"));
            assertEquals(0, ((Number) status.get("queuedCount")).intValue());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void autoEnabledServiceProcessesQueuedRequestFromBackgroundPoller() throws Exception {
        NpcRuntimeHarnessConfig config = new NpcRuntimeHarnessConfig(
                false,
                true,
                1200,
                64,
                1_048_576L,
                "npc_runtime_test_flatworld",
                25,
                25,
                5000,
                "npc_runtime_test_flatworld",
                NpcRuntimePaths.underUserData(tempDir)
        );
        NpcRuntimeHarnessService service = new NpcRuntimeHarnessService(config, request -> NpcRuntimeResult.passed(
                request,
                1,
                config.paths().traces().resolve(request.requestId() + ".trace.jsonl"),
                NpcRuntimeResult.Summary.empty()
        ),
                new NpcRuntimeHarnessStatusWriter(config.paths()),
                () -> NpcRuntimeWorldReadiness.ready(config.defaultWorldId(), 0),
                () -> NpcRuntimeWorldReadiness.ready(config.defaultWorldId(), 0));
        service.initializeDirectories();
        Files.writeString(
                config.paths().requests().resolve("auto.request.json"),
                "{\"version\":1,\"requestId\":\"auto\",\"assetId\":\"Asset\",\"roleId\":\"Role\",\"ticks\":5}"
        );

        service.start();
        try {
            long deadline = System.currentTimeMillis() + 3000;
            while (!Files.exists(config.paths().results().resolve("auto.result.json")) && System.currentTimeMillis() < deadline) {
                Thread.sleep(25);
            }

            assertTrue(Files.exists(config.paths().results().resolve("auto.result.json")));
            Map<String, Object> status = NpcRuntimeJson.parseObject(Files.readString(config.paths().statusFile()));
            assertEquals(true, status.get("harnessEnabled"));
            assertEquals(true, status.get("worldReady"));
            assertEquals("auto", ((Map<?, ?>) status.get("lastResult")).get("requestId"));
        } finally {
            service.shutdown();
        }
    }

    @Test
    void enabledServiceFailsQueuedRequestWhenWorldCannotBecomeReady() throws Exception {
        NpcRuntimeHarnessConfig config = new NpcRuntimeHarnessConfig(
                false,
                true,
                1200,
                64,
                1_048_576L,
                "npc_runtime_test_flatworld",
                25,
                25,
                5000,
                "npc_runtime_test_flatworld",
                NpcRuntimePaths.underUserData(tempDir)
        );
        NpcRuntimeWorldReadiness notTicking = NpcRuntimeWorldReadiness.fromWorld(
                config.defaultWorldId(),
                config.defaultWorldId(),
                false,
                false,
                0
        );
        NpcRuntimeHarnessService service = new NpcRuntimeHarnessService(
                config,
                request -> {
                    throw new AssertionError("runner should not execute while world is not ready");
                },
                new NpcRuntimeHarnessStatusWriter(config.paths()),
                () -> notTicking,
                () -> notTicking
        );
        service.initializeDirectories();
        Files.writeString(
                config.paths().requests().resolve("world_bad.request.json"),
                "{\"version\":1,\"requestId\":\"world_bad\",\"assetId\":\"Asset\",\"roleId\":\"Role\",\"ticks\":5}"
        );

        NpcRuntimeHarnessService.ProcessOutcome outcome = service.processNextQueuedRequest();

        assertTrue(outcome.processed());
        assertEquals("world_bad", outcome.requestId());
        Map<String, Object> result = NpcRuntimeJson.parseObject(Files.readString(config.paths().results().resolve("world_bad.result.json")));
        assertEquals("failed", result.get("status"));
        assertEquals("harness-world-not-ready", result.get("classification"));
        assertTrue(result.get("error").toString().contains("world-not-ticking"));
        Map<String, Object> status = NpcRuntimeJson.parseObject(Files.readString(config.paths().statusFile()));
        assertEquals(false, status.get("worldReady"));
        assertEquals("world-not-ticking", status.get("worldReadyReason"));
        assertTrue(Files.exists(config.paths().archive().resolve("world_bad.request.json")));
    }

    @Test
    void cancelWritesCanceledResultAndArchivesQueuedRequest() throws Exception {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(tempDir);
        NpcRuntimeHarnessService service = new NpcRuntimeHarnessService(config);
        service.initializeDirectories();
        Files.writeString(
                config.paths().requests().resolve("cancel_me.request.json"),
                "{\"version\":1,\"requestId\":\"cancel_me\",\"assetId\":\"Asset\",\"roleId\":\"Role\",\"ticks\":5}"
        );

        NpcRuntimeHarnessService.CancelOutcome outcome = service.cancel();

        assertTrue(outcome.canceled());
        assertEquals("cancel_me", outcome.requestId());
        Map<String, Object> result = NpcRuntimeJson.parseObject(Files.readString(config.paths().results().resolve("cancel_me.result.json")));
        assertEquals("canceled", result.get("status"));
        assertEquals("user-canceled", result.get("classification"));
        assertTrue(result.get("cleanup").toString().contains("not started"));
        assertTrue(Files.exists(config.paths().archive().resolve("cancel_me.request.json")));
        assertFalse(Files.exists(config.paths().requests().resolve("cancel_me.request.json")));
    }

    @Test
    void initializeDirectoriesRecoversStaleActiveRequest() throws Exception {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(tempDir);
        Files.createDirectories(config.paths().active());
        Files.writeString(
                config.paths().active().resolve("stale.request.json"),
                "{\"version\":1,\"requestId\":\"stale\",\"assetId\":\"Asset\",\"roleId\":\"Role\",\"ticks\":7}"
        );
        NpcRuntimeHarnessService service = new NpcRuntimeHarnessService(config);

        service.initializeDirectories();

        Map<String, Object> result = NpcRuntimeJson.parseObject(Files.readString(config.paths().results().resolve("stale.result.json")));
        assertEquals("failed", result.get("status"));
        assertEquals("harness-recovered-stale-active-request", result.get("classification"));
        assertEquals(7, ((Number) result.get("ticksRequested")).intValue());
        assertTrue(result.get("cleanup").toString().contains("archived stale active request during startup"));
        assertTrue(Files.exists(config.paths().archive().resolve("stale.request.json")));
        assertFalse(Files.exists(config.paths().active().resolve("stale.request.json")));

        Map<String, Object> status = NpcRuntimeJson.parseObject(Files.readString(config.paths().statusFile()));
        assertTrue(status.get("lastRecovery").toString().contains("stale"));
        assertEquals("stale", ((Map<?, ?>) status.get("lastResult")).get("requestId"));
    }

    @Test
    void shutdownRecoversLeftoverActiveRequest() throws Exception {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(tempDir);
        NpcRuntimeHarnessService service = new NpcRuntimeHarnessService(config);
        service.initializeDirectories();
        Files.writeString(
                config.paths().active().resolve("shutdown_stale.request.json"),
                "{\"version\":1,\"requestId\":\"shutdown_stale\",\"assetId\":\"Asset\",\"roleId\":\"Role\",\"ticks\":3}"
        );

        service.shutdown();

        Map<String, Object> result = NpcRuntimeJson.parseObject(Files.readString(config.paths().results().resolve("shutdown_stale.result.json")));
        assertEquals("harness-recovered-stale-active-request", result.get("classification"));
        assertTrue(result.get("cleanup").toString().contains("archived stale active request during shutdown"));
        assertTrue(Files.exists(config.paths().archive().resolve("shutdown_stale.request.json")));
        assertFalse(Files.exists(config.paths().active().resolve("shutdown_stale.request.json")));
    }
}
