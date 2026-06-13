package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Canonical fixture schema used by runtime scenarios.
 */
public record NpcRuntimeFixtureSpec(
        @Nonnull String fixtureId,
        @Nonnull NpcRuntimeFixtureKind kind,
        @Nonnull List<Object> position,
        @Nonnull List<Object> rotation,
        @Nonnull List<Object> tags,
        @Nullable String roleId,
        @Nullable String asset,
        @Nullable String entityId,
        @Nullable String blockId,
        @Nullable Object state,
        @Nullable String itemId,
        @Nullable String targetSlot,
        boolean visible,
        @Nullable String faction,
        @Nullable String attitude,
        @Nullable Integer health,
        @Nullable String flockId,
        @Nullable String flockRole,
        @Nullable String familyId,
        @Nullable String familyRole,
        @Nullable String leaderFixtureId,
        @Nullable String parentFixtureId,
        @Nullable String messageId,
        @Nullable String messageType,
        @Nullable String senderFixtureId,
        @Nullable String receiverFixtureId,
        @Nullable String targetFixtureId,
        @Nonnull List<Object> payloadKeys,
        @Nullable String beaconId,
        @Nullable String beaconType,
        @Nullable String sourceFixtureId,
        @Nullable Double radius,
        @Nullable Integer ttlTicks,
        @Nonnull List<Object> requiredConsumerFixtureIds,
        @Nonnull TameworkMutation tamework
) {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final List<Object> DEFAULT_POSITION = List.of(0, 64, 0);

    @Nonnull
    static NpcRuntimeFixtureSpec npcUnderTest(@Nonnull NpcRuntimeRequest.NpcFixture npc, @Nonnull String defaultRoleId) {
        return new NpcRuntimeFixtureSpec(
                "npcUnderTest",
                NpcRuntimeFixtureKind.NPC_UNDER_TEST,
                npc.position(),
                List.of(),
                List.of(),
                defaultRoleId,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                TameworkMutation.empty()
        );
    }

    @Nonnull
    static NpcRuntimeFixtureSpec fromLegacyTarget(@Nonnull NpcRuntimeRequest.TargetFixture target,
                                                  @Nonnull String defaultRoleId,
                                                  @Nonnull String requestId) {
        String slot = target.slot().isBlank() ? "Target" : target.slot();
        return new NpcRuntimeFixtureSpec(
                "target." + sanitizeId(slot),
                NpcRuntimeFixtureKind.fromJson(target.kind(), requestId, "fixtures.targets[].kind"),
                target.position().isEmpty() ? DEFAULT_POSITION : target.position(),
                List.of(),
                target.tags(),
                defaultRoleId,
                null,
                null,
                null,
                null,
                null,
                slot,
                target.visible(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                TameworkMutation.empty()
        );
    }

    @Nonnull
    static NpcRuntimeFixtureSpec fromMap(@Nonnull Map<String, Object> data,
                                         @Nonnull String requestId,
                                         @Nonnull String path,
                                         @Nonnull String defaultRoleId) {
        rejectUnsupportedKeys(
                data,
                path,
                List.of("id", "fixtureId", "kind", "type", "position", "rotation", "tags", "roleId",
                        "asset", "entityId", "blockId", "state", "itemId", "slot", "targetSlot", "visible", "faction", "attitude",
                        "health", "flockId", "flockRole", "familyId", "familyRole", "leaderFixtureId", "parentFixtureId",
                        "messageId", "messageType", "senderFixtureId", "receiverFixtureId", "targetFixtureId", "payloadKeys",
                        "beaconId", "beaconType", "sourceFixtureId", "radius", "ttlTicks", "requiredConsumerFixtureIds",
                        "tamework"),
                requestId
        );
        String fixtureId = stringOrNull(data.get("fixtureId"));
        if (fixtureId == null) {
            fixtureId = stringOrNull(data.get("id"));
        }
        String kindText = stringOrNull(data.get("kind"));
        if (kindText == null) {
            kindText = stringOrNull(data.get("type"));
        }
        NpcRuntimeFixtureKind kind = NpcRuntimeFixtureKind.fromJson(
                kindText != null ? kindText : "npc",
                requestId,
                path + ".kind"
        );
        String resolvedFixtureId = fixtureId != null ? fixtureId : defaultFixtureId(kind);
        validateFixtureId(resolvedFixtureId, requestId, path + ".fixtureId");

        String roleId = stringOrNull(data.get("roleId"));

        return new NpcRuntimeFixtureSpec(
                resolvedFixtureId,
                kind,
                coordinatesOrDefault(data.get("position"), DEFAULT_POSITION, requestId, path + ".position"),
                optionalCoordinates(data.get("rotation"), requestId, path + ".rotation"),
                stringObjectList(data.get("tags"), requestId, path + ".tags"),
                roleId,
                stringOrNull(data.get("asset")),
                stringOrNull(data.get("entityId")),
                stringOrNull(data.get("blockId")),
                data.get("state"),
                stringOrNull(data.get("itemId")),
                firstPresentString(data, "targetSlot", "slot"),
                boolValue(data.get("visible"), true, requestId, path + ".visible"),
                stringOrNull(data.get("faction")),
                stringOrNull(data.get("attitude")),
                integerOrNull(data.get("health"), requestId, path + ".health"),
                stringOrNull(data.get("flockId")),
                stringOrNull(data.get("flockRole")),
                stringOrNull(data.get("familyId")),
                stringOrNull(data.get("familyRole")),
                stringOrNull(data.get("leaderFixtureId")),
                stringOrNull(data.get("parentFixtureId")),
                stringOrNull(data.get("messageId")),
                stringOrNull(data.get("messageType")),
                stringOrNull(data.get("senderFixtureId")),
                stringOrNull(data.get("receiverFixtureId")),
                stringOrNull(data.get("targetFixtureId")),
                stringObjectList(data.get("payloadKeys"), requestId, path + ".payloadKeys"),
                stringOrNull(data.get("beaconId")),
                stringOrNull(data.get("beaconType")),
                stringOrNull(data.get("sourceFixtureId")),
                doubleOrNull(data.get("radius"), requestId, path + ".radius"),
                integerOrNull(data.get("ttlTicks"), requestId, path + ".ttlTicks"),
                stringObjectList(data.get("requiredConsumerFixtureIds"), requestId, path + ".requiredConsumerFixtureIds"),
                TameworkMutation.from(asMap(data.get("tamework"), requestId, path + ".tamework"), requestId, path + ".tamework")
        );
    }

    @Nonnull
    NpcRuntimeRequest.NpcFixture toLegacyNpcFixture() {
        return new NpcRuntimeRequest.NpcFixture(position, null);
    }

    @Nonnull
    Map<String, Object> toMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("fixtureId", fixtureId);
        map.put("kind", kind.jsonName());
        map.put("position", position);
        if (!rotation.isEmpty()) {
            map.put("rotation", rotation);
        }
        if (!tags.isEmpty()) {
            map.put("tags", tags);
        }
        if (roleId != null) {
            map.put("roleId", roleId);
        }
        if (asset != null) {
            map.put("asset", asset);
        }
        if (entityId != null) {
            map.put("entityId", entityId);
        }
        if (blockId != null) {
            map.put("blockId", blockId);
        }
        if (state != null) {
            map.put("state", state);
        }
        if (itemId != null) {
            map.put("itemId", itemId);
        }
        if (targetSlot != null) {
            map.put("targetSlot", targetSlot);
        }
        map.put("visible", visible);
        if (faction != null) {
            map.put("faction", faction);
        }
        if (attitude != null) {
            map.put("attitude", attitude);
        }
        if (health != null) {
            map.put("health", health);
        }
        if (flockId != null) {
            map.put("flockId", flockId);
        }
        if (flockRole != null) {
            map.put("flockRole", flockRole);
        }
        if (familyId != null) {
            map.put("familyId", familyId);
        }
        if (familyRole != null) {
            map.put("familyRole", familyRole);
        }
        if (leaderFixtureId != null) {
            map.put("leaderFixtureId", leaderFixtureId);
        }
        if (parentFixtureId != null) {
            map.put("parentFixtureId", parentFixtureId);
        }
        if (messageId != null) {
            map.put("messageId", messageId);
        }
        if (messageType != null) {
            map.put("messageType", messageType);
        }
        if (senderFixtureId != null) {
            map.put("senderFixtureId", senderFixtureId);
        }
        if (receiverFixtureId != null) {
            map.put("receiverFixtureId", receiverFixtureId);
        }
        if (targetFixtureId != null) {
            map.put("targetFixtureId", targetFixtureId);
        }
        if (!payloadKeys.isEmpty()) {
            map.put("payloadKeys", payloadKeys);
        }
        if (beaconId != null) {
            map.put("beaconId", beaconId);
        }
        if (beaconType != null) {
            map.put("beaconType", beaconType);
        }
        if (sourceFixtureId != null) {
            map.put("sourceFixtureId", sourceFixtureId);
        }
        if (radius != null) {
            map.put("radius", radius);
        }
        if (ttlTicks != null) {
            map.put("ttlTicks", ttlTicks);
        }
        if (!requiredConsumerFixtureIds.isEmpty()) {
            map.put("requiredConsumerFixtureIds", requiredConsumerFixtureIds);
        }
        if (!tamework.isEmpty()) {
            map.put("tamework", tamework.toMap());
        }
        return map;
    }

    private static void validateFixtureId(@Nonnull String fixtureId, @Nonnull String requestId, @Nonnull String path) {
        if (!SAFE_ID.matcher(fixtureId).matches()) {
            throw NpcRuntimeRequest.unsupportedFixture(
                    requestId,
                    "request contains unsupported fixture id",
                    List.of(new NpcRuntimeRequest.UnsupportedField(path, "fixture id must match [A-Za-z0-9_.-]+"))
            );
        }
    }

    @Nonnull
    private static String defaultFixtureId(@Nonnull NpcRuntimeFixtureKind kind) {
        return switch (kind) {
            case NPC_UNDER_TEST -> "npcUnderTest";
            case TARGET_DUMMY -> "targetDummy";
            case NPC -> "npc.fixture";
            case MOB -> "mob.fixture";
            case ITEM -> "item.fixture";
            case BLOCK -> "block.fixture";
            case BEACON -> "beacon.fixture";
            case MESSAGE -> "message.fixture";
            case PLAYER_ANCHOR -> "playerAnchor";
            case FAMILY_MEMBER -> "family.fixture";
            case FLOCK_MEMBER -> "flock.fixture";
        };
    }

    @Nonnull
    private static String sanitizeId(@Nonnull String text) {
        String sanitized = text.replaceAll("[^A-Za-z0-9_.-]", "_");
        return sanitized.isBlank() ? "Target" : sanitized;
    }

    private static void rejectUnsupportedKeys(@Nonnull Map<String, Object> data,
                                              @Nonnull String prefix,
                                              @Nonnull List<String> allowed,
                                              @Nonnull String requestId) {
        List<NpcRuntimeRequest.UnsupportedField> unsupported = new ArrayList<>();
        for (String key : data.keySet()) {
            if (!allowed.contains(key)) {
                unsupported.add(new NpcRuntimeRequest.UnsupportedField(prefix + "." + key, "field is not supported by the fixture schema"));
            }
        }
        if (!unsupported.isEmpty()) {
            throw NpcRuntimeRequest.unsupportedFixture(requestId, "request contains unsupported fixture fields", unsupported);
        }
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    private static Map<String, Object> asMap(@Nullable Object value, @Nonnull String requestId, @Nonnull String path) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw NpcRuntimeRequest.invalid(requestId, path + " must be an object");
    }

    @Nonnull
    private static List<Object> listOrDefault(@Nullable Object value,
                                              @Nonnull List<Object> defaultValue,
                                              @Nonnull String requestId,
                                              @Nonnull String path) {
        List<Object> list = asList(value, requestId, path);
        return list.isEmpty() ? defaultValue : list;
    }

    @Nonnull
    private static List<Object> coordinatesOrDefault(@Nullable Object value,
                                                     @Nonnull List<Object> defaultValue,
                                                     @Nonnull String requestId,
                                                     @Nonnull String path) {
        if (value == null) {
            return defaultValue;
        }
        List<Object> coordinates = asList(value, requestId, path);
        validateCoordinateTriplet(coordinates, requestId, path);
        return coordinates;
    }

    @Nonnull
    private static List<Object> optionalCoordinates(@Nullable Object value,
                                                    @Nonnull String requestId,
                                                    @Nonnull String path) {
        if (value == null) {
            return List.of();
        }
        List<Object> coordinates = asList(value, requestId, path);
        validateCoordinateTriplet(coordinates, requestId, path);
        return coordinates;
    }

    private static void validateCoordinateTriplet(@Nonnull List<Object> coordinates,
                                                  @Nonnull String requestId,
                                                  @Nonnull String path) {
        if (coordinates.size() != 3) {
            throw NpcRuntimeRequest.invalid(requestId, path + " must contain exactly three numeric coordinates");
        }
        for (Object coordinate : coordinates) {
            if (!(coordinate instanceof Number)) {
                throw NpcRuntimeRequest.invalid(requestId, path + " must contain exactly three numeric coordinates");
            }
        }
    }

    @Nonnull
    private static List<Object> asList(@Nullable Object value, @Nonnull String requestId, @Nonnull String path) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return List.of(value);
        }
        throw NpcRuntimeRequest.invalid(requestId, path + " must be an array-compatible value");
    }

    @Nonnull
    private static List<Object> stringObjectList(@Nullable Object value, @Nonnull String requestId, @Nonnull String path) {
        if (value == null) {
            return List.of();
        }
        List<Object> list = asList(value, requestId, path);
        for (Object item : list) {
            if (!(item instanceof String text) || text.isBlank()) {
                throw NpcRuntimeRequest.invalid(requestId, path + " must contain only non-empty strings");
            }
        }
        return list;
    }

    @Nullable
    private static Integer integerOrNull(@Nullable Object value, @Nonnull String requestId, @Nonnull String path) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw NpcRuntimeRequest.invalid(requestId, path + " must be an integer");
    }

    @Nullable
    private static Double doubleOrNull(@Nullable Object value, @Nonnull String requestId, @Nonnull String path) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw NpcRuntimeRequest.invalid(requestId, path + " must be a number");
    }

    private static boolean boolValue(@Nullable Object value,
                                     boolean defaultValue,
                                     @Nonnull String requestId,
                                     @Nonnull String path) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw NpcRuntimeRequest.invalid(requestId, path + " must be a boolean");
    }

    @Nullable
    private static String firstPresentString(@Nonnull Map<String, Object> data, @Nonnull String first, @Nonnull String second) {
        String value = stringOrNull(data.get(first));
        return value != null ? value : stringOrNull(data.get(second));
    }

    @Nullable
    private static String stringOrNull(@Nullable Object value) {
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    public record TameworkMutation(
            @Nullable Boolean tamed,
            @Nonnull Map<String, Object> owner,
            @Nonnull Map<String, Object> needs,
            @Nonnull List<String> effects,
            @Nullable String commandState,
            @Nullable String lifeStage
    ) {
        @Nonnull
        static TameworkMutation empty() {
            return new TameworkMutation(null, Map.of(), Map.of(), List.of(), null, null);
        }

        @Nonnull
        static TameworkMutation from(@Nonnull Map<String, Object> data,
                                     @Nonnull String requestId,
                                     @Nonnull String path) {
            rejectUnsupportedKeys(
                    data,
                    path,
                    List.of("tamed", "owner", "needs", "effects", "command", "commandState", "lifeStage"),
                    requestId
            );
            String commandState = firstPresentString(data, "commandState", "command");
            return new TameworkMutation(
                    booleanOrNull(data.get("tamed"), requestId, path + ".tamed"),
                    asMap(data.get("owner"), requestId, path + ".owner"),
                    normalizeNumberMap(asMap(data.get("needs"), requestId, path + ".needs")),
                    stringList(data.get("effects"), requestId, path + ".effects"),
                    commandState,
                    stringOrNull(data.get("lifeStage"))
            );
        }

        boolean isEmpty() {
            return tamed == null
                    && owner.isEmpty()
                    && needs.isEmpty()
                    && effects.isEmpty()
                    && commandState == null
                    && lifeStage == null;
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            if (tamed != null) {
                map.put("tamed", tamed);
            }
            if (!owner.isEmpty()) {
                map.put("owner", owner);
            }
            if (!needs.isEmpty()) {
                map.put("needs", needs);
            }
            if (!effects.isEmpty()) {
                map.put("effects", effects);
            }
            if (commandState != null) {
                map.put("commandState", commandState);
            }
            if (lifeStage != null) {
                map.put("lifeStage", lifeStage);
            }
            return map;
        }

        @Nonnull
        private static Map<String, Object> normalizeNumberMap(@Nonnull Map<String, Object> values) {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Double doubleValue && doubleValue % 1 == 0) {
                    normalized.put(entry.getKey(), doubleValue.intValue());
                } else {
                    normalized.put(entry.getKey(), value);
                }
            }
            return Map.copyOf(normalized);
        }

        @Nullable
        private static Boolean booleanOrNull(@Nullable Object value, @Nonnull String requestId, @Nonnull String path) {
            if (value == null) {
                return null;
            }
            if (value instanceof Boolean bool) {
                return bool;
            }
            throw NpcRuntimeRequest.invalid(requestId, path + " must be a boolean");
        }

        @Nonnull
        private static List<String> stringList(@Nullable Object value, @Nonnull String requestId, @Nonnull String path) {
            if (value == null) {
                return List.of();
            }
            if (!(value instanceof List<?> rawList)) {
                throw NpcRuntimeRequest.invalid(requestId, path + " must be an array");
            }
            List<String> result = new ArrayList<>();
            for (Object item : rawList) {
                String text = stringOrNull(item);
                if (text == null) {
                    throw NpcRuntimeRequest.invalid(requestId, path + " must contain only strings");
                }
                result.add(text);
            }
            return List.copyOf(result);
        }
    }
}
