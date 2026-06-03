package com.alechilles.alecsnpcdebuginspector.runtime;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.IOException;
import javax.annotation.Nonnull;

/**
 * Development-only control surface for the file-driven runtime harness.
 */
public final class NpcRuntimeCommand extends AbstractPlayerCommand {
    private final NpcRuntimeHarnessService harnessService;

    public NpcRuntimeCommand(@Nonnull NpcRuntimeHarnessService harnessService) {
        super("npcruntime", "Control Alec's NPC runtime harness.");
        this.harnessService = harnessService;
        setAllowsExtraArguments(true);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String action = token(commandContext, 1, "status").toLowerCase();
        switch (action) {
            case "status" -> send(commandContext, harnessService.statusText());
            case "enable" -> {
                harnessService.setEnabled(true);
                send(commandContext, "NPC Runtime Harness enabled.");
            }
            case "disable" -> {
                harnessService.setEnabled(false);
                send(commandContext, "NPC Runtime Harness disabled.");
            }
            case "paths" -> send(commandContext, harnessService.pathsText());
            case "run" -> runNext(commandContext);
            case "cancel" -> send(commandContext, "NPC Runtime Harness cancel is not active in the file-queue milestone.");
            default -> send(commandContext, "Usage: /npcruntime status|enable|disable|paths|run|cancel");
        }
    }

    private void runNext(@Nonnull CommandContext commandContext) {
        try {
            harnessService.initializeDirectories();
            NpcRuntimeHarnessService.ProcessOutcome outcome = harnessService.processNextQueuedRequest();
            if (!outcome.processed()) {
                send(commandContext, "NPC Runtime Harness queue is empty.");
                return;
            }
            send(commandContext, "NPC Runtime Harness processed request " + outcome.requestId() + ".");
        } catch (IOException exception) {
            send(commandContext, "NPC Runtime Harness failed: " + exception.getMessage());
        }
    }

    @Nonnull
    private static String token(@Nonnull CommandContext commandContext, int tokenIndex, @Nonnull String defaultValue) {
        String input = commandContext.getInputString();
        if (input == null || input.isBlank()) {
            return defaultValue;
        }
        String[] tokens = input.trim().split("\\s+");
        if (tokens.length <= tokenIndex || tokens[tokenIndex].isBlank()) {
            return defaultValue;
        }
        return tokens[tokenIndex];
    }

    private static void send(@Nonnull CommandContext commandContext, @Nonnull String message) {
        commandContext.sender().sendMessage(Message.raw(message));
    }
}
