package com.alechilles.alecsnpcdebuginspector.commands;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshotService;
import com.alechilles.alecsnpcdebuginspector.ui.NpcDebugInspectorPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Opens the NPC Debug Inspector page for a target NPC.
 */
public final class NpcDebugCommand extends AbstractPlayerCommand {
    private final NpcDebugSnapshotService snapshotService = new NpcDebugSnapshotService();

    public NpcDebugCommand() {
        super("npcdebug", "Open NPC Debug Inspector for the NPC in view or by UUID.");
        setAllowsExtraArguments(true);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            commandContext.sender().sendMessage(Message.raw("Player context unavailable."));
            return;
        }
        if (player.getPageManager() == null) {
            commandContext.sender().sendMessage(Message.raw("Page manager unavailable."));
            return;
        }
        if (playerRef == null || !playerRef.isValid()) {
            commandContext.sender().sendMessage(Message.raw("UI player reference unavailable."));
            return;
        }

        UUID requestedUuid = parseUuidArg(commandContext, 2);
        Ref<EntityStore> npcRef = null;
        UUID targetUuid = requestedUuid;
        if (targetUuid == null) {
            NpcDebugTargeting.Candidate candidate = NpcDebugTargeting.findTargetNpc(store, ref);
            if (candidate == null || candidate.ref == null || !candidate.ref.isValid()) {
                commandContext.sender().sendMessage(Message.raw("No NPC found in view."));
                return;
            }
            npcRef = candidate.ref;
            targetUuid = candidate.npcUuid;
        } else {
            Ref<EntityStore> resolved = world.getEntityRef(targetUuid);
            if (resolved != null && resolved.isValid()) {
                npcRef = resolved;
            }
        }

        NpcDebugSnapshot snapshot = snapshotService.capture(targetUuid, npcRef, store);
        player.getPageManager().openCustomPage(ref, store, new NpcDebugInspectorPage(playerRef, snapshot));
    }

    @Nullable
    private UUID parseUuidArg(@Nonnull CommandContext commandContext, int tokenIndex) {
        String input = commandContext.getInputString();
        if (input == null || input.isBlank()) {
            return null;
        }
        String[] tokens = input.trim().split("\\s+");
        if (tokens.length <= tokenIndex) {
            return null;
        }
        String raw = tokens[tokenIndex];
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}

