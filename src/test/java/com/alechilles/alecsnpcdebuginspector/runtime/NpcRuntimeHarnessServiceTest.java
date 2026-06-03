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
    void unsupportedRequestWritesUnsupportedDetails() throws Exception {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(tempDir);
        NpcRuntimeHarnessService service = new NpcRuntimeHarnessService(config);
        service.initializeDirectories();
        Files.writeString(
                config.paths().requests().resolve("unsupported.request.json"),
                "{\"version\":1,\"requestId\":\"unsupported\",\"assetId\":\"A\",\"roleId\":\"R\",\"ticks\":1,"
                        + "\"assertions\":[{\"kind\":\"sensor\"}]}"
        );

        NpcRuntimeHarnessService.ProcessOutcome outcome = service.processNextQueuedRequest();

        assertTrue(outcome.processed());
        Map<String, Object> result = NpcRuntimeJson.parseObject(Files.readString(config.paths().results().resolve("unsupported.result.json")));
        assertEquals("failed", result.get("status"));
        assertEquals("unsupported-request", result.get("classification"));
        assertTrue(result.get("unsupported").toString().contains("assertions[0]"));
        assertTrue(Files.exists(config.paths().archive().resolve("unsupported.request.json")));
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
        assertTrue(Files.exists(config.paths().archive().resolve("cancel_me.request.json")));
        assertFalse(Files.exists(config.paths().requests().resolve("cancel_me.request.json")));
    }
}
