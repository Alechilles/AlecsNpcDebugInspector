package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.Locale;
import javax.annotation.Nonnull;

/**
 * Fixture kinds accepted by the runtime request schema.
 */
public enum NpcRuntimeFixtureKind {
    NPC_UNDER_TEST("npcUnderTest", true),
    TARGET_DUMMY("targetDummy", true),
    NPC("npc", true),
    MOB("mob", true),
    ITEM("item", false),
    BLOCK("block", false),
    BEACON("beacon", false),
    PLAYER_ANCHOR("playerAnchor", false),
    FAMILY_MEMBER("familyMember", true),
    FLOCK_MEMBER("flockMember", true);

    private final String jsonName;
    private final boolean entityLike;

    NpcRuntimeFixtureKind(@Nonnull String jsonName, boolean entityLike) {
        this.jsonName = jsonName;
        this.entityLike = entityLike;
    }

    @Nonnull
    public String jsonName() {
        return jsonName;
    }

    public boolean entityLike() {
        return entityLike;
    }

    @Nonnull
    static NpcRuntimeFixtureKind fromJson(@Nonnull String value, @Nonnull String requestId, @Nonnull String path) {
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw NpcRuntimeRequest.invalid(requestId, path + " is required");
        }
        if ("dummy".equalsIgnoreCase(normalized) || "target".equalsIgnoreCase(normalized)) {
            return TARGET_DUMMY;
        }
        String folded = normalized.toLowerCase(Locale.ROOT);
        for (NpcRuntimeFixtureKind kind : values()) {
            if (kind.jsonName.toLowerCase(Locale.ROOT).equals(folded)) {
                return kind;
            }
        }
        throw NpcRuntimeRequest.unsupportedFixture(
                requestId,
                "request contains unsupported fixture kind",
                java.util.List.of(new NpcRuntimeRequest.UnsupportedField(path, "unsupported fixture kind: " + value))
        );
    }
}
