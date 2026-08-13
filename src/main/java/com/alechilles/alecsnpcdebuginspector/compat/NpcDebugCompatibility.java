package com.alechilles.alecsnpcdebuginspector.compat;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.instructions.Instruction;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.CombatSupport;
import com.hypixel.hytale.server.npc.role.support.EntitySupport;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.storage.AlarmStore;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import javax.annotation.Nullable;

/**
 * Keeps NPC Inspector's support reads compatible with the two supported Hytale API generations.
 *
 * <p>Update 6 moved role support into ECS components. This class detects that move without
 * linking {@code ExecutionSupport}; all methods that exist in only one generation are bound by
 * name after the active generation is known.</p>
 */
public final class NpcDebugCompatibility {
    private static final String UPDATE_6_MARKER =
            "com.hypixel.hytale.server.npc.instructions.ExecutionSupport";
    private static final String MOTION_CONTEXT_SUPPORT =
            "com.hypixel.hytale.server.npc.role.support.MotionContextSupport";

    private static final boolean UPDATE_6 = detectsUpdate6();

    private static final MethodHandle LEGACY_STATE_SUPPORT =
            bindLegacyRoleGetter("getStateSupport", StateSupport.class);
    private static final MethodHandle LEGACY_ENTITY_SUPPORT =
            bindLegacyRoleGetter("getEntitySupport", EntitySupport.class);
    private static final MethodHandle LEGACY_MARKED_ENTITY_SUPPORT =
            bindLegacyRoleGetter("getMarkedEntitySupport", MarkedEntitySupport.class);
    private static final MethodHandle LEGACY_COMBAT_SUPPORT =
            bindLegacyRoleGetter("getCombatSupport", CombatSupport.class);

    private static final MethodHandle ECS_STATE_SUPPORT = bindEcsGetter(StateSupport.class);
    private static final MethodHandle ECS_ENTITY_SUPPORT = bindEcsGetter(EntitySupport.class);
    private static final MethodHandle ECS_MARKED_ENTITY_SUPPORT = bindEcsGetter(MarkedEntitySupport.class);
    private static final MethodHandle ECS_COMBAT_SUPPORT = bindEcsGetter(CombatSupport.class);

    private static final MethodHandle LEGACY_NEXT_BODY_MOTION_STEP =
            bindLegacySupportGetter("getNextBodyMotionStep");
    private static final MethodHandle LEGACY_NEXT_HEAD_MOTION_STEP =
            bindLegacySupportGetter("getNextHeadMotionStep");
    private static final MotionContextBinding MOTION_CONTEXT = bindMotionContext();

    private static final MethodHandle LEGACY_ALARM_STORE =
            bindLegacyNpcGetter("getAlarmStore");
    private static final MethodHandle ECS_ALARM_STORE = bindEcsGetter(AlarmStore.class);

    private NpcDebugCompatibility() {
    }

    /** Returns true when the active server is an Update 6 generation server. */
    public static boolean isUpdate6() {
        return UPDATE_6;
    }

    @Nullable
    public static StateSupport stateSupport(@Nullable Role role,
                                            @Nullable Ref<EntityStore> ref,
                                            @Nullable ComponentAccessor<EntityStore> accessor) {
        return accessSupport(role, ref, accessor, LEGACY_STATE_SUPPORT, ECS_STATE_SUPPORT, StateSupport.class);
    }

    @Nullable
    public static EntitySupport entitySupport(@Nullable Role role,
                                              @Nullable Ref<EntityStore> ref,
                                              @Nullable ComponentAccessor<EntityStore> accessor) {
        return accessSupport(role, ref, accessor, LEGACY_ENTITY_SUPPORT, ECS_ENTITY_SUPPORT, EntitySupport.class);
    }

    @Nullable
    public static MarkedEntitySupport markedEntitySupport(@Nullable Role role,
                                                          @Nullable Ref<EntityStore> ref,
                                                          @Nullable ComponentAccessor<EntityStore> accessor) {
        return accessSupport(
                role,
                ref,
                accessor,
                LEGACY_MARKED_ENTITY_SUPPORT,
                ECS_MARKED_ENTITY_SUPPORT,
                MarkedEntitySupport.class
        );
    }

    @Nullable
    public static CombatSupport combatSupport(@Nullable Role role,
                                              @Nullable Ref<EntityStore> ref,
                                              @Nullable ComponentAccessor<EntityStore> accessor) {
        return accessSupport(role, ref, accessor, LEGACY_COMBAT_SUPPORT, ECS_COMBAT_SUPPORT, CombatSupport.class);
    }

    /**
     * Returns the queued body motion step without acquiring the pooled Update 6 execution support.
     */
    @Nullable
    public static Instruction nextBodyMotionStep(@Nullable Role role,
                                                 @Nullable EntitySupport legacyEntitySupport,
                                                 @Nullable Ref<EntityStore> ref,
                                                 @Nullable ComponentAccessor<EntityStore> accessor) {
        if (UPDATE_6) {
            if (!hasComponentContext(ref, accessor)) {
                return null;
            }
        } else if (!hasContext(role, ref, accessor)) {
            return null;
        } else {
            return invoke(LEGACY_NEXT_BODY_MOTION_STEP, legacyEntitySupport, Instruction.class);
        }
        return MOTION_CONTEXT != null
                ? MOTION_CONTEXT.queuedBody(ref, accessor)
                : null;
    }

    /**
     * Returns the queued head motion step without acquiring the pooled Update 6 execution support.
     */
    @Nullable
    public static Instruction nextHeadMotionStep(@Nullable Role role,
                                                 @Nullable EntitySupport legacyEntitySupport,
                                                 @Nullable Ref<EntityStore> ref,
                                                 @Nullable ComponentAccessor<EntityStore> accessor) {
        if (UPDATE_6) {
            if (!hasComponentContext(ref, accessor)) {
                return null;
            }
        } else if (!hasContext(role, ref, accessor)) {
            return null;
        } else {
            return invoke(LEGACY_NEXT_HEAD_MOTION_STEP, legacyEntitySupport, Instruction.class);
        }
        return MOTION_CONTEXT != null
                ? MOTION_CONTEXT.queuedHead(ref, accessor)
                : null;
    }

    @Nullable
    public static AlarmStore alarmStore(@Nullable NPCEntity npc,
                                        @Nullable Ref<EntityStore> ref,
                                        @Nullable ComponentAccessor<EntityStore> accessor) {
        if (npc == null) {
            return null;
        }
        if (!UPDATE_6) {
            return invoke(LEGACY_ALARM_STORE, npc, AlarmStore.class);
        }
        if (ref == null || !ref.isValid() || accessor == null) {
            return null;
        }
        return invoke(ECS_ALARM_STORE, ref, accessor, AlarmStore.class);
    }

    private static boolean hasContext(@Nullable Role role,
                                      @Nullable Ref<EntityStore> ref,
                                      @Nullable ComponentAccessor<EntityStore> accessor) {
        return role != null && hasComponentContext(ref, accessor);
    }

    private static boolean hasComponentContext(@Nullable Ref<EntityStore> ref,
                                               @Nullable ComponentAccessor<EntityStore> accessor) {
        return ref != null && ref.isValid() && accessor != null;
    }

    @Nullable
    private static <T> T accessSupport(@Nullable Role role,
                                       @Nullable Ref<EntityStore> ref,
                                       @Nullable ComponentAccessor<EntityStore> accessor,
                                       @Nullable MethodHandle legacy,
                                       @Nullable MethodHandle ecs,
                                       Class<T> type) {
        if (UPDATE_6) {
            if (!hasComponentContext(ref, accessor)) {
                return null;
            }
            return invoke(ecs, ref, accessor, type);
        }
        if (role == null) {
            return null;
        }
        return invoke(legacy, role, type);
    }

    @Nullable
    private static MethodHandle bindLegacyRoleGetter(String methodName, Class<?> returnType) {
        if (UPDATE_6) {
            return null;
        }
        return requireHandle(
                bindVirtual(Role.class, methodName, MethodType.methodType(returnType)),
                "Role." + methodName
        );
    }

    @Nullable
    private static MethodHandle bindLegacySupportGetter(String methodName) {
        if (UPDATE_6) {
            return null;
        }
        return requireHandle(
                bindVirtual(EntitySupport.class, methodName, MethodType.methodType(Instruction.class)),
                "EntitySupport." + methodName
        );
    }

    @Nullable
    private static MethodHandle bindLegacyNpcGetter(String methodName) {
        if (UPDATE_6) {
            return null;
        }
        return requireHandle(
                bindVirtual(NPCEntity.class, methodName, MethodType.methodType(AlarmStore.class)),
                "NPCEntity." + methodName
        );
    }

    @Nullable
    private static MethodHandle bindEcsGetter(Class<?> supportType) {
        if (!UPDATE_6) {
            return null;
        }
        return requireHandle(
                bindStatic(
                        supportType,
                        "get",
                        MethodType.methodType(supportType, Ref.class, ComponentAccessor.class)
                ),
                supportType.getName() + ".get(Ref, ComponentAccessor)"
        );
    }

    private static MethodHandle requireHandle(@Nullable MethodHandle handle, String description) {
        if (handle == null) {
            throw new ExceptionInInitializerError("Required Hytale compatibility binding is unavailable: " + description);
        }
        return handle;
    }

    @Nullable
    private static MethodHandle bindVirtual(Class<?> owner, String methodName, MethodType type) {
        try {
            return MethodHandles.publicLookup().findVirtual(owner, methodName, type);
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            return null;
        }
    }

    @Nullable
    private static MethodHandle bindStatic(Class<?> owner, String methodName, MethodType type) {
        try {
            return MethodHandles.publicLookup().findStatic(owner, methodName, type);
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            return null;
        }
    }

    @Nullable
    private static <T> T invoke(@Nullable MethodHandle handle, Object first, Class<T> type) {
        if (handle == null || first == null) {
            return null;
        }
        try {
            return type.cast(handle.invoke(first));
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static <T> T invoke(@Nullable MethodHandle handle,
                                Object first,
                                Object second,
                                Class<T> type) {
        if (handle == null || first == null || second == null) {
            return null;
        }
        try {
            return type.cast(handle.invoke(first, second));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean detectsUpdate6() {
        try {
            Class.forName(UPDATE_6_MARKER, false, NpcDebugCompatibility.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    @Nullable
    private static MotionContextBinding bindMotionContext() {
        if (!UPDATE_6) {
            return null;
        }
        try {
            Class<?> supportType = Class.forName(
                    MOTION_CONTEXT_SUPPORT,
                    false,
                    NpcDebugCompatibility.class.getClassLoader()
            );
            MethodHandle componentType = MethodHandles.publicLookup().findStatic(
                    supportType,
                    "getComponentType",
                    MethodType.methodType(ComponentType.class)
            );
            MethodHandle body = MethodHandles.publicLookup().findVirtual(
                    supportType,
                    "getNextBodyMotionStep",
                    MethodType.methodType(Instruction.class)
            );
            MethodHandle head = MethodHandles.publicLookup().findVirtual(
                    supportType,
                    "getNextHeadMotionStep",
                    MethodType.methodType(Instruction.class)
            );
            return new MotionContextBinding(componentType, body, head);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | LinkageError exception) {
            throw new ExceptionInInitializerError(
                    new IllegalStateException("Required Hytale compatibility binding is unavailable: "
                            + MOTION_CONTEXT_SUPPORT, exception)
            );
        }
    }

    private record MotionContextBinding(MethodHandle componentType,
                                       MethodHandle body,
                                       MethodHandle head) {
        @Nullable
        Instruction queuedBody(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
            return queued(ref, accessor, body);
        }

        @Nullable
        Instruction queuedHead(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
            return queued(ref, accessor, head);
        }

        @Nullable
        private Instruction queued(Ref<EntityStore> ref,
                                   ComponentAccessor<EntityStore> accessor,
                                   MethodHandle getter) {
            try {
                Object componentTypeValue = componentType.invoke();
                if (!(componentTypeValue instanceof ComponentType<?, ?>)) {
                    return null;
                }
                Object support = getComponent(accessor, ref, componentTypeValue);
                return support == null ? null : (Instruction) getter.invoke(support);
            } catch (Throwable ignored) {
                return null;
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private Object getComponent(ComponentAccessor<EntityStore> accessor,
                                    Ref<EntityStore> ref,
                                    Object componentType) {
            return accessor.getComponent(ref, (ComponentType) componentType);
        }
    }
}
