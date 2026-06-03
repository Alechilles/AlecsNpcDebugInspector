package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
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
        assertTrue(Files.exists(config.paths().results().resolve("simple.result.json")));
        assertTrue(Files.exists(config.paths().traces().resolve("simple.trace.jsonl")));
        assertTrue(Files.exists(config.paths().archive().resolve("simple.request.json")));
        assertFalse(Files.exists(config.paths().active().resolve("simple.request.json")));
    }

    @Test
    void malformedRequestWritesFailedResultAndArchivesInput() throws Exception {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(tempDir);
        NpcRuntimeHarnessService service = new NpcRuntimeHarnessService(config);
        service.initializeDirectories();
        Files.writeString(config.paths().requests().resolve("bad.request.json"), "{\"version\":1}");

        NpcRuntimeHarnessService.ProcessOutcome outcome = service.processNextQueuedRequest();

        assertTrue(outcome.processed());
        assertEquals("bad", outcome.requestId());
        String result = Files.readString(config.paths().results().resolve("bad.result.json"));
        assertTrue(result.contains("\"status\":\"failed\""));
        assertTrue(result.contains("requestId is required"));
        assertTrue(Files.exists(config.paths().archive().resolve("bad.request.json")));
    }
}
