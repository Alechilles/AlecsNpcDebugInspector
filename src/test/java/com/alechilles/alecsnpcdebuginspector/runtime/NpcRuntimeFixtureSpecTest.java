package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeFixtureSpecTest {
    @Test
    void parsesRichNpcBackedFixtureFields() {
        NpcRuntimeFixtureSpec spec = NpcRuntimeFixtureSpec.fromMap(
                Map.ofEntries(
                        Map.entry("fixtureId", "target.Enemy"),
                        Map.entry("kind", "targetDummy"),
                        Map.entry("position", List.of(4, 64, 0)),
                        Map.entry("rotation", List.of(0, 90, 0)),
                        Map.entry("tags", List.of("hostile", "close")),
                        Map.entry("roleId", "Mob_Tamework_Example_Simple"),
                        Map.entry("targetSlot", "Enemy"),
                        Map.entry("visible", true),
                        Map.entry("health", 25),
                        Map.entry("faction", "hostile"),
                        Map.entry("attitude", "aggressive")
                ),
                "rich_fixture",
                "fixtures.list[1]",
                "FallbackRole"
        );

        assertEquals("target.Enemy", spec.fixtureId());
        assertEquals(NpcRuntimeFixtureKind.TARGET_DUMMY, spec.kind());
        assertEquals("Mob_Tamework_Example_Simple", spec.roleId());
        assertEquals("Enemy", spec.targetSlot());
        assertEquals(25, spec.health());
        assertEquals("hostile", spec.faction());
        assertEquals("aggressive", spec.attitude());
        assertTrue(spec.toMap().toString().contains("targetSlot=Enemy"));
    }

    @Test
    void parsesTameworkMutationFieldsAndNormalizesCommandAlias() {
        NpcRuntimeFixtureSpec spec = NpcRuntimeFixtureSpec.fromMap(
                Map.of(
                        "fixtureId", "npc_under_test",
                        "kind", "npcUnderTest",
                        "tamework", Map.of(
                                "tamed", true,
                                "owner", Map.of("type", "syntheticPlayer", "id", "owner_a"),
                                "needs", Map.of("hunger", 80, "thirst", 60),
                                "effects", List.of("tamework:well_fed"),
                                "command", "follow",
                                "lifeStage", "adult"
                        )
                ),
                "tamework_fixture",
                "fixtures.list[0]",
                "FallbackRole"
        );

        NpcRuntimeFixtureSpec.TameworkMutation mutation = spec.tamework();
        assertEquals(true, mutation.tamed());
        assertEquals("owner_a", mutation.owner().get("id"));
        assertEquals(80, mutation.needs().get("hunger"));
        assertEquals("tamework:well_fed", mutation.effects().getFirst());
        assertEquals("follow", mutation.commandState());
        assertEquals("adult", mutation.lifeStage());
        assertTrue(spec.toMap().toString().contains("commandState=follow"));
    }

    @Test
    void mapsLegacyDummyTargetsToCanonicalTargetDummySpecs() {
        NpcRuntimeRequest.TargetFixture target = new NpcRuntimeRequest.TargetFixture(
                "LockedTarget",
                "dummy",
                List.of(2, 64, 0),
                List.of("hostile"),
                true
        );

        NpcRuntimeFixtureSpec spec = NpcRuntimeFixtureSpec.fromLegacyTarget(target, "Role_Default", "legacy_request");

        assertEquals("target.LockedTarget", spec.fixtureId());
        assertEquals(NpcRuntimeFixtureKind.TARGET_DUMMY, spec.kind());
        assertEquals("Role_Default", spec.roleId());
        assertEquals("LockedTarget", spec.targetSlot());
    }

    @Test
    void parsesFlockFamilyRelationshipFields() {
        NpcRuntimeFixtureSpec spec = NpcRuntimeFixtureSpec.fromMap(
                Map.of(
                        "fixtureId", "flock.guard_1",
                        "kind", "flockMember",
                        "roleId", "Boar",
                        "flockId", "guard-boars",
                        "flockRole", "follower",
                        "familyId", "boar-family",
                        "familyRole", "adult",
                        "leaderFixtureId", "npcUnderTest",
                        "parentFixtureId", "family.parent"
                ),
                "relationship_fixture",
                "fixtures.list[1]",
                "FallbackRole"
        );

        assertEquals(NpcRuntimeFixtureKind.FLOCK_MEMBER, spec.kind());
        assertEquals("guard-boars", spec.flockId());
        assertEquals("follower", spec.flockRole());
        assertEquals("boar-family", spec.familyId());
        assertEquals("adult", spec.familyRole());
        assertEquals("npcUnderTest", spec.leaderFixtureId());
        assertEquals("family.parent", spec.parentFixtureId());
        assertTrue(spec.toMap().toString().contains("leaderFixtureId=npcUnderTest"));
    }

    @Test
    void parsesBeaconFixtureKindButLeavesSafeMutationToAllowlist() {
        NpcRuntimeFixtureSpec spec = NpcRuntimeFixtureSpec.fromMap(
                Map.of(
                        "fixtureId", "beacon.combat",
                        "kind", "beacon",
                        "position", List.of(0, 64, 3),
                        "blockId", "hytale:stone"
                ),
                "beacon_fixture",
                "fixtures.list[1]",
                "FallbackRole"
        );

        assertEquals(NpcRuntimeFixtureKind.BEACON, spec.kind());
        assertEquals("beacon.combat", spec.fixtureId());
        assertEquals("hytale:stone", spec.blockId());
    }

    @Test
    void rejectsMalformedPositionRotationAndTags() {
        assertThrows(NpcRuntimeRequest.ValidationException.class, () -> NpcRuntimeFixtureSpec.fromMap(
                Map.of("fixtureId", "npc.bad", "kind", "npc", "position", List.of(0, 64)),
                "bad_position",
                "fixtures.list[0]",
                "FallbackRole"
        ));
        assertThrows(NpcRuntimeRequest.ValidationException.class, () -> NpcRuntimeFixtureSpec.fromMap(
                Map.of("fixtureId", "npc.bad", "kind", "npc", "rotation", List.of(0, "east", 0)),
                "bad_rotation",
                "fixtures.list[0]",
                "FallbackRole"
        ));
        assertThrows(NpcRuntimeRequest.ValidationException.class, () -> NpcRuntimeFixtureSpec.fromMap(
                Map.of("fixtureId", "npc.bad", "kind", "npc", "tags", List.of("hostile", 7)),
                "bad_tags",
                "fixtures.list[0]",
                "FallbackRole"
        ));
    }
}
