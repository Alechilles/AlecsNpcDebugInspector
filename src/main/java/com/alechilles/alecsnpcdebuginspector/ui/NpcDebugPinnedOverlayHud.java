package com.alechilles.alecsnpcdebuginspector.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/**
 * Non-modal pinned inspector overlay HUD.
 */
final class NpcDebugPinnedOverlayHud extends CustomUIHud {
    static final String UI_PATH = "NpcDebugPinnedOverlayHud.ui";

    private String title = "NPC Debug Pinned Overlay";
    private String subtitle = "";
    private String body = "";

    NpcDebugPinnedOverlayHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
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

    private void applyText(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.set("#NpcDebugPinnedOverlayTitle.Text", title);
        commandBuilder.set("#NpcDebugPinnedOverlaySubtitle.Text", subtitle);
        commandBuilder.set("#NpcDebugPinnedOverlayBody.Text", body);
    }
}
