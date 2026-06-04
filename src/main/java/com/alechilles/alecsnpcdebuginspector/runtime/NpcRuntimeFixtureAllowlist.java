package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Keeps runtime fixture mutation scoped to known, deterministic fixture ids and safe spawn modes.
 */
public final class NpcRuntimeFixtureAllowlist {
    @Nonnull
    public static NpcRuntimeFixtureAllowlist defaults() {
        return new NpcRuntimeFixtureAllowlist();
    }

    void validate(@Nonnull List<NpcRuntimeFixtureSpec> specs, @Nonnull String requestId) {
        List<NpcRuntimeRequest.UnsupportedField> unsupported = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int npcUnderTestCount = 0;

        for (int i = 0; i < specs.size(); i++) {
            NpcRuntimeFixtureSpec spec = specs.get(i);
            String path = "fixtures.list[" + i + "]";
            if (!seen.add(spec.fixtureId())) {
                unsupported.add(new NpcRuntimeRequest.UnsupportedField(path + ".fixtureId", "duplicate fixture id: " + spec.fixtureId()));
            }
            if (spec.kind() == NpcRuntimeFixtureKind.NPC_UNDER_TEST) {
                npcUnderTestCount++;
            }
            if (!allowsId(spec)) {
                unsupported.add(new NpcRuntimeRequest.UnsupportedField(path + ".fixtureId", "fixture id is not allowlisted for kind " + spec.kind().jsonName()));
            }
            if (!spec.kind().entityLike() && !isDeclarativeFixture(spec.kind())) {
                unsupported.add(new NpcRuntimeRequest.UnsupportedField(path + ".kind", "fixture kind parses but safe world mutation is not implemented yet"));
            }
            if (spec.entityId() != null) {
                unsupported.add(new NpcRuntimeRequest.UnsupportedField(path + ".entityId", "direct entity id spawning is not implemented; use roleId for NPC-backed fixtures"));
            }
            if (spec.kind().entityLike() && (spec.roleId() == null || spec.roleId().isBlank())) {
                unsupported.add(new NpcRuntimeRequest.UnsupportedField(path + ".roleId", "entity-like fixtures require a roleId"));
            }
            validateReference(spec.leaderFixtureId(), path + ".leaderFixtureId", specs, unsupported);
            validateReference(spec.parentFixtureId(), path + ".parentFixtureId", specs, unsupported);
            validateReference(spec.senderFixtureId(), path + ".senderFixtureId", specs, unsupported);
            validateReference(spec.receiverFixtureId(), path + ".receiverFixtureId", specs, unsupported);
            validateReference(spec.sourceFixtureId(), path + ".sourceFixtureId", specs, unsupported);
            validateReference(spec.targetFixtureId(), path + ".targetFixtureId", specs, unsupported);
            for (int consumerIndex = 0; consumerIndex < spec.requiredConsumerFixtureIds().size(); consumerIndex++) {
                Object consumerFixtureId = spec.requiredConsumerFixtureIds().get(consumerIndex);
                validateReference(
                        consumerFixtureId instanceof String text ? text : null,
                        path + ".requiredConsumerFixtureIds[" + consumerIndex + "]",
                        specs,
                        unsupported
                );
            }
        }

        if (npcUnderTestCount != 1) {
            unsupported.add(new NpcRuntimeRequest.UnsupportedField("fixtures.list", "exactly one npcUnderTest fixture is required"));
        }

        if (!unsupported.isEmpty()) {
            throw NpcRuntimeRequest.unsupportedFixture(requestId, "request contains unsupported fixture semantics", unsupported);
        }
    }

    private boolean allowsId(@Nonnull NpcRuntimeFixtureSpec spec) {
        String id = spec.fixtureId();
        return switch (spec.kind()) {
            case NPC_UNDER_TEST -> "npcUnderTest".equals(id) || "npc_under_test".equals(id);
            case TARGET_DUMMY -> "targetDummy".equals(id) || id.startsWith("target.") || id.startsWith("target-");
            case NPC -> id.startsWith("npc.") || id.startsWith("npc-");
            case MOB -> id.startsWith("mob.") || id.startsWith("mob-");
            case ITEM -> id.startsWith("item.") || id.startsWith("item-");
            case BLOCK -> id.startsWith("block.") || id.startsWith("block-");
            case BEACON -> id.startsWith("beacon.") || id.startsWith("beacon-");
            case MESSAGE -> id.startsWith("message.") || id.startsWith("message-");
            case PLAYER_ANCHOR -> "playerAnchor".equals(id) || id.startsWith("playerAnchor.") || id.startsWith("anchor.");
            case FAMILY_MEMBER -> id.startsWith("family.") || id.startsWith("family-");
            case FLOCK_MEMBER -> id.startsWith("flock.") || id.startsWith("flock-");
        };
    }

    private boolean isDeclarativeFixture(@Nonnull NpcRuntimeFixtureKind kind) {
        return kind == NpcRuntimeFixtureKind.MESSAGE
                || kind == NpcRuntimeFixtureKind.BEACON
                || kind == NpcRuntimeFixtureKind.PLAYER_ANCHOR;
    }

    private void validateReference(String referencedFixtureId,
                                   String path,
                                   List<NpcRuntimeFixtureSpec> specs,
                                   List<NpcRuntimeRequest.UnsupportedField> unsupported) {
        if (referencedFixtureId == null || referencedFixtureId.isBlank()) {
            return;
        }
        boolean exists = specs.stream().anyMatch(spec -> referencedFixtureId.equals(spec.fixtureId()));
        if (!exists) {
            unsupported.add(new NpcRuntimeRequest.UnsupportedField(path, "referenced fixture id does not exist: " + referencedFixtureId));
        }
    }
}
