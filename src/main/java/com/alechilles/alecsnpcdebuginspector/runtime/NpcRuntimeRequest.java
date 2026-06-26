package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        boolean requiresPlayer,
        @Nonnull TimingSpec timing,
        @Nonnull WorldSpec world,
        @Nonnull EnvironmentSpec environment,
        @Nonnull MultiNpcSpec multiNpc,
        @Nonnull Fixtures fixtures,
        @Nonnull EngineHooksSpec engineHooks,
        @Nonnull ProfileSpec profile,
        @Nonnull RecordSpec record,
        @Nonnull List<AssertionSpec> assertions,
        @Nonnull LimitsSpec limits
) {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern SAFE_ASSET_ID = Pattern.compile("[A-Za-z0-9_.:-]+");

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
                        "requiresPlayer", "timing", "environment", "multiNpc", "multiNpcMode",
                        "deliveryWindowTicks", "maxFixtureCount", "fixtures", "preseedTargetSlots", "engineHooks",
                        "profile", "record", "assertions", "limits"),
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
        boolean requiresPlayer = boolValue(data.get("requiresPlayer"), false, requestId);
        if (requiresPlayer) {
            throw unsupported(
                    requestId,
                    "requiresPlayer is not supported in headless runtime mode",
                    List.of(new UnsupportedField("requiresPlayer", "logged-in player runtime mode is not implemented for headless batch runs"))
            );
        }
        if (data.containsKey("preseedTargetSlots")) {
            throw unsupported(
                    requestId,
                    "preseedTargetSlots is not supported in headless runtime mode",
                    List.of(new UnsupportedField(
                            "preseedTargetSlots",
                            "direct target-slot preseeding is not implemented safely; use fixture-driven sensor induction"
                    ))
            );
        }
        TimingSpec timing = TimingSpec.from(asMap(data.get("timing"), requestId), ticks, requestId);
        ScenarioSpec scenario = ScenarioSpec.from(asMap(data.get("scenario"), requestId), requestId);
        WorldSpec world = WorldSpec.from(asMap(data.get("world"), requestId), config, requestId);
        EnvironmentSpec environment = EnvironmentSpec.from(asMap(data.get("environment"), requestId), requestId);
        MultiNpcSpec multiNpc = MultiNpcSpec.fromRequest(data, config, requestId);
        Fixtures fixtures = Fixtures.from(asMap(data.get("fixtures"), requestId), requestId, roleId);
        multiNpc.validateScenarioWindow(ticks, timing.warmupTicks(), fixtures.list().size(), requestId);
        EngineHooksSpec engineHooks = EngineHooksSpec.from(asMap(data.get("engineHooks"), requestId), requestId);
        LimitsSpec limits = LimitsSpec.from(asMap(data.get("limits"), requestId), config, requestId);
        validateEntityCount(config, fixtures.entityCount(), limits.maxEntities(), requestId);
        NpcRuntimeFixtureAllowlist.defaults().validate(fixtures.list(), requestId);
        ProfileSpec profile = ProfileSpec.from(asMap(data.get("profile"), requestId), requestId);
        RecordSpec record = RecordSpec.from(asMap(data.get("record"), requestId), requestId);
        List<AssertionSpec> assertions = assertions(data.get("assertions"), requestId, timing.warmupTicks(), ticks);

        return new NpcRuntimeRequest(
                version,
                requestId,
                scenario,
                assetId,
                roleId,
                ticks,
                seed,
                requiresPlayer,
                timing,
                world,
                environment,
                multiNpc,
                fixtures,
                engineHooks,
                profile,
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
        if (requiresPlayer) {
            map.put("requiresPlayer", true);
        }
        map.put("timing", timing.toMap());
        map.put("world", world.toMap());
        map.put("environment", environment.toMap());
        map.put("multiNpcMode", multiNpc.mode());
        map.put("deliveryWindowTicks", multiNpc.deliveryWindowTicks());
        map.put("maxFixtureCount", multiNpc.maxFixtureCount());
        map.put("multiNpc", multiNpc.toMap());
        map.put("fixtures", fixtures.toMap());
        if (engineHooks.anyEnabled()) {
            map.put("engineHooks", engineHooks.toMap());
        }
        if (profile.npcWorkMetrics()) {
            map.put("profile", profile.toMap());
        }
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
    private static Boolean booleanOrNull(@Nullable Object value, @Nonnull String requestId, @Nonnull String path) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw invalid(requestId, path + " must be a boolean");
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
    private static List<AssertionSpec> assertions(@Nullable Object value,
                                                  @Nonnull String requestId,
                                                  int defaultStartTick,
                                                  int defaultEndTick) {
        return objectList(value, requestId).stream()
                .map(item -> AssertionSpec.from(item, defaultStartTick, defaultEndTick, requestId))
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
    static ValidationException invalid(@Nullable String requestId, @Nonnull String message) {
        return new ValidationException("invalid-request", requestId, message, List.of());
    }

    @Nonnull
    private static ValidationException unsupported(@Nullable String requestId,
                                                   @Nonnull String message,
                                                   @Nonnull List<UnsupportedField> unsupported) {
        return new ValidationException("unsupported-request", requestId, message, unsupported);
    }

    @Nonnull
    static ValidationException unsupportedFixture(@Nullable String requestId,
                                                  @Nonnull String message,
                                                  @Nonnull List<UnsupportedField> unsupported) {
        return new ValidationException("unsupported-fixture", requestId, message, unsupported);
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

    public record EnvironmentSpec(@Nullable Integer timeOfDay,
                                  @Nullable String weather,
                                  @Nullable Integer light,
                                  @Nullable Boolean pauseTime) {
        @Nonnull
        static EnvironmentSpec from(@Nonnull Map<String, Object> data, @Nonnull String requestId) {
            rejectUnsupportedKeys(data, "environment", List.of("timeOfDay", "weather", "light", "pauseTime"), requestId);
            String weather = stringOrNull(data.get("weather"));
            if (weather != null && !SAFE_ASSET_ID.matcher(weather).matches()) {
                throw invalid(requestId, "environment.weather must be a safe asset id or keyword");
            }
            return new EnvironmentSpec(
                    integerOrNull(data.get("timeOfDay"), requestId),
                    weather,
                    integerOrNull(data.get("light"), requestId),
                    booleanOrNull(data.get("pauseTime"), requestId, "environment.pauseTime")
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
            if (pauseTime != null) {
                map.put("pauseTime", pauseTime);
            }
            return map;
        }

        boolean effectivePauseTime() {
            return pauseTime == null || pauseTime;
        }
    }

    public record TimingSpec(int warmupTicks, boolean stopWhenAssertionsResolved) {
        @Nonnull
        static TimingSpec from(@Nonnull Map<String, Object> data, int ticks, @Nonnull String requestId) {
            rejectUnsupportedKeys(data, "timing", List.of("warmupTicks", "stopWhenAssertionsResolved"), requestId);
            int warmupTicks = Math.max(0, intValue(data.get("warmupTicks"), 0, requestId));
            if (warmupTicks > ticks) {
                throw invalid(requestId, "timing.warmupTicks must not exceed ticks");
            }
            return new TimingSpec(
                    warmupTicks,
                    boolValue(data.get("stopWhenAssertionsResolved"), false, requestId)
            );
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("warmupTicks", warmupTicks);
            map.put("stopWhenAssertionsResolved", stopWhenAssertionsResolved);
            return map;
        }
    }

    public record MultiNpcSpec(@Nonnull String mode, int deliveryWindowTicks, int maxFixtureCount) {
        private static final int DEFAULT_DELIVERY_WINDOW_TICKS = 90;

        @Nonnull
        static MultiNpcSpec fromRequest(@Nonnull Map<String, Object> request,
                                        @Nonnull NpcRuntimeHarnessConfig config,
                                        @Nonnull String requestId) {
            Map<String, Object> nested = asMap(request.get("multiNpc"), requestId);
            LinkedHashMap<String, Object> data = new LinkedHashMap<>(nested);
            copyRootAlias(request, data, "multiNpcMode", "mode", requestId);
            copyRootAlias(request, data, "deliveryWindowTicks", "deliveryWindowTicks", requestId);
            copyRootAlias(request, data, "maxFixtureCount", "maxFixtureCount", requestId);
            return from(data, config, requestId);
        }

        @Nonnull
        private static MultiNpcSpec from(@Nonnull Map<String, Object> data,
                                         @Nonnull NpcRuntimeHarnessConfig config,
                                         @Nonnull String requestId) {
            rejectUnsupportedKeys(data, "multiNpc", List.of("mode", "deliveryWindowTicks", "maxFixtureCount"), requestId);
            String mode = stringOrNull(data.get("mode"));
            if (mode == null) {
                mode = "single";
            }
            if (!List.of("single", "linked", "swarm").contains(mode)) {
                throw invalid(requestId, "unsupported multiNpc.mode " + mode);
            }
            int deliveryWindowTicks = intValue(data.get("deliveryWindowTicks"), DEFAULT_DELIVERY_WINDOW_TICKS, requestId);
            if (deliveryWindowTicks <= 0) {
                throw invalid(requestId, "multiNpc.deliveryWindowTicks must be greater than zero");
            }
            int maxFixtureCount = intValue(data.get("maxFixtureCount"), config.maxEntities(), requestId);
            if (maxFixtureCount <= 0) {
                throw invalid(requestId, "multiNpc.maxFixtureCount must be greater than zero");
            }
            try {
                config.validateEntityCount(maxFixtureCount);
            } catch (IllegalArgumentException exception) {
                throw invalid(requestId, exception.getMessage());
            }
            return new MultiNpcSpec(mode, deliveryWindowTicks, maxFixtureCount);
        }

        private static void copyRootAlias(@Nonnull Map<String, Object> request,
                                          @Nonnull Map<String, Object> target,
                                          @Nonnull String rootKey,
                                          @Nonnull String nestedKey,
                                          @Nonnull String requestId) {
            if (!request.containsKey(rootKey)) {
                return;
            }
            Object rootValue = request.get(rootKey);
            if (target.containsKey(nestedKey) && !String.valueOf(target.get(nestedKey)).equals(String.valueOf(rootValue))) {
                throw invalid(requestId, rootKey + " conflicts with multiNpc." + nestedKey);
            }
            target.put(nestedKey, rootValue);
        }

        void validateScenarioWindow(int ticks,
                                    int warmupTicks,
                                    int fixtureCount,
                                    @Nonnull String requestId) {
            if (fixtureCount > maxFixtureCount) {
                throw invalid(requestId, "multiNpc.maxFixtureCount exceeded by fixture list");
            }
            if (!"single".equals(mode) && ticks < warmupTicks + deliveryWindowTicks) {
                throw invalid(requestId, "multiNpc.deliveryWindowTicks requires ticks to be at least warmupTicks + deliveryWindowTicks");
            }
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("mode", mode);
            map.put("deliveryWindowTicks", deliveryWindowTicks);
            map.put("maxFixtureCount", maxFixtureCount);
            return map;
        }
    }

    public record Fixtures(@Nonnull NpcFixture npc,
                           @Nonnull List<TargetFixture> targets,
                           @Nonnull List<NpcRuntimeFixtureSpec> list) {
        @Nonnull
        static Fixtures from(@Nonnull Map<String, Object> data, @Nonnull String requestId, @Nonnull String defaultRoleId) {
            rejectUnsupportedKeys(data, "fixtures", List.of("npc", "targets", "list"), requestId);
            NpcFixture npc = NpcFixture.from(asMap(data.get("npc"), requestId), requestId);
            List<TargetFixture> targets = objectList(data.get("targets"), requestId).stream()
                    .map(item -> TargetFixture.from(item, requestId))
                    .toList();
            List<NpcRuntimeFixtureSpec> specs = new ArrayList<>();
            if (data.containsKey("list")) {
                List<Map<String, Object>> fixtureItems = objectList(data.get("list"), requestId);
                for (int i = 0; i < fixtureItems.size(); i++) {
                    specs.add(NpcRuntimeFixtureSpec.fromMap(fixtureItems.get(i), requestId, "fixtures.list[" + i + "]", defaultRoleId));
                }
            } else {
                specs.add(NpcRuntimeFixtureSpec.npcUnderTest(npc, defaultRoleId));
                for (TargetFixture target : targets) {
                    specs.add(NpcRuntimeFixtureSpec.fromLegacyTarget(target, defaultRoleId, requestId));
                }
            }
            NpcRuntimeFixtureSpec npcSpec = npcUnderTestSpec(specs, requestId);
            NpcFixture resolvedNpc = data.containsKey("list") ? npcSpec.toLegacyNpcFixture() : npc;
            return new Fixtures(resolvedNpc, targets, List.copyOf(specs));
        }

        int entityCount() {
            return (int) list.stream().filter(spec -> spec.kind().entityLike()).count();
        }

        @Nonnull
        NpcRuntimeFixtureSpec npcUnderTest() {
            return list.stream()
                    .filter(spec -> spec.kind() == NpcRuntimeFixtureKind.NPC_UNDER_TEST)
                    .findFirst()
                    .orElseThrow();
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("npc", npc.toMap());
            map.put("targets", targets.stream().map(TargetFixture::toMap).toList());
            map.put("list", list.stream().map(NpcRuntimeFixtureSpec::toMap).toList());
            return map;
        }

        @Nonnull
        private static NpcRuntimeFixtureSpec npcUnderTestSpec(@Nonnull List<NpcRuntimeFixtureSpec> specs,
                                                              @Nonnull String requestId) {
            List<NpcRuntimeFixtureSpec> matches = specs.stream()
                    .filter(spec -> spec.kind() == NpcRuntimeFixtureKind.NPC_UNDER_TEST)
                    .toList();
            if (matches.isEmpty()) {
                throw unsupportedFixture(
                        requestId,
                        "request must contain exactly one npcUnderTest fixture",
                        List.of(new UnsupportedField("fixtures.list", "exactly one npcUnderTest fixture is required"))
                );
            }
            return matches.getFirst();
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

    public record RecordSpec(
            @Nonnull String profile,
            int everyTicks,
            boolean includeSnapshots,
            boolean includeEvents,
            @Nonnull String fixtureScope,
            @Nonnull List<String> fixtureIds
    ) {
        @Nonnull
        static RecordSpec from(@Nonnull Map<String, Object> data, @Nonnull String requestId) {
            rejectUnsupportedKeys(
                    data,
                    "record",
                    List.of("profile", "everyTicks", "includeSnapshots", "includeEvents", "fixtureScope", "fixtureIds"),
                    requestId
            );
            String profile = stringOrNull(data.get("profile"));
            if (profile == null) {
                profile = "full";
            }
            if (!List.of("full", "standard", "minimal").contains(profile)) {
                throw invalid(requestId, "unsupported record profile " + profile);
            }
            String fixtureScope = stringOrNull(data.get("fixtureScope"));
            if (fixtureScope == null) {
                fixtureScope = "npcUnderTest";
            }
            if (!List.of("npcUnderTest", "allFixtures").contains(fixtureScope)) {
                throw invalid(requestId, "unsupported record fixtureScope " + fixtureScope);
            }
            return new RecordSpec(
                    profile,
                    Math.max(1, intValue(data.get("everyTicks"), 1, requestId)),
                    boolValue(data.get("includeSnapshots"), true, requestId),
                    boolValue(data.get("includeEvents"), true, requestId),
                    fixtureScope,
                    fixtureIds(data.get("fixtureIds"), requestId)
            );
        }

        @Nonnull
        private static List<String> fixtureIds(@Nullable Object value, @Nonnull String requestId) {
            if (value == null) {
                return List.of();
            }
            if (!(value instanceof List<?> list)) {
                throw invalid(requestId, "record.fixtureIds must be an array of safe fixture ids");
            }
            List<String> ids = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof String id) || id.isBlank() || !SAFE_ID.matcher(id).matches()) {
                    throw invalid(requestId, "record.fixtureIds must be an array of safe fixture ids");
                }
                ids.add(id);
            }
            return List.copyOf(ids);
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("profile", profile);
            map.put("everyTicks", everyTicks);
            map.put("includeSnapshots", includeSnapshots);
            map.put("includeEvents", includeEvents);
            if (!"npcUnderTest".equals(fixtureScope)) {
                map.put("fixtureScope", fixtureScope);
            }
            if (!fixtureIds.isEmpty()) {
                map.put("fixtureIds", fixtureIds);
            }
            return map;
        }
    }

    public record ProfileSpec(boolean npcWorkMetrics, int windowTicks, int emitEveryTicks, boolean includeFinalSummary) {
        @Nonnull
        static ProfileSpec disabled() {
            return new ProfileSpec(false, 100, 20, true);
        }

        @Nonnull
        static ProfileSpec from(@Nonnull Map<String, Object> data, @Nonnull String requestId) {
            if (data.isEmpty()) {
                return disabled();
            }
            rejectUnsupportedKeys(data, "profile", List.of("npcWorkMetrics", "windowTicks", "emitEveryTicks", "includeFinalSummary"), requestId);
            int windowTicks = Math.max(1, intValue(data.get("windowTicks"), 100, requestId));
            int emitEveryTicks = Math.max(1, intValue(data.get("emitEveryTicks"), 20, requestId));
            return new ProfileSpec(
                    boolValue(data.get("npcWorkMetrics"), false, requestId),
                    windowTicks,
                    emitEveryTicks,
                    boolValue(data.get("includeFinalSummary"), true, requestId)
            );
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("npcWorkMetrics", npcWorkMetrics);
            map.put("windowTicks", windowTicks);
            map.put("emitEveryTicks", emitEveryTicks);
            map.put("includeFinalSummary", includeFinalSummary);
            return map;
        }
    }

    public record EngineHooksSpec(
            boolean targetSelection,
            boolean pathing,
            boolean combatEligibility,
            boolean instructionLifecycle
    ) {
        private static final Set<String> SUPPORTED = Set.of(
                "targetSelection",
                "pathing",
                "combatEligibility",
                "instructionLifecycle"
        );

        @Nonnull
        static EngineHooksSpec from(@Nonnull Map<String, Object> data, @Nonnull String requestId) {
            List<UnsupportedField> unsupported = new ArrayList<>();
            for (String key : data.keySet()) {
                if (!SUPPORTED.contains(key)) {
                    unsupported.add(new UnsupportedField(
                            "engineHooks." + key,
                            "engine hook is not supported by the current runtime contract"
                    ));
                }
            }
            if (!unsupported.isEmpty()) {
                throw unsupported(requestId, "request contains unsupported engine hooks", unsupported);
            }
            return new EngineHooksSpec(
                    boolValue(data.get("targetSelection"), false, requestId),
                    boolValue(data.get("pathing"), false, requestId),
                    boolValue(data.get("combatEligibility"), false, requestId),
                    boolValue(data.get("instructionLifecycle"), false, requestId)
            );
        }

        boolean anyEnabled() {
            return targetSelection || pathing || combatEligibility || instructionLifecycle;
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("targetSelection", targetSelection);
            map.put("pathing", pathing);
            map.put("combatEligibility", combatEligibility);
            map.put("instructionLifecycle", instructionLifecycle);
            return map;
        }
    }

    public record AssertionSpec(@Nonnull Map<String, Object> fields, @Nonnull AssertionWindowSpec window) {
        public AssertionSpec(@Nonnull Map<String, Object> fields) {
            this(fields, AssertionWindowSpec.from(Map.of(), 0, Integer.MAX_VALUE, "<direct>"));
        }

        @Nonnull
        static AssertionSpec from(@Nonnull Map<String, Object> data,
                                  int defaultStartTick,
                                  int defaultEndTick,
                                  @Nonnull String requestId) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>(data);
            AssertionWindowSpec window = AssertionWindowSpec.from(asMap(copy.get("window"), requestId), defaultStartTick, defaultEndTick, requestId);
            return new AssertionSpec(copy, window);
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>(fields);
            map.put("window", window.toMap());
            return map;
        }
    }

    public record AssertionWindowSpec(@Nonnull String mode, int startTick, int endTick, int sustainedTicks) {
        @Nonnull
        static AssertionWindowSpec from(@Nonnull Map<String, Object> data,
                                        int defaultStartTick,
                                        int defaultEndTick,
                                        @Nonnull String requestId) {
            rejectUnsupportedKeys(data, "assertions[].window", List.of("mode", "startTick", "endTick", "sustainedTicks"), requestId);
            String mode = stringOrNull(data.get("mode"));
            if (mode == null) {
                mode = "eventually";
            }
            if (!List.of("eventually", "sustained", "never").contains(mode)) {
                throw invalid(requestId, "unsupported assertion window mode " + mode);
            }
            int startTick = intValue(data.get("startTick"), defaultStartTick, requestId);
            int endTick = intValue(data.get("endTick"), defaultEndTick, requestId);
            int sustainedTicks = Math.max(1, intValue(data.get("sustainedTicks"), 1, requestId));
            if (startTick < 0 || endTick < startTick) {
                throw invalid(requestId, "assertion window ticks are invalid");
            }
            return new AssertionWindowSpec(mode, startTick, endTick, sustainedTicks);
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("mode", mode);
            map.put("startTick", startTick);
            map.put("endTick", endTick);
            map.put("sustainedTicks", sustainedTicks);
            return map;
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
