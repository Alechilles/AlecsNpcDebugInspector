package com.alechilles.alecsnpcdebuginspector.commands;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Resolves the NPC currently closest to player crosshair.
 */
final class NpcDebugTargeting {
    private static final double MAX_DISTANCE = 8.0;
    private static final double MIN_DOT = 0.7;

    private NpcDebugTargeting() {
    }

    @Nullable
    static Candidate findTargetNpc(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        if (store == null || playerRef == null || !playerRef.isValid()) {
            return null;
        }
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) {
            return null;
        }

        Vector3d playerPos = new Vector3d(transform.getPosition());
        Vector3f rotation = new Vector3f(transform.getRotation());
        HeadRotation headRotation = store.getComponent(playerRef, HeadRotation.getComponentType());
        Vector3f headRot = headRotation != null ? headRotation.getRotation() : rotation;

        Vector3f forward = new Vector3f(Vector3f.FORWARD);
        forward.rotateY(headRot.getYaw());
        forward.rotateX(headRot.getPitch());
        forward.normalize();
        Vector3d forwardDir = new Vector3d(forward.x, forward.y, forward.z);

        BestCandidate best = new BestCandidate();
        store.forEachChunk(Query.any(), (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
                NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                TransformComponent npcTransform = chunk.getComponent(i, TransformComponent.getComponentType());
                if (npc == null || npcTransform == null) {
                    continue;
                }
                Vector3d toNpc = new Vector3d(npcTransform.getPosition()).subtract(playerPos);
                double distance = toNpc.length();
                if (distance <= 0.1 || distance > MAX_DISTANCE) {
                    continue;
                }
                Vector3d dir = new Vector3d(toNpc).normalize();
                double dot = forwardDir.dot(dir);
                if (dot < MIN_DOT) {
                    continue;
                }
                double score = dot / distance;
                if (score > best.score) {
                    best.score = score;
                    best.ref = chunk.getReferenceTo(i);
                    best.npcUuid = npc.getUuid();
                }
            }
        });

        if (best.ref == null || best.npcUuid == null) {
            return null;
        }
        return new Candidate(best.ref, best.npcUuid);
    }

    static final class Candidate {
        final Ref<EntityStore> ref;
        final UUID npcUuid;

        Candidate(Ref<EntityStore> ref, UUID npcUuid) {
            this.ref = ref;
            this.npcUuid = npcUuid;
        }
    }

    private static final class BestCandidate {
        private Ref<EntityStore> ref;
        private UUID npcUuid;
        private double score = -1.0;
    }
}

