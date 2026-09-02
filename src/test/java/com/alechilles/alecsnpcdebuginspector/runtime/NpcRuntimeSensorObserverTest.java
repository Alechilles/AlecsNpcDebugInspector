package com.alechilles.alecsnpcdebuginspector.runtime;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeSensorObserverTest {
    @Test
    void emitsTargetSlotAndTimerSensorEvidence() {
        NpcRuntimeObservedNpc observed = NpcRuntimeObservedNpc.fromSnapshot(
                null,
                new NpcDebugSnapshot(
                        "NPC Debug Inspector",
                        "",
                        """
                                === Targeting / Sensors ===
                                - Target LockedTarget: <none>
                                - Target MasterTarget: Tamework Example (abc)
                                - Sensor Scope Keys: 2

                                === Timers / Cooldowns ===
                                - Attack Executing: false
                                """
                )
        );

        List<NpcRuntimeTraceRecord> records = new NpcRuntimeSensorObserver().traceRecords("request-a", 2, observed);

        assertEquals(4, records.size());
        assertTrue(records.stream().allMatch(record -> "sensor-evidence".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "TargetSlot".equals(record.fields().get("sensorType"))
                && "not-matched".equals(record.fields().get("matchResult"))));
        assertTrue(records.stream().anyMatch(record -> "TargetSlot".equals(record.fields().get("sensorType"))
                && "matched".equals(record.fields().get("matchResult"))));
        assertTrue(records.stream().anyMatch(record -> record.toJson().contains("unsupportedFields")));
        assertTrue(records.stream().anyMatch(record -> "Timer".equals(record.fields().get("sensorType"))));
    }

    @Test
    void enrichesTargetSlotEvidenceFromFixtureMetadata() {
        NpcRuntimeObservedNpc observed = NpcRuntimeObservedNpc.fromSnapshot(
                null,
                new NpcDebugSnapshot(
                        "NPC Debug Inspector",
                        "",
                        """
                                === Targeting / Sensors ===
                                - Target Enemy: Tamework Example (abc)
                                """
                )
        );
        List<NpcRuntimeFixtureSpec> fixtures = List.of(
                fixture("npcUnderTest", "npcUnderTest", List.of(0, 64, 0), null, List.of(), null, null, null),
                fixtureWithRadius("target.enemy", "targetDummy", List.of(3, 64, 4), "Enemy", List.of("hostile", "close"), "enemyFaction", "aggressive", true, 6.0)
        );

        NpcRuntimeTraceRecord record = new NpcRuntimeSensorObserver()
                .traceRecords("request-a", 2, observed, fixtures)
                .stream()
                .filter(item -> "TargetSlot".equals(item.fields().get("sensorType")))
                .findFirst()
                .orElseThrow();

        assertEquals("target.enemy", record.fields().get("targetFixtureId"));
        assertEquals(5.0, record.fields().get("distance"));
        assertEquals("medium", record.fields().get("distanceBand"));
        assertEquals(6.0, record.fields().get("configuredRange"));
        assertEquals(true, record.fields().get("rangeThresholdMet"));
        assertEquals(true, record.fields().get("visibility"));
        assertEquals(true, record.fields().get("lineOfSight"));
        assertEquals(List.of("hostile", "close"), record.fields().get("tags"));
        assertEquals("enemyFaction", record.fields().get("faction"));
        assertEquals("aggressive", record.fields().get("attitude"));
        assertFalse(((List<?>) record.fields().get("unsupportedFields")).contains("distance"));
        assertFalse(((List<?>) record.fields().get("unsupportedFields")).contains("distanceBand"));
        assertFalse(((List<?>) record.fields().get("unsupportedFields")).contains("configuredRange"));
        assertFalse(((List<?>) record.fields().get("unsupportedFields")).contains("rangeThresholdMet"));
        assertFalse(((List<?>) record.fields().get("unsupportedFields")).contains("targetFixtureId"));
    }

    @Test
    void emitsTimerAlarmAndFlagEvidenceWithCommonFields() {
        NpcRuntimeObservedNpc observed = NpcRuntimeObservedNpc.fromSnapshot(
                null,
                new NpcDebugSnapshot(
                        "NPC Debug Inspector",
                        "",
                        """
                                === Timers / Cooldowns ===
                                - Attack Cooldown Active: true

                                === Alarms ===
                                - Protect Baby: false

                                === Flags ===
                                - Can Forage: true
                                """
                )
        );

        List<NpcRuntimeTraceRecord> records = new NpcRuntimeSensorObserver().traceRecords("request-a", 2, observed);

        assertTrue(records.stream().anyMatch(record -> "Timer".equals(record.fields().get("sensorType"))
                && "attackCooldownActive".equals(record.fields().get("sourceField"))
                && "matched".equals(record.fields().get("matchResult"))));
        assertTrue(records.stream().anyMatch(record -> "Alarm".equals(record.fields().get("sensorType"))
                && "protectBaby".equals(record.fields().get("sourceField"))
                && "not-matched".equals(record.fields().get("matchResult"))));
        assertTrue(records.stream().anyMatch(record -> "Flag".equals(record.fields().get("sensorType"))
                && "canForage".equals(record.fields().get("sourceField"))
                && "matched".equals(record.fields().get("matchResult"))));
        assertTrue(records.stream().allMatch(record -> record.fields().containsKey("observedValue")
                && record.fields().containsKey("sourceSection")
                && record.fields().containsKey("sourceField")));
    }


    private static NpcRuntimeFixtureSpec fixtureWithRadius(String fixtureId,
                                                           String kind,
                                                           List<Object> position,
                                                           String targetSlot,
                                                           List<Object> tags,
                                                           String faction,
                                                           String attitude,
                                                           Boolean visible,
                                                           Double radius) {
        return NpcRuntimeFixtureSpec.fromMap(
                Map.ofEntries(
                        Map.entry("fixtureId", fixtureId),
                        Map.entry("kind", kind),
                        Map.entry("position", position),
                        Map.entry("tags", tags),
                        Map.entry("roleId", "Role"),
                        Map.entry("targetSlot", targetSlot != null ? targetSlot : ""),
                        Map.entry("faction", faction != null ? faction : ""),
                        Map.entry("attitude", attitude != null ? attitude : ""),
                        Map.entry("visible", visible != null ? visible : true),
                        Map.entry("radius", radius != null ? radius : 0)
                ),
                "request-a",
                "fixtures.list[]",
                "Role"
        );
    }

    private static NpcRuntimeFixtureSpec fixture(String fixtureId,
                                                 String kind,
                                                 List<Object> position,
                                                 String targetSlot,
                                                 List<Object> tags,
                                                 String faction,
                                                 String attitude,
                                                 Boolean visible) {
        return NpcRuntimeFixtureSpec.fromMap(
                Map.ofEntries(
                        Map.entry("fixtureId", fixtureId),
                        Map.entry("kind", kind),
                        Map.entry("position", position),
                        Map.entry("tags", tags),
                        Map.entry("roleId", "Role"),
                        Map.entry("targetSlot", targetSlot != null ? targetSlot : ""),
                        Map.entry("faction", faction != null ? faction : ""),
                        Map.entry("attitude", attitude != null ? attitude : ""),
                        Map.entry("visible", visible != null ? visible : true)
                ),
                "request-a",
                "fixtures.list[]",
                "Role"
        );
    }
}
