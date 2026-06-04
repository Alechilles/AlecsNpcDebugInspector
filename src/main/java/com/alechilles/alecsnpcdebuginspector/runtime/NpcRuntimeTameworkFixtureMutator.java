package com.alechilles.alecsnpcdebuginspector.runtime;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Applies requested Tamework fixture state to spawned NPCs and emits auditable mutation records.
 */
public final class NpcRuntimeTameworkFixtureMutator {
    private final MutationBridge bridge;

    public NpcRuntimeTameworkFixtureMutator() {
        this(new ReflectiveMutationBridge());
    }

    NpcRuntimeTameworkFixtureMutator(@Nonnull MutationBridge bridge) {
        this.bridge = bridge;
    }

    @Nonnull
    List<NpcRuntimeTraceRecord> apply(@Nonnull String requestId,
                                      int tick,
                                      @Nullable Object store,
                                      @Nonnull NpcRuntimeFixtureSpawner.SpawnedNpc spawned,
                                      @Nonnull NpcRuntimeFixtureSpec.TameworkMutation mutation) {
        ArrayList<NpcRuntimeTraceRecord> records = new ArrayList<>();
        if (mutation.isEmpty()) {
            return records;
        }
        if (mutation.tamed() != null) {
            records.add(record(requestId, tick, spawned.fixtureId(), "tamed", mutation.tamed(),
                    bridge.setTamed(store, spawned, mutation.tamed())));
        }
        if (!mutation.owner().isEmpty()) {
            records.add(record(requestId, tick, spawned.fixtureId(), "owner", mutation.owner(),
                    bridge.setOwner(store, spawned, mutation.owner())));
        }
        for (Map.Entry<String, Object> need : mutation.needs().entrySet()) {
            String key = need.getKey();
            Object value = need.getValue();
            if (value instanceof Number number) {
                records.add(record(requestId, tick, spawned.fixtureId(), "needs." + key, value,
                        bridge.setNeed(store, spawned, key, number.doubleValue())));
            } else {
                records.add(record(requestId, tick, spawned.fixtureId(), "needs." + key, value,
                        MutationOutcome.unsupported("needs." + key)));
            }
        }
        for (String effectId : mutation.effects()) {
            records.add(record(requestId, tick, spawned.fixtureId(), "effects", effectId,
                    bridge.applyEffect(store, spawned, effectId)));
        }
        if (mutation.commandState() != null) {
            records.add(record(requestId, tick, spawned.fixtureId(), "commandState", mutation.commandState(),
                    bridge.setCommandState(store, spawned, mutation.commandState())));
        }
        if (mutation.lifeStage() != null) {
            records.add(record(requestId, tick, spawned.fixtureId(), "lifeStage", mutation.lifeStage(),
                    bridge.setLifeStage(store, spawned, mutation.lifeStage())));
        }
        return records;
    }

    @Nonnull
    private NpcRuntimeTraceRecord record(@Nonnull String requestId,
                                         int tick,
                                         @Nonnull String fixtureId,
                                         @Nonnull String field,
                                         @Nullable Object requestedValue,
                                         @Nonnull MutationOutcome outcome) {
        return NpcRuntimeTraceRecord.tameworkFixtureMutation(
                requestId,
                tick,
                fixtureId,
                field,
                requestedValue,
                outcome.appliedValue(),
                outcome.status(),
                outcome.unsupportedFields()
        );
    }

    interface MutationBridge {
        @Nonnull
        MutationOutcome setTamed(@Nullable Object store, @Nonnull NpcRuntimeFixtureSpawner.SpawnedNpc spawned, boolean value);

        @Nonnull
        MutationOutcome setOwner(@Nullable Object store, @Nonnull NpcRuntimeFixtureSpawner.SpawnedNpc spawned, @Nonnull Map<String, Object> value);

        @Nonnull
        MutationOutcome setNeed(@Nullable Object store, @Nonnull NpcRuntimeFixtureSpawner.SpawnedNpc spawned, @Nonnull String key, double value);

        @Nonnull
        MutationOutcome applyEffect(@Nullable Object store, @Nonnull NpcRuntimeFixtureSpawner.SpawnedNpc spawned, @Nonnull String effectId);

        @Nonnull
        MutationOutcome setCommandState(@Nullable Object store, @Nonnull NpcRuntimeFixtureSpawner.SpawnedNpc spawned, @Nonnull String value);

        @Nonnull
        MutationOutcome setLifeStage(@Nullable Object store, @Nonnull NpcRuntimeFixtureSpawner.SpawnedNpc spawned, @Nonnull String value);
    }

    record MutationOutcome(@Nonnull String status,
                           @Nullable Object appliedValue,
                           @Nonnull List<String> unsupportedFields) {
        @Nonnull
        static MutationOutcome applied(@Nullable Object appliedValue) {
            return new MutationOutcome("applied", appliedValue, List.of());
        }

        @Nonnull
        static MutationOutcome unsupported(@Nonnull String field) {
            return new MutationOutcome("unsupported", null, List.of(field));
        }
    }

    private static final class ReflectiveMutationBridge implements MutationBridge {
        private static final String TAMED_COMPONENT_CLASS =
                "com.alechilles.alecstamework.npc.components.TameworkTamedComponent";
        private static final String OWNER_COMPONENT_CLASS =
                "com.alechilles.alecstamework.npc.components.TameworkOwnerComponent";
        private static final String NEEDS_COMPONENT_CLASS =
                "com.alechilles.alecstamework.npc.components.TameworkNeedsComponent";
        private static final String LIFE_STAGE_COMPONENT_CLASS =
                "com.alechilles.alecstamework.npc.components.TameworkLifeStageComponent";
        private static final String EFFECT_SERVICE_CLASS =
                "com.alechilles.alecstamework.effects.TameworkEntityEffectService";

        @Nonnull
        @Override
        public MutationOutcome setTamed(@Nullable Object store,
                                        @Nonnull NpcRuntimeFixtureSpawner.SpawnedNpc spawned,
                                        boolean value) {
            Object component = component(store, spawned, TAMED_COMPONENT_CLASS);
            if (component == null) {
                return MutationOutcome.unsupported("tamed");
            }
            if (!invokeSetter(component, "setTamed", boolean.class, value)) {
                return MutationOutcome.unsupported("tamed");
            }
            Object applied = invoke(component, "isTamed");
            return MutationOutcome.applied(applied instanceof Boolean ? applied : value);
        }

        @Nonnull
        @Override
        public MutationOutcome setOwner(@Nullable Object store,
                                        @Nonnull NpcRuntimeFixtureSpawner.SpawnedNpc spawned,
                                        @Nonnull Map<String, Object> value) {
            Object component = component(store, spawned, OWNER_COMPONENT_CLASS);
            if (component == null) {
                return MutationOutcome.unsupported("owner");
            }
            UUID ownerId = ownerUuid(value);
            String ownerName = ownerName(value);
            if (ownerId == null || !invokeSetter(component, "setOwnerId", UUID.class, ownerId)) {
                return MutationOutcome.unsupported("owner");
            }
            invokeSetter(component, "setOwnerName", String.class, ownerName);
            return MutationOutcome.applied(new LinkedHashMap<>(value));
        }

        @Nonnull
        @Override
        public MutationOutcome setNeed(@Nullable Object store,
                                       @Nonnull NpcRuntimeFixtureSpawner.SpawnedNpc spawned,
                                       @Nonnull String key,
                                       double value) {
            Object component = component(store, spawned, NEEDS_COMPONENT_CLASS);
            if (component == null) {
                return MutationOutcome.unsupported("needs." + key);
            }
            String setter = switch (key) {
                case "hunger" -> "setHunger";
                case "thirst" -> "setThirst";
                case "appliedHappinessPenalty" -> "setAppliedHappinessPenalty";
                case "pendingNeedsDamage" -> "setPendingNeedsDamage";
                default -> null;
            };
            if (setter == null || !invokeSetter(component, setter, double.class, value)) {
                return MutationOutcome.unsupported("needs." + key);
            }
            return MutationOutcome.applied(value);
        }

        @Nonnull
        @Override
        public MutationOutcome applyEffect(@Nullable Object store,
                                           @Nonnull NpcRuntimeFixtureSpawner.SpawnedNpc spawned,
                                           @Nonnull String effectId) {
            if (!(store instanceof Store<?> typedStore) || spawned.ref() == null) {
                return MutationOutcome.unsupported("effects");
            }
            try {
                Class<?> serviceClass = Class.forName(EFFECT_SERVICE_CLASS);
                Class<?> componentAccessorClass = Class.forName("com.hypixel.hytale.component.ComponentAccessor");
                Method method = serviceClass.getMethod("applyEffect", Ref.class, String.class, componentAccessorClass);
                Object result = method.invoke(null, spawned.ref(), effectId, typedStore);
                return Boolean.TRUE.equals(result)
                        ? MutationOutcome.applied(effectId)
                        : MutationOutcome.unsupported("effects");
            } catch (ReflectiveOperationException exception) {
                return MutationOutcome.unsupported("effects");
            }
        }

        @Nonnull
        @Override
        public MutationOutcome setCommandState(@Nullable Object store,
                                               @Nonnull NpcRuntimeFixtureSpawner.SpawnedNpc spawned,
                                               @Nonnull String value) {
            return MutationOutcome.unsupported("commandState");
        }

        @Nonnull
        @Override
        public MutationOutcome setLifeStage(@Nullable Object store,
                                            @Nonnull NpcRuntimeFixtureSpawner.SpawnedNpc spawned,
                                            @Nonnull String value) {
            Object component = component(store, spawned, LIFE_STAGE_COMPONENT_CLASS);
            if (component == null) {
                return MutationOutcome.unsupported("lifeStage");
            }
            if (!invokeSetter(component, "setStage", String.class, value)) {
                return MutationOutcome.unsupported("lifeStage");
            }
            Object applied = invoke(component, "getStage");
            return MutationOutcome.applied(applied != null ? applied : value);
        }

        @Nullable
        private Object component(@Nullable Object store,
                                 @Nonnull NpcRuntimeFixtureSpawner.SpawnedNpc spawned,
                                 @Nonnull String componentClassName) {
            if (!(store instanceof Store<?> typedStore) || spawned.ref() == null) {
                return null;
            }
            try {
                Class<?> componentClass = Class.forName(componentClassName);
                Object type = componentClass.getMethod("getComponentType").invoke(null);
                if (!(type instanceof ComponentType<?, ?> rawType)) {
                    return null;
                }
                @SuppressWarnings("unchecked")
                Store<EntityStore> entityStore = (Store<EntityStore>) typedStore;
                @SuppressWarnings("unchecked")
                ComponentType<EntityStore, ?> componentType = (ComponentType<EntityStore, ?>) rawType;
                return entityStore.getComponent(spawned.ref(), componentType);
            } catch (ReflectiveOperationException exception) {
                return null;
            }
        }

        @Nullable
        private Object invoke(@Nonnull Object target, @Nonnull String methodName) {
            try {
                return target.getClass().getMethod(methodName).invoke(target);
            } catch (ReflectiveOperationException exception) {
                return null;
            }
        }

        private boolean invokeSetter(@Nonnull Object target,
                                     @Nonnull String methodName,
                                     @Nonnull Class<?> parameterType,
                                     @Nullable Object value) {
            try {
                target.getClass().getMethod(methodName, parameterType).invoke(target, value);
                return true;
            } catch (ReflectiveOperationException exception) {
                return false;
            }
        }

        @Nullable
        private UUID ownerUuid(@Nonnull Map<String, Object> owner) {
            Object uuid = first(owner, "uuid", "ownerUuid");
            if (uuid instanceof UUID value) {
                return value;
            }
            if (uuid instanceof String text && !text.isBlank()) {
                try {
                    return UUID.fromString(text);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
            Object id = owner.get("id");
            if (id instanceof String text && !text.isBlank()) {
                return UUID.nameUUIDFromBytes(("npc-runtime:" + text).getBytes(StandardCharsets.UTF_8));
            }
            return null;
        }

        @Nonnull
        private String ownerName(@Nonnull Map<String, Object> owner) {
            Object name = first(owner, "name", "ownerName", "id");
            return name instanceof String text && !text.isBlank() ? text : "npc-runtime-owner";
        }

        @Nullable
        private Object first(@Nonnull Map<String, Object> values, @Nonnull String... keys) {
            for (String key : keys) {
                Object value = values.get(key);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
    }
}
