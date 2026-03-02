package com.alechilles.alecsnpcdebuginspector.ui;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Read-only page that renders one NPC debug snapshot.
 */
public final class NpcDebugInspectorPage extends InteractiveCustomUIPage<NpcDebugInspectorPage.PageEventData> {
    public static final String UI_PATH = "NpcDebugInspectorPage.ui";
    private static final String EVENT_ACTION = "Action";
    private static final String ACTION_CLOSE = "Close";

    private final NpcDebugSnapshot snapshot;
    private boolean handled;

    public NpcDebugInspectorPage(@Nonnull PlayerRef playerRef, @Nonnull NpcDebugSnapshot snapshot) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageEventData.CODEC);
        this.snapshot = snapshot;
        this.handled = false;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder,
                      @Nonnull Store<EntityStore> store) {
        commandBuilder.append(UI_PATH);
        commandBuilder.set("#NpcDebugInspectorTitle.Text", snapshot.title());
        commandBuilder.set("#NpcDebugInspectorSubtitle.Text", snapshot.subtitle());
        commandBuilder.set("#NpcDebugInspectorDetails.Text", snapshot.details());

        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#NpcDebugInspectorCloseButton",
                EventData.of(EVENT_ACTION, ACTION_CLOSE),
                false
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PageEventData data) {
        handled = true;
        close();
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        handled = true;
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
            .build();

        private String action;
    }
}

