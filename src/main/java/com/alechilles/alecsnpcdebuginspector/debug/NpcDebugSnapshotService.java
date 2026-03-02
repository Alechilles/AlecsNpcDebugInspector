package com.alechilles.alecsnpcdebuginspector.debug;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.group.EntityGroup;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.path.IPath;
import com.hypixel.hytale.server.core.universe.world.path.IPathWaypoint;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.flock.FlockMembership;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.entities.PathManager;
import com.hypixel.hytale.server.npc.instructions.Instruction;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.CombatSupport;
import com.hypixel.hytale.server.npc.role.support.EntitySupport;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.storage.AlarmStore;
import com.hypixel.hytale.server.npc.util.Alarm;
import com.hypixel.hytale.server.npc.util.DamageData;
import com.hypixel.hytale.server.npc.util.expression.StdScope;
import com.hypixel.hytale.server.npc.util.expression.ValueType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Builds read-only debug snapshots for a specific NPC.
 */
public final class NpcDebugSnapshotService {
    private static final DateTimeFormatter UTC_TIME_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final String HIGHLIGHT_CHANGED_PREFIX = ">> ";
    private static final String NORMAL_PREFIX = "- ";
    private static final String EVENTS_LOG_HINT = "Events Log: Pin this to mirror recent events in overlay.";
    private static final String TIMER_PHASE_ACTIVE = "active";
    private static final String TIMER_PHASE_INACTIVE = "inactive";
    private static final String ALARM_STATUS_SET = "set";
    private static final String ALARM_STATUS_UNSET = "unset";
    private static final String ALARM_STATUS_READY = "ready";

    private final NpcDebugHistoryStore historyStore = new NpcDebugHistoryStore();

    /**
     * Captures a detail snapshot for the target NPC.
     */
    @Nonnull
    public NpcDebugSnapshot capture(@Nullable UUID targetUuid,
                                    @Nullable Ref<EntityStore> targetRef,
                                    @Nonnull Store<EntityStore> store) {
        return capture(targetUuid, targetRef, null, store);
    }

    /**
     * Captures a detail snapshot for the target NPC, including observer-relative data.
     */
    @Nonnull
    public NpcDebugSnapshot capture(@Nullable UUID targetUuid,
                                    @Nullable Ref<EntityStore> targetRef,
                                    @Nullable Ref<EntityStore> observerRef,
                                    @Nonnull Store<EntityStore> store) {
        Instant gameTime = resolveGameTime(store);
        boolean loaded = targetRef != null && targetRef.isValid();
        NPCEntity npc = loaded ? store.getComponent(targetRef, NPCEntity.getComponentType()) : null;
        if (npc == null) {
            loaded = false;
        }

        UUID resolvedUuid = targetUuid;
        if (resolvedUuid == null && npc != null) {
            resolvedUuid = npc.getUuid();
        }
        String uuidText = resolvedUuid != null ? resolvedUuid.toString() : "<unknown>";

        String title = "NPC Debug Inspector";
        String subtitle = "UUID: " + uuidText
                + " | Loaded: " + loaded
                + " | Game Time (UTC): " + formatInstant(gameTime)
                + " | Changed lines are marked with '>>'";

        StringBuilder details = new StringBuilder();
        appendSection(details, "Overview", buildOverviewSection(resolvedUuid, targetRef, observerRef, store, npc, gameTime));
        appendSection(details, "AI", buildAiSection(resolvedUuid, npc, gameTime));
        appendSection(details, "Targeting / Sensors", buildTargetingSection(resolvedUuid, targetRef, store, npc, gameTime));
        appendSection(details, "Pathing", buildPathingSection(resolvedUuid, targetRef, store, npc, gameTime));
        appendSection(details, "Timers / Cooldowns", buildTimerSection(resolvedUuid, npc, gameTime));
        appendSection(details, "Lifecycle / Persistence", buildLifecycleSection(resolvedUuid, targetRef, store, npc, gameTime));
        appendSection(details, "Relationships", buildRelationshipsSection(resolvedUuid, targetRef, store, npc, gameTime));
        appendSection(details, "Combat", buildCombatSection(resolvedUuid, targetRef, store, npc, gameTime));
        appendSection(details, "Inventory / Equipment", buildInventorySection(resolvedUuid, npc, gameTime));
        appendSection(details, "Alarms", buildAlarmSection(resolvedUuid, npc, gameTime));
        appendSection(details, "Flags", buildFlagSection(resolvedUuid, npc, gameTime));
        appendSection(details, "Components", buildComponentSection(resolvedUuid, targetRef, store, npc, gameTime));
        appendSection(details, "Flock", buildFlockSection(resolvedUuid, targetRef, store, npc, gameTime));
        appendSection(details, "Recent Events", buildEventsSection(resolvedUuid));

        return new NpcDebugSnapshot(title, subtitle, details.toString().trim());
    }

    @Nonnull
    private String buildOverviewSection(@Nullable UUID npcUuid,
                                        @Nullable Ref<EntityStore> npcRef,
                                        @Nullable Ref<EntityStore> observerRef,
                                        @Nonnull Store<EntityStore> store,
                                        @Nullable NPCEntity npc,
                                        @Nonnull Instant now) {
        StringBuilder sb = new StringBuilder();
        String uuidText = npcUuid != null ? npcUuid.toString() : "<unknown>";
        appendTrackedLine(sb, npcUuid, now, "overview.uuid", "UUID", uuidText, false, false);
        appendTrackedLine(sb, npcUuid, now, "overview.refValid", "Entity Ref Valid", String.valueOf(npcRef != null && npcRef.isValid()), true, false);
        appendTrackedLine(sb, npcUuid, now, "overview.loaded", "Loaded", String.valueOf(npc != null), true, true);

        if (npc == null) {
            appendTrackedLine(sb, npcUuid, now, "overview.reason", "Load Reason", "No loaded NPC for this UUID in the current world", true, true);
            return sb.toString().trim();
        }

        appendTrackedLine(sb, npcUuid, now, "overview.displayName", "Display Name", resolveDisplayName(npcRef, store, npc), true, false);
        appendTrackedLine(sb, npcUuid, now, "overview.roleName", "Role Name", safeText(npc.getRoleName(), "<unknown>"), true, false);
        appendTrackedLine(sb, npcUuid, now, "overview.roleId", "Role Id", resolveRoleId(npc), true, true);
        appendTrackedLine(sb, npcUuid, now, "overview.state", "State", resolveStateName(npc), true, true);
        appendTrackedLine(sb, npcUuid, now, "overview.health", "Health", resolveHealthText(npcRef, store), true, false);

        TransformComponent transform = npcRef != null && npcRef.isValid()
                ? store.getComponent(npcRef, TransformComponent.getComponentType())
                : null;
        appendTrackedLine(sb, npcUuid, now, "overview.position", "Position", formatVector(transform != null ? transform.getPosition() : null), true, false);

        if (transform != null) {
            int blockX = floorToInt(transform.getPosition().x);
            int blockY = floorToInt(transform.getPosition().y);
            int blockZ = floorToInt(transform.getPosition().z);
            int chunkX = Math.floorDiv(blockX, 16);
            int chunkZ = Math.floorDiv(blockZ, 16);
            appendTrackedLine(sb, npcUuid, now, "overview.chunk", "Chunk", "(" + chunkX + ", " + chunkZ + ") @ y=" + blockY, true, false);
        } else {
            appendTrackedLine(sb, npcUuid, now, "overview.chunk", "Chunk", "n/a", true, false);
        }

        appendTrackedLine(sb, npcUuid, now, "overview.observerDistance", "Distance To Observer", resolveDistanceText(npcRef, observerRef, store), true, false);
        appendTrackedLine(sb, npcUuid, now, "overview.world", "World", resolveWorldName(store), true, false);
        appendTrackedLine(sb, npcUuid, now, "overview.spawnInstant", "Spawn Instant (UTC)", formatNullableInstant(npc.getSpawnInstant()), true, false);
        return sb.toString().trim();
    }

    @Nonnull
    private String buildAiSection(@Nullable UUID npcUuid,
                                  @Nullable NPCEntity npc,
                                  @Nonnull Instant now) {
        StringBuilder sb = new StringBuilder();
        if (npc == null || npc.getRole() == null) {
            appendTrackedLine(sb, npcUuid, now, "ai.available", "Available", "false", true, false);
            return sb.toString().trim();
        }

        Role role = npc.getRole();
        StateSupport stateSupport = role.getStateSupport();
        EntitySupport entitySupport = role.getEntitySupport();
        appendTrackedLine(sb, npcUuid, now, "ai.available", "Available", "true", true, false);
        appendTrackedLine(sb, npcUuid, now, "ai.stateIndex", "State Index", String.valueOf(stateSupport.getStateIndex()), true, false);
        appendTrackedLine(sb, npcUuid, now, "ai.subStateIndex", "Sub-State Index", String.valueOf(stateSupport.getSubStateIndex()), true, false);
        appendTrackedLine(sb, npcUuid, now, "ai.busy", "In Busy State", String.valueOf(stateSupport.isInBusyState()), true, true);
        appendTrackedLine(sb, npcUuid, now, "ai.transitionsRunning", "Transition Actions Running", String.valueOf(stateSupport.isRunningTransitionActions()), true, false);

        appendTrackedLine(sb, npcUuid, now, "ai.rootInstruction", "Root Instruction", resolveInstructionLabel(role.getRootInstruction()), true, false);
        appendTrackedLine(sb, npcUuid, now, "ai.currentTreeModeStep", "Current Tree Step", resolveInstructionLabel(readField(role, "currentTreeModeStep", Instruction.class)), true, true);
        appendTrackedLine(sb, npcUuid, now, "ai.bodyStep", "Current Body Step", resolveInstructionLabel(readField(role, "lastBodyMotionStep", Instruction.class)), true, false);
        appendTrackedLine(sb, npcUuid, now, "ai.headStep", "Current Head Step", resolveInstructionLabel(readField(role, "lastHeadMotionStep", Instruction.class)), true, false);
        appendTrackedLine(sb, npcUuid, now, "ai.nextBodyStep", "Queued Body Step", resolveInstructionLabel(entitySupport.getNextBodyMotionStep()), true, false);
        appendTrackedLine(sb, npcUuid, now, "ai.nextHeadStep", "Queued Head Step", resolveInstructionLabel(entitySupport.getNextHeadMotionStep()), true, false);
        appendTrackedLine(sb, npcUuid, now, "ai.interactionInstruction", "Interaction Instruction", resolveInstructionLabel(role.getInteractionInstruction()), true, false);
        appendTrackedLine(sb, npcUuid, now, "ai.deathInstruction", "Death Instruction", resolveInstructionLabel(role.getDeathInstruction()), true, false);

        appendTrackedLine(sb, npcUuid, now, "ai.steeringMotion", "Steering Motion", safeText(role.getSteeringMotionName(), "<none>"), true, false);
        return sb.toString().trim();
    }

    @Nonnull
    private String buildTargetingSection(@Nullable UUID npcUuid,
                                         @Nullable Ref<EntityStore> npcRef,
                                         @Nonnull Store<EntityStore> store,
                                         @Nullable NPCEntity npc,
                                         @Nonnull Instant now) {
        StringBuilder sb = new StringBuilder();
        if (npc == null || npc.getRole() == null) {
            appendTrackedLine(sb, npcUuid, now, "targeting.available", "Available", "false", true, false);
            return sb.toString().trim();
        }

        appendTrackedLine(sb, npcUuid, now, "targeting.available", "Available", "true", true, false);
        Role role = npc.getRole();
        MarkedEntitySupport marked = role.getMarkedEntitySupport();
        if (marked != null) {
            int slotCount = marked.getMarkedEntitySlotCount();
            appendTrackedLine(sb, npcUuid, now, "targeting.slotCount", "Marked Target Slots", String.valueOf(slotCount), true, false);
            for (int i = 0; i < slotCount; i++) {
                String slotName = safeText(marked.getSlotName(i), "Slot" + i);
                Ref<EntityStore> target = marked.getMarkedEntityRef(i);
                String targetText = resolveEntityTargetLabel(target, store);
                appendTrackedLine(sb, npcUuid, now, "targeting.slot." + slotName, "Target " + slotName, targetText, true, true);
            }
        }

        StateSupport stateSupport = role.getStateSupport();
        Ref<EntityStore> interactionTarget = stateSupport.getInteractionIterationTarget();
        appendTrackedLine(
                sb,
                npcUuid,
                now,
                "targeting.interactionTarget",
                "Interaction Iteration Target",
                resolveEntityTargetLabel(interactionTarget, store),
                true,
                false
        );

        StdScope scope = role.getEntitySupport() != null ? role.getEntitySupport().getSensorScope() : null;
        Map<String, String> scopeValues = snapshotScopeValues(scope);
        appendTrackedLine(sb, npcUuid, now, "targeting.scopeCount", "Sensor Scope Keys", String.valueOf(scopeValues.size()), true, false);

        List<Map.Entry<String, String>> targetingKeys = filterScope(scopeValues,
                "target", "seen", "los", "aggro", "threat", "attack", "enemy", "hostile", "distance");
        appendTopEntries(sb, npcUuid, now, "targeting.scope", targetingKeys, 12, true);

        if (npcRef != null && npcRef.isValid()) {
            appendTrackedLine(sb, npcUuid, now, "targeting.selfRef", "Self Ref", resolveEntityTargetLabel(npcRef, store), true, false);
        }
        return sb.toString().trim();
    }

    @Nonnull
    private String buildPathingSection(@Nullable UUID npcUuid,
                                       @Nullable Ref<EntityStore> npcRef,
                                       @Nonnull Store<EntityStore> store,
                                       @Nullable NPCEntity npc,
                                       @Nonnull Instant now) {
        StringBuilder sb = new StringBuilder();
        if (npc == null || npcRef == null || !npcRef.isValid() || npc.getRole() == null) {
            appendTrackedLine(sb, npcUuid, now, "pathing.available", "Available", "false", true, false);
            return sb.toString().trim();
        }

        appendTrackedLine(sb, npcUuid, now, "pathing.available", "Available", "true", true, false);
        PathManager pathManager = npc.getPathManager();
        appendTrackedLine(sb, npcUuid, now, "pathing.following", "Following Path", String.valueOf(pathManager.isFollowingPath()), true, true);
        appendTrackedLine(sb, npcUuid, now, "pathing.pathHint", "Path Hint", String.valueOf(pathManager.getCurrentPathHint()), true, false);

        IPath<?> path = pathManager.getPath(npcRef, store);
        if (path == null) {
            appendTrackedLine(sb, npcUuid, now, "pathing.path", "Resolved Path", "<none>", true, false);
        } else {
            appendTrackedLine(sb, npcUuid, now, "pathing.pathId", "Path Id", String.valueOf(path.getId()), true, false);
            appendTrackedLine(sb, npcUuid, now, "pathing.pathName", "Path Name", safeText(path.getName(), "<unnamed>"), true, false);
            appendTrackedLine(sb, npcUuid, now, "pathing.pathLength", "Path Length", String.valueOf(path.length()), true, false);
            if (path.length() > 0) {
                IPathWaypoint first = path.get(0);
                IPathWaypoint last = path.get(path.length() - 1);
                appendTrackedLine(sb, npcUuid, now, "pathing.firstWaypoint", "First Waypoint", formatVector(first.getWaypointPosition(store)), true, false);
                appendTrackedLine(sb, npcUuid, now, "pathing.lastWaypoint", "Last Waypoint", formatVector(last.getWaypointPosition(store)), true, false);
            }
        }

        MotionController controller = npc.getRole().getActiveMotionController();
        if (controller == null) {
            appendTrackedLine(sb, npcUuid, now, "pathing.controller", "Motion Controller", "<none>", true, false);
            return sb.toString().trim();
        }

        appendTrackedLine(sb, npcUuid, now, "pathing.controller", "Motion Controller", controller.getType(), true, false);
        appendTrackedLine(sb, npcUuid, now, "pathing.navState", "Nav State", String.valueOf(controller.getNavState()), true, true);
        appendTrackedLine(sb, npcUuid, now, "pathing.inProgress", "In Progress", String.valueOf(controller.isInProgress()), true, false);
        appendTrackedLine(sb, npcUuid, now, "pathing.obstructed", "Obstructed", String.valueOf(controller.isObstructed()), true, true);
        appendTrackedLine(sb, npcUuid, now, "pathing.forceRecompute", "Force Recompute Path", String.valueOf(controller.isForceRecomputePath()), true, false);
        appendTrackedLine(sb, npcUuid, now, "pathing.throttle", "Throttle Duration", formatNumber(controller.getThrottleDuration()), true, false);
        appendTrackedLine(sb, npcUuid, now, "pathing.targetDeltaSquared", "Target Delta Squared", formatNumber(controller.getTargetDeltaSquared()), true, false);
        appendTrackedLine(sb, npcUuid, now, "pathing.maxSpeed", "Maximum Speed", formatNumber(controller.getMaximumSpeed()), true, false);
        appendTrackedLine(sb, npcUuid, now, "pathing.currentSpeed", "Current Speed", formatNumber(controller.getCurrentSpeed()), true, false);
        appendTrackedLine(sb, npcUuid, now, "pathing.onGround", "On Ground", String.valueOf(controller.onGround()), true, false);
        appendTrackedLine(sb, npcUuid, now, "pathing.inWater", "In Water", String.valueOf(controller.inWater()), true, false);
        return sb.toString().trim();
    }

    @Nonnull
    private String buildTimerSection(@Nullable UUID npcUuid,
                                     @Nullable NPCEntity npc,
                                     @Nonnull Instant now) {
        StringBuilder sb = new StringBuilder();
        if (npc == null || npc.getRole() == null) {
            appendTrackedLine(sb, npcUuid, now, "timers.available", "Available", "false", true, false);
            return sb.toString().trim();
        }

        appendTrackedLine(sb, npcUuid, now, "timers.available", "Available", "true", true, false);
        String despawnSeconds = formatNumber(npc.getDespawnTime());
        appendTrackedLine(sb, npcUuid, now, "timers.despawnSeconds", "Despawn Countdown (s)", despawnSeconds, true, false);
        trackTimerTransition(npcUuid, now, "despawnSeconds", "Despawn Countdown (s)", despawnSeconds);

        CombatSupport combatSupport = npc.getRole().getCombatSupport();
        String attackExecuting = String.valueOf(combatSupport.isExecutingAttack());
        appendTrackedLine(sb, npcUuid, now, "timers.attackExecuting", "Attack Executing", attackExecuting, true, false);
        trackTimerTransition(npcUuid, now, "attackExecuting", "Attack Executing", attackExecuting);
        Double attackPause = readField(combatSupport, "attackPause", Double.class);
        String attackPauseText = attackPause != null ? formatNumber(attackPause) : "n/a";
        appendTrackedLine(sb, npcUuid, now, "timers.attackPause", "Attack Pause (s)", attackPauseText, true, false);
        trackTimerTransition(npcUuid, now, "attackPause", "Attack Pause (s)", attackPauseText);

        Map<String, String> scopeValues = snapshotScopeValues(npc.getRole().getEntitySupport().getSensorScope());
        List<Map.Entry<String, String>> timerKeys = filterScope(scopeValues,
                "timer", "time", "cooldown", "window", "delay", "pause", "until", "respawn", "lock");
        for (Map.Entry<String, String> timerEntry : timerKeys) {
            trackTimerTransition(
                    npcUuid,
                    now,
                    "scope." + timerEntry.getKey(),
                    timerEntry.getKey(),
                    timerEntry.getValue()
            );
        }
        appendTopEntries(sb, npcUuid, now, "timers.scope", timerKeys, 14, false);
        return sb.toString().trim();
    }

    @Nonnull
    private String buildLifecycleSection(@Nullable UUID npcUuid,
                                         @Nullable Ref<EntityStore> npcRef,
                                         @Nonnull Store<EntityStore> store,
                                         @Nullable NPCEntity npc,
                                         @Nonnull Instant now) {
        StringBuilder sb = new StringBuilder();
        if (npc == null || npc.getRole() == null) {
            appendTrackedLine(sb, npcUuid, now, "lifecycle.available", "Available", "false", true, false);
            return sb.toString().trim();
        }

        appendTrackedLine(sb, npcUuid, now, "lifecycle.available", "Available", "true", true, false);
        appendTrackedLine(sb, npcUuid, now, "lifecycle.spawnLockTime", "Spawn Lock Time (s)", formatNumber(npc.getRole().getSpawnLockTime()), true, false);
        appendTrackedLine(sb, npcUuid, now, "lifecycle.isDespawning", "Is Despawning", String.valueOf(npc.isDespawning()), true, true);
        appendTrackedLine(sb, npcUuid, now, "lifecycle.isPlayingDespawnAnim", "Playing Despawn Animation", String.valueOf(npc.isPlayingDespawnAnim()), true, false);
        appendTrackedLine(sb, npcUuid, now, "lifecycle.requiresLeashPosition", "Requires Leash Position", String.valueOf(npc.requiresLeashPosition()), true, false);
        appendTrackedLine(sb, npcUuid, now, "lifecycle.isReserved", "Reserved", String.valueOf(npc.isReserved()), true, false);
        appendTrackedLine(sb, npcUuid, now, "lifecycle.isSpawnTracked", "Spawn Tracked", String.valueOf(readBooleanField(npc, "isSpawnTracked", false)), true, false);
        appendTrackedLine(sb, npcUuid, now, "lifecycle.worldGenId", "Legacy Worldgen Id", String.valueOf(npc.getLegacyWorldgenId()), true, false);
        appendTrackedLine(sb, npcUuid, now, "lifecycle.spawnConfig", "Spawn Config", safeText(readField(npc, "spawnConfigurationName", String.class), "<none>"), true, false);
        appendTrackedLine(sb, npcUuid, now, "lifecycle.spawnRole", "Spawn Role", safeText(readField(npc, "spawnRoleName", String.class), "<none>"), true, false);

        boolean dead = npcRef != null && npcRef.isValid() && store.getArchetype(npcRef).contains(DeathComponent.getComponentType());
        appendTrackedLine(sb, npcUuid, now, "lifecycle.dead", "Has Death Component", String.valueOf(dead), true, true);

        Map<String, String> scopeValues = snapshotScopeValues(npc.getRole().getEntitySupport().getSensorScope());
        List<Map.Entry<String, String>> lifecycleKeys = filterScope(scopeValues,
                "age", "baby", "adult", "spawn", "despawn", "growth", "mature", "stage", "life");
        appendTopEntries(sb, npcUuid, now, "lifecycle.scope", lifecycleKeys, 10, true);
        return sb.toString().trim();
    }

    @Nonnull
    private String buildRelationshipsSection(@Nullable UUID npcUuid,
                                             @Nullable Ref<EntityStore> npcRef,
                                             @Nonnull Store<EntityStore> store,
                                             @Nullable NPCEntity npc,
                                             @Nonnull Instant now) {
        StringBuilder sb = new StringBuilder();
        if (npc == null || npc.getRole() == null || npcRef == null || !npcRef.isValid()) {
            appendTrackedLine(sb, npcUuid, now, "relationships.available", "Available", "false", true, false);
            return sb.toString().trim();
        }

        appendTrackedLine(sb, npcUuid, now, "relationships.available", "Available", "true", true, false);
        appendTrackedLine(sb, npcUuid, now, "relationships.leashPoint", "Leash Point", formatVector(npc.getLeashPoint()), true, false);

        Role role = npc.getRole();
        MarkedEntitySupport marked = role.getMarkedEntitySupport();
        if (marked != null) {
            int slotCount = marked.getMarkedEntitySlotCount();
            appendTrackedLine(sb, npcUuid, now, "relationships.markedCount", "Marked Relation Slots", String.valueOf(slotCount), true, false);
            for (int i = 0; i < slotCount; i++) {
                String slotName = safeText(marked.getSlotName(i), "Slot" + i);
                Ref<EntityStore> ref = marked.getMarkedEntityRef(i);
                appendTrackedLine(
                        sb,
                        npcUuid,
                        now,
                        "relationships.marked." + slotName,
                        "Relation " + slotName,
                        resolveEntityTargetLabel(ref, store),
                        true,
                        true
                );
            }
        }

        Map<String, String> scopeValues = snapshotScopeValues(role.getEntitySupport().getSensorScope());
        List<Map.Entry<String, String>> relationshipKeys = filterScope(scopeValues,
                "owner", "tamer", "leader", "follower", "flock", "home", "leash", "parent", "mate", "bond");
        appendTopEntries(sb, npcUuid, now, "relationships.scope", relationshipKeys, 12, true);
        return sb.toString().trim();
    }

    @Nonnull
    private String buildCombatSection(@Nullable UUID npcUuid,
                                      @Nullable Ref<EntityStore> npcRef,
                                      @Nonnull Store<EntityStore> store,
                                      @Nullable NPCEntity npc,
                                      @Nonnull Instant now) {
        StringBuilder sb = new StringBuilder();
        if (npc == null || npc.getRole() == null) {
            appendTrackedLine(sb, npcUuid, now, "combat.available", "Available", "false", true, false);
            return sb.toString().trim();
        }

        appendTrackedLine(sb, npcUuid, now, "combat.available", "Available", "true", true, false);

        CombatSupport combatSupport = npc.getRole().getCombatSupport();
        appendTrackedLine(sb, npcUuid, now, "combat.executing", "Executing Attack", String.valueOf(combatSupport.isExecutingAttack()), true, true);
        appendTrackedLine(sb, npcUuid, now, "combat.friendlyDamage", "Dealing Friendly Damage", String.valueOf(combatSupport.isDealingFriendlyDamage()), true, false);

        DamageData damageData = npc.getDamageData();
        appendTrackedLine(sb, npcUuid, now, "combat.maxInflicted", "Max Damage Inflicted", formatNumber(damageData.getMaxDamageInflicted()), true, false);
        appendTrackedLine(sb, npcUuid, now, "combat.maxSuffered", "Max Damage Suffered", formatNumber(damageData.getMaxDamageSuffered()), true, false);
        Ref<EntityStore> victim = damageData.getMostDamagedVictim();
        Ref<EntityStore> attacker = damageData.getMostDamagingAttacker();
        appendTrackedLine(sb, npcUuid, now, "combat.victim", "Most Damaged Victim", resolveEntityTargetLabel(victim, store), true, true);
        appendTrackedLine(sb, npcUuid, now, "combat.attacker", "Most Damaging Attacker", resolveEntityTargetLabel(attacker, store), true, true);

        List<String> damageByCause = new ArrayList<>();
        for (DamageCause cause : DamageCause.getAssetMap().getAssetMap().values()) {
            if (cause == null) {
                continue;
            }
            if (damageData.hasSufferedDamage(cause)) {
                damageByCause.add(cause.getId() + "=" + formatNumber(damageData.getDamage(cause)));
            }
        }
        damageByCause.sort(String.CASE_INSENSITIVE_ORDER);
        appendTrackedLine(sb, npcUuid, now, "combat.damageCauseCount", "Damage Cause Entries", String.valueOf(damageByCause.size()), true, false);
        for (int i = 0; i < Math.min(8, damageByCause.size()); i++) {
            appendTrackedLine(sb, npcUuid, now, "combat.damageCause." + i, "Damage Cause " + (i + 1), damageByCause.get(i), true, false);
        }

        @SuppressWarnings("unchecked")
        List<String> overrides = readField(combatSupport, "attackOverrides", List.class);
        appendTrackedLine(sb, npcUuid, now, "combat.attackOverrides", "Attack Override Count", String.valueOf(overrides != null ? overrides.size() : 0), true, false);
        return sb.toString().trim();
    }

    @Nonnull
    private String buildInventorySection(@Nullable UUID npcUuid,
                                         @Nullable NPCEntity npc,
                                         @Nonnull Instant now) {
        StringBuilder sb = new StringBuilder();
        if (npc == null) {
            appendTrackedLine(sb, npcUuid, now, "inventory.available", "Available", "false", true, false);
            return sb.toString().trim();
        }

        Inventory inventory = npc.getInventory();
        appendTrackedLine(sb, npcUuid, now, "inventory.available", "Available", String.valueOf(inventory != null), true, false);
        if (inventory == null) {
            return sb.toString().trim();
        }

        appendContainerSummary(sb, npcUuid, now, "inventory.hotbar", "Hotbar", inventory.getHotbar(), 8);
        appendContainerSummary(sb, npcUuid, now, "inventory.offhand", "Offhand", inventory.getUtility(), 4);
        appendContainerSummary(sb, npcUuid, now, "inventory.armor", "Armor", inventory.getArmor(), 6);
        appendContainerSummary(sb, npcUuid, now, "inventory.storage", "Storage", inventory.getStorage(), 8);
        return sb.toString().trim();
    }

    @Nonnull
    private String buildAlarmSection(@Nullable UUID npcUuid,
                                     @Nullable NPCEntity npc,
                                     @Nonnull Instant now) {
        StringBuilder sb = new StringBuilder();
        if (npc == null) {
            appendTrackedLine(sb, npcUuid, now, "alarms.available", "Available", "false", true, false);
            return sb.toString().trim();
        }

        AlarmStore alarmStore = npc.getAlarmStore();
        Map<String, Alarm> alarms = readAlarmMap(alarmStore);
        appendTrackedLine(sb, npcUuid, now, "alarms.count", "Alarm Count", String.valueOf(alarms.size()), true, false);
        if (alarms.isEmpty()) {
            appendTrackedLine(sb, npcUuid, now, "alarms.none", "Entries", "No alarm entries discovered", false, false);
            return sb.toString().trim();
        }

        List<Map.Entry<String, Alarm>> ordered = new ArrayList<>(alarms.entrySet());
        ordered.sort(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));
        int limit = Math.min(20, ordered.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Alarm> entry = ordered.get(i);
            Alarm alarm = entry.getValue();
            String status = resolveAlarmStatus(alarm, now);
            String remaining = resolveAlarmRemainingText(alarm, now);
            String value = remaining != null ? status + " (" + remaining + ")" : status;
            appendTrackedLine(sb, npcUuid, now, "alarms." + entry.getKey(), entry.getKey(), value, true, false);
            trackAlarmTransition(npcUuid, now, entry.getKey(), status);
        }
        if (ordered.size() > limit) {
            appendTrackedLine(sb, npcUuid, now, "alarms.remaining", "Additional Alarms", String.valueOf(ordered.size() - limit), false, false);
        }
        return sb.toString().trim();
    }

    @Nonnull
    private String buildFlagSection(@Nullable UUID npcUuid,
                                    @Nullable NPCEntity npc,
                                    @Nonnull Instant now) {
        StringBuilder sb = new StringBuilder();
        if (npc == null || npc.getRole() == null || npc.getRole().getEntitySupport() == null) {
            appendTrackedLine(sb, npcUuid, now, "flags.available", "Available", "false", true, false);
            return sb.toString().trim();
        }

        appendTrackedLine(sb, npcUuid, now, "flags.available", "Available", "true", true, false);
        Role role = npc.getRole();
        boolean[] roleFlags = readBooleanArrayField(role, "flags");
        if (roleFlags != null) {
            int enabled = 0;
            List<String> enabledIndexes = new ArrayList<>();
            for (int i = 0; i < roleFlags.length; i++) {
                if (roleFlags[i]) {
                    enabled++;
                    enabledIndexes.add(String.valueOf(i));
                }
            }
            appendTrackedLine(sb, npcUuid, now, "flags.role.enabled", "Role Flags Enabled", enabled + "/" + roleFlags.length, true, false);
            appendTrackedLine(sb, npcUuid, now, "flags.role.indexes", "Enabled Flag Indexes", enabledIndexes.isEmpty() ? "<none>" : String.join(",", enabledIndexes), true, false);
        }

        Map<String, String> scopeValues = snapshotScopeValues(role.getEntitySupport().getSensorScope());
        List<Map.Entry<String, String>> boolEntries = new ArrayList<>();
        for (Map.Entry<String, String> entry : scopeValues.entrySet()) {
            String value = entry.getValue();
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                boolEntries.add(entry);
            }
        }
        boolEntries.sort(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));
        appendTrackedLine(sb, npcUuid, now, "flags.scopeBoolCount", "Boolean Scope Keys", String.valueOf(boolEntries.size()), true, false);
        appendTopEntries(sb, npcUuid, now, "flags.scope", boolEntries, 20, false);
        return sb.toString().trim();
    }

    @Nonnull
    private String buildComponentSection(@Nullable UUID npcUuid,
                                         @Nullable Ref<EntityStore> npcRef,
                                         @Nonnull Store<EntityStore> store,
                                         @Nullable NPCEntity npc,
                                         @Nonnull Instant now) {
        StringBuilder sb = new StringBuilder();
        if (npcRef == null || !npcRef.isValid() || npc == null) {
            appendTrackedLine(sb, npcUuid, now, "components.available", "Available", "false", true, false);
            return sb.toString().trim();
        }

        appendTrackedLine(sb, npcUuid, now, "components.available", "Available", "true", true, false);
        appendTrackedLine(sb, npcUuid, now, "components.npcEntity", "NPCEntity", "true", false, false);
        appendTrackedLine(sb, npcUuid, now, "components.transform", "TransformComponent", String.valueOf(store.getComponent(npcRef, TransformComponent.getComponentType()) != null), true, false);
        appendTrackedLine(sb, npcUuid, now, "components.stats", "EntityStatMap", String.valueOf(store.getComponent(npcRef, EntityStatMap.getComponentType()) != null), true, false);
        appendTrackedLine(sb, npcUuid, now, "components.flock", "FlockMembership", String.valueOf(FlockMembership.getComponentType() != null && store.getComponent(npcRef, FlockMembership.getComponentType()) != null), true, false);
        appendTrackedLine(sb, npcUuid, now, "components.death", "DeathComponent", String.valueOf(store.getArchetype(npcRef).contains(DeathComponent.getComponentType())), true, true);
        appendTrackedLine(sb, npcUuid, now, "components.inventory", "Inventory", String.valueOf(npc.getInventory() != null), true, false);
        appendTrackedLine(sb, npcUuid, now, "components.role", "Role", String.valueOf(npc.getRole() != null), true, false);
        appendTrackedLine(sb, npcUuid, now, "components.alarmStore", "AlarmStore", String.valueOf(npc.getAlarmStore() != null), true, false);
        appendTrackedLine(sb, npcUuid, now, "components.damageData", "DamageData", String.valueOf(npc.getDamageData() != null), true, false);
        appendTrackedLine(sb, npcUuid, now, "components.pathManager", "PathManager", String.valueOf(npc.getPathManager() != null), true, false);
        return sb.toString().trim();
    }

    @Nonnull
    private String buildFlockSection(@Nullable UUID npcUuid,
                                     @Nullable Ref<EntityStore> npcRef,
                                     @Nonnull Store<EntityStore> store,
                                     @Nullable NPCEntity npc,
                                     @Nonnull Instant now) {
        StringBuilder sb = new StringBuilder();
        if (npc == null || npcRef == null || !npcRef.isValid()) {
            appendTrackedLine(sb, npcUuid, now, "flock.available", "Available", "false", true, false);
            return sb.toString().trim();
        }

        ComponentType<EntityStore, FlockMembership> flockType = FlockMembership.getComponentType();
        if (flockType == null) {
            appendTrackedLine(sb, npcUuid, now, "flock.componentType", "Flock Component Type", "unavailable", true, false);
            return sb.toString().trim();
        }

        FlockMembership membership = store.getComponent(npcRef, flockType);
        if (membership == null) {
            appendTrackedLine(sb, npcUuid, now, "flock.membership", "Membership", "none", true, true);
            return sb.toString().trim();
        }

        appendTrackedLine(sb, npcUuid, now, "flock.membership", "Membership", String.valueOf(membership.getMembershipType()), true, true);
        appendTrackedLine(sb, npcUuid, now, "flock.id", "Flock Id", String.valueOf(membership.getFlockId()), true, true);
        appendTrackedLine(sb, npcUuid, now, "flock.ref", "Flock Ref Valid", String.valueOf(membership.getFlockRef() != null && membership.getFlockRef().isValid()), true, false);

        Ref<EntityStore> flockRef = membership.getFlockRef();
        if (flockRef != null && flockRef.isValid()) {
            EntityGroup group = store.getComponent(flockRef, EntityGroup.getComponentType());
            if (group != null) {
                Ref<EntityStore> leaderRef = group.getLeaderRef();
                boolean hasLeader = leaderRef != null && leaderRef.isValid();
                appendTrackedLine(sb, npcUuid, now, "flock.hasLeader", "Has Leader", String.valueOf(hasLeader), true, true);
                if (hasLeader) {
                    appendTrackedLine(sb, npcUuid, now, "flock.leaderRef", "Leader Ref", resolveEntityTargetLabel(leaderRef, store), true, false);
                    appendTrackedLine(sb, npcUuid, now, "flock.distanceToLeader", "Distance To Leader", resolveDistanceBetween(npcRef, leaderRef, store), true, false);
                }
            }
        }
        return sb.toString().trim();
    }

    @Nonnull
    private String buildEventsSection(@Nullable UUID npcUuid) {
        List<String> events = historyStore.events(npcUuid);
        StringBuilder sb = new StringBuilder();
        sb.append(EVENTS_LOG_HINT).append('\n');
        if (events.isEmpty()) {
            sb.append("Event Count: 0").append('\n');
            sb.append("- No recent tracked changes yet.");
            return sb.toString().trim();
        }
        sb.append("Event Count: ").append(events.size()).append('\n');
        for (String event : events) {
            sb.append("- ").append(event).append('\n');
        }
        return sb.toString().trim();
    }

    private void appendContainerSummary(@Nonnull StringBuilder sb,
                                        @Nullable UUID npcUuid,
                                        @Nonnull Instant now,
                                        @Nonnull String keyPrefix,
                                        @Nonnull String label,
                                        @Nullable ItemContainer container,
                                        int maxEntries) {
        if (container == null) {
            appendTrackedLine(sb, npcUuid, now, keyPrefix + ".present", label, "none", true, false);
            return;
        }

        List<String> entries = new ArrayList<>();
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            entries.add(slot + ":" + stack.getItemId() + " x" + stack.getQuantity());
        }

        appendTrackedLine(sb, npcUuid, now, keyPrefix + ".count", label + " Filled Slots", String.valueOf(entries.size()), true, false);
        int limit = Math.min(maxEntries, entries.size());
        for (int i = 0; i < limit; i++) {
            appendTrackedLine(sb, npcUuid, now, keyPrefix + ".slot." + i, label + " " + (i + 1), entries.get(i), true, false);
        }
        if (entries.size() > limit) {
            appendTrackedLine(sb, npcUuid, now, keyPrefix + ".overflow", label + " Additional Slots", String.valueOf(entries.size() - limit), false, false);
        }
    }

    private void appendTopEntries(@Nonnull StringBuilder sb,
                                  @Nullable UUID npcUuid,
                                  @Nonnull Instant now,
                                  @Nonnull String keyPrefix,
                                  @Nonnull List<Map.Entry<String, String>> entries,
                                  int maxEntries,
                                  boolean recordEvents) {
        if (entries.isEmpty()) {
            return;
        }
        int limit = Math.min(maxEntries, entries.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, String> entry = entries.get(i);
            appendTrackedLine(sb, npcUuid, now, keyPrefix + "." + entry.getKey(), entry.getKey(), entry.getValue(), true, recordEvents);
        }
        if (entries.size() > limit) {
            appendTrackedLine(sb, npcUuid, now, keyPrefix + ".overflow", "Additional Entries", String.valueOf(entries.size() - limit), false, false);
        }
    }

    @Nonnull
    private List<Map.Entry<String, String>> filterScope(@Nonnull Map<String, String> scopeValues,
                                                        @Nonnull String... tokens) {
        List<Map.Entry<String, String>> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : scopeValues.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            for (String token : tokens) {
                if (key.contains(token)) {
                    out.add(entry);
                    break;
                }
            }
        }
        out.sort(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Nonnull
    private Map<String, String> snapshotScopeValues(@Nullable StdScope scope) {
        if (scope == null) {
            return Map.of();
        }
        Map<String, String> out = new HashMap<>();
        Map<String, ?> symbolTable = readField(scope, "symbolTable", Map.class);
        if (symbolTable == null || symbolTable.isEmpty()) {
            return Map.of();
        }

        for (String key : symbolTable.keySet()) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String value = resolveScopeValue(scope, key);
            if (value != null) {
                out.put(key, value);
            }
        }
        return out;
    }

    @Nullable
    private String resolveScopeValue(@Nonnull StdScope scope, @Nonnull String key) {
        try {
            ValueType type = scope.getType(key);
            if (type == null) {
                return null;
            }
            return switch (type) {
                case STRING -> trimText(readStringSupplier(scope.getStringSupplier(key)), 128);
                case NUMBER -> formatNumber(readNumberSupplier(scope.getNumberSupplier(key)));
                case BOOLEAN -> String.valueOf(readBooleanSupplier(scope.getBooleanSupplier(key)));
                case STRING_ARRAY -> formatStringArray(readStringArraySupplier(scope.getStringArraySupplier(key)));
                case NUMBER_ARRAY -> formatNumberArray(readNumberArraySupplier(scope.getNumberArraySupplier(key)));
                case BOOLEAN_ARRAY -> formatBooleanArray(readBooleanArraySupplier(scope.getBooleanArraySupplier(key)));
                case EMPTY_ARRAY -> "[]";
                case VOID -> "<void>";
            };
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private String readStringSupplier(@Nullable Supplier<String> supplier) {
        return supplier != null ? supplier.get() : null;
    }

    private double readNumberSupplier(@Nullable DoubleSupplier supplier) {
        return supplier != null ? supplier.getAsDouble() : Double.NaN;
    }

    private boolean readBooleanSupplier(@Nullable BooleanSupplier supplier) {
        return supplier != null && supplier.getAsBoolean();
    }

    @Nullable
    private String[] readStringArraySupplier(@Nullable Supplier<String[]> supplier) {
        return supplier != null ? supplier.get() : null;
    }

    @Nullable
    private double[] readNumberArraySupplier(@Nullable Supplier<double[]> supplier) {
        return supplier != null ? supplier.get() : null;
    }

    @Nullable
    private boolean[] readBooleanArraySupplier(@Nullable Supplier<boolean[]> supplier) {
        return supplier != null ? supplier.get() : null;
    }

    @Nonnull
    private String formatStringArray(@Nullable String[] value) {
        if (value == null) {
            return "null";
        }
        int limit = Math.min(6, value.length);
        List<String> values = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            values.add(trimText(value[i], 24));
        }
        String joined = String.join(",", values);
        if (value.length > limit) {
            joined += ",...";
        }
        return "[" + joined + "]";
    }

    @Nonnull
    private String formatNumberArray(@Nullable double[] value) {
        if (value == null) {
            return "null";
        }
        int limit = Math.min(6, value.length);
        List<String> values = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            values.add(formatNumber(value[i]));
        }
        String joined = String.join(",", values);
        if (value.length > limit) {
            joined += ",...";
        }
        return "[" + joined + "]";
    }

    @Nonnull
    private String formatBooleanArray(@Nullable boolean[] value) {
        if (value == null) {
            return "null";
        }
        int limit = Math.min(12, value.length);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(value[i]);
        }
        if (value.length > limit) {
            sb.append(",...");
        }
        sb.append(']');
        return sb.toString();
    }

    @Nonnull
    private Map<String, Alarm> readAlarmMap(@Nullable AlarmStore alarmStore) {
        if (alarmStore == null) {
            return Map.of();
        }
        Map<String, Alarm> out = new HashMap<>();
        Object rawParameters = readField(alarmStore, "parameters", Map.class);
        if (!(rawParameters instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }

        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof Alarm alarm)) {
                continue;
            }
            out.put(key, alarm);
        }
        return out;
    }

    @Nonnull
    private String resolveAlarmStatus(@Nullable Alarm alarm, @Nonnull Instant gameTime) {
        if (alarm == null) {
            return "missing";
        }
        if (!alarm.isSet()) {
            return ALARM_STATUS_UNSET;
        }
        if (alarm.hasPassed(gameTime)) {
            return ALARM_STATUS_READY;
        }
        return ALARM_STATUS_SET;
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

    @Nullable
    private Instant readAlarmInstant(@Nonnull Alarm alarm) {
        return readField(alarm, "alarmInstant", Instant.class);
    }

    private void appendTrackedLine(@Nonnull StringBuilder sb,
                                   @Nullable UUID npcUuid,
                                   @Nonnull Instant now,
                                   @Nonnull String key,
                                   @Nonnull String label,
                                   @Nonnull String value,
                                   boolean trackChange,
                                   boolean recordEvent) {
        boolean changed = false;
        if (trackChange && npcUuid != null) {
            changed = historyStore.track(npcUuid, key, label, value, now, recordEvent).changed;
        }
        sb.append(changed ? HIGHLIGHT_CHANGED_PREFIX : NORMAL_PREFIX)
                .append(label)
                .append(": ")
                .append(value)
                .append('\n');
    }

    private void trackTimerTransition(@Nullable UUID npcUuid,
                                      @Nonnull Instant now,
                                      @Nonnull String key,
                                      @Nonnull String label,
                                      @Nonnull String value) {
        if (npcUuid == null) {
            return;
        }
        String phase = resolveTimerPhase(value);
        NpcDebugHistoryStore.TrackResult phaseResult = historyStore.track(
                npcUuid,
                "events.timer." + key,
                "Timer " + label,
                phase,
                now,
                false
        );
        if (!phaseResult.changed) {
            return;
        }
        if (TIMER_PHASE_ACTIVE.equals(phase)) {
            historyStore.recordEvent(npcUuid, now, "Timer started: " + label + " = " + value);
            return;
        }
        historyStore.recordEvent(npcUuid, now, "Timer ended: " + label);
    }

    private void trackAlarmTransition(@Nullable UUID npcUuid,
                                      @Nonnull Instant now,
                                      @Nonnull String alarmKey,
                                      @Nonnull String status) {
        if (npcUuid == null) {
            return;
        }
        NpcDebugHistoryStore.TrackResult statusResult = historyStore.track(
                npcUuid,
                "events.alarm." + alarmKey + ".status",
                "Alarm " + alarmKey,
                status,
                now,
                false
        );
        if (!statusResult.changed) {
            return;
        }
        String previous = statusResult.previous != null ? statusResult.previous : "unknown";
        historyStore.recordEvent(npcUuid, now, "Alarm " + alarmKey + ": " + previous + " -> " + status);
    }

    @Nonnull
    private String resolveTimerPhase(@Nullable String value) {
        return isTimerActiveValue(value) ? TIMER_PHASE_ACTIVE : TIMER_PHASE_INACTIVE;
    }

    private boolean isTimerActiveValue(@Nullable String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return false;
        }
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)
                || "n/a".equals(normalized)
                || "none".equals(normalized)
                || "<none>".equals(normalized)
                || "unset".equals(normalized)
                || "[]".equals(normalized)
                || "0".equals(normalized)
                || "0.0".equals(normalized)
                || "0.00".equals(normalized)
                || "0s".equals(normalized)) {
            return false;
        }
        try {
            return Math.abs(Double.parseDouble(normalized)) > 0.000_001d;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    @Nonnull
    private String resolveStateName(@Nonnull NPCEntity npc) {
        Role role = npc.getRole();
        if (role == null || role.getStateSupport() == null) {
            return "<unknown>";
        }
        return safeText(role.getStateSupport().getStateName(), "<unknown>");
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
        return safeText(npc.getRoleName(), "<unknown>");
    }

    @Nonnull
    private String resolveDisplayName(@Nullable Ref<EntityStore> npcRef,
                                      @Nonnull Store<EntityStore> store,
                                      @Nonnull NPCEntity npc) {
        String preferred = resolveDisplayNameOnly(npcRef, store, npc);
        if (preferred != null) {
            return preferred;
        }
        return resolveRoleId(npc);
    }

    @Nullable
    private String resolveDisplayNameOnly(@Nullable Ref<EntityStore> npcRef,
                                          @Nonnull Store<EntityStore> store,
                                          @Nonnull NPCEntity npc) {
        if (npcRef != null && npcRef.isValid()) {
            Object displayNameComponent = store.getComponent(npcRef, com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent.getComponentType());
            if (displayNameComponent != null) {
                try {
                    Method getDisplayName = displayNameComponent.getClass().getMethod("getDisplayName");
                    Object message = getDisplayName.invoke(displayNameComponent);
                    if (message != null) {
                        Method getAnsiMessage = message.getClass().getMethod("getAnsiMessage");
                        Object ansi = getAnsiMessage.invoke(message);
                        if (ansi instanceof String text && !text.isBlank()) {
                            return text;
                        }
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Best-effort only.
                }
            }
        }
        String legacy = npc.getLegacyDisplayName();
        if (legacy != null && !legacy.isBlank()) {
            return legacy;
        }
        return null;
    }

    @Nonnull
    private String resolveEntityTargetLabel(@Nullable Ref<EntityStore> ref,
                                            @Nonnull Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) {
            return "<none>";
        }

        NPCEntity targetNpc = store.getComponent(ref, NPCEntity.getComponentType());
        if (targetNpc == null) {
            return String.valueOf(ref);
        }

        String preferredName = firstNonBlank(
                resolveDisplayNameOnly(ref, store, targetNpc),
                resolveEntityNameFromNameKey(targetNpc),
                resolveRoleId(targetNpc)
        );
        UUID targetUuid = targetNpc.getUuid();
        String uuidText = targetUuid != null ? targetUuid.toString() : "<unknown-uuid>";
        return preferredName + " (" + uuidText + ")";
    }

    @Nullable
    private String resolveEntityNameFromNameKey(@Nonnull NPCEntity npc) {
        Role role = npc.getRole();
        if (role != null) {
            String nameTranslationKey = readField(role, "nameTranslationKey", String.class);
            if (nameTranslationKey != null && !nameTranslationKey.isBlank()) {
                return nameTranslationKey;
            }
        }
        String npcTypeId = npc.getNPCTypeId();
        if (npcTypeId != null && !npcTypeId.isBlank()) {
            return npcTypeId;
        }
        return null;
    }

    @Nonnull
    private String firstNonBlank(@Nullable String... values) {
        if (values == null || values.length == 0) {
            return "<unknown>";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "<unknown>";
    }

    @Nonnull
    private String resolveHealthText(@Nullable Ref<EntityStore> npcRef,
                                     @Nonnull Store<EntityStore> store) {
        if (npcRef == null || !npcRef.isValid()) {
            return "n/a";
        }
        EntityStatMap statMap = store.getComponent(npcRef, EntityStatMap.getComponentType());
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
    private String resolveDistanceText(@Nullable Ref<EntityStore> npcRef,
                                       @Nullable Ref<EntityStore> observerRef,
                                       @Nonnull Store<EntityStore> store) {
        if (npcRef == null || observerRef == null || !npcRef.isValid() || !observerRef.isValid()) {
            return "n/a";
        }
        return resolveDistanceBetween(npcRef, observerRef, store);
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
    private String resolveWorldName(@Nonnull Store<EntityStore> store) {
        if (store.getExternalData() == null || store.getExternalData().getWorld() == null) {
            return "<unknown>";
        }
        World world = store.getExternalData().getWorld();
        String reflectedName = tryInvokeString(world, "getName");
        if (reflectedName != null && !reflectedName.isBlank()) {
            return reflectedName;
        }
        reflectedName = tryInvokeString(world, "getId");
        if (reflectedName != null && !reflectedName.isBlank()) {
            return reflectedName;
        }
        return world.toString();
    }

    @Nullable
    private String tryInvokeString(@Nonnull Object target, @Nonnull String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value != null ? String.valueOf(value) : null;
        } catch (ReflectiveOperationException ignored) {
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
    private String formatNullableInstant(@Nullable Instant instant) {
        return instant != null ? formatInstant(instant) : "n/a";
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
        return "(" + formatNumber(vector.x) + ", " + formatNumber(vector.y) + ", " + formatNumber(vector.z) + ")";
    }

    @Nonnull
    private String formatNumber(double value) {
        if (!Double.isFinite(value)) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Nonnull
    private String resolveInstructionLabel(@Nullable Instruction instruction) {
        if (instruction == null) {
            return "<none>";
        }
        String label = safeText(instruction.getLabel(), "");
        if (!label.isBlank()) {
            return label;
        }
        String debugTag = safeText(instruction.getDebugTag(), "");
        if (!debugTag.isBlank()) {
            return debugTag;
        }
        return instruction.getClass().getSimpleName();
    }

    private int floorToInt(double value) {
        return (int) Math.floor(value);
    }

    @Nonnull
    private String safeText(@Nullable String value, @Nonnull String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    @Nonnull
    private String trimText(@Nullable String value, int maxLength) {
        if (value == null) {
            return "null";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T> T readField(@Nullable Object target, @Nonnull String fieldName, @Nonnull Class<T> expectedType) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(target);
                if (value == null) {
                    return null;
                }
                if (expectedType.isAssignableFrom(value.getClass())) {
                    return (T) value;
                }
                return null;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean readBooleanField(@Nullable Object target, @Nonnull String fieldName, boolean fallback) {
        Boolean value = readField(target, fieldName, Boolean.class);
        return value != null ? value : fallback;
    }

    @Nullable
    private boolean[] readBooleanArrayField(@Nullable Object target, @Nonnull String fieldName) {
        return readField(target, fieldName, boolean[].class);
    }

    private void appendSection(@Nonnull StringBuilder builder,
                               @Nonnull String sectionTitle,
                               @Nonnull String sectionBody) {
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append("=== ").append(sectionTitle).append(" ===").append('\n');
        builder.append(sectionBody == null || sectionBody.isBlank() ? "<no data>" : sectionBody.trim());
    }
}
