package com.alechilles.alecsnpcdebuginspector.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.RoleDebugFlags;
import java.io.IOException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * UI page for toggling built-in NPC role debug flags on one NPC.
 */
public final class NpcDebugInspectorDebugFlagsPage
        extends InteractiveCustomUIPage<NpcDebugInspectorDebugFlagsPage.PageEventData> {
    public static final String UI_PATH = "NpcDebugInspectorDebugFlagsPage.ui";
    private static final String CATEGORY_ROW_UI_PATH = "NpcDebugInspectorDebugFlagsCategoryRow.ui";
    private static final String FLAG_ROW_UI_PATH = "NpcDebugInspectorDebugFlagsFlagRow.ui";

    private static final String EVENT_ACTION = "Action";
    private static final String ACTION_CLOSE = "Close";
    private static final String ACTION_BACK = "Back";
    private static final String ACTION_SET_ALL = "SetAll";
    private static final String ACTION_SET_NONE = "SetNone";
    private static final String ACTION_TOGGLE_FLAG_PREFIX = "ToggleFlag:";
    private static final long STATUS_MESSAGE_DURATION_MS = 3000L;

    private final UUID targetNpcUuid;
    @Nullable
    private final Runnable backCallback;
    private final NpcDebugRoleDebugFlagService debugFlagService;
    private final LinkedHashMap<String, List<NpcDebugRoleDebugFlagCatalog.FlagDescriptor>> groupedFlags;

    @Nullable
    private String npcDisplayLabel;
    private boolean targetLoaded;
    private EnumSet<RoleDebugFlags> activeFlags;
    @Nullable
    private String statusMessage;
    private long statusMessageUntilMs;

    public NpcDebugInspectorDebugFlagsPage(@Nonnull PlayerRef playerRef,
                                           @Nonnull UUID targetNpcUuid,
                                           @Nullable Runnable backCallback) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageEventData.CODEC);
        this.targetNpcUuid = targetNpcUuid;
        this.backCallback = backCallback;
        this.debugFlagService = new NpcDebugRoleDebugFlagService();
        this.groupedFlags = NpcDebugRoleDebugFlagCatalog.groupedDescriptors();
        this.npcDisplayLabel = null;
        this.targetLoaded = false;
        this.activeFlags = EnumSet.noneOf(RoleDebugFlags.class);
        this.statusMessage = null;
        this.statusMessageUntilMs = 0L;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder,
                      @Nonnull Store<EntityStore> store) {
        commandBuilder.append(UI_PATH);
        refreshFlagState(store);
        applyHeader(commandBuilder);
        rebuildRows(commandBuilder, eventBuilder);
        bindGlobalEvents(eventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull String rawData) {
        String action = decodeAction(rawData);
        if (action == null || action.isBlank()) {
            super.handleDataEvent(ref, store, rawData);
            return;
        }
        handleResolvedAction(action, store);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PageEventData data) {
        handleResolvedAction(data != null ? data.action : null, store);
    }

    private void handleResolvedAction(@Nullable String action, @Nonnull Store<EntityStore> store) {
        if (action == null || action.isBlank()) {
            return;
        }
        String normalizedAction = action.trim();
        if (ACTION_CLOSE.equals(normalizedAction)) {
            close();
            return;
        }
        if (ACTION_BACK.equals(normalizedAction)) {
            if (backCallback != null) {
                backCallback.run();
            } else {
                close();
            }
            return;
        }
        if (ACTION_SET_ALL.equals(normalizedAction)) {
            applyPreset(store, NpcDebugRoleDebugFlagService.Preset.ALL, "Enabled all debug flags.");
            return;
        }
        if (ACTION_SET_NONE.equals(normalizedAction)) {
            applyPreset(store, NpcDebugRoleDebugFlagService.Preset.NONE, "Disabled all debug flags.");
            return;
        }
        if (normalizedAction.startsWith(ACTION_TOGGLE_FLAG_PREFIX)) {
            String suffix = normalizedAction.substring(ACTION_TOGGLE_FLAG_PREFIX.length()).trim();
            toggleFlagByName(store, suffix);
            return;
        }
    }

    private void applyPreset(@Nonnull Store<EntityStore> store,
                             @Nonnull NpcDebugRoleDebugFlagService.Preset preset,
                             @Nonnull String successMessage) {
        TargetContext context = resolveTargetContext(store);
        if (context == null) {
            setStatusMessage("Target NPC is not currently loaded.");
            sendRefreshUpdate(store);
            return;
        }
        EnumSet<RoleDebugFlags> presetFlags = debugFlagService.resolvePreset(preset);
        debugFlagService.applyFlags(context.npcRef(), context.npc(), context.store(), presetFlags);
        setStatusMessage(successMessage);
        sendRefreshUpdate(store);
    }

    private void toggleFlagByName(@Nonnull Store<EntityStore> store, @Nonnull String flagName) {
        RoleDebugFlags flag;
        try {
            flag = RoleDebugFlags.valueOf(flagName);
        } catch (IllegalArgumentException ignored) {
            setStatusMessage("Unknown debug flag: " + flagName);
            sendRefreshUpdate(store);
            return;
        }

        TargetContext context = resolveTargetContext(store);
        if (context == null) {
            setStatusMessage("Target NPC is not currently loaded.");
            sendRefreshUpdate(store);
            return;
        }

        EnumSet<RoleDebugFlags> flags = debugFlagService.readFlags(context.npc());
        boolean enabled;
        if (flags.contains(flag)) {
            flags.remove(flag);
            enabled = false;
        } else {
            flags.add(flag);
            enabled = true;
        }
        debugFlagService.applyFlags(context.npcRef(), context.npc(), context.store(), flags);
        setStatusMessage((enabled ? "Enabled " : "Disabled ") + flag.name() + ".");
        sendRefreshUpdate(store);
    }

    private void sendRefreshUpdate(@Nonnull Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        refreshFlagState(store);
        applyHeader(commandBuilder);
        rebuildRows(commandBuilder, eventBuilder);
        bindGlobalEvents(eventBuilder);
        sendUpdate(commandBuilder, eventBuilder, false);
    }

    @Nullable
    private String decodeAction(@Nullable String rawData) {
        if (rawData == null || rawData.isBlank()) {
            return null;
        }
        try {
            Map<String, String> payload = MapCodec.STRING_HASH_MAP_CODEC.decodeJson(
                    new RawJsonReader(rawData.toCharArray()),
                    ExtraInfo.THREAD_LOCAL.get()
            );
            if (payload == null || payload.isEmpty()) {
                return null;
            }
            String action = payload.get(EVENT_ACTION);
            if (action != null && !action.isBlank()) {
                return action;
            }
            return payload.get("action");
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private void bindGlobalEvents(@Nonnull UIEventBuilder eventBuilder) {
        bindStaticAction(eventBuilder, "#NpcDebugFlagsCloseButton", ACTION_CLOSE);
        bindStaticAction(eventBuilder, "#NpcDebugFlagsBackButton", ACTION_BACK);
        bindStaticAction(eventBuilder, "#NpcDebugFlagsPresetAllButton", ACTION_SET_ALL);
        bindStaticAction(eventBuilder, "#NpcDebugFlagsPresetNoneButton", ACTION_SET_NONE);
    }

    private void bindStaticAction(@Nonnull UIEventBuilder eventBuilder,
                                  @Nonnull String selector,
                                  @Nonnull String action) {
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                EventData.of(EVENT_ACTION, action),
                false
        );
    }

    private void applyHeader(@Nonnull UICommandBuilder commandBuilder) {
        String label = npcDisplayLabel != null && !npcDisplayLabel.isBlank() ? npcDisplayLabel : "NPC";
        String subtitle = "NPC: " + label + " (" + targetNpcUuid + ")"
                + " | Active Flags: " + activeFlags.size() + "/" + RoleDebugFlags.values().length;
        commandBuilder.set("#NpcDebugFlagsSubtitle.Text", subtitle);
        commandBuilder.set("#NpcDebugFlagsStatus.Text", resolveStatusLine());
    }

    @Nonnull
    private String resolveStatusLine() {
        long now = System.currentTimeMillis();
        if (statusMessage != null && now <= statusMessageUntilMs) {
            return statusMessage;
        }
        if (!targetLoaded) {
            return "Target NPC not loaded in this world. Toggle actions are unavailable.";
        }
        return "Toggle built-in /npc debug flags for this NPC.";
    }

    private void rebuildRows(@Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
        commandBuilder.clear("#NpcDebugFlagsList");
        int rowIndex = 0;
        for (Map.Entry<String, List<NpcDebugRoleDebugFlagCatalog.FlagDescriptor>> entry : groupedFlags.entrySet()) {
            List<NpcDebugRoleDebugFlagCatalog.FlagDescriptor> flags = entry.getValue();
            if (flags == null || flags.isEmpty()) {
                continue;
            }

            String categorySelector = "#NpcDebugFlagsList[" + rowIndex + "]";
            rowIndex++;
            commandBuilder.append("#NpcDebugFlagsList", CATEGORY_ROW_UI_PATH);
            commandBuilder.set(categorySelector + " #CategoryTitle.Text", entry.getKey());

            for (NpcDebugRoleDebugFlagCatalog.FlagDescriptor descriptor : flags) {
                String flagSelector = "#NpcDebugFlagsList[" + rowIndex + "]";
                rowIndex++;
                commandBuilder.append("#NpcDebugFlagsList", FLAG_ROW_UI_PATH);
                commandBuilder.set(flagSelector + " #FlagName.Text", descriptor.label());
                commandBuilder.set(flagSelector + " #FlagDescription.Text", descriptor.description());
                boolean enabled = activeFlags.contains(descriptor.flag());
                commandBuilder.set(flagSelector + " #FlagToggleCheck.Value", enabled);
                eventBuilder.addEventBinding(
                        CustomUIEventBindingType.ValueChanged,
                        flagSelector + " #FlagToggleCheck",
                        EventData.of(EVENT_ACTION, ACTION_TOGGLE_FLAG_PREFIX + descriptor.flag().name()),
                        false
                );
            }
        }
    }

    private void refreshFlagState(@Nonnull Store<EntityStore> store) {
        TargetContext context = resolveTargetContext(store);
        if (context == null) {
            targetLoaded = false;
            activeFlags = EnumSet.noneOf(RoleDebugFlags.class);
            npcDisplayLabel = "NPC";
            return;
        }
        targetLoaded = true;
        activeFlags = debugFlagService.readFlags(context.npc());
        npcDisplayLabel = resolveNpcDisplayLabel(context.npcRef(), context.store(), context.npc());
    }

    @Nullable
    private TargetContext resolveTargetContext(@Nonnull Store<EntityStore> store) {
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return null;
        }
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (world == null) {
            return null;
        }
        Ref<EntityStore> npcRef = world.getEntityRef(targetNpcUuid);
        if (npcRef == null || !npcRef.isValid()) {
            return null;
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null) {
            return null;
        }
        return new TargetContext(npcRef, npc, store);
    }

    @Nonnull
    private String resolveNpcDisplayLabel(@Nonnull Ref<EntityStore> npcRef,
                                          @Nonnull Store<EntityStore> store,
                                          @Nonnull NPCEntity npc) {
        DisplayNameComponent displayNameComponent = store.getComponent(npcRef, DisplayNameComponent.getComponentType());
        if (displayNameComponent != null && displayNameComponent.getDisplayName() != null) {
            String ansi = displayNameComponent.getDisplayName().getAnsiMessage();
            if (ansi != null && !ansi.isBlank()) {
                return ansi.trim();
            }
        }
        String legacy = npc.getLegacyDisplayName();
        if (legacy != null && !legacy.isBlank()) {
            return legacy.trim();
        }
        String roleName = npc.getRoleName();
        if (roleName != null && !roleName.isBlank()) {
            return roleName.trim();
        }
        return "NPC";
    }

    private void setStatusMessage(@Nonnull String message) {
        statusMessage = message;
        statusMessageUntilMs = System.currentTimeMillis() + STATUS_MESSAGE_DURATION_MS;
    }

    private record TargetContext(@Nonnull Ref<EntityStore> npcRef,
                                 @Nonnull NPCEntity npc,
                                 @Nonnull Store<EntityStore> store) {
    }

    /**
     * Event payload for debug flags page actions.
     */
    public static final class PageEventData {
        @Nullable
        public String action;

        public static final BuilderCodec<PageEventData> CODEC = BuilderCodec.builder(
                PageEventData.class,
                PageEventData::new
        )
                .append(
                        new KeyedCodec<>("Action", Codec.STRING),
                        (data, value) -> data.action = value,
                        data -> data.action
                )
                .add()
                .build();
    }
}
