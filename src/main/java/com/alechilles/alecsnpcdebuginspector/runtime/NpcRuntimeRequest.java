package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Versioned request contract consumed by the runtime harness.
 */
public record NpcRuntimeRequest(
        int version,
        @Nonnull String requestId,
        @Nonnull ScenarioSpec scenario,
        @Nonnull String assetId,
        @Nonnull String roleId,
        int ticks,
        @Nullable Long seed,
        @Nonnull WorldSpec world,
        @Nonnull EnvironmentSpec environment,
        @Nonnull Fixtures fixtures,
        @Nonnull RecordSpec record,
        @Nonnull List<AssertionSpec> assertions,
        @Nonnull LimitsSpec limits
) {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_.-]+");

    @Nonnull
    public static NpcRuntimeRequest parse(@Nonnull String json, @Nonnull NpcRuntimeHarnessConfig config) {
        return fromMap(NpcRuntimeJson.parseObject(json), config);
    }

    @Nonnull
    public static NpcRuntimeRequest fromMap(@Nonnull Map<String, Object> data, @Nonnull NpcRuntimeHarnessConfig config) {
        String requestId = stringOrNull(data.get("requestId"));
        rejectUnsupportedKeys(
                data,
                "",
                List.of("version", "requestId", "scenario", "assetId", "roleId", "ticks", "seed", "world",
                        "environment", "fixtures", "record", "assertions", "limits"),
                requestId
        );
        int version = intValue(data.get("version"), 1, requestId);
        if (version != 1) {
            throw invalid(requestId, "unsupported request version " + version);
        }

        requestId = requiredString(data, "requestId", requestId);
        if (!SAFE_ID.matcher(requestId).matches()) {
            throw invalid(requestId, "requestId contains unsupported characters");
        }

        String assetId = requiredString(data, "assetId", requestId);
        String roleId = requiredString(data, "roleId", requestId);
        int ticks = clampTicks(config, intValue(data.get("ticks"), 1, requestId), requestId);
        Long seed = longOrNull(data.get("seed"), requestId);
        ScenarioSpec scenario = ScenarioSpec.from(asMap(data.get("scenario"), requestId), requestId);
        WorldSpec world = WorldSpec.from(asMap(data.get("world"), requestId), config, requestId);
        EnvironmentSpec environment = EnvironmentSpec.from(asMap(data.get("environment"), requestId), requestId);
        Fixtures fixtures = Fixtures.from(asMap(data.get("fixtures"), requestId), requestId);
        LimitsSpec limits = LimitsSpec.from(asMap(data.get("limits"), requestId), config, requestId);
        validateEntityCount(config, fixtures.entityCount(), limits.maxEntities(), requestId);
        RecordSpec record = RecordSpec.from(asMap(data.get("record"), requestId), requestId);
        List<AssertionSpec> assertions = assertions(data.get("assertions"), requestId);
        if (!assertions.isEmpty()) {
            List<UnsupportedField> unsupported = new ArrayList<>();
            for (int i = 0; i < assertions.size(); i++) {
                unsupported.add(new UnsupportedField(
                        "assertions[" + i + "]",
                        "assertions are not supported by the current runtime contract"
                ));
            }
            throw unsupported(requestId, "request contains unsupported assertion sections", unsupported);
        }

        return new NpcRuntimeRequest(
                version,
                requestId,
                scenario,
                assetId,
                roleId,
                ticks,
                seed,
                world,
                environment,
                fixtures,
                record,
                assertions,
                limits
        );
    }

    @Nonnull
    public String toJson() {
        return NpcRuntimeJson.stringify(toMap());
    }

    @Nonnull
    public Map<String, Object> toMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("version", version);
        map.put("requestId", requestId);
        map.put("scenario", scenario.toMap());
        map.put("assetId", assetId);
        map.put("roleId", roleId);
        map.put("ticks", ticks);
        if (seed != null) {
            map.put("seed", seed);
        }
        map.put("world", world.toMap());
        map.put("environment", environment.toMap());
        map.put("fixtures", fixtures.toMap());
        map.put("record", record.toMap());
        map.put("assertions", assertions.stream().map(AssertionSpec::toMap).toList());
        map.put("limits", limits.toMap());
        return map;
    }

    @Nonnull
    private static String requiredString(@Nonnull Map<String, Object> data,
                                         @Nonnull String key,
                                         @Nullable String requestId) {
        Object value = data.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalid(requestId, key + " is required");
        }
        return text.trim();
    }

    private static int intValue(@Nullable Object value, int defaultValue, @Nullable String requestId) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw invalid(requestId, "expected integer value");
    }

    @Nullable
    private static Long longOrNull(@Nullable Object value, @Nullable String requestId) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw invalid(requestId, "expected long value");
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    private static Map<String, Object> asMap(@Nullable Object value, @Nullable String requestId) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw invalid(requestId, "expected object value");
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    private static List<Map<String, Object>> objectList(@Nullable Object value, @Nullable String requestId) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> rawList)) {
            throw invalid(requestId, "expected array value");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> map)) {
                throw invalid(requestId, "expected object array item");
            }
            result.add((Map<String, Object>) map);
        }
        return result;
    }

    @Nonnull
    private static List<Object> asList(@Nullable Object value, @Nullable String requestId) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return List.of(value);
        }
        throw invalid(requestId, "expected array-compatible value");
    }

    private static boolean boolValue(@Nullable Object value, boolean defaultValue, @Nullable String requestId) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw invalid(requestId, "expected boolean value");
    }

    @Nullable
    private static String stringOrNull(@Nullable Object value) {
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    @Nullable
    private static Integer integerOrNull(@Nullable Object value, @Nullable String requestId) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw invalid(requestId, "expected integer value");
    }

    @Nonnull
    private static List<AssertionSpec> assertions(@Nullable Object value, @Nonnull String requestId) {
        return objectList(value, requestId).stream()
                .map(AssertionSpec::from)
                .toList();
    }

    private static int clampTicks(@Nonnull NpcRuntimeHarnessConfig config, int ticks, @Nonnull String requestId) {
        try {
            return config.clampTicks(ticks);
        } catch (IllegalArgumentException exception) {
            throw invalid(requestId, exception.getMessage());
        }
    }

    private static void validateEntityCount(@Nonnull NpcRuntimeHarnessConfig config,
                                            int entityCount,
                                            int requestMaxEntities,
                                            @Nonnull String requestId) {
        try {
            config.validateEntityCount(entityCount);
        } catch (IllegalArgumentException exception) {
            throw invalid(requestId, exception.getMessage());
        }
        if (entityCount > requestMaxEntities) {
            throw invalid(requestId, "fixture entity count exceeds request limit " + requestMaxEntities);
        }
    }

    private static void rejectUnsupportedKeys(@Nonnull Map<String, Object> data,
                                              @Nonnull String prefix,
                                              @Nonnull List<String> allowed,
                                              @Nullable String requestId) {
        List<UnsupportedField> unsupported = new ArrayList<>();
        for (String key : data.keySet()) {
            if (!allowed.contains(key)) {
                unsupported.add(new UnsupportedField(
                        prefix.isBlank() ? key : prefix + "." + key,
                        "field is not supported by the current runtime contract"
                ));
            }
        }
        if (!unsupported.isEmpty()) {
            throw unsupported(requestId, "request contains unsupported fields", unsupported);
        }
    }

    @Nonnull
    private static ValidationException invalid(@Nullable String requestId, @Nonnull String message) {
        return new ValidationException("invalid-request", requestId, message, List.of());
    }

    @Nonnull
    private static ValidationException unsupported(@Nullable String requestId,
                                                   @Nonnull String message,
                                                   @Nonnull List<UnsupportedField> unsupported) {
        return new ValidationException("unsupported-request", requestId, message, unsupported);
    }

    public record UnsupportedField(@Nonnull String path, @Nonnull String reason) {
        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("path", path);
            map.put("reason", reason);
            return map;
        }
    }

    public static final class ValidationException extends IllegalArgumentException {
        private final String classification;
        private final String requestId;
        private final List<UnsupportedField> unsupported;

        private ValidationException(@Nonnull String classification,
                                    @Nullable String requestId,
                                    @Nonnull String message,
                                    @Nonnull List<UnsupportedField> unsupported) {
            super(message);
            this.classification = classification;
            this.requestId = requestId != null && !requestId.isBlank() ? requestId : "<unknown>";
            this.unsupported = List.copyOf(unsupported);
        }

        @Nonnull
        public String classification() {
            return classification;
        }

        @Nonnull
        public String requestId() {
            return requestId;
        }

        @Nonnull
        public List<UnsupportedField> unsupported() {
            return unsupported;
        }
    }

    public record ScenarioSpec(@Nonnull String id, @Nullable String description) {
        @Nonnull
        static ScenarioSpec from(@Nonnull Map<String, Object> data, @Nonnull String requestId) {
            rejectUnsupportedKeys(data, "scenario", List.of("id", "description"), requestId);
            String id = stringOrNull(data.get("id"));
            return new ScenarioSpec(
                    id != null ? id : requestId,
                    stringOrNull(data.get("description"))
            );
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            if (description != null) {
                map.put("description", description);
            }
            return map;
        }
    }

    public record WorldSpec(@Nonnull String instanceId, @Nonnull String arena) {
        @Nonnull
        static WorldSpec from(@Nonnull Map<String, Object> data,
                              @Nonnull NpcRuntimeHarnessConfig config,
                              @Nonnull String requestId) {
            rejectUnsupportedKeys(data, "world", List.of("instanceId", "arena"), requestId);
            String instanceId = stringOrNull(data.get("instanceId"));
            String arena = stringOrNull(data.get("arena"));
            String resolvedInstanceId = instanceId != null ? instanceId : config.instanceId();
            try {
                config.validateWorldId(resolvedInstanceId);
            } catch (IllegalArgumentException exception) {
                throw invalid(requestId, exception.getMessage());
            }
            return new WorldSpec(
                    resolvedInstanceId,
                    arena != null ? arena : "default"
            );
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("instanceId", instanceId);
            map.put("arena", arena);
            return map;
        }
    }

    public record EnvironmentSpec(@Nullable Integer timeOfDay, @Nullable String weather, @Nullable Integer light) {
        @Nonnull
        static EnvironmentSpec from(@Nonnull Map<String, Object> data, @Nonnull String requestId) {
            rejectUnsupportedKeys(data, "environment", List.of("timeOfDay", "weather", "light"), requestId);
            return new EnvironmentSpec(
                    integerOrNull(data.get("timeOfDay"), requestId),
                    stringOrNull(data.get("weather")),
                    integerOrNull(data.get("light"), requestId)
            );
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            if (timeOfDay != null) {
                map.put("timeOfDay", timeOfDay);
            }
            if (weather != null) {
                map.put("weather", weather);
            }
            if (light != null) {
                map.put("light", light);
            }
            return map;
        }
    }

    public record Fixtures(@Nonnull NpcFixture npc, @Nonnull List<TargetFixture> targets) {
        @Nonnull
        static Fixtures from(@Nonnull Map<String, Object> data, @Nonnull String requestId) {
            rejectUnsupportedKeys(data, "fixtures", List.of("npc", "targets"), requestId);
            NpcFixture npc = NpcFixture.from(asMap(data.get("npc"), requestId), requestId);
            List<TargetFixture> targets = objectList(data.get("targets"), requestId).stream()
                    .map(item -> TargetFixture.from(item, requestId))
                    .toList();
            return new Fixtures(npc, targets);
        }

        int entityCount() {
            return targets.size() + 1;
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("npc", npc.toMap());
            map.put("targets", targets.stream().map(TargetFixture::toMap).toList());
            return map;
        }
    }

    public record NpcFixture(@Nonnull List<Object> position, @Nullable String state) {
        @Nonnull
        private static final List<Object> DEFAULT_POSITION = List.of(0, 64, 0);

        @Nonnull
        static NpcFixture from(@Nonnull Map<String, Object> data, @Nonnull String requestId) {
            rejectUnsupportedKeys(data, "fixtures.npc", List.of("position", "state"), requestId);
            List<Object> position = asList(data.get("position"), requestId);
            return new NpcFixture(
                    position.isEmpty() ? DEFAULT_POSITION : position,
                    stringOrNull(data.get("state"))
            );
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("position", position);
            if (state != null) {
                map.put("state", state);
            }
            return map;
        }
    }

    public record TargetFixture(
            @Nonnull String slot,
            @Nonnull String kind,
            @Nonnull List<Object> position,
            @Nonnull List<Object> tags,
            boolean visible
    ) {
        @Nonnull
        static TargetFixture from(@Nonnull Map<String, Object> data, @Nonnull String requestId) {
            rejectUnsupportedKeys(data, "fixtures.targets[]", List.of("slot", "kind", "position", "tags", "visible"), requestId);
            String slot = stringOrNull(data.get("slot"));
            String kind = stringOrNull(data.get("kind"));
            return new TargetFixture(
                    slot != null ? slot : "Target",
                    kind != null ? kind : "dummy",
                    asList(data.get("position"), requestId),
                    asList(data.get("tags"), requestId),
                    boolValue(data.get("visible"), true, requestId)
            );
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("slot", slot);
            map.put("kind", kind);
            if (!position.isEmpty()) {
                map.put("position", position);
            }
            if (!tags.isEmpty()) {
                map.put("tags", tags);
            }
            map.put("visible", visible);
            return map;
        }
    }

    public record RecordSpec(int everyTicks, boolean includeSnapshots, boolean includeEvents) {
        @Nonnull
        static RecordSpec from(@Nonnull Map<String, Object> data, @Nonnull String requestId) {
            rejectUnsupportedKeys(data, "record", List.of("everyTicks", "includeSnapshots", "includeEvents"), requestId);
            return new RecordSpec(
                    Math.max(1, intValue(data.get("everyTicks"), 1, requestId)),
                    boolValue(data.get("includeSnapshots"), true, requestId),
                    boolValue(data.get("includeEvents"), true, requestId)
            );
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("everyTicks", everyTicks);
            map.put("includeSnapshots", includeSnapshots);
            map.put("includeEvents", includeEvents);
            return map;
        }
    }

    public record AssertionSpec(@Nonnull Map<String, Object> fields) {
        @Nonnull
        static AssertionSpec from(@Nonnull Map<String, Object> data) {
            return new AssertionSpec(new LinkedHashMap<>(data));
        }

        @Nonnull
        Map<String, Object> toMap() {
            return new LinkedHashMap<>(fields);
        }
    }

    public record LimitsSpec(int maxEntities, long maxTraceBytes) {
        @Nonnull
        static LimitsSpec from(@Nonnull Map<String, Object> data,
                               @Nonnull NpcRuntimeHarnessConfig config,
                               @Nonnull String requestId) {
            rejectUnsupportedKeys(data, "limits", List.of("maxEntities", "maxTraceBytes"), requestId);
            int maxEntities = intValue(data.get("maxEntities"), config.maxEntities(), requestId);
            long maxTraceBytes = longOrNull(data.get("maxTraceBytes"), requestId) != null
                    ? longOrNull(data.get("maxTraceBytes"), requestId)
                    : config.maxTraceBytes();
            try {
                config.validateEntityCount(maxEntities);
                config.validateTraceBytes(maxTraceBytes);
            } catch (IllegalArgumentException exception) {
                throw invalid(requestId, exception.getMessage());
            }
            return new LimitsSpec(maxEntities, maxTraceBytes);
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("maxEntities", maxEntities);
            map.put("maxTraceBytes", maxTraceBytes);
            return map;
        }
    }
}
