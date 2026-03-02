package com.alechilles.alecsnpcdebuginspector.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Linked NPC roster page for the debug inspector item.
 */
public final class NpcDebugInspectorRosterPage
        extends InteractiveCustomUIPage<NpcDebugInspectorRosterPage.RosterEventData> {
    public static final String UI_PATH = "NpcDebugInspectorRoster.ui";
    public static final String CARD_UI_PATH = "NpcDebugInspectorRosterCard.ui";
    private static final String EVENT_ACTION = "Action";
    private static final String ACTION_CLOSE = "__close__";
    private static final String INSPECT_PREFIX = "__inspect__:";
    private static final String UNLINK_PREFIX = "__unlink__:";
    private static final long REFRESH_INTERVAL_MS = 1000L;

    private final Supplier<List<NpcDebugLinkedEntry>> entrySupplier;
    private final Consumer<UUID> inspectCallback;
    private final Consumer<UUID> unlinkCallback;
    private NpcDebugLinkedEntry[] entries;
    private int renderedCardCount;
    private volatile boolean refreshLoopStarted;
    private volatile boolean dismissed;

    public NpcDebugInspectorRosterPage(@Nonnull PlayerRef playerRef,
                                       @Nonnull Supplier<List<NpcDebugLinkedEntry>> entrySupplier,
                                       @Nonnull Consumer<UUID> inspectCallback,
                                       @Nonnull Consumer<UUID> unlinkCallback) {
        super(playerRef, CustomPageLifetime.CanDismiss, RosterEventData.CODEC);
        this.entrySupplier = entrySupplier;
        this.inspectCallback = inspectCallback;
        this.unlinkCallback = unlinkCallback;
        this.entries = new NpcDebugLinkedEntry[0];
        this.renderedCardCount = 0;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder,
                      @Nonnull Store<EntityStore> store) {
        refreshEntries();
        commandBuilder.append(UI_PATH);
        commandBuilder.set("#NpcDebugRosterSubtitle.Text", resolveSubtitle());
        buildCards(commandBuilder, eventBuilder);
        bindCloseEvent(eventBuilder);
        startRefreshLoop();
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull RosterEventData data) {
        if (data.action == null || data.action.isBlank() || ACTION_CLOSE.equals(data.action)) {
            close();
            return;
        }
        if (data.action.startsWith(INSPECT_PREFIX)) {
            UUID npcUuid = parseUuidAction(data.action, INSPECT_PREFIX);
            if (npcUuid != null) {
                inspectCallback.accept(npcUuid);
            }
            return;
        }
        if (data.action.startsWith(UNLINK_PREFIX)) {
            UUID npcUuid = parseUuidAction(data.action, UNLINK_PREFIX);
            if (npcUuid != null) {
                unlinkCallback.accept(npcUuid);
                refreshEntries();
                sendRefreshUpdate();
            }
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        dismissed = true;
    }

    private void buildCards(@Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
        commandBuilder.clear("#NpcDebugRosterList");
        renderedCardCount = entries.length;
        boolean hasEntries = entries.length > 0;
        commandBuilder.set("#NpcDebugRosterEmpty.Visible", !hasEntries);
        commandBuilder.set("#NpcDebugRosterListViewport.Visible", hasEntries);
        if (!hasEntries) {
            return;
        }
        for (int i = 0; i < entries.length; i++) {
            bindCard(commandBuilder, eventBuilder, i, entries[i], true);
        }
    }

    private void bindCard(@Nonnull UICommandBuilder commandBuilder,
                          @Nonnull UIEventBuilder eventBuilder,
                          int index,
                          @Nonnull NpcDebugLinkedEntry entry,
                          boolean appendCard) {
        String entrySelector = "#NpcDebugRosterList[" + index + "]";
        if (appendCard) {
            commandBuilder.append("#NpcDebugRosterList", CARD_UI_PATH);
        }
        commandBuilder.set(entrySelector + " #CardName.Text", entry.displayName());
        commandBuilder.set(entrySelector + " #CardUuid.Text", "UUID: " + entry.npcUuid());
        commandBuilder.set(entrySelector + " #CardRole.Text", "Role: " + entry.roleId());
        commandBuilder.set(entrySelector + " #CardStatus.Text", "Status: " + (entry.loaded() ? "Loaded" : "Unloaded"));
        commandBuilder.set(entrySelector + " #CardState.Text", "State: " + defaultText(entry.stateName(), "n/a"));
        commandBuilder.set(entrySelector + " #CardHealth.Text", "Health: " + defaultText(entry.healthText(), "n/a"));
        commandBuilder.set(entrySelector + " #CardFlock.Text", "Flock: " + defaultText(entry.flockText(), "n/a"));

        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                entrySelector + " #InspectButton",
                EventData.of(EVENT_ACTION, INSPECT_PREFIX + entry.npcUuid()),
                false
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                entrySelector + " #UnlinkButton",
                EventData.of(EVENT_ACTION, UNLINK_PREFIX + entry.npcUuid()),
                false
        );
    }

    private void startRefreshLoop() {
        if (refreshLoopStarted) {
            return;
        }
        refreshLoopStarted = true;
        scheduleRefreshTick();
    }

    private void scheduleRefreshTick() {
        CompletableFuture.runAsync(
                this::dispatchRefreshTick,
                CompletableFuture.delayedExecutor(REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS)
        );
    }

    private void dispatchRefreshTick() {
        if (dismissed) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        if (store == null || store.getExternalData() == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        world.execute(this::runRefreshTickOnWorldThread);
    }

    private void runRefreshTickOnWorldThread() {
        if (dismissed) {
            return;
        }
        refreshEntries();
        sendRefreshUpdate();
        if (!dismissed) {
            scheduleRefreshTick();
        }
    }

    private void sendRefreshUpdate() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        commandBuilder.set("#NpcDebugRosterSubtitle.Text", resolveSubtitle());
        boolean hasEntries = entries.length > 0;
        commandBuilder.set("#NpcDebugRosterEmpty.Visible", !hasEntries);
        commandBuilder.set("#NpcDebugRosterListViewport.Visible", hasEntries);
        boolean structureChanged = renderedCardCount != entries.length;
        if (structureChanged) {
            commandBuilder.clear("#NpcDebugRosterList");
            renderedCardCount = entries.length;
            if (hasEntries) {
                for (int i = 0; i < entries.length; i++) {
                    bindCard(commandBuilder, eventBuilder, i, entries[i], true);
                }
            }
        } else if (hasEntries) {
            for (int i = 0; i < entries.length; i++) {
                bindCard(commandBuilder, eventBuilder, i, entries[i], false);
            }
        }
        bindCloseEvent(eventBuilder);
        sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindCloseEvent(@Nonnull UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#NpcDebugRosterCloseButton",
                EventData.of(EVENT_ACTION, ACTION_CLOSE),
                false
        );
    }

    private void refreshEntries() {
        List<NpcDebugLinkedEntry> values = entrySupplier != null ? entrySupplier.get() : List.of();
        if (values == null || values.isEmpty()) {
            entries = new NpcDebugLinkedEntry[0];
            return;
        }
        ArrayList<NpcDebugLinkedEntry> safe = new ArrayList<>(values.size());
        for (NpcDebugLinkedEntry entry : values) {
            if (entry == null || entry.npcUuid() == null) {
                continue;
            }
            safe.add(entry);
        }
        safe.sort(
                Comparator.comparing(NpcDebugLinkedEntry::loaded).reversed()
                        .thenComparing(NpcDebugLinkedEntry::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(entry -> entry.npcUuid().toString())
        );
        entries = safe.toArray(new NpcDebugLinkedEntry[0]);
    }

    @Nonnull
    private String resolveSubtitle() {
        int loadedCount = 0;
        for (NpcDebugLinkedEntry entry : entries) {
            if (entry.loaded()) {
                loadedCount++;
            }
        }
        return "Linked NPCs: " + entries.length + " (" + loadedCount + " loaded)";
    }

    @Nullable
    private UUID parseUuidAction(@Nonnull String action, @Nonnull String prefix) {
        String raw = action.substring(prefix.length());
        if (raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nonnull
    private String defaultText(@Nullable String value, @Nonnull String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    /** Event payload emitted by roster button clicks. */
    public static final class RosterEventData {
        public static final BuilderCodec<RosterEventData> CODEC = BuilderCodec.builder(
                RosterEventData.class,
                RosterEventData::new
        )
            .append(
                new KeyedCodec<>(EVENT_ACTION, Codec.STRING),
                (data, value) -> data.action = value,
                data -> data.action
            )
            .add()
            .build();

        private String action;
    }
}
