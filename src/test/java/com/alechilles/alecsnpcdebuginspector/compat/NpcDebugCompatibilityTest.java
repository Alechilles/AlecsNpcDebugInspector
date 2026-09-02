package com.alechilles.alecsnpcdebuginspector.compat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.instructions.Instruction;
import com.hypixel.hytale.server.npc.role.support.CombatSupport;
import com.hypixel.hytale.server.npc.role.support.EntitySupport;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.storage.AlarmStore;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

class NpcDebugCompatibilityTest {
    private static final String UPDATE_6_MARKER =
            "com.hypixel.hytale.server.npc.instructions.ExecutionSupport";

    @Test
    void initializesAgainstTheActiveApiGeneration() throws Exception {
        boolean markerPresent;
        try {
            Class.forName(UPDATE_6_MARKER, false, getClass().getClassLoader());
            markerPresent = true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            markerPresent = false;
        }

        assertDoesNotThrow(() -> Class.forName(
                NpcDebugCompatibility.class.getName(),
                true,
                NpcDebugCompatibility.class.getClassLoader()
        ));
        assertEquals(markerPresent, NpcDebugCompatibility.isUpdate6());
    }

    @Test
    void missingContextReturnsUnavailableValues() {
        assertNull(NpcDebugCompatibility.stateSupport(null, null, null));
        assertNull(NpcDebugCompatibility.entitySupport(null, null, null));
        assertNull(NpcDebugCompatibility.markedEntitySupport(null, null, null));
        assertNull(NpcDebugCompatibility.combatSupport(null, null, null));
        assertNull(NpcDebugCompatibility.nextBodyMotionStep(null, null, null, null));
        assertNull(NpcDebugCompatibility.nextHeadMotionStep(null, null, null, null));
        assertNull(NpcDebugCompatibility.alarmStore(null, null, null));
    }

    @Test
    void updateSixBindingsReadSupportComponentsAndQueuedMotion() throws Exception {
        if (!NpcDebugCompatibility.isUpdate6()) {
            return;
        }

        try (UpdateSixFixture fixture = UpdateSixFixture.open()) {
            Ref<EntityStore> ref = new Ref<>(null, 0);
            ComponentAccessor<EntityStore> accessor = fixture.accessor();

            assertSame(fixture.stateSupport(), NpcDebugCompatibility.stateSupport(null, ref, accessor));
            assertSame(fixture.entitySupport(), NpcDebugCompatibility.entitySupport(null, ref, accessor));
            assertSame(fixture.markedEntitySupport(), NpcDebugCompatibility.markedEntitySupport(null, ref, accessor));
            assertSame(fixture.combatSupport(), NpcDebugCompatibility.combatSupport(null, ref, accessor));
            assertSame(fixture.alarmStore(), NpcDebugCompatibility.alarmStore(
                    fixture.npcEntity(), ref, accessor));
            assertSame(fixture.bodyInstruction(), NpcDebugCompatibility.nextBodyMotionStep(
                    null, null, ref, accessor));
            assertSame(fixture.headInstruction(), NpcDebugCompatibility.nextHeadMotionStep(
                    null, null, ref, accessor));
        }
    }

    private static final class UpdateSixFixture implements AutoCloseable {
        private static final Unsafe UNSAFE = unsafe();
        private static final String MOTION_CONTEXT_SUPPORT =
                "com.hypixel.hytale.server.npc.role.support.MotionContextSupport";

        private final NPCPlugin originalPlugin;
        private final NPCPlugin fakePlugin;
        private final EntityModule originalEntityModule;
        private final EntityModule fakeEntityModule;
        private final Map<String, Object> originalComponentTypes = new IdentityHashMap<>();
        private final Map<Object, Object> components = new IdentityHashMap<>();
        private final Object motionContext;
        private final Instruction bodyInstruction = allocate(Instruction.class);
        private final Instruction headInstruction = allocate(Instruction.class);
        private final StateSupport stateSupport = allocate(StateSupport.class);
        private final EntitySupport entitySupport = allocate(EntitySupport.class);
        private final MarkedEntitySupport markedEntitySupport = allocate(MarkedEntitySupport.class);
        private final CombatSupport combatSupport = allocate(CombatSupport.class);
        private final AlarmStore alarmStore = new AlarmStore();
        private final NPCEntity npcEntity = allocate(NPCEntity.class);

        private UpdateSixFixture(NPCPlugin originalPlugin,
                                 NPCPlugin fakePlugin,
                                 EntityModule originalEntityModule,
                                 EntityModule fakeEntityModule,
                                 Object motionContext) {
            this.originalPlugin = originalPlugin;
            this.fakePlugin = fakePlugin;
            this.originalEntityModule = originalEntityModule;
            this.fakeEntityModule = fakeEntityModule;
            this.motionContext = motionContext;
        }

        static UpdateSixFixture open() throws Exception {
            Class<?> motionType = Class.forName(MOTION_CONTEXT_SUPPORT);
            Object motionContext = motionType.getConstructor().newInstance();
            NPCPlugin originalPlugin = (NPCPlugin) readStaticField(NPCPlugin.class, "instance");
            NPCPlugin fakePlugin = allocate(NPCPlugin.class);
            EntityModule originalEntityModule = (EntityModule) readStaticField(EntityModule.class, "instance");
            EntityModule fakeEntityModule = allocate(EntityModule.class);
            setStaticField(NPCPlugin.class, "instance", fakePlugin);
            setPluginLogger(fakePlugin);
            setStaticField(EntityModule.class, "instance", fakeEntityModule);
            setPluginLogger(fakeEntityModule);
            Map<Class<?>, ComponentType<EntityStore, ?>> entityTypes = new IdentityHashMap<>();
            entityTypes.put(NPCEntity.class, new ComponentType<>());
            setField(fakeEntityModule, "classToComponentType", entityTypes);
            UpdateSixFixture fixture = new UpdateSixFixture(
                    originalPlugin,
                    fakePlugin,
                    originalEntityModule,
                    fakeEntityModule,
                    motionContext
            );
            fixture.install();
            return fixture;
        }

        @SuppressWarnings("unchecked")
        ComponentAccessor<EntityStore> accessor() {
            InvocationHandler handler = (proxy, method, args) -> {
                if (method.getName().equals("getComponent")) {
                    return components.get(args[1]);
                }
                return defaultValue(method.getReturnType());
            };
            return (ComponentAccessor<EntityStore>) Proxy.newProxyInstance(
                    ComponentAccessor.class.getClassLoader(),
                    new Class<?>[]{ComponentAccessor.class},
                    handler
            );
        }

        StateSupport stateSupport() {
            return stateSupport;
        }

        EntitySupport entitySupport() {
            return entitySupport;
        }

        MarkedEntitySupport markedEntitySupport() {
            return markedEntitySupport;
        }

        CombatSupport combatSupport() {
            return combatSupport;
        }

        AlarmStore alarmStore() {
            return alarmStore;
        }

        NPCEntity npcEntity() {
            return npcEntity;
        }

        Instruction bodyInstruction() {
            return bodyInstruction;
        }

        Instruction headInstruction() {
            return headInstruction;
        }

        private void install() throws Exception {
            setStaticField(NPCPlugin.class, "instance", fakePlugin);
            installComponent("stateSupportComponentType", stateSupport);
            installComponent("entitySupportComponentType", entitySupport);
            installComponent("markedEntitySupportComponentType", markedEntitySupport);
            installComponent("combatSupportComponentType", combatSupport);
            installComponent("alarmStoreComponentType", alarmStore);

            ComponentType<EntityStore, ?> motionComponentType = new ComponentType<>();
            setField(fakePlugin, "motionContextSupportComponentType", motionComponentType);
            components.put(motionComponentType, motionContext);

            invoke(motionContext, "setNextBodyMotionStep", bodyInstruction);
            invoke(motionContext, "setNextHeadMotionStep", headInstruction);
        }

        private void installComponent(String fieldName, Object component) throws Exception {
            ComponentType<EntityStore, ?> type = new ComponentType<>();
            originalComponentTypes.put(fieldName, readField(fakePlugin, fieldName));
            setField(fakePlugin, fieldName, type);
            components.put(type, component);
        }

        @Override
        public void close() throws Exception {
            for (Map.Entry<String, Object> entry : originalComponentTypes.entrySet()) {
                setField(fakePlugin, entry.getKey(), entry.getValue());
            }
            setStaticField(NPCPlugin.class, "instance", originalPlugin);
            setStaticField(EntityModule.class, "instance", originalEntityModule);
        }

        private static void invoke(Object target, String methodName, Object value) throws Exception {
            Method method = target.getClass().getMethod(methodName, Instruction.class);
            method.invoke(target, value);
        }

        private static Object defaultValue(Class<?> returnType) {
            if (!returnType.isPrimitive()) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == float.class) {
                return 0F;
            }
            if (returnType == double.class) {
                return 0D;
            }
            if (returnType == char.class) {
                return '\0';
            }
            return null;
        }

        private static Object readStaticField(Class<?> type, String name) throws Exception {
            return readField(null, type.getDeclaredField(name));
        }

        private static Object readField(Object target, String name) throws Exception {
            return readField(target, target.getClass().getDeclaredField(name));
        }

        private static Object readField(Object target, Field field) throws Exception {
            field.setAccessible(true);
            return Modifier.isStatic(field.getModifiers()) ? field.get(null) : field.get(target);
        }

        private static void setStaticField(Class<?> type, String name, Object value) throws Exception {
            setField(null, type.getDeclaredField(name), value);
        }

        private static void setField(Object target, String name, Object value) throws Exception {
            setField(target, findField(target.getClass(), name), value);
        }

        private static void setField(Object target, Field field, Object value) throws Exception {
            field.setAccessible(true);
            if (Modifier.isStatic(field.getModifiers())) {
                UNSAFE.putObject(UNSAFE.staticFieldBase(field), UNSAFE.staticFieldOffset(field), value);
            } else {
                UNSAFE.putObject(target, UNSAFE.objectFieldOffset(field), value);
            }
        }

        @SuppressWarnings("unchecked")
        private static <T> T allocate(Class<T> type) {
            try {
                return (T) UNSAFE.allocateInstance(type);
            } catch (InstantiationException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        private static Unsafe unsafe() {
            try {
                Field field = Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                return (Unsafe) field.get(null);
            } catch (ReflectiveOperationException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        private static void setPluginLogger(Object plugin) throws Exception {
            Class<?> owner = plugin.getClass().getSuperclass();
            while (owner != null) {
                try {
                    setField(plugin, owner.getDeclaredField("logger"), HytaleLogger.getLogger());
                    return;
                } catch (NoSuchFieldException ignored) {
                    owner = owner.getSuperclass();
                }
            }
            throw new NoSuchFieldException("Plugin logger");
        }

        private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
            Class<?> owner = type;
            while (owner != null) {
                try {
                    return owner.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    owner = owner.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        }
    }
}
