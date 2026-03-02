package com.alechilles.alecsnpcdebuginspector.debug;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.group.EntityGroup;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.flock.FlockMembership;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.storage.AlarmStore;
import com.hypixel.hytale.server.npc.util.Alarm;
import com.hypixel.hytale.server.npc.util.expression.StdScope;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Builds read-only debug snapshots for a specific NPC.
 */
public final class NpcDebugSnapshotService {
    private static final DateTimeFormatter UTC_TIME_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final String[] BOOLEAN_SCOPE_KEYS = {
            "Tamework_Baby_DirectFollow",
            "LeashEnabled",
            "HookHasTargetPosition"
    };
    private static final String[] NUMBER_SCOPE_KEYS = {
            "FlockFollowerWanderRadius",
            "FlockFollowerStopDistance",
            "FlockFollowerRelativeSpeed",
            "HookTargetX",
            "HookTargetY",
            "HookTargetZ"
    };
    private static final String[] FALLBACK_ALARM_KEYS = {
            "Harvest_Ready",
            "Tamework_Baby_DirectFollow_Window"
    };

    /**
     * Captures a detail snapshot for the target NPC.
     */
    @Nonnull
    public NpcDebugSnapshot capture(@Nullable UUID targetUuid,
                                    @Nullable Ref<EntityStore> targetRef,
                                    @Nonnull Store<EntityStore> store) {
        Instant gameTime = resolveGameTime(store);
        UUID resolvedUuid = targetUuid;
        boolean loaded = targetRef != null && targetRef.isValid();
        NPCEntity npc = loaded ? store.getComponent(targetRef, NPCEntity.getComponentType()) : null;
        if (npc == null) {
            loaded = false;
        }
        if (resolvedUuid == null && npc != null) {
            resolvedUuid = npc.getUuid();
        }

        String uuidText = resolvedUuid != null ? resolvedUuid.toString() : "<unknown>";
        String title = "NPC Debug Inspector";
        String subtitle = "UUID: " + uuidText
                + " | Loaded: " + loaded
                + " | Game Time (UTC): " + formatInstant(gameTime);

        StringBuilder details = new StringBuilder();
        appendSection(details, "Overview", buildOverview(uuidText, targetRef, store, npc));
        if (!loaded || targetRef == null || npc == null) {
            appendSection(details, "State", "Target NPC is not currently loaded.");
            appendSection(details, "Alarms", "Target NPC is not currently loaded.");
            appendSection(details, "Flags", "Target NPC is not currently loaded.");
            appendSection(details, "Components", "Target NPC is not currently loaded.");
            appendSection(details, "Flock", "Target NPC is not currently loaded.");
            return new NpcDebugSnapshot(title, subtitle, details.toString().trim());
        }

        appendSection(details, "State", buildStateSection(npc));
        appendSection(details, "Alarms", buildAlarmSection(npc, gameTime));
        appendSection(details, "Flags", buildFlagSection(npc));
        appendSection(details, "Components", buildComponentSection(targetRef, store, npc));
        appendSection(details, "Flock", buildFlockSection(targetRef, store, npc));
        return new NpcDebugSnapshot(title, subtitle, details.toString().trim());
    }

    @Nonnull
    private String buildOverview(@Nonnull String uuidText,
                                 @Nullable Ref<EntityStore> npcRef,
                                 @Nonnull Store<EntityStore> store,
                                 @Nullable NPCEntity npc) {
        StringBuilder sb = new StringBuilder();
        sb.append("UUID: ").append(uuidText).append('\n');
        sb.append("Entity Ref Valid: ").append(npcRef != null && npcRef.isValid()).append('\n');
        if (npc == null) {
            sb.append("Loaded: false").append('\n');
            return sb.toString().trim();
        }
        String roleId = resolveRoleId(npc);
        sb.append("Loaded: true").append('\n');
        sb.append("Role Name: ").append(nonBlankOrFallback(npc.getRoleName(), "<unknown>")).append('\n');
        sb.append("Role Id: ").append(nonBlankOrFallback(roleId, "<unknown>")).append('\n');
        sb.append("State Name: ").append(resolveStateName(npc)).append('\n');
        sb.append("Health: ").append(resolveHealthText(npcRef, store)).append('\n');
        sb.append("Position: ").append(resolvePositionText(npcRef, store)).append('\n');
        return sb.toString().trim();
    }

    @Nonnull
    private String buildStateSection(@Nonnull NPCEntity npc) {
        Role role = npc.getRole();
        if (role == null) {
            return "Role is null.";
        }
        StateSupport stateSupport = role.getStateSupport();
        if (stateSupport == null) {
            return "StateSupport is null.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Current State Name: ").append(nonBlankOrFallback(stateSupport.getStateName(), "<unknown>")).append('\n');
        int index = stateSupport.getStateIndex();
        sb.append("Current State Index: ").append(index).append('\n');
        String defaultSubState = stateSupport.getStateHelper() != null
                ? stateSupport.getStateHelper().getDefaultSubState()
                : null;
        sb.append("Default Sub-State: ").append(nonBlankOrFallback(defaultSubState, "<none>")).append('\n');
        sb.append("Has Active State: ").append(index != StateSupport.NO_STATE);
        return sb.toString().trim();
    }

    @Nonnull
    private String buildAlarmSection(@Nonnull NPCEntity npc, @Nonnull Instant gameTime) {
        AlarmStore alarmStore = npc.getAlarmStore();
        if (alarmStore == null) {
            return "AlarmStore is null.";
        }
        Map<String, Alarm> alarms = tryReadAlarmMap(alarmStore);
        if (alarms.isEmpty()) {
            alarms = buildFallbackAlarmMap(npc, alarmStore);
        }
        if (alarms.isEmpty()) {
            return "No alarm entries were discovered.";
        }
        List<Map.Entry<String, Alarm>> ordered = new ArrayList<>(alarms.entrySet());
        ordered.sort(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));

        StringBuilder sb = new StringBuilder();
        sb.append("Alarm Count: ").append(ordered.size()).append('\n');
        for (Map.Entry<String, Alarm> entry : ordered) {
            Alarm alarm = entry.getValue();
            String status = resolveAlarmStatus(alarm, gameTime);
            String remaining = resolveAlarmRemainingText(alarm, gameTime);
            sb.append("- ").append(entry.getKey()).append(": ").append(status);
            if (remaining != null) {
                sb.append(" (").append(remaining).append(')');
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    @Nonnull
    private String buildFlagSection(@Nonnull NPCEntity npc) {
        Role role = npc.getRole();
        if (role == null || role.getEntitySupport() == null) {
            return "Sensor scope unavailable (role/entity support missing).";
        }
        StdScope scope = role.getEntitySupport().getSensorScope();
        if (scope == null) {
            return "Sensor scope is null.";
        }

        StringBuilder sb = new StringBuilder();
        int resolved = 0;
        for (String key : BOOLEAN_SCOPE_KEYS) {
            Boolean value = readBooleanScope(scope, key);
            if (value == null) {
                continue;
            }
            resolved++;
            sb.append("- ").append(key).append(": ").append(value).append('\n');
        }
        for (String key : NUMBER_SCOPE_KEYS) {
            Double value = readNumberScope(scope, key);
            if (value == null) {
                continue;
            }
            resolved++;
            sb.append("- ").append(key).append(": ").append(formatNumber(value)).append('\n');
        }
        if (resolved <= 0) {
            return "No tracked scope keys were available.";
        }
        sb.insert(0, "Resolved Keys: " + resolved + '\n');
        return sb.toString().trim();
    }

    @Nonnull
    private String buildComponentSection(@Nonnull Ref<EntityStore> npcRef,
                                         @Nonnull Store<EntityStore> store,
                                         @Nonnull NPCEntity npc) {
        StringBuilder sb = new StringBuilder();
        sb.append("- NPCEntity: true").append('\n');
        sb.append("- TransformComponent: ").append(store.getComponent(npcRef, TransformComponent.getComponentType()) != null).append('\n');
        sb.append("- EntityStatMap: ").append(store.getComponent(npcRef, EntityStatMap.getComponentType()) != null).append('\n');
        ComponentType<EntityStore, FlockMembership> flockType = FlockMembership.getComponentType();
        sb.append("- FlockMembership: ").append(flockType != null && store.getComponent(npcRef, flockType) != null).append('\n');
        sb.append("- AlarmStore: ").append(npc.getAlarmStore() != null).append('\n');
        sb.append("- Role: ").append(npc.getRole() != null).append('\n');
        return sb.toString().trim();
    }

    @Nonnull
    private String buildFlockSection(@Nonnull Ref<EntityStore> npcRef,
                                     @Nonnull Store<EntityStore> store,
                                     @Nonnull NPCEntity npc) {
        ComponentType<EntityStore, FlockMembership> flockType = FlockMembership.getComponentType();
        if (flockType == null) {
            return "FlockMembership component type unavailable.";
        }
        FlockMembership membership = store.getComponent(npcRef, flockType);
        if (membership == null) {
            return "NPC has no flock membership.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Membership Type: ").append(membership.getMembershipType()).append('\n');
        sb.append("Flock Id: ").append(membership.getFlockId()).append('\n');
        sb.append("Flock Ref Valid: ").append(membership.getFlockRef() != null && membership.getFlockRef().isValid()).append('\n');
        sb.append("Leash Point: ").append(formatVector(npc.getLeashPoint())).append('\n');

        Ref<EntityStore> flockRef = membership.getFlockRef();
        if (flockRef != null && flockRef.isValid()) {
            EntityGroup group = store.getComponent(flockRef, EntityGroup.getComponentType());
            if (group != null) {
                Ref<EntityStore> leaderRef = group.getLeaderRef();
                boolean hasLeader = leaderRef != null && leaderRef.isValid();
                sb.append("Has Leader: ").append(hasLeader).append('\n');
                if (hasLeader) {
                    sb.append("Leader Ref: ").append(leaderRef).append('\n');
                    sb.append("Distance To Leader: ").append(resolveDistanceBetween(npcRef, leaderRef, store)).append('\n');
                }
            }
        }
        return sb.toString().trim();
    }

    @Nonnull
    private String resolveStateName(@Nonnull NPCEntity npc) {
        Role role = npc.getRole();
        if (role == null || role.getStateSupport() == null) {
            return "<unknown>";
        }
        return nonBlankOrFallback(role.getStateSupport().getStateName(), "<unknown>");
    }

    @Nonnull
    private String resolveRoleId(@Nonnull NPCEntity npc) {
        int roleIndex = npc.getRoleIndex();
        if (roleIndex >= 0) {
            NPCPlugin npcPlugin = NPCPlugin.get();
            if (npcPlugin != null) {
                String roleId = npcPlugin.getName(roleIndex);
                if (roleId != null && !roleId.isBlank()) {
                    return roleId;
                }
            }
        }
        return nonBlankOrFallback(npc.getRoleName(), "<unknown>");
    }

    @Nonnull
    private String resolveHealthText(@Nullable Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        if (npcRef == null || !npcRef.isValid()) {
            return "n/a";
        }
        ComponentType<EntityStore, EntityStatMap> statType = EntityStatMap.getComponentType();
        if (statType == null) {
            return "n/a";
        }
        EntityStatMap statMap = store.getComponent(npcRef, statType);
        if (statMap == null) {
            return "n/a";
        }
        int healthIndex = EntityStatType.getAssetMap().getIndex("Health");
        if (healthIndex < 0) {
            return "n/a";
        }
        EntityStatValue value = statMap.get(healthIndex);
        if (value == null) {
            return "n/a";
        }
        int current = Math.max(0, Math.round(value.get()));
        int max = Math.max(1, Math.round(value.getMax()));
        if (current > max) {
            current = max;
        }
        return current + "/" + max;
    }

    @Nonnull
    private String resolvePositionText(@Nullable Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        if (npcRef == null || !npcRef.isValid()) {
            return "n/a";
        }
        TransformComponent transform = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (transform == null) {
            return "n/a";
        }
        return formatVector(transform.getPosition());
    }

    @Nonnull
    private String resolveDistanceBetween(@Nonnull Ref<EntityStore> fromRef,
                                          @Nonnull Ref<EntityStore> toRef,
                                          @Nonnull Store<EntityStore> store) {
        TransformComponent from = store.getComponent(fromRef, TransformComponent.getComponentType());
        TransformComponent to = store.getComponent(toRef, TransformComponent.getComponentType());
        if (from == null || to == null) {
            return "n/a";
        }
        return formatNumber(from.getPosition().distanceTo(to.getPosition()));
    }

    @Nonnull
    private String resolveAlarmStatus(@Nullable Alarm alarm, @Nonnull Instant gameTime) {
        if (alarm == null) {
            return "missing";
        }
        if (!alarm.isSet()) {
            return "unset";
        }
        if (alarm.hasPassed(gameTime)) {
            return "passed";
        }
        return "active";
    }

    @Nullable
    private String resolveAlarmRemainingText(@Nullable Alarm alarm, @Nonnull Instant gameTime) {
        if (alarm == null || !alarm.isSet() || alarm.hasPassed(gameTime)) {
            return null;
        }
        Instant alarmInstant = readAlarmInstant(alarm);
        if (alarmInstant == null) {
            return null;
        }
        Duration remaining = Duration.between(gameTime, alarmInstant);
        if (remaining.isNegative()) {
            return "0s";
        }
        return formatDuration(remaining);
    }

    @Nonnull
    private Map<String, Alarm> tryReadAlarmMap(@Nonnull AlarmStore alarmStore) {
        for (Field field : alarmStore.getClass().getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object raw = field.get(alarmStore);
                if (!(raw instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                Map<String, Alarm> out = new HashMap<>();
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof Alarm alarm)) {
                        continue;
                    }
                    out.put(key, alarm);
                }
                if (!out.isEmpty()) {
                    return out;
                }
            } catch (ReflectiveOperationException ignored) {
                // Ignore reflection failures and fall back to known alarm names.
            }
        }
        return Map.of();
    }

    @Nonnull
    private Map<String, Alarm> buildFallbackAlarmMap(@Nonnull NPCEntity npc, @Nonnull AlarmStore alarmStore) {
        Map<String, Alarm> out = new HashMap<>();
        for (String key : FALLBACK_ALARM_KEYS) {
            Alarm alarm = alarmStore.get(npc, key);
            if (alarm != null) {
                out.put(key, alarm);
            }
        }
        return out;
    }

    @Nullable
    private Instant readAlarmInstant(@Nonnull Alarm alarm) {
        try {
            Field field = Alarm.class.getDeclaredField("alarmInstant");
            field.setAccessible(true);
            Object value = field.get(alarm);
            if (value instanceof Instant instant) {
                return instant;
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    @Nullable
    private Boolean readBooleanScope(@Nonnull StdScope scope, @Nonnull String key) {
        try {
            BooleanSupplier supplier = scope.getBooleanSupplier(key);
            return supplier != null ? supplier.getAsBoolean() : null;
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    @Nullable
    private Double readNumberScope(@Nonnull StdScope scope, @Nonnull String key) {
        try {
            DoubleSupplier supplier = scope.getNumberSupplier(key);
            return supplier != null ? supplier.getAsDouble() : null;
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    @Nonnull
    private Instant resolveGameTime(@Nonnull Store<EntityStore> store) {
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        return time != null ? time.getGameTime() : Instant.now();
    }

    @Nonnull
    private String formatInstant(@Nonnull Instant instant) {
        return UTC_TIME_FORMAT.format(instant);
    }

    @Nonnull
    private String formatDuration(@Nonnull Duration duration) {
        long seconds = Math.max(0L, duration.getSeconds());
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        StringBuilder sb = new StringBuilder();
        if (days > 0L) {
            sb.append(days).append("d ");
        }
        if (hours > 0L || days > 0L) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0L || hours > 0L || days > 0L) {
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    @Nonnull
    private String formatVector(@Nullable Vector3d vector) {
        if (vector == null) {
            return "n/a";
        }
        return "("
                + formatNumber(vector.x) + ", "
                + formatNumber(vector.y) + ", "
                + formatNumber(vector.z) + ")";
    }

    @Nonnull
    private String formatNumber(double value) {
        if (!Double.isFinite(value)) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Nonnull
    private String nonBlankOrFallback(@Nullable String value, @Nonnull String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private void appendSection(@Nonnull StringBuilder builder, @Nonnull String sectionTitle, @Nonnull String sectionBody) {
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append("=== ").append(sectionTitle).append(" ===").append('\n');
        builder.append(sectionBody == null || sectionBody.isBlank() ? "<no data>" : sectionBody.trim());
    }
}

