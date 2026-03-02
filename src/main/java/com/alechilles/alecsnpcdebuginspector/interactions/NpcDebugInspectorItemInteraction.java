package com.alechilles.alecsnpcdebuginspector.interactions;

import com.alechilles.alecsnpcdebuginspector.AlecsNpcDebugInspector;
import com.alechilles.alecsnpcdebuginspector.items.NpcDebugItemFeatureHandler;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Custom interaction for NPC Debug Inspector items.
 */
public final class NpcDebugInspectorItemInteraction extends SimpleInteraction {
    public static final BuilderCodec<NpcDebugInspectorItemInteraction> CODEC = BuilderCodec.builder(
            NpcDebugInspectorItemInteraction.class,
            NpcDebugInspectorItemInteraction::new,
            SimpleInteraction.CODEC
    )
        .documentation("Handles NPC Debug Inspector item link and roster actions.")
        .<String>appendInherited(
            new KeyedCodec<>("Action", Codec.STRING),
            (interaction, value) -> interaction.action = value,
            interaction -> interaction.action,
            (interaction, parent) -> interaction.action = parent.action
        )
        .add()
        .build();

    private String action;

    protected NpcDebugInspectorItemInteraction() {
        super();
    }

    public NpcDebugInspectorItemInteraction(String id) {
        super(id);
    }

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void tick0(boolean firstRun,
                         float time,
                         @Nonnull InteractionType type,
                         @Nonnull InteractionContext context,
                         @Nonnull CooldownHandler cooldownHandler) {
        if (!firstRun) {
            super.tick0(false, time, type, context, cooldownHandler);
            return;
        }
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Ref<EntityStore> playerRef = context.getEntity();
        if (commandBuffer == null || playerRef == null) {
            context.getState().state = InteractionState.Failed;
            super.tick0(true, time, type, context, cooldownHandler);
            return;
        }
        Player player = commandBuffer.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            context.getState().state = InteractionState.Failed;
            super.tick0(true, time, type, context, cooldownHandler);
            return;
        }
        ItemStack heldItem = context.getHeldItem();
        if (heldItem == null || heldItem.isEmpty()) {
            context.getState().state = InteractionState.Failed;
            super.tick0(true, time, type, context, cooldownHandler);
            return;
        }
        AlecsNpcDebugInspector plugin = AlecsNpcDebugInspector.getInstance();
        NpcDebugItemFeatureHandler handler = plugin != null ? plugin.getItemFeatureHandler() : null;
        if (handler == null) {
            context.getState().state = InteractionState.Failed;
            super.tick0(true, time, type, context, cooldownHandler);
            return;
        }
        Ref<EntityStore> targetEntity = context.getTargetEntity();
        commandBuffer.run(store -> handler.handleUse(
                player,
                heldItem,
                targetEntity,
                action
        ));
        context.setHeldItem(heldItem);
        super.tick0(true, time, type, context, cooldownHandler);
    }

    @Override
    protected void simulateTick0(boolean firstRun,
                                 float time,
                                 @Nonnull InteractionType type,
                                 @Nonnull InteractionContext context,
                                 @Nonnull CooldownHandler cooldownHandler) {
        if (context.getServerState() != null && context.getServerState().state == InteractionState.Failed) {
            context.getState().state = InteractionState.Failed;
        }
        super.tick0(firstRun, time, type, context, cooldownHandler);
    }
}

