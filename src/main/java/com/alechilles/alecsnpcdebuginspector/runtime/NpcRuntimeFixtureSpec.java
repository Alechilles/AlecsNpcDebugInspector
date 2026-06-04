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
        @Nullable String entityId,
        @Nullable String blockId,
        @Nullable String itemId,
        @Nullable String targetSlot,
        boolean visible,
        @Nullable String faction,
        @Nullable String attitude
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
                true,
                null,
                null
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
                slot,
                target.visible(),
                null,
                null
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
                        "entityId", "blockId", "itemId", "slot", "targetSlot", "visible", "faction", "attitude"),
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
        if (roleId == null && kind.entityLike()) {
            roleId = defaultRoleId;
        }

        return new NpcRuntimeFixtureSpec(
                resolvedFixtureId,
                kind,
                listOrDefault(data.get("position"), DEFAULT_POSITION, requestId, path + ".position"),
                asList(data.get("rotation"), requestId, path + ".rotation"),
                asList(data.get("tags"), requestId, path + ".tags"),
                roleId,
                stringOrNull(data.get("entityId")),
                stringOrNull(data.get("blockId")),
                stringOrNull(data.get("itemId")),
                firstPresentString(data, "targetSlot", "slot"),
                boolValue(data.get("visible"), true, requestId, path + ".visible"),
                stringOrNull(data.get("faction")),
                stringOrNull(data.get("attitude"))
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
        if (entityId != null) {
            map.put("entityId", entityId);
        }
        if (blockId != null) {
            map.put("blockId", blockId);
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

    @Nonnull
    private static List<Object> listOrDefault(@Nullable Object value,
                                              @Nonnull List<Object> defaultValue,
                                              @Nonnull String requestId,
                                              @Nonnull String path) {
        List<Object> list = asList(value, requestId, path);
        return list.isEmpty() ? defaultValue : list;
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
}
