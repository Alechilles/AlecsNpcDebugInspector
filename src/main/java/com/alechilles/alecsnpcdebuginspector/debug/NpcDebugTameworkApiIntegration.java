package com.alechilles.alecsnpcdebuginspector.debug;

import com.alechilles.alecsnpcdebuginspector.AlecsNpcDebugInspector;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Optional reflected bridge to Tamework's public API.
 */
final class NpcDebugTameworkApiIntegration implements AutoCloseable {
    private static final String TAMEWORK_MAIN_CLASS = "com.alechilles.alecstamework.Tamework";
    private static final String PROFILE_CHANGED_EVENT_CLASS =
            "com.alechilles.alecstamework.api.NpcProfileChangedEvent";
    private static final String NPC_CAPTURED_EVENT_CLASS =
            "com.alechilles.alecstamework.api.NpcCapturedEvent";
    private static final String NPC_DEATH_EVENT_CLASS =
            "com.alechilles.alecstamework.api.NpcDeathRecordedEvent";
    private static final String NPC_LOST_EVENT_CLASS =
            "com.alechilles.alecstamework.api.NpcLostRecordedEvent";
    private static final String CONFIG_RELOADED_EVENT_CLASS =
            "com.alechilles.alecstamework.api.ConfigReloadedEvent";
    private static final int MAX_RECENT_CONFIG_EVENTS = 8;
    private static final DateTimeFormatter EVENT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);

    private final NpcDebugHistoryStore historyStore;
    private final List<AutoCloseable> subscriptions = new CopyOnWriteArrayList<>();
    private final Deque<String> recentConfigReloadEvents = new ArrayDeque<>();

    private volatile Class<?> tameworkClass;
    private volatile boolean tameworkClassChecked;
    private volatile boolean subscriptionsInitialized;

    NpcDebugTameworkApiIntegration(@Nonnull NpcDebugHistoryStore historyStore) {
        this.historyStore = historyStore;
    }

    boolean isDetected() {
        return resolveApi() != null;
    }

    @Nonnull
    List<NpcDebugTameworkField> captureProfileFields(@Nullable UUID npcUuid) {
        Object api = resolveApi();
        if (api == null) {
            return List.of();
        }
        ensureEventSubscriptions(api);

        ArrayList<NpcDebugTameworkField> fields = new ArrayList<>();
        addField(fields, "tamework.api.profile.available", "API Available", "true", false, false);

        if (npcUuid == null) {
            addField(fields, "tamework.api.profile.status", "Status", "NPC UUID unavailable", true, false);
            return fields;
        }

        ResolvedProfileContext profileContext = resolveProfileContext(api, npcUuid);
        if (profileContext == null) {
            addField(fields, "tamework.api.profile.status", "Status", "No persisted Tamework profile found", true, true);
            return fields;
        }

        Object profile = profileContext.profileView;
        addField(fields, "tamework.api.profile.id", "Profile Id", profileContext.profileId, true, true);
        addField(fields, "tamework.api.profile.currentNpcUuid", "Current NPC UUID",
                stringify(invoke(profile, "currentNpcUuid"), "<none>"), true, true);
        addField(fields, "tamework.api.profile.ownerUuid", "Owner UUID",
                stringify(invoke(profile, "ownerUuid"), "<none>"), true, true);
        addField(fields, "tamework.api.profile.ownerName", "Owner Name",
                stringify(invoke(profile, "ownerName"), "<none>"), true, true);
        addField(fields, "tamework.api.profile.roleId", "Role Id",
                stringify(invoke(profile, "roleId"), "<none>"), true, true);
        addField(fields, "tamework.api.profile.displayName", "Display Name",
                stringify(invoke(profile, "displayName"), "<none>"), true, false);
        addField(fields, "tamework.api.profile.customName", "Custom Name",
                stringify(invoke(profile, "customName"), "<none>"), true, true);
        addField(fields, "tamework.api.profile.tamed", "Tamed",
                formatBoolean(readBoolean(invoke(profile, "tamed"))), true, true);
        addField(fields, "tamework.api.profile.coopId", "Co-op Id",
                stringify(invoke(profile, "coopId"), "<none>"), true, false);
        addField(fields, "tamework.api.profile.coopSlot", "Co-op Slot",
                stringify(invoke(profile, "coopSlot"), "<none>"), true, false);
        addField(fields, "tamework.api.profile.toolIds", "Linked Tool Ids",
                summarizeCollection(readCollection(invoke(profile, "toolIds")), 8), true, false);
        addField(fields, "tamework.api.profile.snapshotTypes", "Active Snapshot Types",
                summarizeCollection(readCollection(invoke(profile, "activeSnapshotTypes")), 8), true, true);
        addField(fields, "tamework.api.profile.lastUpdatedAt", "Profile Updated At",
                formatTimestamp(invoke(profile, "lastUpdatedAtMs")), true, false);

        Object profilesApi = invoke(api, "profiles");
        if (profilesApi != null) {
            for (String snapshotType : readStringSet(invoke(profile, "activeSnapshotTypes"))) {
                Object payload = unwrapOptional(invoke(
                        profilesApi,
                        "getActiveSnapshot",
                        new Class<?>[] {String.class, String.class},
                        profileContext.profileId,
                        snapshotType
                ));
                addField(
                        fields,
                        "tamework.api.profile.snapshot." + sanitizeKey(snapshotType),
                        "Snapshot " + snapshotType,
                        summarizeJson(payload, 220),
                        true,
                        false
                );
            }
        }

        return fields;
    }

    @Nonnull
    List<NpcDebugTameworkField> captureCommandLinkFields(@Nullable UUID npcUuid) {
        Object api = resolveApi();
        if (api == null) {
            return List.of();
        }
        ensureEventSubscriptions(api);

        ArrayList<NpcDebugTameworkField> fields = new ArrayList<>();
        if (npcUuid == null) {
            addField(fields, "tamework.api.links.status", "Status", "NPC UUID unavailable", true, false);
            return fields;
        }

        Object commandLinksApi = invoke(api, "commandLinks");
        Object commandLink = unwrapOptional(invoke(
                commandLinksApi,
                "getByNpcUuid",
                new Class<?>[] {UUID.class},
                npcUuid
        ));
        if (commandLink == null) {
            addField(fields, "tamework.api.links.status", "Status", "No persisted command-link state found", true, true);
            return fields;
        }

        addField(fields, "tamework.api.links.profileId", "Profile Id",
                stringify(invoke(commandLink, "profileId"), "<none>"), true, false);
        addField(fields, "tamework.api.links.currentNpcUuid", "Current NPC UUID",
                stringify(invoke(commandLink, "currentNpcUuid"), "<none>"), true, true);
        addField(fields, "tamework.api.links.ownerUuid", "Owner UUID",
                stringify(invoke(commandLink, "ownerUuid"), "<none>"), true, false);
        addField(fields, "tamework.api.links.toolIds", "Linked Tool Ids",
                summarizeCollection(readCollection(invoke(commandLink, "toolIds")), 8), true, true);
        addField(fields, "tamework.api.links.hasHome", "Has Home Position",
                formatBoolean(readBoolean(invoke(commandLink, "hasHomePosition"))), true, true);
        addField(fields, "tamework.api.links.home", "Home Position",
                formatVector3View(invoke(commandLink, "homePosition")), true, true);
        addField(fields, "tamework.api.links.lastKnownPosition", "Last Known Position",
                formatVector3View(invoke(commandLink, "lastKnownPosition")), true, true);
        addField(fields, "tamework.api.links.snapshotTypes", "Active Snapshot Types",
                summarizeCollection(readCollection(invoke(commandLink, "activeSnapshotTypes")), 8), true, false);
        addField(fields, "tamework.api.links.lastUpdatedAt", "Command State Updated At",
                formatTimestamp(invoke(commandLink, "lastUpdatedAtMs")), true, false);
        return fields;
    }

    @Nonnull
    List<NpcDebugTameworkField> capturePolicyFields(@Nullable UUID npcUuid, @Nullable UUID viewerUuid) {
        Object api = resolveApi();
        if (api == null) {
            return List.of();
        }
        ensureEventSubscriptions(api);

        ArrayList<NpcDebugTameworkField> fields = new ArrayList<>();
        if (npcUuid == null) {
            addField(fields, "tamework.api.policy.status", "Status", "NPC UUID unavailable", true, false);
            return fields;
        }

        Object policiesApi = invoke(api, "policies");
        Object ownership = unwrapOptional(invoke(
                policiesApi,
                "getOwnershipByNpcUuid",
                new Class<?>[] {UUID.class},
                npcUuid
        ));
        ResolvedProfileContext profileContext = resolveProfileContext(api, npcUuid);

        if (ownership == null && profileContext == null) {
            addField(fields, "tamework.api.policy.status", "Status", "No persisted policy data found", true, true);
            return fields;
        }

        if (ownership != null) {
            addField(fields, "tamework.api.policy.profileId", "Profile Id",
                    stringify(invoke(ownership, "profileId"), "<none>"), true, false);
            addField(fields, "tamework.api.policy.ownerUuid", "Owner UUID",
                    stringify(invoke(ownership, "ownerUuid"), "<none>"), true, true);
            addField(fields, "tamework.api.policy.ownerName", "Owner Name",
                    stringify(invoke(ownership, "ownerName"), "<none>"), true, true);
            addField(fields, "tamework.api.policy.roleId", "Role Id",
                    stringify(invoke(ownership, "roleId"), "<none>"), true, false);
            addField(fields, "tamework.api.policy.tamed", "Tamed",
                    formatBoolean(readBoolean(invoke(ownership, "tamed"))), true, true);
            addField(fields, "tamework.api.policy.inCoop", "In Co-op",
                    formatBoolean(readBoolean(invoke(ownership, "inCoop"))), true, false);
            addField(fields, "tamework.api.policy.coopId", "Co-op Id",
                    stringify(invoke(ownership, "coopId"), "<none>"), true, false);
            addField(fields, "tamework.api.policy.coopSlot", "Co-op Slot",
                    stringify(invoke(ownership, "coopSlot"), "<none>"), true, false);
            addField(fields, "tamework.api.policy.blockOwnerDamage", "Blocks Owner Damage",
                    formatBoolean(readBoolean(invoke(ownership, "blockOwnerDamage"))), true, false);
            addField(fields, "tamework.api.policy.blockAllPlayerDamageIfOwned", "Blocks All Player Damage If Owned",
                    formatBoolean(readBoolean(invoke(ownership, "blockAllPlayerDamageIfOwned"))), true, false);
            addField(fields, "tamework.api.policy.invulnerableIfOwned", "Invulnerable If Owned",
                    formatBoolean(readBoolean(invoke(ownership, "invulnerableIfOwned"))), true, false);
        }

        String profileId = profileContext != null ? profileContext.profileId : stringify(invoke(ownership, "profileId"), null);
        if (profileId == null || profileId.isBlank()) {
            addField(fields, "tamework.api.policy.actionsStatus", "Action Checks",
                    "Profile id unavailable for claim/damage evaluation", true, false);
            return fields;
        }

        Object claimAccess = invoke(
                policiesApi,
                "evaluateClaimAccess",
                new Class<?>[] {String.class, UUID.class},
                profileId,
                viewerUuid
        );
        if (claimAccess != null) {
            addField(fields, "tamework.api.policy.claim.available", "Claim Check Available",
                    formatBoolean(readBoolean(invoke(claimAccess, "available"))), true, false);
            addField(fields, "tamework.api.policy.claim.allowed", "Claim Access Allowed",
                    formatBoolean(readBoolean(invoke(claimAccess, "allowed"))), true, true);
            addField(fields, "tamework.api.policy.claim.status", "Claim Access Status",
                    stringify(invoke(claimAccess, "status"), "<none>"), true, true);
            addField(fields, "tamework.api.policy.claim.reason", "Claim Access Reason",
                    stringify(invoke(claimAccess, "reason"), "<none>"), true, false);
            addField(fields, "tamework.api.policy.claim.partyId", "Claim Party Id",
                    stringify(invoke(claimAccess, "claimPartyId"), "<none>"), true, false);
            addField(fields, "tamework.api.policy.claim.chunkCount", "Claim Chunk Count",
                    stringify(invoke(claimAccess, "claimChunkCount"), "<none>"), true, false);
            addField(fields, "tamework.api.policy.claim.world", "Claim World",
                    stringify(invoke(claimAccess, "worldName"), "<none>"), true, false);
            addField(fields, "tamework.api.policy.claim.position", "Claim Position",
                    formatVector3View(invoke(claimAccess, "position")), true, false);
            addField(fields, "tamework.api.policy.claim.positionSource", "Claim Position Source",
                    stringify(invoke(claimAccess, "positionSource"), "<none>"), true, false);
        }

        Object damage = invoke(
                policiesApi,
                "evaluateDamage",
                new Class<?>[] {String.class, UUID.class},
                profileId,
                viewerUuid
        );
        if (damage != null) {
            addField(fields, "tamework.api.policy.damage.allowed", "Damage Allowed",
                    formatBoolean(readBoolean(invoke(damage, "allowed"))), true, true);
            addField(fields, "tamework.api.policy.damage.status", "Damage Status",
                    stringify(invoke(damage, "status"), "<none>"), true, true);
            addField(fields, "tamework.api.policy.damage.reason", "Damage Reason",
                    stringify(invoke(damage, "reason"), "<none>"), true, true);
        }

        UUID ownerUuid = readUuid(invoke(ownership, "ownerUuid"));
        Object population = invoke(
                policiesApi,
                "evaluatePopulationCap",
                new Class<?>[] {UUID.class},
                ownerUuid
        );
        if (population != null) {
            addField(fields, "tamework.api.policy.population.allowed", "Population Cap Allows Acquisition",
                    formatBoolean(readBoolean(invoke(population, "allowed"))), true, false);
            addField(fields, "tamework.api.policy.population.capEnabled", "Population Cap Enabled",
                    formatBoolean(readBoolean(invoke(population, "capEnabled"))), true, false);
            addField(fields, "tamework.api.policy.population.limit", "Population Cap Limit",
                    stringify(invoke(population, "limit"), "<none>"), true, false);
            addField(fields, "tamework.api.policy.population.current", "Population Current Count",
                    stringify(invoke(population, "currentCount"), "<none>"), true, false);
            addField(fields, "tamework.api.policy.population.headroom", "Population Remaining Headroom",
                    stringify(invoke(population, "remainingHeadroom"), "<none>"), true, false);
            addField(fields, "tamework.api.policy.population.scope", "Population Cap Scope",
                    stringify(invoke(population, "scope"), "<none>"), true, false);
            addField(fields, "tamework.api.policy.population.reason", "Population Cap Reason",
                    stringify(invoke(population, "reason"), "<none>"), true, false);
        }

        return fields;
    }

    @Nonnull
    List<NpcDebugTameworkField> captureResolvedConfigFields(@Nullable UUID npcUuid,
                                                            @Nullable String liveRoleId) {
        Object api = resolveApi();
        if (api == null) {
            return List.of();
        }
        ensureEventSubscriptions(api);

        ArrayList<NpcDebugTameworkField> fields = new ArrayList<>();
        if (npcUuid == null) {
            addField(fields, "tamework.api.config.status", "Status", "NPC UUID unavailable", true, false);
            return fields;
        }

        ResolvedProfileContext profileContext = resolveProfileContext(api, npcUuid);
        String persistedRoleId = profileContext != null
                ? stringify(invoke(profileContext.profileView, "roleId"), null)
                : null;
        String roleId = firstUsableRoleId(liveRoleId, persistedRoleId);
        Object configsApi = invoke(api, "configs");
        if (configsApi == null) {
            addField(fields, "tamework.api.config.status", "Status", "Config API unavailable", true, false);
            return fields;
        }

        Object globalConfig = invoke(configsApi, "getGlobalConfig");
        if (globalConfig != null) {
            addField(fields, "tamework.api.config.global.enabled", "Global Config Enabled",
                    formatBoolean(readBoolean(invoke(globalConfig, "enabled"))), true, false);
            addField(fields, "tamework.api.config.global.priority", "Global Config Priority",
                    stringify(invoke(globalConfig, "priority"), "<none>"), true, false);
            addField(fields, "tamework.api.config.global.ownerProtection",
                    "Owner Protection",
                    summarizeOwnerProtection(invoke(globalConfig, "ownershipProtection")),
                    true,
                    false
            );
            addField(fields, "tamework.api.config.global.command",
                    "Command Defaults",
                    summarizeCommandSettings(invoke(globalConfig, "command")),
                    true,
                    false
            );
            addField(fields, "tamework.api.config.global.population",
                    "Population Settings",
                    summarizePopulationSettings(invoke(globalConfig, "population")),
                    true,
                    false
            );
            addField(fields, "tamework.api.config.global.simpleClaims",
                    "SimpleClaims Settings",
                    summarizeSimpleClaimsSettings(invoke(globalConfig, "simpleClaims")),
                    true,
                    false
            );
        }

        if (roleId == null) {
            addField(fields, "tamework.api.config.role.status", "Role Config Resolution",
                    "Role id unavailable from live NPC or persisted profile", true, false);
            return fields;
        }

        addField(fields, "tamework.api.config.role.id", "Resolved Role Id", roleId, true, false);

        appendInteractionConfigFields(fields, configsApi, roleId);
        appendRoleConfigFields(fields, configsApi, "Companion", "companion", "resolveCompanionConfigForRole", roleId);
        appendRoleConfigFields(fields, configsApi, "Happiness", "happiness", "resolveHappinessConfigForRole", roleId);
        appendRoleConfigFields(fields, configsApi, "Needs", "needs", "resolveNeedsConfigForRole", roleId);
        appendRoleConfigFields(fields, configsApi, "Breeding", "breeding", "resolveBreedingConfigForRole", roleId);
        appendRoleConfigFields(fields, configsApi, "Trait", "trait", "resolveTraitConfigForRole", roleId);
        return fields;
    }

    @Nonnull
    List<NpcDebugTameworkField> captureDiagnosticsFields() {
        Object api = resolveApi();
        if (api == null) {
            return List.of();
        }
        ensureEventSubscriptions(api);

        ArrayList<NpcDebugTameworkField> fields = new ArrayList<>();
        addField(fields, "tamework.api.diagnostics.version", "API Version",
                stringify(invoke(api, "getApiVersion"), "<unknown>"), true, false);
        addField(fields, "tamework.api.diagnostics.capabilities", "API Capabilities",
                summarizeCollection(readCollection(invoke(api, "getCapabilities")), 12), true, false);

        Object diagnosticsApi = invoke(api, "diagnostics");
        Object diagnostics = invoke(diagnosticsApi, "getPersistenceDiagnostics");
        if (diagnostics == null) {
            addField(fields, "tamework.api.diagnostics.status", "Status", "Persistence diagnostics unavailable", true, false);
            return fields;
        }

        addField(fields, "tamework.api.diagnostics.databasePath", "Database Path",
                stringify(invoke(diagnostics, "databasePath"), "<none>"), false, false);
        addField(fields, "tamework.api.diagnostics.totalBytes", "Database Total Bytes",
                stringify(invoke(diagnostics, "totalBytes"), "0"), true, false);
        addField(fields, "tamework.api.diagnostics.sqliteBytes", "SQLite Bytes",
                stringify(invoke(diagnostics, "sqliteBytes"), "0"), true, false);
        addField(fields, "tamework.api.diagnostics.walBytes", "WAL Bytes",
                stringify(invoke(diagnostics, "walBytes"), "0"), true, false);
        addField(fields, "tamework.api.diagnostics.shmBytes", "SHM Bytes",
                stringify(invoke(diagnostics, "shmBytes"), "0"), true, false);

        Object queueMetrics = invoke(diagnostics, "queueMetrics");
        if (queueMetrics != null) {
            addField(fields, "tamework.api.diagnostics.queue.depth", "Write Queue Depth",
                    stringify(invoke(queueMetrics, "queueDepth"), "0"), true, true);
            addField(fields, "tamework.api.diagnostics.queue.lastBatchSize", "Last Batch Size",
                    stringify(invoke(queueMetrics, "lastBatchSize"), "0"), true, false);
            addField(fields, "tamework.api.diagnostics.queue.maxBatchSize", "Max Batch Size",
                    stringify(invoke(queueMetrics, "maxBatchSize"), "0"), true, false);
            addField(fields, "tamework.api.diagnostics.queue.batchesProcessed", "Batches Processed",
                    stringify(invoke(queueMetrics, "batchesProcessed"), "0"), true, false);
            addField(fields, "tamework.api.diagnostics.queue.operationsProcessed", "Operations Processed",
                    stringify(invoke(queueMetrics, "operationsProcessed"), "0"), true, false);
            addField(fields, "tamework.api.diagnostics.queue.retryAttempts", "Retry Attempts",
                    stringify(invoke(queueMetrics, "retryAttempts"), "0"), true, false);
            addField(fields, "tamework.api.diagnostics.queue.failedBatches", "Failed Batches",
                    stringify(invoke(queueMetrics, "failedBatches"), "0"), true, true);
            addField(fields, "tamework.api.diagnostics.queue.averageBatchSize", "Average Batch Size",
                    formatNumber(invoke(queueMetrics, "averageBatchSize")), true, false);
            addField(fields, "tamework.api.diagnostics.queue.averageWriteMs", "Average Write ms",
                    formatNumber(invoke(queueMetrics, "averageWriteMs")), true, false);
            addField(fields, "tamework.api.diagnostics.queue.lastBatchWriteMs", "Last Batch Write ms",
                    formatNumber(invoke(queueMetrics, "lastBatchWriteMs")), true, false);
            addField(fields, "tamework.api.diagnostics.queue.lastFailureReason", "Last Failure Reason",
                    stringify(invoke(queueMetrics, "lastFailureReason"), "<none>"), true, true);
            addField(fields, "tamework.api.diagnostics.queue.lastFailureAt", "Last Failure At",
                    formatTimestamp(invoke(queueMetrics, "lastFailureAtMs")), true, false);
        }

        Object health = invoke(diagnostics, "health");
        if (health != null) {
            addField(fields, "tamework.api.diagnostics.health.status", "Health Status",
                    stringify(invoke(health, "status"), "<none>"), true, true);
            addField(fields, "tamework.api.diagnostics.health.reason", "Health Reason",
                    stringify(invoke(health, "reason"), "<none>"), true, false);
            addField(fields, "tamework.api.diagnostics.health.lastFailureAt", "Health Last Failure At",
                    formatTimestamp(invoke(health, "lastFailureAtMs")), true, false);
        }

        synchronized (recentConfigReloadEvents) {
            int index = 0;
            for (String entry : recentConfigReloadEvents) {
                addField(fields,
                        "tamework.api.diagnostics.configReload." + index,
                        "Recent Config Reload " + (index + 1),
                        entry,
                        true,
                        false
                );
                index++;
            }
        }
        if (recentConfigReloadEvents.isEmpty()) {
            addField(fields, "tamework.api.diagnostics.configReload.none", "Recent Config Reloads",
                    "No config reload events observed yet", true, false);
        }

        return fields;
    }

    private void appendInteractionConfigFields(@Nonnull List<NpcDebugTameworkField> fields,
                                               @Nonnull Object configsApi,
                                               @Nonnull String roleId) {
        Object interactionConfig = unwrapOptional(invoke(
                configsApi,
                "resolveInteractionConfigForRole",
                new Class<?>[] {String.class},
                roleId
        ));
        if (interactionConfig == null) {
            addField(fields, "tamework.api.config.interaction.status", "Interaction Config", "No resolved config", true, false);
            return;
        }

        addField(fields, "tamework.api.config.interaction.id", "Interaction Config Id",
                stringify(invoke(interactionConfig, "id"), "<none>"), true, false);
        addField(fields, "tamework.api.config.interaction.parentId", "Interaction Parent Id",
                stringify(invoke(interactionConfig, "parentId"), "<none>"), true, false);
        addField(fields, "tamework.api.config.interaction.enabled", "Interaction Enabled",
                formatBoolean(readBoolean(invoke(interactionConfig, "enabled"))), true, false);
        addField(fields, "tamework.api.config.interaction.priority", "Interaction Priority",
                stringify(invoke(interactionConfig, "priority"), "<none>"), true, false);
        addField(fields, "tamework.api.config.interaction.cooldown", "Interaction Cooldown Seconds",
                stringify(invoke(interactionConfig, "interactionCooldownSeconds"), "<none>"), true, false);

        Collection<?> interactions = readCollection(invoke(interactionConfig, "interactions"));
        addField(fields, "tamework.api.config.interaction.count", "Interaction Entry Count",
                String.valueOf(interactions.size()), true, false);
        int index = 0;
        for (Object entry : interactions) {
            addField(fields,
                    "tamework.api.config.interaction.entry." + index,
                    "Interaction Entry " + (index + 1),
                    summarizeInteractionEntry(entry),
                    true,
                    false
            );
            index++;
            if (index >= 6) {
                break;
            }
        }
    }

    private void appendRoleConfigFields(@Nonnull List<NpcDebugTameworkField> fields,
                                        @Nonnull Object configsApi,
                                        @Nonnull String familyLabel,
                                        @Nonnull String keyPrefix,
                                        @Nonnull String methodName,
                                        @Nonnull String roleId) {
        Object config = unwrapOptional(invoke(
                configsApi,
                methodName,
                new Class<?>[] {String.class},
                roleId
        ));
        if (config == null) {
            addField(fields, "tamework.api.config." + keyPrefix + ".status", familyLabel + " Config", "No resolved config", true, false);
            return;
        }

        addField(fields, "tamework.api.config." + keyPrefix + ".id", familyLabel + " Config Id",
                stringify(invoke(config, "id"), "<none>"), true, false);
        addField(fields, "tamework.api.config." + keyPrefix + ".parentId", familyLabel + " Parent Id",
                stringify(invoke(config, "parentId"), "<none>"), true, false);
        addField(fields, "tamework.api.config." + keyPrefix + ".enabled", familyLabel + " Enabled",
                formatBoolean(readBoolean(invoke(config, "enabled"))), true, false);
        addField(fields, "tamework.api.config." + keyPrefix + ".priority", familyLabel + " Priority",
                stringify(invoke(config, "priority"), "<none>"), true, false);
        addField(fields, "tamework.api.config." + keyPrefix + ".roles", familyLabel + " Roles",
                summarizeCollection(readCollection(invoke(config, "roleIds")), 8), true, false);
        addField(fields, "tamework.api.config." + keyPrefix + ".details", familyLabel + " Details",
                summarizeJson(invoke(config, "detailsJson"), 220), true, false);
    }

    private void ensureEventSubscriptions(@Nonnull Object api) {
        if (subscriptionsInitialized) {
            return;
        }
        synchronized (this) {
            if (subscriptionsInitialized) {
                return;
            }
            Object eventsApi = invoke(api, "events");
            if (eventsApi == null) {
                subscriptionsInitialized = true;
                return;
            }

            subscribe(eventsApi, PROFILE_CHANGED_EVENT_CLASS, this::handleProfileChangedEvent);
            subscribe(eventsApi, NPC_CAPTURED_EVENT_CLASS, this::handleNpcCapturedEvent);
            subscribe(eventsApi, NPC_DEATH_EVENT_CLASS, this::handleNpcDeathEvent);
            subscribe(eventsApi, NPC_LOST_EVENT_CLASS, this::handleNpcLostEvent);
            subscribe(eventsApi, CONFIG_RELOADED_EVENT_CLASS, this::handleConfigReloadedEvent);
            subscriptionsInitialized = true;
        }
    }

    private void subscribe(@Nonnull Object eventsApi,
                           @Nonnull String eventClassName,
                           @Nonnull Consumer<Object> handler) {
        Class<?> eventClass = tryLoadClass(eventClassName);
        if (eventClass == null) {
            return;
        }
        Object subscription = invoke(
                eventsApi,
                "subscribe",
                new Class<?>[] {Class.class, Consumer.class},
                eventClass,
                (Consumer<Object>) handler::accept
        );
        if (subscription instanceof AutoCloseable closeable) {
            subscriptions.add(closeable);
        }
    }

    private void handleProfileChangedEvent(@Nonnull Object event) {
        String profileId = stringify(invoke(event, "profileId"), "<unknown>");
        String changeTypes = summarizeCollection(readCollection(invoke(event, "changeTypes")), 8);
        Object before = invoke(event, "before");
        Object after = invoke(event, "after");
        LinkedHashSet<UUID> uuids = new LinkedHashSet<>();
        addProfileUuid(uuids, before);
        addProfileUuid(uuids, after);
        String description = "Tamework profile changed [" + changeTypes + "] for " + profileId;
        recordForUuids(uuids, instantFromEvent(event), description);
    }

    private void handleNpcCapturedEvent(@Nonnull Object event) {
        UUID npcUuid = readUuid(invoke(event, "npcUuid"));
        if (npcUuid == null) {
            return;
        }
        String displayName = firstNonBlank(
                stringify(invoke(event, "displayName"), null),
                stringify(invoke(event, "roleId"), null),
                npcUuid.toString()
        );
        String description = "Tamework captured: " + displayName
                + " | tools=" + summarizeCollection(readCollection(invoke(event, "toolIds")), 6)
                + " | home=" + formatVector3View(invoke(event, "homePosition"));
        historyStore.recordEvent(npcUuid, instantFromEvent(event), description, NpcDebugEventCategory.CORE);
    }

    private void handleNpcDeathEvent(@Nonnull Object event) {
        UUID npcUuid = readUuid(invoke(event, "npcUuid"));
        if (npcUuid == null) {
            return;
        }
        String displayName = firstNonBlank(
                stringify(invoke(event, "displayName"), null),
                stringify(invoke(event, "roleId"), null),
                npcUuid.toString()
        );
        String description = "Tamework death recorded: " + displayName
                + " | respawn=" + formatTimestamp(invoke(event, "respawnAvailableAtMs"))
                + " | home=" + formatVector3View(invoke(event, "homePosition"));
        historyStore.recordEvent(npcUuid, instantFromEvent(event), description, NpcDebugEventCategory.CORE);
    }

    private void handleNpcLostEvent(@Nonnull Object event) {
        UUID npcUuid = readUuid(invoke(event, "npcUuid"));
        if (npcUuid == null) {
            return;
        }
        String description = "Tamework lost recorded"
                + " | retries=" + stringify(invoke(event, "relocationRetryAttempts"), "0")
                + " | lastKnown=" + formatVector3View(invoke(event, "lastKnownPosition"))
                + " | home=" + formatVector3View(invoke(event, "homePosition"));
        historyStore.recordEvent(npcUuid, instantFromEvent(event), description, NpcDebugEventCategory.CORE);
    }

    private void handleConfigReloadedEvent(@Nonnull Object event) {
        String family = stringify(invoke(event, "family"), "<unknown>");
        String changedIds = summarizeCollection(readCollection(invoke(event, "changedIds")), 8);
        String entry = EVENT_TIME_FORMAT.format(instantFromEvent(event))
                + " " + family + " reloaded"
                + " | ids=" + changedIds;
        synchronized (recentConfigReloadEvents) {
            recentConfigReloadEvents.addFirst(entry);
            while (recentConfigReloadEvents.size() > MAX_RECENT_CONFIG_EVENTS) {
                recentConfigReloadEvents.removeLast();
            }
        }
    }

    private void recordForUuids(@Nonnull Set<UUID> uuids,
                                @Nonnull Instant instant,
                                @Nonnull String description) {
        for (UUID uuid : uuids) {
            if (uuid == null) {
                continue;
            }
            historyStore.recordEvent(uuid, instant, description, NpcDebugEventCategory.CORE);
        }
    }

    private void addProfileUuid(@Nonnull Set<UUID> uuids, @Nullable Object profile) {
        if (profile == null) {
            return;
        }
        UUID uuid = readUuid(invoke(profile, "currentNpcUuid"));
        if (uuid != null) {
            uuids.add(uuid);
        }
    }

    @Nullable
    private ResolvedProfileContext resolveProfileContext(@Nonnull Object api, @Nonnull UUID npcUuid) {
        Object profilesApi = invoke(api, "profiles");
        if (profilesApi == null) {
            return null;
        }
        Object profile = unwrapOptional(invoke(
                profilesApi,
                "getByNpcUuid",
                new Class<?>[] {UUID.class},
                npcUuid
        ));
        if (profile == null) {
            Object profileId = unwrapOptional(invoke(
                    profilesApi,
                    "resolveProfileId",
                    new Class<?>[] {UUID.class},
                    npcUuid
            ));
            if (profileId instanceof String id && !id.isBlank()) {
                profile = unwrapOptional(invoke(
                        profilesApi,
                        "getByProfileId",
                        new Class<?>[] {String.class},
                        id
                ));
            }
        }
        if (profile == null) {
            return null;
        }
        String profileId = stringify(invoke(profile, "profileId"), null);
        return profileId == null || profileId.isBlank() ? null : new ResolvedProfileContext(profileId, profile);
    }

    @Nullable
    private Object resolveApi() {
        Object plugin = resolveTameworkPlugin();
        if (plugin == null) {
            return null;
        }
        Object api = invoke(plugin, "getApi");
        if (api != null) {
            return api;
        }
        return invoke(plugin, "getExperimentalApi");
    }

    @Nullable
    private Object resolveTameworkPlugin() {
        Class<?> type = resolveTameworkClass();
        if (type == null) {
            return null;
        }
        return invokeStatic(type, "getInstance");
    }

    @Nullable
    private Class<?> resolveTameworkClass() {
        if (tameworkClassChecked) {
            return tameworkClass;
        }
        tameworkClass = tryLoadClass(TAMEWORK_MAIN_CLASS);
        tameworkClassChecked = true;
        return tameworkClass;
    }

    @Nullable
    private Class<?> tryLoadClass(@Nonnull String className) {
        ClassLoader pluginClassLoader = AlecsNpcDebugInspector.class.getClassLoader();
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();

        ClassLoader[] candidates = new ClassLoader[] {pluginClassLoader, contextClassLoader, systemClassLoader};
        for (ClassLoader candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            try {
                return Class.forName(className, false, candidate);
            } catch (ClassNotFoundException ignored) {
                // Try next class loader.
            }
        }
        return null;
    }

    @Nullable
    private Object invokeStatic(@Nonnull Class<?> targetClass, @Nonnull String methodName) {
        try {
            Method method = targetClass.getMethod(methodName);
            return method.invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    private Object invoke(@Nullable Object target, @Nonnull String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    private Object invoke(@Nullable Object target,
                          @Nonnull String methodName,
                          @Nonnull Class<?>[] parameterTypes,
                          Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    private Object unwrapOptional(@Nullable Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return value;
    }

    @Nullable
    private UUID readUuid(@Nullable Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    @Nonnull
    private Collection<?> readCollection(@Nullable Object value) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        return List.of();
    }

    @Nonnull
    private Set<String> readStringSet(@Nullable Object value) {
        Collection<?> collection = readCollection(value);
        LinkedHashSet<String> strings = new LinkedHashSet<>();
        for (Object entry : collection) {
            String text = stringify(entry, null);
            if (text != null && !text.isBlank()) {
                strings.add(text);
            }
        }
        return strings;
    }

    private boolean readBoolean(@Nullable Object value) {
        return value instanceof Boolean bool && bool;
    }

    @Nonnull
    private Instant instantFromEvent(@Nonnull Object event) {
        Object emittedAt = invoke(event, "emittedAtMs");
        if (emittedAt instanceof Number number) {
            try {
                return Instant.ofEpochMilli(number.longValue());
            } catch (RuntimeException ignored) {
                return Instant.now();
            }
        }
        return Instant.now();
    }

    @Nonnull
    private String summarizeInteractionEntry(@Nullable Object entry) {
        if (entry == null) {
            return "<none>";
        }
        String type = stringify(invoke(entry, "type"), "<unknown>");
        String enabled = formatBoolean(readBoolean(invoke(entry, "enabled")));
        String cooldown = stringify(invoke(entry, "cooldownSeconds"), "<none>");
        String prompt = stringify(invoke(entry, "promptHint"), "<none>");
        return type
                + " | enabled=" + enabled
                + " | cooldown=" + cooldown
                + " | prompt=" + trimText(prompt, 80);
    }

    @Nonnull
    private String summarizeOwnerProtection(@Nullable Object ownershipProtection) {
        if (ownershipProtection == null) {
            return "<none>";
        }
        return "blockOwnerDamage=" + formatBoolean(readBoolean(invoke(ownershipProtection, "blockOwnerDamage")))
                + ", blockAllPlayerDamageIfOwned=" + formatBoolean(readBoolean(invoke(ownershipProtection, "blockAllPlayerDamageIfOwned")))
                + ", invulnerableIfOwned=" + formatBoolean(readBoolean(invoke(ownershipProtection, "invulnerableIfOwned")));
    }

    @Nonnull
    private String summarizeCommandSettings(@Nullable Object commandSettings) {
        if (commandSettings == null) {
            return "<none>";
        }
        return "teleportDistance=" + formatNumber(invoke(commandSettings, "returnHomeTeleportDistance"))
                + ", respawnEnabled=" + formatBoolean(readBoolean(invoke(commandSettings, "deadRespawnEnabled")))
                + ", respawnCooldownMs=" + stringify(invoke(commandSettings, "deadRespawnCooldownMs"), "<none>")
                + ", relocationRetries=" + stringify(invoke(commandSettings, "relocationMaxRetryAttempts"), "<none>");
    }

    @Nonnull
    private String summarizePopulationSettings(@Nullable Object populationSettings) {
        if (populationSettings == null) {
            return "<none>";
        }
        return "limit=" + stringify(invoke(populationSettings, "limitPerPlayerOwnedTotal"), "<none>")
                + ", scope=" + stringify(invoke(populationSettings, "perPlayerLimitScope"), "<none>");
    }

    @Nonnull
    private String summarizeSimpleClaimsSettings(@Nullable Object simpleClaimsSettings) {
        if (simpleClaimsSettings == null) {
            return "<none>";
        }
        Object breeding = invoke(simpleClaimsSettings, "breeding");
        Object damage = invoke(simpleClaimsSettings, "damage");
        return "enabled=" + formatBoolean(readBoolean(invoke(simpleClaimsSettings, "enabled")))
                + ", breedingRequiresClaim=" + formatBoolean(readBoolean(invoke(breeding, "requiresClaim")))
                + ", breedingTotalLimit=" + stringify(invoke(breeding, "limitPerClaimTotal"), "<none>")
                + ", protectTamedFromNonMembers=" + formatBoolean(readBoolean(invoke(damage, "protectTamedFromNonMembers")));
    }

    @Nonnull
    private String formatVector3View(@Nullable Object value) {
        if (value == null) {
            return "<none>";
        }
        Object x = invoke(value, "x");
        Object y = invoke(value, "y");
        Object z = invoke(value, "z");
        if (x instanceof Number xNumber && y instanceof Number yNumber && z instanceof Number zNumber) {
            return "("
                    + formatNumber(xNumber)
                    + ", "
                    + formatNumber(yNumber)
                    + ", "
                    + formatNumber(zNumber)
                    + ")";
        }
        return trimText(String.valueOf(value), 96);
    }

    @Nonnull
    private String formatNumber(@Nullable Object value) {
        if (!(value instanceof Number number)) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.2f", number.doubleValue());
    }

    @Nonnull
    private String formatTimestamp(@Nullable Object value) {
        if (!(value instanceof Number number)) {
            return "n/a";
        }
        long millis = number.longValue();
        if (millis <= 0L) {
            return String.valueOf(millis);
        }
        try {
            return millis + " (" + EVENT_TIME_FORMAT.format(Instant.ofEpochMilli(millis)) + " UTC)";
        } catch (RuntimeException ignored) {
            return String.valueOf(millis);
        }
    }

    @Nonnull
    private String formatBoolean(boolean value) {
        return value ? "true" : "false";
    }

    @Nullable
    private String stringify(@Nullable Object value, @Nullable String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    @Nonnull
    private String summarizeCollection(@Nonnull Collection<?> values, int limit) {
        if (values.isEmpty()) {
            return "<none>";
        }
        ArrayList<String> out = new ArrayList<>(Math.min(values.size(), limit));
        int count = 0;
        for (Object value : values) {
            String text = stringify(value, null);
            if (text == null || text.isBlank()) {
                continue;
            }
            out.add(text);
            count++;
            if (count >= limit) {
                break;
            }
        }
        if (out.isEmpty()) {
            return "<none>";
        }
        return values.size() > out.size()
                ? String.join(", ", out) + " ... +" + (values.size() - out.size())
                : String.join(", ", out);
    }

    @Nonnull
    private String summarizeJson(@Nullable Object payload, int maxLength) {
        String text = stringify(payload, "<none>");
        if (text == null) {
            return "<none>";
        }
        return trimText(text.replaceAll("\\s+", " "), maxLength);
    }

    @Nonnull
    private String trimText(@Nullable String value, int maxLength) {
        if (value == null) {
            return "<none>";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    @Nonnull
    private String firstNonBlank(@Nullable String... values) {
        if (values == null) {
            return "<unknown>";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "<unknown>";
    }

    @Nullable
    private String firstUsableRoleId(@Nullable String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty() || "<unknown>".equalsIgnoreCase(trimmed)) {
                continue;
            }
            return trimmed;
        }
        return null;
    }

    @Nonnull
    private String sanitizeKey(@Nonnull String key) {
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".");
    }

    private void addField(@Nonnull List<NpcDebugTameworkField> fields,
                          @Nonnull String key,
                          @Nonnull String label,
                          @Nonnull String value,
                          boolean trackChange,
                          boolean recordEvent) {
        fields.add(new NpcDebugTameworkField(key, label, value, trackChange, recordEvent));
    }

    private record ResolvedProfileContext(@Nonnull String profileId, @Nonnull Object profileView) {
    }

    @Override
    public void close() {
        for (AutoCloseable subscription : subscriptions) {
            if (subscription == null) {
                continue;
            }
            try {
                subscription.close();
            } catch (Exception ignored) {
                // Best-effort shutdown only.
            }
        }
        subscriptions.clear();
        subscriptionsInitialized = false;
    }
}
