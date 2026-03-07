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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
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
    private static final String EVENT_FILTER_QUERY = "FilterQuery";

    private static final String ACTION_CLOSE = "__close__";
    private static final String ACTION_FILTER_APPLY = "__filter_apply__";
    private static final String ACTION_FILTER_CLEAR = "__filter_clear__";

    private static final String INSPECT_PREFIX = "__inspect__:";
    private static final String DEBUG_FLAGS_PREFIX = "__debug_flags__:";
    private static final String UNLINK_PREFIX = "__unlink__:";
    private static final String COPY_PREFIX = "__copy__:";
    private static final String HIGHLIGHT_PREFIX = "__highlight__:";

    private static final long STATUS_MESSAGE_DURATION_MS = 3000L;
    private static final long IMMEDIATE_REARM_DELAY_MS = 75L;
    private static final long NAVIGATION_DELAY_MS = 80L;

    private final Supplier<List<NpcDebugLinkedEntry>> entrySupplier;
    private final Consumer<UUID> inspectCallback;
    private final Consumer<UUID> debugFlagsCallback;
    private final Consumer<UUID> unlinkCallback;
    private final Supplier<Set<UUID>> highlightedNpcSupplier;
    private final BiConsumer<UUID, Boolean> highlightPersistenceCallback;
    @Nullable
    private final Consumer<String> notificationCallback;

    private NpcDebugLinkedEntry[] sourceEntries;
    private NpcDebugLinkedEntry[] entries;
    private int renderedCardCount;
    private final Map<UUID, NpcDebugLinkedEntry> previousEntriesByUuid;
    private final Map<UUID, String> changeSummaryByUuid;
    private final Set<UUID> highlightedNpcUuids;

    private String filterQuery;

    @Nullable
    private String statusMessage;
    private long statusMessageUntilMs;
    private boolean syncQueryFieldValue;
    private String copyBufferValue;
    private boolean copyBufferVisible;

    private volatile boolean refreshLoopStarted;
    private volatile boolean dismissed;
    private volatile boolean navigationPending;
    private final AtomicLong refreshTickGeneration;

    public NpcDebugInspectorRosterPage(@Nonnull PlayerRef playerRef,
                                       @Nonnull Supplier<List<NpcDebugLinkedEntry>> entrySupplier,
                                       @Nonnull Consumer<UUID> inspectCallback,
                                       @Nonnull Consumer<UUID> debugFlagsCallback,
                                       @Nonnull Consumer<UUID> unlinkCallback,
                                       @Nonnull Supplier<Set<UUID>> highlightedNpcSupplier,
                                       @Nonnull BiConsumer<UUID, Boolean> highlightPersistenceCallback,
                                        @Nullable Consumer<String> notificationCallback) {
        super(playerRef, CustomPageLifetime.CanDismiss, RosterEventData.CODEC);
        this.entrySupplier = entrySupplier;
        this.inspectCallback = inspectCallback;
        this.debugFlagsCallback = debugFlagsCallback;
        this.unlinkCallback = unlinkCallback;
        this.highlightedNpcSupplier = highlightedNpcSupplier;
        this.highlightPersistenceCallback = highlightPersistenceCallback;
        this.notificationCallback = notificationCallback;
        this.sourceEntries = new NpcDebugLinkedEntry[0];
        this.entries = new NpcDebugLinkedEntry[0];
        this.renderedCardCount = 0;
        this.previousEntriesByUuid = new HashMap<>();
        this.changeSummaryByUuid = new HashMap<>();
        this.highlightedNpcUuids = new HashSet<>();
        this.filterQuery = "";
        this.statusMessage = null;
        this.statusMessageUntilMs = 0L;
        this.syncQueryFieldValue = true;
        this.copyBufferValue = "";
        this.copyBufferVisible = false;
        this.navigationPending = false;
        this.refreshTickGeneration = new AtomicLong();
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder,
                      @Nonnull Store<EntityStore> store) {
        loadPersistedHighlights();
        refreshEntries();
        commandBuilder.append(UI_PATH);
        applyCopyBufferState(commandBuilder);
        commandBuilder.set("#NpcDebugRosterFilterQueryField.Value", filterQuery);
        syncQueryFieldValue = false;
        commandBuilder.set("#NpcDebugRosterSubtitle.Text", resolveSubtitle());
        buildCards(commandBuilder, eventBuilder);
        bindGlobalEvents(eventBuilder);
        startRefreshLoop();
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull String rawData) {
        Map<String, String> rawEventData = decodeRawEventData(rawData);
        String action = firstNonBlank(rawEventData.get(EVENT_ACTION), rawEventData.get("action"));
        String filterQueryValue = firstNonBlank(
                rawEventData.get(EVENT_FILTER_QUERY),
                rawEventData.get("filterQuery"),
                rawEventData.get("query")
        );
        if (action == null && filterQueryValue == null) {
            super.handleDataEvent(ref, store, rawData);
            return;
        }
        handleResolvedEvent(action, filterQueryValue);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull RosterEventData data) {
        handleResolvedEvent(data != null ? data.action : null, data != null ? data.filterQuery : null);
    }

    private void handleResolvedEvent(@Nullable String action, @Nullable String dataFilterQuery) {
        String normalizedAction = action != null ? action.trim() : null;

        if (dataFilterQuery != null
                && (normalizedAction == null || normalizedAction.isBlank() || ACTION_FILTER_APPLY.equals(normalizedAction))) {
            applyFilterQuery(dataFilterQuery);
            refreshEntries();
            sendRefreshUpdate();
            rearmRefreshLoopSoon();
            return;
        }

        if (normalizedAction == null || normalizedAction.isBlank() || ACTION_CLOSE.equals(normalizedAction)) {
            stopRefreshLoop();
            close();
            return;
        }

        if (ACTION_FILTER_CLEAR.equals(normalizedAction)) {
            clearFilters();
            refreshEntries();
            sendRefreshUpdate();
            rearmRefreshLoopSoon();
            return;
        }

        if (normalizedAction.startsWith(INSPECT_PREFIX)) {
            UUID npcUuid = parseUuidAction(normalizedAction, INSPECT_PREFIX);
            if (npcUuid != null) {
                if (navigationPending) {
                    return;
                }
                stopRefreshLoop();
                navigationPending = true;
                navigateAfterUiDrain(() -> inspectCallback.accept(npcUuid));
            }
            return;
        }

        if (normalizedAction.startsWith(DEBUG_FLAGS_PREFIX)) {
            UUID npcUuid = parseUuidAction(normalizedAction, DEBUG_FLAGS_PREFIX);
            if (npcUuid != null) {
                if (navigationPending) {
                    return;
                }
                stopRefreshLoop();
                navigationPending = true;
                navigateAfterUiDrain(() -> debugFlagsCallback.accept(npcUuid));
            }
            return;
        }

        if (normalizedAction.startsWith(UNLINK_PREFIX)) {
            UUID npcUuid = parseUuidAction(normalizedAction, UNLINK_PREFIX);
            if (npcUuid != null) {
                highlightedNpcUuids.remove(npcUuid);
                unlinkCallback.accept(npcUuid);
                setStatusMessage("Unlinked " + npcUuid + ".", STATUS_MESSAGE_DURATION_MS);
                refreshEntries();
                sendRefreshUpdate();
                rearmRefreshLoopSoon();
            }
            return;
        }

        if (normalizedAction.startsWith(COPY_PREFIX)) {
            UUID npcUuid = parseUuidAction(normalizedAction, COPY_PREFIX);
            if (npcUuid != null) {
                String uuidText = npcUuid.toString();
                copyBufferValue = uuidText;
                copyBufferVisible = true;
                setStatusMessage("UUID ready in copy buffer. Click the field and press Ctrl+C.", STATUS_MESSAGE_DURATION_MS);
                sendRefreshUpdate();
                rearmRefreshLoopSoon();
            }
            return;
        }

        if (normalizedAction.startsWith(HIGHLIGHT_PREFIX)) {
            UUID npcUuid = parseUuidAction(normalizedAction, HIGHLIGHT_PREFIX);
            if (npcUuid != null) {
                toggleHighlight(npcUuid);
                refreshEntries();
                sendRefreshUpdate();
                rearmRefreshLoopSoon();
            }
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        stopRefreshLoop();
    }

    private void stopRefreshLoop() {
        dismissed = true;
        refreshLoopStarted = false;
        refreshTickGeneration.incrementAndGet();
    }

    private void navigateAfterUiDrain(@Nonnull Runnable action) {
        CompletableFuture.runAsync(
                () -> dispatchNavigationAction(action),
                CompletableFuture.delayedExecutor(NAVIGATION_DELAY_MS, TimeUnit.MILLISECONDS)
        );
    }

    private void dispatchNavigationAction(@Nonnull Runnable action) {
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
        world.execute(() -> {
            Ref<EntityStore> activeRef = playerRef.getReference();
            if (activeRef == null || !activeRef.isValid()) {
                return;
            }
            action.run();
        });
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
            bindCard(commandBuilder, eventBuilder, i, entries[i], true, true);
        }
    }

    private void bindCard(@Nonnull UICommandBuilder commandBuilder,
                          @Nullable UIEventBuilder eventBuilder,
                          int index,
                          @Nonnull NpcDebugLinkedEntry entry,
                          boolean appendCard,
                          boolean bindEvents) {
        String entrySelector = "#NpcDebugRosterList[" + index + "]";
        if (appendCard) {
            commandBuilder.append("#NpcDebugRosterList", CARD_UI_PATH);
        }

        commandBuilder.set(entrySelector + " #CardName.Text", entry.displayName());
        commandBuilder.set(entrySelector + " #CardUuid.Text", entry.npcUuid().toString());
        commandBuilder.set(entrySelector + " #CardRole.Text", defaultText(entry.roleId(), "n/a"));
        commandBuilder.set(entrySelector + " #CardStatus.Text", entry.loaded() ? "Loaded" : "Unloaded");
        commandBuilder.set(entrySelector + " #CardState.Text", defaultText(entry.stateName(), "n/a"));
        commandBuilder.set(entrySelector + " #CardHealth.Text", defaultText(entry.healthText(), "n/a"));
        commandBuilder.set(entrySelector + " #CardFlock.Text", defaultText(entry.flockText(), "n/a"));
        commandBuilder.set(entrySelector + " #CardLocation.Text", defaultText(entry.locationText(), "n/a"));
        commandBuilder.set(entrySelector + " #CardDistance.Text", defaultText(entry.distanceText(), "n/a"));
        commandBuilder.set(entrySelector + " #CardChange.Text", defaultText(changeSummaryByUuid.get(entry.npcUuid()), ""));
        commandBuilder.set(
                entrySelector + " #CardHighlightState.Text",
                highlightedNpcUuids.contains(entry.npcUuid()) ? "Highlight: On" : ""
        );

        if (bindEvents && eventBuilder != null) {
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    entrySelector + " #InspectButton",
                    EventData.of(EVENT_ACTION, INSPECT_PREFIX + entry.npcUuid()),
                    false
            );
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    entrySelector + " #DebugFlagsButton",
                    EventData.of(EVENT_ACTION, DEBUG_FLAGS_PREFIX + entry.npcUuid()),
                    false
            );
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    entrySelector + " #UnlinkButton",
                    EventData.of(EVENT_ACTION, UNLINK_PREFIX + entry.npcUuid()),
                    false
            );
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    entrySelector + " #CopyButton",
                    EventData.of(EVENT_ACTION, COPY_PREFIX + entry.npcUuid()),
                    false
            );
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    entrySelector + " #HighlightButton",
                    EventData.of(EVENT_ACTION, HIGHLIGHT_PREFIX + entry.npcUuid()),
                    false
            );
        }
    }

    private void startRefreshLoop() {
        if (refreshLoopStarted) {
            return;
        }
        refreshLoopStarted = true;
        scheduleRefreshTickAfter(NpcDebugUiRefreshSettings.getIntervalMs(playerRef));
    }

    private void scheduleRefreshTick() {
        scheduleRefreshTickAfter(NpcDebugUiRefreshSettings.getIntervalMs(playerRef));
    }

    private void scheduleRefreshTickAfter(long delayMs) {
        long generation = refreshTickGeneration.incrementAndGet();
        long safeDelayMs = Math.max(0L, delayMs);
        CompletableFuture.runAsync(
                () -> dispatchRefreshTick(generation),
                CompletableFuture.delayedExecutor(safeDelayMs, TimeUnit.MILLISECONDS)
        );
    }

    private void dispatchRefreshTick(long generation) {
        if (dismissed || generation != refreshTickGeneration.get()) {
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
        world.execute(() -> runRefreshTickOnWorldThread(generation));
    }

    private void runRefreshTickOnWorldThread(long generation) {
        if (dismissed || generation != refreshTickGeneration.get()) {
            return;
        }
        if (!isCurrentPageActive()) {
            dismissed = true;
            return;
        }
        refreshEntries();
        sendRefreshUpdate();
        if (!dismissed && isCurrentPageActive() && generation == refreshTickGeneration.get()) {
            scheduleRefreshTick();
        }
    }

    private void rearmRefreshLoopSoon() {
        if (!refreshLoopStarted || dismissed) {
            return;
        }
        long refreshIntervalMs = NpcDebugUiRefreshSettings.getIntervalMs(playerRef);
        long delayMs = Math.min(IMMEDIATE_REARM_DELAY_MS, refreshIntervalMs);
        scheduleRefreshTickAfter(delayMs);
    }

    private void sendRefreshUpdate() {
        if (dismissed || !isCurrentPageActive()) {
            dismissed = true;
            return;
        }

        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = null;

        applyCopyBufferState(commandBuilder);
        if (syncQueryFieldValue) {
            commandBuilder.set("#NpcDebugRosterFilterQueryField.Value", filterQuery);
            syncQueryFieldValue = false;
        }
        commandBuilder.set("#NpcDebugRosterSubtitle.Text", resolveSubtitle());

        boolean hasEntries = entries.length > 0;
        commandBuilder.set("#NpcDebugRosterEmpty.Visible", !hasEntries);
        commandBuilder.set("#NpcDebugRosterListViewport.Visible", hasEntries);

        boolean structureChanged = renderedCardCount != entries.length;
        if (structureChanged) {
            eventBuilder = new UIEventBuilder();
            commandBuilder.clear("#NpcDebugRosterList");
            renderedCardCount = entries.length;
            if (hasEntries) {
                for (int i = 0; i < entries.length; i++) {
                    bindCard(commandBuilder, eventBuilder, i, entries[i], true, true);
                }
            }
        } else if (hasEntries) {
            for (int i = 0; i < entries.length; i++) {
                bindCard(commandBuilder, null, i, entries[i], false, false);
            }
        }

        if (eventBuilder != null) {
            bindGlobalEvents(eventBuilder);
        }
        if (dismissed || !isCurrentPageActive()) {
            dismissed = true;
            return;
        }
        sendUpdate(commandBuilder, eventBuilder, false);
    }

    private boolean isCurrentPageActive() {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return false;
        }
        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            return false;
        }
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null || playerComponent.getPageManager() == null) {
            return false;
        }
        return playerComponent.getPageManager().getCustomPage() == this;
    }

    private void bindGlobalEvents(@Nonnull UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#NpcDebugRosterCloseButton",
                EventData.of(EVENT_ACTION, ACTION_CLOSE),
                false
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#NpcDebugRosterApplyFilterButton",
                EventData.of(EVENT_FILTER_QUERY, "#NpcDebugRosterFilterQueryField.Value"),
                false
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#NpcDebugRosterClearFilterButton",
                EventData.of(EVENT_ACTION, ACTION_FILTER_CLEAR),
                false
        );
    }

    private void applyCopyBufferState(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.set("#NpcDebugRosterCopyRow.Visible", copyBufferVisible);
        commandBuilder.set("#NpcDebugRosterCopyField.Value", copyBufferValue);
    }

    private void refreshEntries() {
        List<NpcDebugLinkedEntry> values = entrySupplier != null ? entrySupplier.get() : List.of();
        if (values == null || values.isEmpty()) {
            sourceEntries = new NpcDebugLinkedEntry[0];
            entries = new NpcDebugLinkedEntry[0];
            previousEntriesByUuid.clear();
            changeSummaryByUuid.clear();
            highlightedNpcUuids.clear();
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
        sourceEntries = safe.toArray(new NpcDebugLinkedEntry[0]);

        refreshChangeSummaries();
        pruneHighlights();
        entries = applyFilters();
    }

    private void refreshChangeSummaries() {
        Map<UUID, NpcDebugLinkedEntry> currentByUuid = new HashMap<>();
        Map<UUID, String> summaries = new HashMap<>();
        for (NpcDebugLinkedEntry current : sourceEntries) {
            UUID uuid = current.npcUuid();
            currentByUuid.put(uuid, current);
            String summary = resolveChangeSummary(previousEntriesByUuid.get(uuid), current);
            if (summary != null && !summary.isBlank()) {
                summaries.put(uuid, summary);
            }
        }
        previousEntriesByUuid.clear();
        previousEntriesByUuid.putAll(currentByUuid);
        changeSummaryByUuid.clear();
        changeSummaryByUuid.putAll(summaries);
    }

    @Nullable
    private String resolveChangeSummary(@Nullable NpcDebugLinkedEntry previous, @Nonnull NpcDebugLinkedEntry current) {
        if (previous == null) {
            return "Newly linked";
        }
        List<String> fields = new ArrayList<>(6);
        if (previous.loaded() != current.loaded()) {
            fields.add("status");
        }
        if (!equalsValue(previous.stateName(), current.stateName())) {
            fields.add("state");
        }
        if (!equalsValue(previous.healthText(), current.healthText())) {
            fields.add("health");
        }
        if (!equalsValue(previous.flockText(), current.flockText())) {
            fields.add("flock");
        }
        if (!equalsValue(previous.locationText(), current.locationText())) {
            fields.add("location");
        }
        if (!equalsValue(previous.distanceText(), current.distanceText())) {
            fields.add("distance");
        }
        if (fields.isEmpty()) {
            return null;
        }
        String joined = String.join(", ", fields);
        if (joined.length() > 44) {
            joined = joined.substring(0, 41) + "...";
        }
        return "Changed: " + joined;
    }

    private boolean equalsValue(@Nullable String left, @Nullable String right) {
        String a = normalizeMaybe(left);
        String b = normalizeMaybe(right);
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    private void pruneHighlights() {
        Set<UUID> currentUuids = new HashSet<>();
        for (NpcDebugLinkedEntry entry : sourceEntries) {
            currentUuids.add(entry.npcUuid());
        }
        highlightedNpcUuids.retainAll(currentUuids);
    }

    @Nonnull
    private NpcDebugLinkedEntry[] applyFilters() {
        if (sourceEntries.length == 0) {
            return new NpcDebugLinkedEntry[0];
        }
        String query = normalizeMaybe(filterQuery);
        String loweredQuery = query != null ? query.toLowerCase(Locale.ROOT) : null;
        ArrayList<NpcDebugLinkedEntry> filtered = new ArrayList<>(sourceEntries.length);
        for (NpcDebugLinkedEntry entry : sourceEntries) {
            if (loweredQuery != null && !matchesQuery(entry, loweredQuery)) {
                continue;
            }
            filtered.add(entry);
        }
        return filtered.toArray(new NpcDebugLinkedEntry[0]);
    }

    private boolean matchesQuery(@Nonnull NpcDebugLinkedEntry entry, @Nonnull String loweredQuery) {
        return containsToken(entry.displayName(), loweredQuery)
                || containsToken(entry.roleId(), loweredQuery)
                || containsToken(entry.stateName(), loweredQuery)
                || containsToken(entry.healthText(), loweredQuery)
                || containsToken(entry.flockText(), loweredQuery)
                || containsToken(entry.locationText(), loweredQuery)
                || containsToken(entry.distanceText(), loweredQuery)
                || containsToken(entry.npcUuid().toString(), loweredQuery);
    }

    private boolean containsToken(@Nullable String text, @Nonnull String loweredQuery) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(loweredQuery);
    }

    private void toggleHighlight(@Nonnull UUID npcUuid) {
        boolean enabled;
        if (highlightedNpcUuids.contains(npcUuid)) {
            highlightedNpcUuids.remove(npcUuid);
            enabled = false;
        } else {
            highlightedNpcUuids.add(npcUuid);
            enabled = true;
        }
        persistHighlightState(npcUuid, enabled);
        String stateText = enabled ? "enabled" : "disabled";
        setStatusMessage("Highlight " + stateText + " for " + npcUuid + ".", STATUS_MESSAGE_DURATION_MS);
        notifyPlayer("Highlight " + stateText + ": " + npcUuid);
    }

    private void loadPersistedHighlights() {
        highlightedNpcUuids.clear();
        Set<UUID> persisted = highlightedNpcSupplier != null ? highlightedNpcSupplier.get() : Set.of();
        if (persisted == null || persisted.isEmpty()) {
            return;
        }
        highlightedNpcUuids.addAll(persisted);
    }

    private void persistHighlightState(@Nonnull UUID npcUuid, boolean highlighted) {
        if (highlightPersistenceCallback == null) {
            return;
        }
        highlightPersistenceCallback.accept(npcUuid, highlighted);
    }

    private void applyFilterQuery(@Nullable String rawQuery) {
        String trimmed = rawQuery != null ? rawQuery.trim() : "";
        if (trimmed.length() > 48) {
            trimmed = trimmed.substring(0, 48);
        }
        filterQuery = trimmed;
        syncQueryFieldValue = true;
        if (filterQuery.isBlank()) {
            setStatusMessage("Search query cleared.", STATUS_MESSAGE_DURATION_MS);
        } else {
            setStatusMessage("Search applied: \"" + filterQuery + "\"", STATUS_MESSAGE_DURATION_MS);
        }
    }

    private void clearFilters() {
        filterQuery = "";
        syncQueryFieldValue = true;
        setStatusMessage("Search cleared.", STATUS_MESSAGE_DURATION_MS);
    }

    @Nonnull
    private Map<String, String> decodeRawEventData(@Nullable String rawData) {
        if (rawData == null || rawData.isBlank()) {
            return Map.of();
        }
        try {
            return MapCodec.STRING_HASH_MAP_CODEC.decodeJson(
                    new RawJsonReader(rawData.toCharArray()),
                    ExtraInfo.THREAD_LOCAL.get()
            );
        } catch (IOException | RuntimeException ignored) {
            return Map.of();
        }
    }

    @Nonnull
    private String resolveSubtitle() {
        int totalCount = sourceEntries.length;
        int loadedCount = countLoaded(sourceEntries);
        int shownCount = entries.length;

        StringBuilder subtitle = new StringBuilder();
        subtitle.append("Linked NPCs: ").append(totalCount).append(" (").append(loadedCount).append(" loaded)");
        if (shownCount != totalCount) {
            subtitle.append(" | Showing: ").append(shownCount);
        }

        List<String> activeFilters = new ArrayList<>(1);
        if (!filterQuery.isBlank()) {
            activeFilters.add("query \"" + filterQuery + "\"");
        }
        if (!activeFilters.isEmpty()) {
            subtitle.append(" | Filters: ").append(String.join(", ", activeFilters));
        }

        String activeStatus = resolveActiveStatusMessage();
        if (activeStatus != null) {
            subtitle.append(" | ").append(activeStatus);
        }
        return subtitle.toString();
    }

    private int countLoaded(@Nonnull NpcDebugLinkedEntry[] values) {
        int loaded = 0;
        for (NpcDebugLinkedEntry entry : values) {
            if (entry.loaded()) {
                loaded++;
            }
        }
        return loaded;
    }

    @Nullable
    private String resolveActiveStatusMessage() {
        if (statusMessage == null) {
            return null;
        }
        if (System.currentTimeMillis() > statusMessageUntilMs) {
            statusMessage = null;
            statusMessageUntilMs = 0L;
            return null;
        }
        return statusMessage;
    }

    private void setStatusMessage(@Nonnull String message, long durationMs) {
        statusMessage = message;
        statusMessageUntilMs = System.currentTimeMillis() + Math.max(500L, durationMs);
    }

    private void notifyPlayer(@Nonnull String message) {
        if (notificationCallback != null) {
            notificationCallback.accept(message);
        }
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

    @Nullable
    private String normalizeMaybe(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed;
    }

    @Nullable
    private String firstNonBlank(@Nullable String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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
            .append(
                new KeyedCodec<>(EVENT_FILTER_QUERY, Codec.STRING),
                (data, value) -> data.filterQuery = value,
                data -> data.filterQuery
            )
            .add()
            .build();

        private String action;
        private String filterQuery;
    }
}
