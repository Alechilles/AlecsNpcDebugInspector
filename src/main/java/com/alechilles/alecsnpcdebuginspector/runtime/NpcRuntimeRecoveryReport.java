package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Machine-readable recovery evidence for interrupted active runtime requests.
 */
public record NpcRuntimeRecoveryReport(
        int version,
        @Nonnull String trigger,
        @Nonnull Instant recoveredAt,
        @Nonnull List<RecoveredRequest> recoveredRequests
) {
    @Nonnull
    static NpcRuntimeRecoveryReport of(@Nonnull String trigger, @Nonnull List<RecoveredRequest> recoveredRequests) {
        return new NpcRuntimeRecoveryReport(1, trigger, Instant.now(), List.copyOf(recoveredRequests));
    }

    @Nonnull
    public Map<String, Object> toMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("version", version);
        map.put("trigger", trigger);
        map.put("recoveredAt", recoveredAt.toString());
        map.put("recoveredCount", recoveredRequests.size());
        map.put("recoveredRequests", recoveredRequests.stream().map(RecoveredRequest::toMap).toList());
        return map;
    }

    @Nonnull
    public String toJson() {
        return NpcRuntimeJson.stringify(toMap());
    }

    public record RecoveredRequest(
            @Nonnull String requestId,
            @Nonnull String classification,
            @Nonnull String activePath,
            @Nonnull String resultPath,
            @Nonnull String archivePath,
            @Nonnull String message
    ) {
        @Nonnull
        static RecoveredRequest of(@Nonnull String requestId,
                                   @Nonnull String classification,
                                   @Nonnull Path activePath,
                                   @Nonnull Path resultPath,
                                   @Nonnull Path archivePath,
                                   @Nonnull String message) {
            return new RecoveredRequest(
                    requestId,
                    classification,
                    activePath.toString(),
                    resultPath.toString(),
                    archivePath.toString(),
                    message
            );
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("requestId", requestId);
            map.put("classification", classification);
            map.put("activePath", activePath);
            map.put("resultPath", resultPath);
            map.put("archivePath", archivePath);
            map.put("message", message);
            return map;
        }
    }
}
