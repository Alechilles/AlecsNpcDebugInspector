package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeFixtureSpecTest {
    @Test
    void parsesRichNpcBackedFixtureFields() {
        NpcRuntimeFixtureSpec spec = NpcRuntimeFixtureSpec.fromMap(
                Map.of(
                        "fixtureId", "target.Enemy",
                        "kind", "targetDummy",
                        "position", List.of(4, 64, 0),
                        "rotation", List.of(0, 90, 0),
                        "tags", List.of("hostile", "close"),
                        "roleId", "Mob_Tamework_Example_Simple",
                        "targetSlot", "Enemy",
                        "visible", true,
                        "faction", "hostile",
                        "attitude", "aggressive"
                ),
                "rich_fixture",
                "fixtures.list[1]",
                "FallbackRole"
        );

        assertEquals("target.Enemy", spec.fixtureId());
        assertEquals(NpcRuntimeFixtureKind.TARGET_DUMMY, spec.kind());
        assertEquals("Mob_Tamework_Example_Simple", spec.roleId());
        assertEquals("Enemy", spec.targetSlot());
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
}
