package com.alechilles.alecsnpcdebuginspector.runtime;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import javax.annotation.Nonnull;

/** Update 6 and later block access, kept separate so Update 5 never loads section-only APIs. */
final class NpcRuntimeBlockSectionFixtureMutator {
    private NpcRuntimeBlockSectionFixtureMutator() {
    }

    static int getBlock(@Nonnull World world, int x, int y, int z) {
        var chunkStore = world.getChunkStore();
        var sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            throw new IllegalStateException("block fixture section is not resident: " + x + "," + y + "," + z);
        }
        var section = chunkStore.getStore().getComponent(sectionRef, BlockSection.getComponentType());
        if (section == null) {
            throw new IllegalStateException("block fixture section has no blocks: " + x + "," + y + "," + z);
        }
        return section.get(x, y, z);
    }

    static boolean setBlock(@Nonnull World world, int x, int y, int z,
                            int blockId, @Nonnull BlockType blockType) {
        var chunkStore = world.getChunkStore();
        var sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        return sectionRef != null && sectionRef.isValid()
                && BlockOperations.setBlock(chunkStore, sectionRef, x, y, z,
                blockId, blockType, RotationTuple.NONE_INDEX,
                FillerBlockUtil.NO_FILLER, SetBlockSettings.NONE);
    }
}
