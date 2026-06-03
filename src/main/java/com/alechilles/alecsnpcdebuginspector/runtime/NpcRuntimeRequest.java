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
        @Nonnull String assetId,
        @Nonnull String roleId,
        int ticks,
        @Nullable Long seed,
        @Nonnull WorldSpec world,
        @Nonnull Fixtures fixtures,
        @Nonnull RecordSpec record
) {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_.-]+");

    @Nonnull
    public static NpcRuntimeRequest parse(@Nonnull String json, @Nonnull NpcRuntimeHarnessConfig config) {
        return fromMap(NpcRuntimeJson.parseObject(json), config);
    }

    @Nonnull
    public static NpcRuntimeRequest fromMap(@Nonnull Map<String, Object> data, @Nonnull NpcRuntimeHarnessConfig config) {
        int version = intValue(data.get("version"), 1);
        if (version != 1) {
            throw new IllegalArgumentException("unsupported request version " + version);
        }
        String requestId = requiredString(data, "requestId");
        if (!SAFE_ID.matcher(requestId).matches()) {
            throw new IllegalArgumentException("requestId contains unsupported characters");
        }
        String assetId = requiredString(data, "assetId");
        String roleId = requiredString(data, "roleId");
        int ticks = config.clampTicks(intValue(data.get("ticks"), 1));
        Long seed = longOrNull(data.get("seed"));
        WorldSpec world = WorldSpec.from(asMap(data.get("world")), config);
        Fixtures fixtures = Fixtures.from(asMap(data.get("fixtures")));
        config.validateEntityCount(fixtures.entityCount());
        RecordSpec record = RecordSpec.from(asMap(data.get("record")));
        return new NpcRuntimeRequest(version, requestId, assetId, roleId, ticks, seed, world, fixtures, record);
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
        map.put("assetId", assetId);
        map.put("roleId", roleId);
        map.put("ticks", ticks);
        if (seed != null) {
            map.put("seed", seed);
        }
        map.put("world", world.toMap());
        map.put("fixtures", fixtures.toMap());
        map.put("record", record.toMap());
        return map;
    }

    @Nonnull
    private static String requiredString(@Nonnull Map<String, Object> data, @Nonnull String key) {
        Object value = data.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return text.trim();
    }

    private static int intValue(@Nullable Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException("expected integer value");
    }

    @Nullable
    private static Long longOrNull(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("expected long value");
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    private static Map<String, Object> asMap(@Nullable Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("expected object value");
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    private static List<Map<String, Object>> objectList(@Nullable Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> rawList)) {
            throw new IllegalArgumentException("expected array value");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("expected object array item");
            }
            result.add((Map<String, Object>) map);
        }
        return result;
    }

    @Nonnull
    private static List<Object> asList(@Nullable Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of(value);
    }

    private static boolean boolValue(@Nullable Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException("expected boolean value");
    }

    @Nullable
    private static String stringOrNull(@Nullable Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    public record WorldSpec(@Nonnull String instanceId, @Nonnull String arena) {
        @Nonnull
        static WorldSpec from(@Nonnull Map<String, Object> data, @Nonnull NpcRuntimeHarnessConfig config) {
            String instanceId = stringOrNull(data.get("instanceId"));
            String arena = stringOrNull(data.get("arena"));
            return new WorldSpec(
                    instanceId != null ? instanceId : config.instanceId(),
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

    public record Fixtures(@Nonnull NpcFixture npc, @Nonnull List<TargetFixture> targets) {
        @Nonnull
        static Fixtures from(@Nonnull Map<String, Object> data) {
            NpcFixture npc = NpcFixture.from(asMap(data.get("npc")));
            List<TargetFixture> targets = objectList(data.get("targets")).stream()
                    .map(TargetFixture::from)
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
        static NpcFixture from(@Nonnull Map<String, Object> data) {
            List<Object> position = asList(data.get("position"));
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
        static TargetFixture from(@Nonnull Map<String, Object> data) {
            String slot = stringOrNull(data.get("slot"));
            String kind = stringOrNull(data.get("kind"));
            return new TargetFixture(
                    slot != null ? slot : "Target",
                    kind != null ? kind : "dummy",
                    asList(data.get("position")),
                    asList(data.get("tags")),
                    boolValue(data.get("visible"), true)
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
        static RecordSpec from(@Nonnull Map<String, Object> data) {
            return new RecordSpec(
                    Math.max(1, intValue(data.get("everyTicks"), 1)),
                    boolValue(data.get("includeSnapshots"), true),
                    boolValue(data.get("includeEvents"), true)
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
}
