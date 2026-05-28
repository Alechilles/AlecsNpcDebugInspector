package com.alechilles.alecsnpcdebuginspector.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Non-modal pinned inspector overlay HUD.
 */
final class NpcDebugPinnedOverlayHud extends CustomUIHud {
    static final String UI_PATH = "NpcDebugPinnedOverlayHud.ui";
    private static final String HUD_KEY = "alecsnpcdebuginspector:pinned-overlay";

    private String title = "Pinned Overlay";
    private String subtitle = "";
    private String body = "";
    @Nullable
    private final Runnable removeCallback;

    NpcDebugPinnedOverlayHud(@Nonnull PlayerRef playerRef, @Nullable Runnable removeCallback) {
        super(playerRef, HUD_KEY);
        this.removeCallback = removeCallback;
    }

    void setContent(@Nonnull String title, @Nonnull String subtitle, @Nonnull String body) {
        this.title = title;
        this.subtitle = subtitle;
        this.body = body;
    }

    void showOverlay() {
        show();
    }

    void pushUpdate() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        applyText(commandBuilder);
        update(false, commandBuilder);
    }

    void clearOverlay() {
        update(true, new UICommandBuilder());
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append(UI_PATH);
        applyText(commandBuilder);
    }

    @Override
    protected void onRemove() {
        if (removeCallback != null) {
            removeCallback.run();
        }
    }

    private void applyText(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.set("#NpcDebugPinnedOverlayTitle.Text", title);
        commandBuilder.set("#NpcDebugPinnedOverlaySubtitle.Text", subtitle);
        commandBuilder.set("#NpcDebugPinnedOverlayBody.Text", body);
    }
}
