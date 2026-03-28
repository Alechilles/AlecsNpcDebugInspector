package com.alechilles.alecsnpcdebuginspector.items;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshotService;
import com.alechilles.alecsnpcdebuginspector.ui.NpcDebugHighlightManager;
import com.alechilles.alecsnpcdebuginspector.ui.NpcDebugInspectorDebugFlagsPage;
import com.alechilles.alecsnpcdebuginspector.ui.NpcDebugInspectorPage;
import com.alechilles.alecsnpcdebuginspector.ui.NpcDebugInspectorRosterPage;
import com.alechilles.alecsnpcdebuginspector.ui.NpcDebugLinkedEntry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Handles use behavior for NPC Debug Inspector tools.
 */
public final class NpcDebugItemFeatureHandler {
    private static final String ACTION_OPEN_ROSTER = "OpenRoster";

    private final NpcDebugLinkService linkService = new NpcDebugLinkService();
    private final NpcDebugSnapshotService snapshotService;

    public NpcDebugItemFeatureHandler(@Nonnull NpcDebugSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    /**
     * Handles a use action from the inspector item interaction.
     */
    public boolean handleUse(@Nullable Player player,
                             @Nullable ItemStack heldItem,
                             @Nullable Ref<EntityStore> targetRef,
                             @Nullable String actionId) {
        if (player == null || heldItem == null || heldItem.isEmpty()) {
            return false;
        }
        World world = player.getWorld();
        if (world == null) {
            return false;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        Ref<EntityStore> playerRef = player.getReference();
        PlayerRef uiPlayerRef = player.getPlayerRef();
        if (store == null || playerRef == null || !playerRef.isValid() || uiPlayerRef == null || !uiPlayerRef.isValid()) {
            return false;
        }

        NpcDebugLinkService.ToolResolution tool = linkService.ensureToolId(heldItem);
        ItemStack working = tool.stack;
        if (tool.changed) {
            updateHeldItem(player, working);
        }

        if (isOpenRosterAction(actionId)) {
            return openRosterPage(player, playerRef, uiPlayerRef, store, tool.toolId);
        }

        if (targetRef == null || !targetRef.isValid()) {
            player.sendMessage(Message.raw("No NPC targeted."));
            return false;
        }
        NPCEntity npc = store.getComponent(targetRef, NPCEntity.getComponentType());
        if (npc == null) {
            player.sendMessage(Message.raw("Target is not an NPC."));
            return false;
        }
        UUID npcUuid = npc.getUuid();
        if (npcUuid == null) {
            player.sendMessage(Message.raw("Target NPC UUID unavailable."));
            return false;
        }
        NpcDebugLinkService.LinkToggleResult toggle = linkService.toggleLink(working, npcUuid);
        if (toggle.hitMax) {
            player.sendMessage(Message.raw("Link limit reached (" + toggle.linkedCount + ")."));
            return false;
        }
        if (!toggle.toggled) {
            return false;
        }
        updateHeldItem(player, toggle.stack);
        String status = toggle.linked ? "Linked" : "Unlinked";
        player.sendMessage(Message.raw(status + " NPC " + npcUuid + " (" + toggle.linkedCount + " total)."));
        return true;
    }

    private boolean isOpenRosterAction(@Nullable String actionId) {
        return actionId != null && ACTION_OPEN_ROSTER.equalsIgnoreCase(actionId.trim());
    }

    private boolean openRosterPage(@Nonnull Player player,
                                   @Nonnull Ref<EntityStore> playerRef,
                                   @Nonnull PlayerRef uiPlayerRef,
                                   @Nonnull Store<EntityStore> store,
                                   @Nonnull String toolId) {
        if (player.getPageManager() == null) {
            return false;
        }
        ensureHighlightTracking(player, toolId);
        NpcDebugInspectorRosterPage page = new NpcDebugInspectorRosterPage(
                uiPlayerRef,
                () -> buildEntriesForTool(player, store, toolId),
                npcUuid -> openInspectorForUuid(player, playerRef, uiPlayerRef, store, toolId, npcUuid),
                npcUuid -> openDebugFlagsForUuid(player, playerRef, uiPlayerRef, store, toolId, npcUuid),
                npcUuid -> unlinkNpcFromTool(player, toolId, npcUuid),
                () -> readHighlightsForTool(player, toolId),
                (npcUuid, highlighted) -> setHighlightForTool(player, toolId, npcUuid, highlighted),
                message -> player.sendMessage(Message.raw(message))
        );
        player.getPageManager().openCustomPage(playerRef, store, page);
        return true;
    }

    private List<NpcDebugLinkedEntry> buildEntriesForTool(@Nonnull Player player,
                                                          @Nonnull Store<EntityStore> store,
                                                          @Nonnull String toolId) {
        ItemStack tool = findToolStackById(player, toolId);
        if (tool == null || tool.isEmpty()) {
            return List.of();
        }
        return linkService.buildLinkedEntries(player, store, tool);
    }

    private void unlinkNpcFromTool(@Nonnull Player player, @Nonnull String toolId, @Nonnull UUID npcUuid) {
        ItemSlot slot = findToolSlotById(player, toolId);
        if (slot == null) {
            player.sendMessage(Message.raw("Could not locate inspector tool."));
            return;
        }
        ItemStack updated = linkService.removeLink(slot.stack, npcUuid);
        slot.container.setItemStackForSlot(slot.slot, updated);
        player.sendMessage(Message.raw("Unlinked NPC " + npcUuid + "."));
    }

    @Nonnull
    private Set<UUID> readHighlightsForTool(@Nonnull Player player, @Nonnull String toolId) {
        ItemStack tool = findToolStackById(player, toolId);
        if (tool == null || tool.isEmpty()) {
            return Set.of();
        }
        return linkService.readHighlightedNpcSet(tool);
    }

    private void setHighlightForTool(@Nonnull Player player,
                                     @Nonnull String toolId,
                                     @Nonnull UUID npcUuid,
                                     boolean highlighted) {
        ItemSlot slot = findToolSlotById(player, toolId);
        if (slot == null) {
            return;
        }
        ItemStack updated = linkService.setHighlight(slot.stack, npcUuid, highlighted);
        slot.container.setItemStackForSlot(slot.slot, updated);
        ensureHighlightTracking(player, toolId);
    }

    private void ensureHighlightTracking(@Nonnull Player player, @Nonnull String toolId) {
        PlayerRef uiPlayerRef = player.getPlayerRef();
        if (uiPlayerRef == null || !uiPlayerRef.isValid()) {
            return;
        }
        NpcDebugHighlightManager.ensureTracking(uiPlayerRef, () -> readHighlightsForTool(player, toolId));
    }

    private void openInspectorForUuid(@Nonnull Player player,
                                      @Nonnull Ref<EntityStore> playerRef,
                                      @Nonnull PlayerRef uiPlayerRef,
                                      @Nonnull Store<EntityStore> store,
                                      @Nonnull String toolId,
                                      @Nonnull UUID npcUuid) {
        if (player.getPageManager() != null) {
            player.getPageManager().openCustomPage(
                    playerRef,
                    store,
                    new NpcDebugInspectorPage(
                            uiPlayerRef,
                            npcUuid,
                            () -> {
                                World world = player.getWorld();
                                Ref<EntityStore> targetRef = world != null ? world.getEntityRef(npcUuid) : null;
                                return snapshotService.capture(npcUuid, targetRef, playerRef, store);
                            },
                            () -> openRosterPage(player, playerRef, uiPlayerRef, store, toolId)
                    )
            );
        }
    }

    private void openDebugFlagsForUuid(@Nonnull Player player,
                                       @Nonnull Ref<EntityStore> playerRef,
                                       @Nonnull PlayerRef uiPlayerRef,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull String toolId,
                                       @Nonnull UUID npcUuid) {
        if (player.getPageManager() == null) {
            return;
        }
        player.getPageManager().openCustomPage(
                playerRef,
                store,
                new NpcDebugInspectorDebugFlagsPage(
                        uiPlayerRef,
                        npcUuid,
                        () -> openRosterPage(player, playerRef, uiPlayerRef, store, toolId)
                )
        );
    }

    @Nullable
    private ItemSlot findToolSlotById(@Nonnull Player player, @Nonnull String toolId) {
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return null;
        }
        ItemContainer hotbar = inventory.getHotbar();
        short capacity = hotbar.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = hotbar.getItemStack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String stackToolId = stack.getFromMetadataOrNull(NpcDebugMetadataKeys.TOOL_ID, com.hypixel.hytale.codec.Codec.STRING);
            if (stackToolId != null && stackToolId.equals(toolId)) {
                return new ItemSlot(hotbar, slot, stack);
            }
        }
        return null;
    }

    @Nullable
    private ItemStack findToolStackById(@Nonnull Player player, @Nonnull String toolId) {
        ItemSlot slot = findToolSlotById(player, toolId);
        return slot != null ? slot.stack : null;
    }

    private void updateHeldItem(@Nonnull Player player, @Nonnull ItemStack updated) {
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        byte active = inventory.getActiveHotbarSlot();
        if (active == Inventory.INACTIVE_SLOT_INDEX) {
            return;
        }
        inventory.getHotbar().setItemStackForSlot((short) active, updated);
    }

    private record ItemSlot(ItemContainer container, short slot, ItemStack stack) {
    }
}
