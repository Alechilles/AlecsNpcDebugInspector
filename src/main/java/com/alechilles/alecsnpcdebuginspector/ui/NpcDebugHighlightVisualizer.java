package com.alechilles.alecsnpcdebuginspector.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Renders non-blocking debug visuals for highlighted NPCs.
 */
public final class NpcDebugHighlightVisualizer {
    private static final double MIN_RING_OUTER_RADIUS = 0.35;
    private static final double RING_RADIUS_PADDING = 0.30;
    private static final double RING_THICKNESS = 0.08;
    private static final double PLAYER_RING_OUTER_RADIUS = 0.50;
    private static final double PLAYER_RING_INNER_RADIUS = 0.40;
    private static final double LINE_THICKNESS = 0.04;
    private static final double MIN_HORIZONTAL_DISTANCE = 0.001;
    private static final float SHAPE_OPACITY = 0.85F;
    private static final int SHAPE_SEGMENT_COUNT = 8;
    private static final float SHAPE_LIFETIME_SECONDS = 0.10F;
    private static final Vector3f HIGHLIGHT_COLOR = new Vector3f(0.20F, 0.90F, 1.00F);

    /**
     * Draws a ring around each highlighted NPC and a connector to the observing player.
     */
    public void render(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store, @Nonnull Set<UUID> highlightedNpcUuids) {
        if (highlightedNpcUuids.isEmpty()) {
            return;
        }

        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        Ref<EntityStore> observerRef = playerRef.getReference();
        if (world == null || observerRef == null || !observerRef.isValid()) {
            return;
        }

        TransformComponent observerTransform = store.getComponent(observerRef, TransformComponent.getComponentType());
        BoundingBox observerBoundingBox = store.getComponent(observerRef, BoundingBox.getComponentType());
        if (observerTransform == null || observerBoundingBox == null) {
            return;
        }

        Vector3d observerPosition = observerTransform.getPosition();
        double observerMidY = observerPosition.y + observerBoundingBox.getBoundingBox().max.y / 2.0;
        DebugUtils.addDisc(
                world,
                observerPosition.x,
                observerMidY,
                observerPosition.z,
                PLAYER_RING_OUTER_RADIUS,
                PLAYER_RING_INNER_RADIUS,
                HIGHLIGHT_COLOR,
                SHAPE_OPACITY,
                SHAPE_SEGMENT_COUNT,
                SHAPE_LIFETIME_SECONDS,
                false
        );

        for (UUID highlightedNpcUuid : highlightedNpcUuids) {
            if (highlightedNpcUuid == null) {
                continue;
            }
            Ref<EntityStore> npcRef = world.getEntityRef(highlightedNpcUuid);
            if (npcRef == null || !npcRef.isValid()) {
                continue;
            }

            TransformComponent npcTransform = store.getComponent(npcRef, TransformComponent.getComponentType());
            BoundingBox npcBoundingBox = store.getComponent(npcRef, BoundingBox.getComponentType());
            if (npcTransform == null || npcBoundingBox == null) {
                continue;
            }

            Vector3d npcPosition = npcTransform.getPosition();
            Box npcBox = npcBoundingBox.getBoundingBox();
            double npcMidY = npcPosition.y + npcBox.max.y / 2.0;
            double npcOuterRadius = resolveOuterRadius(npcBox);
            double npcInnerRadius = Math.max(0.0, npcOuterRadius - RING_THICKNESS);

            DebugUtils.addDisc(
                    world,
                    npcPosition.x,
                    npcMidY,
                    npcPosition.z,
                    npcOuterRadius,
                    npcInnerRadius,
                    HIGHLIGHT_COLOR,
                    SHAPE_OPACITY,
                    SHAPE_SEGMENT_COUNT,
                    SHAPE_LIFETIME_SECONDS,
                    false
            );

            renderConnectionLine(
                    world,
                    npcPosition.x,
                    npcMidY,
                    npcPosition.z,
                    npcOuterRadius,
                    observerPosition.x,
                    observerMidY,
                    observerPosition.z,
                    PLAYER_RING_OUTER_RADIUS
            );
        }
    }

    private static double resolveOuterRadius(@Nonnull Box box) {
        double width = Math.max(box.max.x - box.min.x, box.max.z - box.min.z);
        return Math.max(MIN_RING_OUTER_RADIUS, width / 2.0 + RING_RADIUS_PADDING);
    }

    private static void renderConnectionLine(@Nonnull World world,
                                             double npcX,
                                             double npcY,
                                             double npcZ,
                                             double npcRadius,
                                             double observerX,
                                             double observerY,
                                             double observerZ,
                                             double observerRadius) {
        double dirX = observerX - npcX;
        double dirZ = observerZ - npcZ;
        double horizontalDistance = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (horizontalDistance < MIN_HORIZONTAL_DISTANCE) {
            DebugUtils.addLine(world, npcX, npcY, npcZ, observerX, observerY, observerZ, HIGHLIGHT_COLOR, LINE_THICKNESS, SHAPE_LIFETIME_SECONDS, false);
            return;
        }

        double horizontalDirX = dirX / horizontalDistance;
        double horizontalDirZ = dirZ / horizontalDistance;
        double startX = npcX + horizontalDirX * npcRadius;
        double startZ = npcZ + horizontalDirZ * npcRadius;
        double endX = observerX - horizontalDirX * observerRadius;
        double endZ = observerZ - horizontalDirZ * observerRadius;
        DebugUtils.addLine(
                world,
                startX,
                npcY,
                startZ,
                endX,
                observerY,
                endZ,
                HIGHLIGHT_COLOR,
                LINE_THICKNESS,
                SHAPE_LIFETIME_SECONDS,
                false
        );
    }
}
