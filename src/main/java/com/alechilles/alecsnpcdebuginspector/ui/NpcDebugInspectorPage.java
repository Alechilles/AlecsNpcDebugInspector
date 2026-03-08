package com.alechilles.alecsnpcdebuginspector.ui;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugEventCategory;
import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugEventLogFilterSettings;
import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
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
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Read-only page that renders one NPC debug snapshot.
 */
public final class NpcDebugInspectorPage extends InteractiveCustomUIPage<NpcDebugInspectorPage.PageEventData> {
    public static final String UI_PATH = "NpcDebugInspectorPage.ui";
    private static final String SECTION_HEADER_UI_PATH = "NpcDebugInspectorSectionHeaderRow.ui";
    private static final String FIELD_ROW_UI_PATH = "NpcDebugInspectorFieldRow.ui";
    private static final String EVENT_FILTER_ROW_UI_PATH = "NpcDebugInspectorEventFilterRow.ui";

    private static final String EVENT_ACTION = "Action";
    private static final String EVENT_TYPE = "Type";
    private static final String ACTION_CLOSE = "Close";
    private static final String ACTION_BACK = "Back";
    private static final String ACTION_TOGGLE_PIN_MODE = "TogglePinMode";
    private static final String ACTION_TOGGLE_FIELD_PREFIX = "TogglePinnedField:";
    private static final String ACTION_TOGGLE_SECTION_PREFIX = "ToggleSection:";
    private static final String ACTION_MOVE_SECTION_UP_PREFIX = "MoveSectionUp:";
    private static final String ACTION_MOVE_SECTION_DOWN_PREFIX = "MoveSectionDown:";
    private static final String ACTION_TOGGLE_EVENT_FILTER_PREFIX = "ToggleEventFilter:";
    private static final String ACTION_REFRESH_RATE_CHANGED = "RefreshRateChanged";
    private static final String EVENT_REFRESH_INTERVAL_MS = "RefreshIntervalMs";
    private static final String EVENT_REFRESH_INTERVAL_MS_AT = "@RefreshIntervalMs";
    private static final Pattern REFRESH_INTERVAL_VALUE_PATTERN = Pattern.compile(
            "\"(?:@?RefreshIntervalMs|Value|NewValue|SliderValue)\"\\s*:\\s*(?:\"([^\"]*)\"|(-?\\d+(?:\\.\\d+)?))",
            Pattern.CASE_INSENSITIVE
    );

    private static final String OVERVIEW_SECTION_ID = "section.overview";
    private static final long REFRESH_SUPPRESS_AFTER_REORDER_MS = 600L;
    private static final long IMMEDIATE_REARM_DELAY_MS = 75L;
    private final Supplier<NpcDebugSnapshot> snapshotSupplier;
    @Nullable
    private final UUID targetNpcUuid;
    @Nullable
    private final Runnable backCallback;

    private NpcDebugSnapshot latestSnapshot;
    private final Map<String, InspectorSection> sectionsById;
    private final ArrayList<String> sectionOrder;
    private final LinkedHashSet<String> collapsedSectionIds;
    private final LinkedHashSet<String> pinnedFieldKeys;
    private final ArrayList<String> renderedFieldKeys;
    private final EnumSet<NpcDebugEventCategory> enabledEventCategories;
    private String resolvedNpcName;
    private boolean pinModeEnabled;
    private boolean sectionStateInitialized;
    @Nullable
    private String lastRenderedStructureSignature;
    private volatile boolean dismissed;
    private volatile boolean refreshLoopStarted;
    private volatile long refreshSuppressedUntilMs;
    private final AtomicLong refreshTickGeneration;

    public NpcDebugInspectorPage(@Nonnull PlayerRef playerRef,
                                 @Nullable UUID targetNpcUuid,
                                 @Nonnull Supplier<NpcDebugSnapshot> snapshotSupplier,
                                 @Nullable Runnable backCallback) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageEventData.CODEC);
        this.snapshotSupplier = snapshotSupplier;
        this.targetNpcUuid = targetNpcUuid;
        this.backCallback = backCallback;
        this.latestSnapshot = new NpcDebugSnapshot("NPC Debug Inspector", "", "No data.");
        this.sectionsById = new LinkedHashMap<>();
        this.sectionOrder = new ArrayList<>();
        this.collapsedSectionIds = new LinkedHashSet<>();
        this.pinnedFieldKeys = new LinkedHashSet<>();
        this.renderedFieldKeys = new ArrayList<>();
        this.enabledEventCategories = EnumSet.noneOf(NpcDebugEventCategory.class);
        this.resolvedNpcName = "NPC";
        this.pinModeEnabled = false;
        this.sectionStateInitialized = false;
        this.lastRenderedStructureSignature = null;
        this.dismissed = false;
        this.refreshLoopStarted = false;
        this.refreshSuppressedUntilMs = 0L;
        this.refreshTickGeneration = new AtomicLong();
        syncEventCategoryStateFromSettings();
    }

    /**
     * Legacy constructor kept for compatibility where target UUID is unavailable.
     */
    public NpcDebugInspectorPage(@Nonnull PlayerRef playerRef,
                                 @Nullable UUID targetNpcUuid,
                                 @Nonnull Supplier<NpcDebugSnapshot> snapshotSupplier) {
        this(playerRef, targetNpcUuid, snapshotSupplier, null);
    }

    /**
     * Legacy constructor kept for compatibility where target UUID is unavailable.
     */
    public NpcDebugInspectorPage(@Nonnull PlayerRef playerRef,
                                 @Nonnull Supplier<NpcDebugSnapshot> snapshotSupplier) {
        this(playerRef, null, snapshotSupplier, null);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder,
                      @Nonnull Store<EntityStore> store) {
        commandBuilder.append(UI_PATH);
        refreshSnapshotData();
        syncEventCategoryStateFromSettings();
        syncPinnedStateFromManager();
        prunePinnedKeys();
        applySnapshot(commandBuilder);
        rebuildRows(commandBuilder, eventBuilder, true);
        bindGlobalEvents(eventBuilder);
        lastRenderedStructureSignature = buildStructureSignature();
        syncPinnedSectionOrderToOverlay();
        startRefreshLoop();
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull String rawData) {
        Double resolvedRefreshIntervalFromRaw = resolveRefreshIntervalValueFromRawPayload(rawData);
        if (isRefreshEventPayload(rawData) || resolvedRefreshIntervalFromRaw != null) {
            applyRefreshIntervalFromEvent(resolvedRefreshIntervalFromRaw);
            sendRefreshUpdate();
            return;
        }

        Map<String, String> rawEventData = decodeRawEventData(rawData);
        String resolvedAction = firstNonBlank(
                rawEventData.get(EVENT_ACTION),
                rawEventData.get("action"),
                rawEventData.get(EVENT_TYPE),
                rawEventData.get("type")
        );
        Double resolvedRefreshInterval = resolveRefreshIntervalValue(rawEventData);
        if (ACTION_REFRESH_RATE_CHANGED.equals(resolvedAction) || resolvedRefreshInterval != null) {
            applyRefreshIntervalFromEvent(resolvedRefreshInterval);
            sendRefreshUpdate();
            rearmRefreshLoopSoon();
            return;
        }
        super.handleDataEvent(ref, store, rawData);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PageEventData data) {
        String resolvedAction = firstNonBlank(data.action, data.type);
        if (resolvedAction == null || resolvedAction.isBlank()) {
            return;
        }
        if (ACTION_CLOSE.equals(resolvedAction)) {
            close();
            return;
        }
        if (ACTION_BACK.equals(resolvedAction)) {
            if (backCallback != null) {
                backCallback.run();
            } else {
                close();
            }
            return;
        }
        if (ACTION_TOGGLE_PIN_MODE.equals(resolvedAction)) {
            togglePinMode();
            sendRefreshUpdate();
            rearmRefreshLoopSoon();
            return;
        }
        if (resolvedAction.startsWith(ACTION_TOGGLE_EVENT_FILTER_PREFIX)) {
            String categoryId = parseActionSuffix(resolvedAction, ACTION_TOGGLE_EVENT_FILTER_PREFIX);
            if (categoryId != null) {
                toggleEventCategory(categoryId);
                sendRefreshUpdate();
                rearmRefreshLoopSoon();
            }
            return;
        }
        if (resolvedAction.startsWith(ACTION_MOVE_SECTION_UP_PREFIX)) {
            String sectionId = parseActionSuffix(resolvedAction, ACTION_MOVE_SECTION_UP_PREFIX);
            if (sectionId != null && moveSectionByOffset(sectionId, -1)) {
                suppressRefreshTemporarily();
                sendRefreshUpdate();
                rearmRefreshLoopSoon();
            }
            return;
        }
        if (resolvedAction.startsWith(ACTION_MOVE_SECTION_DOWN_PREFIX)) {
            String sectionId = parseActionSuffix(resolvedAction, ACTION_MOVE_SECTION_DOWN_PREFIX);
            if (sectionId != null && moveSectionByOffset(sectionId, 1)) {
                suppressRefreshTemporarily();
                sendRefreshUpdate();
                rearmRefreshLoopSoon();
            }
            return;
        }
        if (resolvedAction.startsWith(ACTION_TOGGLE_SECTION_PREFIX)) {
            String sectionId = parseActionSuffix(resolvedAction, ACTION_TOGGLE_SECTION_PREFIX);
            if (sectionId != null) {
                toggleSectionCollapsed(sectionId);
                sendRefreshUpdate();
                rearmRefreshLoopSoon();
            }
            return;
        }
        if (resolvedAction.startsWith(ACTION_TOGGLE_FIELD_PREFIX)) {
            int index = parseFieldIndex(resolvedAction);
            if (index >= 0) {
                togglePinnedField(index);
                sendRefreshUpdate();
                rearmRefreshLoopSoon();
            }
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        dismissed = true;
    }

    private void togglePinMode() {
        if (targetNpcUuid == null) {
            return;
        }
        if (pinModeEnabled) {
            NpcDebugPinnedOverlayManager.unpinNpc(playerRef, targetNpcUuid);
            pinModeEnabled = false;
            pinnedFieldKeys.clear();
        } else {
            NpcDebugPinnedOverlayManager.pinNpc(playerRef, targetNpcUuid, snapshotSupplier);
            pinModeEnabled = true;
            pinnedFieldKeys.clear();
            NpcDebugPinnedOverlayManager.updatePinnedFieldKeys(playerRef, targetNpcUuid, pinnedFieldKeys);
            syncPinnedSectionOrderToOverlay();
        }
    }

    private void applyRefreshIntervalFromEvent(@Nullable Double rawValue) {
        if (rawValue == null || !Double.isFinite(rawValue)) {
            return;
        }
        NpcDebugUiRefreshSettings.setFromUiValue(playerRef, rawValue);
    }

    @Nullable
    private Double resolveRefreshIntervalValue(@Nonnull Map<String, String> rawEventData) {
        String direct = firstNonBlank(
                rawEventData.get(EVENT_REFRESH_INTERVAL_MS),
                rawEventData.get(EVENT_REFRESH_INTERVAL_MS_AT),
                rawEventData.get("Value"),
                rawEventData.get("NewValue"),
                rawEventData.get("SliderValue")
        );
        Double parsedDirect = parseDouble(direct);
        if (parsedDirect != null) {
            return parsedDirect;
        }
        for (Map.Entry<String, String> entry : rawEventData.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String lowered = key.toLowerCase(Locale.ROOT);
            if (lowered.contains("refresh") && (lowered.contains("value") || lowered.contains("interval"))) {
                Double parsed = parseDouble(entry.getValue());
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    @Nullable
    private Double resolveRefreshIntervalValueFromRawPayload(@Nullable String rawData) {
        if (rawData == null || rawData.isBlank()) {
            return null;
        }
        Matcher matcher = REFRESH_INTERVAL_VALUE_PATTERN.matcher(rawData);
        while (matcher.find()) {
            Double parsed = parseDouble(firstNonBlank(matcher.group(1), matcher.group(2)));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
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

    @Nullable
    private Double parseDouble(@Nullable String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(rawValue.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isRefreshEventPayload(@Nullable String rawData) {
        if (rawData == null || rawData.isBlank()) {
            return false;
        }
        String lowered = rawData.toLowerCase(Locale.ROOT);
        return lowered.contains("\"" + EVENT_REFRESH_INTERVAL_MS.toLowerCase(Locale.ROOT) + "\"")
                || lowered.contains("\"" + EVENT_REFRESH_INTERVAL_MS_AT.toLowerCase(Locale.ROOT) + "\"")
                || lowered.contains(ACTION_REFRESH_RATE_CHANGED.toLowerCase(Locale.ROOT));
    }

    @Nullable
    private String parseActionSuffix(@Nonnull String action, @Nonnull String prefix) {
        String raw = action.substring(prefix.length()).trim();
        if (raw.isBlank()) {
            return null;
        }
        return raw;
    }

    private int parseFieldIndex(@Nonnull String action) {
        String value = action.substring(ACTION_TOGGLE_FIELD_PREFIX.length()).trim();
        if (value.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void togglePinnedField(int index) {
        if (!pinModeEnabled || targetNpcUuid == null || index < 0 || index >= renderedFieldKeys.size()) {
            return;
        }
        String key = renderedFieldKeys.get(index);
        if (key == null || key.isBlank()) {
            return;
        }
        if (pinnedFieldKeys.contains(key)) {
            pinnedFieldKeys.remove(key);
        } else {
            pinnedFieldKeys.add(key);
        }
        NpcDebugPinnedOverlayManager.updatePinnedFieldKeys(playerRef, targetNpcUuid, pinnedFieldKeys);
    }

    private void toggleSectionCollapsed(@Nonnull String sectionId) {
        if (!sectionsById.containsKey(sectionId)) {
            return;
        }
        if (collapsedSectionIds.contains(sectionId)) {
            collapsedSectionIds.remove(sectionId);
        } else {
            collapsedSectionIds.add(sectionId);
        }
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

    private boolean moveSection(int fromIndex, int toIndex) {
        if (sectionOrder.size() < 2) {
            return false;
        }
        if (fromIndex < 0 || fromIndex >= sectionOrder.size()) {
            return false;
        }
        int boundedToIndex = Math.max(0, Math.min(toIndex, sectionOrder.size() - 1));
        if (fromIndex == boundedToIndex) {
            return false;
        }

        String removed = sectionOrder.remove(fromIndex);
        int insertIndex = Math.max(0, Math.min(boundedToIndex, sectionOrder.size()));
        sectionOrder.add(insertIndex, removed);
        return true;
    }

    private boolean moveSectionByOffset(@Nonnull String sectionId, int offset) {
        int fromIndex = sectionOrder.indexOf(sectionId);
        if (fromIndex < 0) {
            return false;
        }
        return moveSection(fromIndex, fromIndex + offset);
    }

    private void suppressRefreshTemporarily() {
        refreshSuppressedUntilMs = System.currentTimeMillis() + REFRESH_SUPPRESS_AFTER_REORDER_MS;
    }

    private boolean isRefreshSuppressed() {
        return System.currentTimeMillis() < refreshSuppressedUntilMs;
    }

    private void bindGlobalEvents(@Nonnull UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#NpcDebugInspectorBackButton",
                EventData.of(EVENT_ACTION, ACTION_BACK),
                false
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#NpcDebugInspectorCloseButton",
                EventData.of(EVENT_ACTION, ACTION_CLOSE),
                false
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#NpcDebugInspectorPinButton",
                EventData.of(EVENT_ACTION, ACTION_TOGGLE_PIN_MODE),
                false
        );
        EventData refreshEventData = new EventData()
                .append(EVENT_ACTION, ACTION_REFRESH_RATE_CHANGED)
                .append(EVENT_TYPE, ACTION_REFRESH_RATE_CHANGED)
                .append(EVENT_REFRESH_INTERVAL_MS, "#NpcDebugInspectorRefreshSlider.Value");
        refreshEventData.append(EVENT_REFRESH_INTERVAL_MS_AT, "#NpcDebugInspectorRefreshSlider.Value");
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#NpcDebugInspectorRefreshSlider",
                refreshEventData,
                false
        );
        if (hasRecentEventsSection()) {
            bindEventFilterToggle(eventBuilder, "#NpcDebugInspectorFilterCore", NpcDebugEventCategory.CORE);
            bindEventFilterToggle(eventBuilder, "#NpcDebugInspectorFilterTargeting", NpcDebugEventCategory.TARGETING);
            bindEventFilterToggle(eventBuilder, "#NpcDebugInspectorFilterTimers", NpcDebugEventCategory.TIMERS);
            bindEventFilterToggle(eventBuilder, "#NpcDebugInspectorFilterAlarms", NpcDebugEventCategory.ALARMS);
            bindEventFilterToggle(eventBuilder, "#NpcDebugInspectorFilterNeeds", NpcDebugEventCategory.NEEDS);
            bindEventFilterToggle(eventBuilder, "#NpcDebugInspectorFilterFlock", NpcDebugEventCategory.FLOCK);
        }
    }

    private void bindEventFilterToggle(@Nonnull UIEventBuilder eventBuilder,
                                       @Nonnull String selector,
                                       @Nonnull NpcDebugEventCategory category) {
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                selector,
                EventData.of(EVENT_ACTION, ACTION_TOGGLE_EVENT_FILTER_PREFIX + category.id()),
                false
        );
    }

    private void applySnapshot(@Nonnull UICommandBuilder commandBuilder) {
        long refreshIntervalMs = NpcDebugUiRefreshSettings.getIntervalMs(playerRef);
        InspectorSubtitleFields subtitleFields = parseInspectorSubtitleFields(latestSnapshot.subtitle());
        commandBuilder.set("#NpcDebugInspectorTitle.Text", "Inspector - " + resolvedNpcName);
        commandBuilder.set("#NpcDebugInspectorSubtitleUuidValue.Text", subtitleFields.uuid);
        commandBuilder.set("#NpcDebugInspectorSubtitleUtcValue.Text", subtitleFields.utc);
        commandBuilder.set("#NpcDebugInspectorPinButton.Text", pinModeEnabled ? "Unpin" : "Pin NPC");
        commandBuilder.set("#NpcDebugInspectorBackButton.Visible", backCallback != null);
        commandBuilder.set("#NpcDebugInspectorFooterGap.Visible", backCallback != null);
        commandBuilder.set("#NpcDebugInspectorRefreshLabel.Text", NpcDebugUiRefreshSettings.formatIntervalLabel(refreshIntervalMs));
        commandBuilder.set("#NpcDebugInspectorRefreshSlider.Value", NpcDebugUiRefreshSettings.toUiValue(refreshIntervalMs));
        if (hasRecentEventsSection()) {
            commandBuilder.set("#NpcDebugInspectorFilterCore.Value", enabledEventCategories.contains(NpcDebugEventCategory.CORE));
            commandBuilder.set("#NpcDebugInspectorFilterTargeting.Value", enabledEventCategories.contains(NpcDebugEventCategory.TARGETING));
            commandBuilder.set("#NpcDebugInspectorFilterTimers.Value", enabledEventCategories.contains(NpcDebugEventCategory.TIMERS));
            commandBuilder.set("#NpcDebugInspectorFilterAlarms.Value", enabledEventCategories.contains(NpcDebugEventCategory.ALARMS));
            commandBuilder.set("#NpcDebugInspectorFilterNeeds.Value", enabledEventCategories.contains(NpcDebugEventCategory.NEEDS));
            commandBuilder.set("#NpcDebugInspectorFilterFlock.Value", enabledEventCategories.contains(NpcDebugEventCategory.FLOCK));
        }
        commandBuilder.set(
                "#NpcDebugInspectorPinHint.Text",
                pinModeEnabled
                        ? "Pinned overlay active. Check fields to pin. Use arrows to reorder sections."
                        : "Pin NPC to create an overlay. Use arrows to reorder sections."
        );
    }

    @Nonnull
    private String compactSubtitle(@Nullable String subtitle) {
        if (subtitle == null || subtitle.isBlank()) {
            return "";
        }
        String compact = subtitle;
        int markerIndex = compact.indexOf("| Changed lines");
        if (markerIndex > 0) {
            compact = compact.substring(0, markerIndex).trim();
        }
        compact = compact
                .replaceAll("\\s*\\|\\s*Loaded:\\s*[^|]+", "")
                .replace("Game Time (UTC):", "UTC:")
                .trim();
        return compact.replaceAll("\\s*\\|\\s*UTC:\\s*", "\nUTC: ").trim();
    }

    @Nonnull
    private InspectorSubtitleFields parseInspectorSubtitleFields(@Nullable String subtitle) {
        String compact = compactSubtitle(subtitle);
        String uuidValue = "n/a";
        String utcValue = "n/a";
        if (!compact.isBlank()) {
            String[] lines = compact.split("\\R");
            for (String line : lines) {
                String trimmed = line != null ? line.trim() : "";
                if (trimmed.regionMatches(true, 0, "UUID:", 0, 5)) {
                    String candidate = trimmed.substring(5).trim();
                    if (!candidate.isBlank()) {
                        uuidValue = candidate;
                    }
                    continue;
                }
                if (trimmed.regionMatches(true, 0, "UTC:", 0, 4)) {
                    String candidate = trimmed.substring(4).trim();
                    if (!candidate.isBlank()) {
                        utcValue = candidate;
                    }
                }
            }
        }
        if ("n/a".equals(uuidValue) && targetNpcUuid != null) {
            uuidValue = targetNpcUuid.toString();
        }
        return new InspectorSubtitleFields(uuidValue, utcValue);
    }

    private void rebuildRows(@Nonnull UICommandBuilder commandBuilder,
                             @Nullable UIEventBuilder eventBuilder,
                             boolean rebuildStructure) {
        if (rebuildStructure) {
            commandBuilder.clear("#NpcDebugInspectorFieldList");
            renderedFieldKeys.clear();
        }

        int sectionIndex = 0;
        for (String sectionId : sectionOrder) {
            InspectorSection section = sectionsById.get(sectionId);
            if (section == null) {
                continue;
            }

            String sectionSelector = "#NpcDebugInspectorFieldList[" + sectionIndex + "]";
            sectionIndex++;
            if (rebuildStructure) {
                commandBuilder.append("#NpcDebugInspectorFieldList", SECTION_HEADER_UI_PATH);
            }
            boolean collapsed = collapsedSectionIds.contains(section.id);
            commandBuilder.set(sectionSelector + " #SectionToggleExpandedIcon.Visible", !collapsed);
            commandBuilder.set(sectionSelector + " #SectionToggleCollapsedIcon.Visible", collapsed);
            commandBuilder.set(sectionSelector + " #SectionTitle.Text", section.title.toUpperCase(Locale.ROOT));
            commandBuilder.set(sectionSelector + " #SectionCount.Text", section.fields.length + " fields");
            if (rebuildStructure) {
                commandBuilder.clear(sectionSelector + " #SectionFields");
            }
            commandBuilder.set(sectionSelector + " #SectionFields.Visible", !collapsed);
            int sectionOrderIndex = sectionOrder.indexOf(section.id);
            commandBuilder.set(sectionSelector + " #SectionMoveUpButton.Visible", sectionOrderIndex > 0);
            commandBuilder.set(sectionSelector + " #SectionMoveDownButton.Visible", sectionOrderIndex >= 0 && sectionOrderIndex < sectionOrder.size() - 1);

            if (rebuildStructure && eventBuilder != null) {
                eventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        sectionSelector + " #SectionToggleButton",
                        EventData.of(EVENT_ACTION, ACTION_TOGGLE_SECTION_PREFIX + section.id),
                        false
                );
                eventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        sectionSelector + " #SectionMoveUpButton",
                        EventData.of(EVENT_ACTION, ACTION_MOVE_SECTION_UP_PREFIX + section.id),
                        false
                );
                eventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        sectionSelector + " #SectionMoveDownButton",
                        EventData.of(EVENT_ACTION, ACTION_MOVE_SECTION_DOWN_PREFIX + section.id),
                        false
                );
            }

            boolean recentEventsSection = isRecentEventsSection(section);
            if (recentEventsSection && rebuildStructure) {
                commandBuilder.append(sectionSelector + " #SectionFields", EVENT_FILTER_ROW_UI_PATH);
            }
            if (collapsed) {
                continue;
            }

            int fieldRowIndex = recentEventsSection ? 1 : 0;
            for (InspectorField field : section.fields) {
                String fieldSelector = sectionSelector + " #SectionFields[" + fieldRowIndex + "]";
                fieldRowIndex++;
                if (rebuildStructure) {
                    commandBuilder.append(sectionSelector + " #SectionFields", FIELD_ROW_UI_PATH);
                }
                FieldNameValue nameValue = splitFieldNameAndValue(field.displayText);
                boolean showSplitField = nameValue != null;
                commandBuilder.set(fieldSelector + " #FieldText.Visible", !showSplitField);
                commandBuilder.set(fieldSelector + " #FieldSplitRow.Visible", showSplitField);
                if (showSplitField) {
                    commandBuilder.set(fieldSelector + " #FieldSplitRow #FieldName.Text", nameValue.nameText);
                    commandBuilder.set(fieldSelector + " #FieldSplitRow #FieldValue.Text", nameValue.valueText);
                } else {
                    commandBuilder.set(fieldSelector + " #FieldText.Text", field.displayText);
                }
                commandBuilder.set(fieldSelector + " #FieldChanged.Visible", field.changed);

                boolean showCheck = pinModeEnabled && field.pinnable && field.key != null;
                commandBuilder.set(fieldSelector + " #FieldCheck.Visible", showCheck);
                commandBuilder.set(fieldSelector + " #FieldCheck.Value", showCheck && pinnedFieldKeys.contains(field.key));

                if (showCheck && rebuildStructure && eventBuilder != null) {
                    int fieldIndex = renderedFieldKeys.size();
                    renderedFieldKeys.add(field.key);
                    eventBuilder.addEventBinding(
                            CustomUIEventBindingType.ValueChanged,
                            fieldSelector + " #FieldCheck",
                            EventData.of(EVENT_ACTION, ACTION_TOGGLE_FIELD_PREFIX + fieldIndex),
                            false
                    );
                }
            }
        }

    }

    @Nonnull
    private NpcDebugSnapshot resolveSnapshot() {
        if (snapshotSupplier == null) {
            return new NpcDebugSnapshot("NPC Debug Inspector", "Snapshot supplier unavailable", "No data.");
        }
        try {
            NpcDebugSnapshot snapshot = snapshotSupplier.get();
            if (snapshot != null) {
                return snapshot;
            }
        } catch (RuntimeException ignored) {
            // Return fallback below.
        }
        return new NpcDebugSnapshot("NPC Debug Inspector", "Snapshot unavailable", "No data.");
    }

    private void refreshSnapshotData() {
        latestSnapshot = resolveSnapshot();
        NpcDebugInspectorLine[] lines = NpcDebugInspectorLineParser.parse(latestSnapshot.details());
        resolvedNpcName = NpcDebugNameHierarchyResolver.resolvePreferredName(lines, "NPC");
        rebuildSections(lines);
        syncSectionState();
    }

    private void rebuildSections(@Nonnull NpcDebugInspectorLine[] lines) {
        sectionsById.clear();
        List<InspectorSection> sections = new ArrayList<>();
        Map<String, Integer> idCounts = new HashMap<>();

        String currentTitle = null;
        String currentId = null;
        List<InspectorField> currentFields = new ArrayList<>();

        for (NpcDebugInspectorLine line : lines) {
            if (isSectionHeaderLine(line.displayText)) {
                if (currentTitle != null && currentId != null) {
                    sections.add(new InspectorSection(currentId, currentTitle, currentFields.toArray(new InspectorField[0])));
                }
                currentTitle = extractSectionTitle(line.displayText);
                currentId = buildSectionId(currentTitle, idCounts);
                currentFields = new ArrayList<>();
                continue;
            }

            if (currentTitle == null) {
                currentTitle = "Overview";
                currentId = OVERVIEW_SECTION_ID;
                idCounts.put(currentId, 1);
            }
            InspectorField field = toInspectorField(line);
            if (field != null) {
                currentFields.add(field);
            }
        }

        if (currentTitle != null && currentId != null) {
            sections.add(new InspectorSection(currentId, currentTitle, currentFields.toArray(new InspectorField[0])));
        }

        if (sections.isEmpty()) {
            sections.add(new InspectorSection(
                    OVERVIEW_SECTION_ID,
                    "Overview",
                    new InspectorField[] {
                            new InspectorField(null, "No inspector fields available.", false, false)
                    }
            ));
        }

        for (InspectorSection section : sections) {
            sectionsById.put(section.id, section);
        }
    }

    private void syncSectionState() {
        List<String> snapshotOrder = new ArrayList<>(sectionsById.size());
        for (InspectorSection section : sectionsById.values()) {
            snapshotOrder.add(section.id);
        }
        applyDefaultSectionOrdering(snapshotOrder);

        if (!sectionStateInitialized) {
            sectionOrder.clear();
            sectionOrder.addAll(snapshotOrder);
            collapsedSectionIds.clear();
            for (String id : sectionOrder) {
                if (!OVERVIEW_SECTION_ID.equals(id)) {
                    collapsedSectionIds.add(id);
                }
            }
            sectionStateInitialized = true;
            return;
        }

        sectionOrder.removeIf(id -> !sectionsById.containsKey(id));
        for (String id : snapshotOrder) {
            if (!sectionOrder.contains(id)) {
                sectionOrder.add(id);
            }
        }
        collapsedSectionIds.removeIf(id -> !sectionsById.containsKey(id));
    }

    private void applyDefaultSectionOrdering(@Nonnull List<String> sectionIds) {
        if (sectionIds.isEmpty()) {
            return;
        }
        String recentEventsId = findSectionIdByTitle(sectionIds, NpcDebugInspectorLineParser.RECENT_EVENTS_SECTION);
        if (recentEventsId == null) {
            return;
        }
        sectionIds.remove(recentEventsId);
        int overviewIndex = sectionIds.indexOf(OVERVIEW_SECTION_ID);
        int insertIndex = overviewIndex >= 0 ? overviewIndex + 1 : 0;
        sectionIds.add(Math.min(insertIndex, sectionIds.size()), recentEventsId);
    }

    @Nullable
    private String findSectionIdByTitle(@Nonnull List<String> sectionIds, @Nonnull String title) {
        for (String sectionId : sectionIds) {
            InspectorSection section = sectionsById.get(sectionId);
            if (section == null || section.title == null) {
                continue;
            }
            if (title.equalsIgnoreCase(section.title)) {
                return sectionId;
            }
        }
        return null;
    }

    private boolean hasRecentEventsSection() {
        return findSectionIdByTitle(new ArrayList<>(sectionsById.keySet()), NpcDebugInspectorLineParser.RECENT_EVENTS_SECTION) != null;
    }

    private boolean isRecentEventsSection(@Nonnull InspectorSection section) {
        return NpcDebugInspectorLineParser.RECENT_EVENTS_SECTION.equalsIgnoreCase(section.title);
    }

    private boolean isSectionHeaderLine(@Nullable String value) {
        if (value == null) {
            return false;
        }
        return value.startsWith("=== ") && value.endsWith(" ===");
    }

    @Nonnull
    private String extractSectionTitle(@Nonnull String sectionLine) {
        String trimmed = sectionLine.substring(4, Math.max(4, sectionLine.length() - 4)).trim();
        return trimmed.isBlank() ? "Section" : trimmed;
    }

    @Nonnull
    private String buildSectionId(@Nonnull String title, @Nonnull Map<String, Integer> idCounts) {
        String normalized = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".");
        normalized = normalized.replaceAll("^\\.+", "").replaceAll("\\.+$", "");
        String baseId = normalized.isBlank() ? "section.unknown" : "section." + normalized;
        int seen = idCounts.merge(baseId, 1, Integer::sum);
        return seen == 1 ? baseId : baseId + "." + seen;
    }

    @Nullable
    private InspectorField toInspectorField(@Nonnull NpcDebugInspectorLine line) {
        String raw = line.displayText;
        if (raw == null || raw.isBlank()) {
            return null;
        }

        boolean changed = raw.startsWith(">> ");
        String value = raw;
        if (value.startsWith(">> ")) {
            value = value.substring(3);
        } else if (value.startsWith("- ")) {
            value = value.substring(2);
        }
        value = value.trim();
        if (value.isBlank()) {
            return null;
        }

        boolean pinnable = line.pinnable && line.key != null;
        return new InspectorField(line.key, value, changed, pinnable);
    }

    @Nullable
    private FieldNameValue splitFieldNameAndValue(@Nonnull String displayText) {
        int separatorIndex = displayText.indexOf(": ");
        if (separatorIndex <= 0 || separatorIndex + 2 > displayText.length()) {
            return null;
        }
        String name = displayText.substring(0, separatorIndex + 1).trim();
        String value = displayText.substring(separatorIndex + 2).trim();
        if (name.isBlank() || value.isBlank()) {
            return null;
        }
        return new FieldNameValue(name, value);
    }

    private void syncPinnedStateFromManager() {
        pinnedFieldKeys.clear();
        if (targetNpcUuid == null) {
            pinModeEnabled = false;
            return;
        }
        pinModeEnabled = NpcDebugPinnedOverlayManager.isPinnedToNpc(playerRef, targetNpcUuid);
        if (!pinModeEnabled) {
            return;
        }
        Set<String> persisted = NpcDebugPinnedOverlayManager.getPinnedFieldKeys(playerRef, targetNpcUuid);
        pinnedFieldKeys.addAll(persisted);
    }

    private void prunePinnedKeys() {
        if (pinnedFieldKeys.isEmpty()) {
            return;
        }
        Set<String> availableKeys = new LinkedHashSet<>();
        for (InspectorSection section : sectionsById.values()) {
            for (InspectorField field : section.fields) {
                if (field.pinnable && field.key != null) {
                    availableKeys.add(field.key);
                }
            }
        }
        pinnedFieldKeys.retainAll(availableKeys);
        if (pinModeEnabled && targetNpcUuid != null) {
            NpcDebugPinnedOverlayManager.updatePinnedFieldKeys(playerRef, targetNpcUuid, pinnedFieldKeys);
        }
    }

    private void toggleEventCategory(@Nonnull String categoryId) {
        NpcDebugEventCategory category = NpcDebugEventCategory.fromId(categoryId);
        if (category == null) {
            return;
        }
        NpcDebugEventLogFilterSettings.toggle(playerRef.getUuid(), category);
        syncEventCategoryStateFromSettings();
    }

    private void syncEventCategoryStateFromSettings() {
        enabledEventCategories.clear();
        enabledEventCategories.addAll(NpcDebugEventLogFilterSettings.getEnabledCategories(playerRef.getUuid()));
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
        if (!isRefreshSuppressed()) {
            sendRefreshUpdate();
        }
        if (!dismissed && generation == refreshTickGeneration.get()) {
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
        refreshSnapshotData();
        syncEventCategoryStateFromSettings();
        syncPinnedStateFromManager();
        prunePinnedKeys();

        String currentSignature = buildStructureSignature();
        boolean rebuildStructure = !currentSignature.equals(lastRenderedStructureSignature);
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = rebuildStructure ? new UIEventBuilder() : null;
        applySnapshot(commandBuilder);
        rebuildRows(commandBuilder, eventBuilder, rebuildStructure);
        if (rebuildStructure && eventBuilder != null) {
            bindGlobalEvents(eventBuilder);
            lastRenderedStructureSignature = currentSignature;
        }
        syncPinnedSectionOrderToOverlay();
        sendUpdate(commandBuilder, eventBuilder, false);
    }

    @Nonnull
    private String buildStructureSignature() {
        StringBuilder signature = new StringBuilder(256);
        signature.append(pinModeEnabled ? '1' : '0');
        for (String sectionId : sectionOrder) {
            InspectorSection section = sectionsById.get(sectionId);
            if (section == null) {
                continue;
            }
            signature.append('|').append(section.id);
            signature.append(':').append(collapsedSectionIds.contains(section.id) ? '1' : '0');
            signature.append(':').append(section.fields.length);
            for (InspectorField field : section.fields) {
                signature.append(';').append(field.pinnable ? '1' : '0');
                if (field.key != null) {
                    signature.append('#').append(field.key);
                }
            }
        }
        return signature.toString();
    }

    private void syncPinnedSectionOrderToOverlay() {
        if (!pinModeEnabled || targetNpcUuid == null) {
            return;
        }
        List<String> orderedTitles = new ArrayList<>(sectionOrder.size());
        for (String sectionId : sectionOrder) {
            InspectorSection section = sectionsById.get(sectionId);
            if (section == null || section.title == null || section.title.isBlank()) {
                continue;
            }
            orderedTitles.add(section.title);
        }
        NpcDebugPinnedOverlayManager.updatePinnedSectionOrder(playerRef, targetNpcUuid, orderedTitles);
    }

    private static final class InspectorSection {
        private final String id;
        private final String title;
        private final InspectorField[] fields;

        private InspectorSection(@Nonnull String id,
                                 @Nonnull String title,
                                 @Nonnull InspectorField[] fields) {
            this.id = id;
            this.title = title;
            this.fields = fields;
        }
    }

    private static final class InspectorField {
        @Nullable
        private final String key;
        private final String displayText;
        private final boolean changed;
        private final boolean pinnable;

        private InspectorField(@Nullable String key,
                               @Nonnull String displayText,
                               boolean changed,
                               boolean pinnable) {
            this.key = key;
            this.displayText = displayText;
            this.changed = changed;
            this.pinnable = pinnable;
        }
    }

    private static final class FieldNameValue {
        private final String nameText;
        private final String valueText;

        private FieldNameValue(@Nonnull String nameText, @Nonnull String valueText) {
            this.nameText = nameText;
            this.valueText = valueText;
        }
    }

    private static final class InspectorSubtitleFields {
        private final String uuid;
        private final String utc;

        private InspectorSubtitleFields(@Nonnull String uuid, @Nonnull String utc) {
            this.uuid = uuid;
            this.utc = utc;
        }
    }

    /** Event payload for the inspector page. */
    public static final class PageEventData {
        public static final BuilderCodec<PageEventData> CODEC = BuilderCodec.builder(
                PageEventData.class,
                PageEventData::new
        )
            .append(
                new KeyedCodec<>(EVENT_ACTION, Codec.STRING),
                (data, value) -> data.action = value,
                data -> data.action
            )
            .add()
            .append(
                new KeyedCodec<>(EVENT_TYPE, Codec.STRING),
                (data, value) -> data.type = value,
                data -> data.type
            )
            .add()
            .build();

        private String action;
        private String type;
    }
}
