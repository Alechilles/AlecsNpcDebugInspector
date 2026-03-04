package com.alechilles.alecsnpcdebuginspector.debug;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Optional runtime bridge that exposes Tamework-specific NPC debug fields when Tamework is installed.
 *
 * <p>All access is reflection-based so this mod can run without Alec's Tamework on the classpath.
 */
final class NpcDebugTameworkIntegration {
    private static final String TAMEWORK_MAIN_CLASS = "com.alechilles.alecstamework.Tamework";
    private static final String OWNER_COMPONENT_CLASS = "com.alechilles.alecstamework.npc.components.TameworkOwnerComponent";
    private static final String TAMED_COMPONENT_CLASS = "com.alechilles.alecstamework.npc.components.TameworkTamedComponent";
    private static final String HOOK_COMPONENT_CLASS = "com.alechilles.alecstamework.npc.components.TameworkHookComponent";
    private static final String NAME_COMPONENT_CLASS = "com.alechilles.alecstamework.npc.components.TameworkNpcNameComponent";
    private static final String COMMAND_LINKS_COMPONENT_CLASS = "com.alechilles.alecstamework.npc.components.TameworkCommandLinksComponent";
    private static final String HAPPINESS_COMPONENT_CLASS = "com.alechilles.alecstamework.npc.components.TameworkHappinessComponent";
    private static final String NEEDS_COMPONENT_CLASS = "com.alechilles.alecstamework.npc.components.TameworkNeedsComponent";
    private static final String BREEDING_COMPONENT_CLASS = "com.alechilles.alecstamework.npc.components.TameworkBreedingComponent";
    private static final String TRAITS_COMPONENT_CLASS = "com.alechilles.alecstamework.npc.components.TameworkTraitsComponent";
    private static final String ATTACHMENTS_COMPONENT_CLASS = "com.alechilles.alecstamework.npc.components.TameworkAttachmentsComponent";
    private static final String LIFE_STAGE_COMPONENT_CLASS = "com.alechilles.alecstamework.npc.components.TameworkLifeStageComponent";
    private static final DateTimeFormatter UTC_TIME_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private volatile Class<?> tameworkClass;
    private volatile boolean tameworkClassChecked;

    boolean isDetected() {
        return resolveTameworkPlugin() != null;
    }

    @Nonnull
    List<NpcDebugTameworkField> capture(@Nullable Ref<EntityStore> npcRef,
                                        @Nullable NPCEntity npc,
                                        @Nonnull Store<EntityStore> store) {
        Object plugin = resolveTameworkPlugin();
        if (plugin == null) {
            return List.of();
        }

        ArrayList<NpcDebugTameworkField> fields = new ArrayList<>();
        addField(fields, "tamework.present", "Plugin Loaded", "true", false, false);
        addField(fields, "tamework.debugHook", "Debug Hook Logs", formatBoolean(invokeBoolean(plugin, "isDebugHookEnabled")), true, false);
        addField(fields, "tamework.debugSpawner", "Debug Spawner Logs", formatBoolean(invokeBoolean(plugin, "isDebugSpawnerEnabled")), true, false);
        addField(fields, "tamework.debugPrompt", "Debug Prompt Logs", formatBoolean(invokeBoolean(plugin, "isDebugPromptEnabled")), true, false);

        if (npcRef == null || !npcRef.isValid() || npc == null) {
            addField(fields, "tamework.npc.status", "Status", "NPC unavailable", true, false);
            return fields;
        }

        appendOwnerFields(fields, npcRef, store);
        appendTamedFields(fields, npcRef, store);
        appendHookFields(fields, npcRef, store);
        appendNameFields(fields, npcRef, store);
        appendCommandLinksFields(fields, npcRef, store);
        appendHappinessFields(fields, npcRef, store);
        appendNeedsFields(fields, npcRef, store);
        appendBreedingFields(fields, npcRef, store);
        appendTraitsFields(fields, npcRef, store);
        appendAttachmentsFields(fields, npcRef, store);
        appendLifeStageFields(fields, npcRef, store);

        return fields;
    }

    private void appendOwnerFields(@Nonnull List<NpcDebugTameworkField> fields,
                                   @Nonnull Ref<EntityStore> npcRef,
                                   @Nonnull Store<EntityStore> store) {
        Object component = resolveComponent(npcRef, store, OWNER_COMPONENT_CLASS);
        addField(fields, "tamework.owner.present", "Owner Component", formatBoolean(component != null), true, false);
        if (component == null) {
            return;
        }
        addField(fields, "tamework.owner.hasOwner", "Has Owner", formatBoolean(invokeBoolean(component, "hasOwner")), true, true);
        addField(fields, "tamework.owner.id", "Owner UUID", stringify(invoke(component, "getOwnerId"), "<none>"), true, true);
        addField(fields, "tamework.owner.name", "Owner Name", stringify(invoke(component, "getOwnerName"), "<none>"), true, true);
    }

    private void appendTamedFields(@Nonnull List<NpcDebugTameworkField> fields,
                                   @Nonnull Ref<EntityStore> npcRef,
                                   @Nonnull Store<EntityStore> store) {
        Object component = resolveComponent(npcRef, store, TAMED_COMPONENT_CLASS);
        addField(fields, "tamework.tamed.present", "Tamed Component", formatBoolean(component != null), true, false);
        if (component == null) {
            return;
        }
        addField(fields, "tamework.tamed.value", "Tamed", formatBoolean(invokeBoolean(component, "isTamed")), true, true);
    }

    private void appendHookFields(@Nonnull List<NpcDebugTameworkField> fields,
                                  @Nonnull Ref<EntityStore> npcRef,
                                  @Nonnull Store<EntityStore> store) {
        Object component = resolveComponent(npcRef, store, HOOK_COMPONENT_CLASS);
        addField(fields, "tamework.hook.present", "Hook Component", formatBoolean(component != null), true, false);
        if (component == null) {
            return;
        }
        addField(fields, "tamework.hook.id", "Hook Id", stringify(invoke(component, "getHookId"), "<none>"), true, true);
        addField(fields, "tamework.hook.playerName", "Hook Player Name", stringify(invoke(component, "getPlayerName"), "<none>"), true, true);
        addField(fields, "tamework.hook.playerId", "Hook Player UUID", stringify(invoke(component, "getPlayerId"), "<none>"), true, true);
        addField(fields, "tamework.hook.itemId", "Hook Held Item", stringify(invoke(component, "getHeldItemId"), "<none>"), true, false);
        addField(fields, "tamework.hook.consume", "Consume On Match", formatBoolean(invokeBoolean(component, "isConsumeOnMatch")), true, false);
        addField(fields, "tamework.hook.hasTarget", "Hook Has Target Position", formatBoolean(invokeBoolean(component, "hasTargetPosition")), true, false);
        addField(fields, "tamework.hook.target", "Hook Target Position", formatVector(invoke(component, "getTargetPosition")), true, false);
        addField(fields, "tamework.hook.timestamp", "Hook Timestamp", formatTimestamp(invoke(component, "getTimestampMs")), true, false);
    }

    private void appendNameFields(@Nonnull List<NpcDebugTameworkField> fields,
                                  @Nonnull Ref<EntityStore> npcRef,
                                  @Nonnull Store<EntityStore> store) {
        Object component = resolveComponent(npcRef, store, NAME_COMPONENT_CLASS);
        addField(fields, "tamework.name.present", "Name Component", formatBoolean(component != null), true, false);
        if (component == null) {
            return;
        }
        addField(fields, "tamework.name.value", "Custom Name", stringify(invoke(component, "getName"), "<none>"), true, true);
        addField(fields, "tamework.name.ownerId", "Name Owner UUID", stringify(invoke(component, "getOwnerId"), "<none>"), true, false);
        addField(fields, "tamework.name.source", "Name Source", stringify(invoke(component, "getSource"), "<none>"), true, false);
        addField(fields, "tamework.name.updated", "Name Updated At", formatTimestamp(invoke(component, "getLastUpdatedMs")), true, false);
    }

    private void appendCommandLinksFields(@Nonnull List<NpcDebugTameworkField> fields,
                                          @Nonnull Ref<EntityStore> npcRef,
                                          @Nonnull Store<EntityStore> store) {
        Object component = resolveComponent(npcRef, store, COMMAND_LINKS_COMPONENT_CLASS);
        addField(fields, "tamework.links.present", "Command Links Component", formatBoolean(component != null), true, false);
        if (component == null) {
            return;
        }
        addField(fields, "tamework.links.ownerId", "Command Owner UUID", stringify(invoke(component, "getOwnerId"), "<none>"), true, false);

        Object toolIds = invoke(component, "getToolIds");
        int count = countArrayEntries(toolIds);
        addField(fields, "tamework.links.toolCount", "Linked Tool Count", String.valueOf(count), true, true);
        addField(fields, "tamework.links.toolIds", "Linked Tool Ids", summarizeArray(toolIds, 8), true, false);

        addField(fields, "tamework.links.hasHome", "Has Home", formatBoolean(invokeBoolean(component, "hasHome")), true, true);
        addField(fields, "tamework.links.home", "Home Position", formatVector(invoke(component, "getHomePosition")), true, true);
    }

    private void appendHappinessFields(@Nonnull List<NpcDebugTameworkField> fields,
                                       @Nonnull Ref<EntityStore> npcRef,
                                       @Nonnull Store<EntityStore> store) {
        Object component = resolveComponent(npcRef, store, HAPPINESS_COMPONENT_CLASS);
        addField(fields, "tamework.happiness.present", "Happiness Component", formatBoolean(component != null), true, false);
        if (component == null) {
            return;
        }
        addField(fields, "tamework.happiness.configId", "Happiness Config Id", stringify(invoke(component, "getConfigId"), "<none>"), true, false);
        addField(fields, "tamework.happiness.value", "Happiness Value", formatNumber(invoke(component, "getValue")), true, true);
        addField(fields, "tamework.happiness.lastUpdate", "Happiness Last Update", formatTimestamp(invoke(component, "getLastUpdateMs")), true, false);
    }

    private void appendNeedsFields(@Nonnull List<NpcDebugTameworkField> fields,
                                   @Nonnull Ref<EntityStore> npcRef,
                                   @Nonnull Store<EntityStore> store) {
        Object component = resolveComponent(npcRef, store, NEEDS_COMPONENT_CLASS);
        addField(fields, "tamework.needs.present", "Needs Component", formatBoolean(component != null), true, false);
        if (component == null) {
            return;
        }
        addField(fields, "tamework.needs.configId", "Needs Config Id", stringify(invoke(component, "getConfigId"), "<none>"), true, false);
        addField(fields, "tamework.needs.hunger", "Needs Hunger", formatNumber(invoke(component, "getHunger")), true, true);
        addField(fields, "tamework.needs.thirst", "Needs Thirst", formatNumber(invoke(component, "getThirst")), true, true);
        addField(fields, "tamework.needs.penalty", "Needs Happiness Penalty", formatNumber(invoke(component, "getAppliedHappinessPenalty")), true, true);
        addField(fields, "tamework.needs.lastUpdate", "Needs Last Update", formatTimestamp(invoke(component, "getLastUpdateMs")), true, false);
        addField(fields, "tamework.needs.lastPassiveSweep", "Needs Last Passive Sweep", formatTimestamp(invoke(component, "getLastPassiveSweepMs")), true, false);
    }

    private void appendBreedingFields(@Nonnull List<NpcDebugTameworkField> fields,
                                      @Nonnull Ref<EntityStore> npcRef,
                                      @Nonnull Store<EntityStore> store) {
        Object component = resolveComponent(npcRef, store, BREEDING_COMPONENT_CLASS);
        addField(fields, "tamework.breeding.present", "Breeding Component", formatBoolean(component != null), true, false);
        if (component == null) {
            return;
        }
        addField(fields, "tamework.breeding.configId", "Breeding Config Id", stringify(invoke(component, "getConfigId"), "<none>"), true, false);
        addField(fields, "tamework.breeding.happiness", "Breeding Happiness", formatNumber(invoke(component, "getHappiness")), true, true);
        addField(fields, "tamework.breeding.ready", "Breeding Ready", formatBoolean(invokeBoolean(component, "isReady")), true, true);
        addField(fields, "tamework.breeding.cooldownUntil", "Breeding Cooldown Until", formatTimestamp(invoke(component, "getCooldownUntilMs")), true, true);
        addField(fields, "tamework.breeding.partner", "Last Partner UUID", stringify(invoke(component, "getLastPartnerUuid"), "<none>"), true, false);
        addField(fields, "tamework.breeding.lastUpdate", "Breeding Happiness Update", formatTimestamp(invoke(component, "getLastHappinessUpdateMs")), true, false);
    }

    private void appendTraitsFields(@Nonnull List<NpcDebugTameworkField> fields,
                                    @Nonnull Ref<EntityStore> npcRef,
                                    @Nonnull Store<EntityStore> store) {
        Object component = resolveComponent(npcRef, store, TRAITS_COMPONENT_CLASS);
        addField(fields, "tamework.traits.present", "Traits Component", formatBoolean(component != null), true, false);
        if (component == null) {
            return;
        }
        addField(fields, "tamework.traits.configId", "Trait Config Id", stringify(invoke(component, "getConfigId"), "<none>"), true, false);
        addField(fields, "tamework.traits.rollSeed", "Trait Roll Seed", stringify(invoke(component, "getRollSeed"), "0"), true, false);
        Object values = invoke(component, "getTraitValues");
        addField(fields, "tamework.traits.count", "Trait Value Count", String.valueOf(countArrayEntries(values)), true, false);
        addField(fields, "tamework.traits.values", "Trait Values", summarizeTraitValues(values, 8), true, true);
    }

    private void appendAttachmentsFields(@Nonnull List<NpcDebugTameworkField> fields,
                                         @Nonnull Ref<EntityStore> npcRef,
                                         @Nonnull Store<EntityStore> store) {
        Object component = resolveComponent(npcRef, store, ATTACHMENTS_COMPONENT_CLASS);
        addField(fields, "tamework.attachments.present", "Attachments Component", formatBoolean(component != null), true, false);
        if (component == null) {
            return;
        }
        addField(fields, "tamework.attachments.configId", "Attachment Config Id", stringify(invoke(component, "getConfigId"), "<none>"), true, false);
        Object attachmentIds = invoke(component, "getAttachmentIds");
        int attachmentCount = attachmentIds instanceof Map<?, ?> map ? map.size() : 0;
        addField(fields, "tamework.attachments.count", "Attachment Entry Count", String.valueOf(attachmentCount), true, false);
        addField(fields, "tamework.attachments.values", "Attachments", summarizeMapEntries(attachmentIds, 8), true, true);
    }

    private void appendLifeStageFields(@Nonnull List<NpcDebugTameworkField> fields,
                                       @Nonnull Ref<EntityStore> npcRef,
                                       @Nonnull Store<EntityStore> store) {
        Object component = resolveComponent(npcRef, store, LIFE_STAGE_COMPONENT_CLASS);
        addField(fields, "tamework.lifeStage.present", "Life Stage Component", formatBoolean(component != null), true, false);
        if (component == null) {
            return;
        }
        addField(fields, "tamework.lifeStage.stage", "Life Stage", stringify(invoke(component, "getStage"), "<none>"), true, true);
        addField(fields, "tamework.lifeStage.scaling", "Growth Scaling Enabled", formatBoolean(invokeBoolean(component, "isGrowthScalingEnabled")), true, false);
        addField(fields, "tamework.lifeStage.babyRole", "Baby Role Id", stringify(invoke(component, "getBabyRoleId"), "<none>"), true, false);
        addField(fields, "tamework.lifeStage.adolescentRole", "Adolescent Role Id", stringify(invoke(component, "getAdolescentRoleId"), "<none>"), true, false);
        addField(fields, "tamework.lifeStage.adultRole", "Adult Role Id", stringify(invoke(component, "getAdultRoleId"), "<none>"), true, false);
        addField(fields, "tamework.lifeStage.bornAt", "Born At", formatTimestamp(invoke(component, "getBornAtMs")), true, false);
        addField(fields, "tamework.lifeStage.adolescentAt", "Adolescent At", formatTimestamp(invoke(component, "getAdolescentAtMs")), true, false);
        addField(fields, "tamework.lifeStage.adultAt", "Adult At", formatTimestamp(invoke(component, "getAdultAtMs")), true, false);
        addField(fields, "tamework.lifeStage.fullyGrownAt", "Fully Grown At", formatTimestamp(invoke(component, "getFullyGrownAtMs")), true, false);
        addField(fields, "tamework.lifeStage.scaleBaby", "Baby Scale", formatNumber(invoke(component, "getBabyScale")), true, false);
        addField(fields, "tamework.lifeStage.scaleAdolescent", "Adolescent Scale", formatNumber(invoke(component, "getAdolescentScale")), true, false);
        addField(fields, "tamework.lifeStage.scaleAdult", "Adult Scale", formatNumber(invoke(component, "getAdultScale")), true, false);
    }

    private void addField(@Nonnull List<NpcDebugTameworkField> fields,
                          @Nonnull String key,
                          @Nonnull String label,
                          @Nonnull String value,
                          boolean trackChange,
                          boolean recordEvent) {
        fields.add(new NpcDebugTameworkField(key, label, value, trackChange, recordEvent));
    }

    @Nullable
    private Object resolveTameworkPlugin() {
        Class<?> type = resolveTameworkClass();
        if (type == null) {
            return null;
        }
        return invokeStatic(type, "getInstance");
    }

    @Nullable
    private Class<?> resolveTameworkClass() {
        if (tameworkClassChecked) {
            return tameworkClass;
        }
        try {
            tameworkClass = Class.forName(TAMEWORK_MAIN_CLASS, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException ignored) {
            tameworkClass = null;
        }
        tameworkClassChecked = true;
        return tameworkClass;
    }

    @Nullable
    private Object resolveComponent(@Nonnull Ref<EntityStore> npcRef,
                                    @Nonnull Store<EntityStore> store,
                                    @Nonnull String componentClassName) {
        Class<?> componentClass = resolveClass(componentClassName);
        if (componentClass == null) {
            return null;
        }
        Object componentType = invokeStatic(componentClass, "getComponentType");
        if (!(componentType instanceof ComponentType<?, ?> rawType)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        ComponentType<EntityStore, ?> typed = (ComponentType<EntityStore, ?>) rawType;
        return store.getComponent(npcRef, typed);
    }

    @Nullable
    private Class<?> resolveClass(@Nonnull String className) {
        try {
            return Class.forName(className, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    @Nullable
    private Object invokeStatic(@Nonnull Class<?> targetClass, @Nonnull String methodName) {
        try {
            Method method = targetClass.getMethod(methodName);
            return method.invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    private Object invoke(@Nullable Object target, @Nonnull String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    private Boolean invokeBoolean(@Nullable Object target, @Nonnull String methodName) {
        Object value = invoke(target, methodName);
        return value instanceof Boolean bool ? bool : null;
    }

    @Nonnull
    private String formatBoolean(@Nullable Boolean value) {
        return value == null ? "n/a" : value.toString();
    }

    @Nonnull
    private String formatBoolean(boolean value) {
        return value ? "true" : "false";
    }

    @Nonnull
    private String formatNumber(@Nullable Object value) {
        if (!(value instanceof Number number)) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.2f", number.doubleValue());
    }

    @Nonnull
    private String formatTimestamp(@Nullable Object value) {
        if (!(value instanceof Number number)) {
            return "n/a";
        }
        long millis = number.longValue();
        if (millis == 0L) {
            return "0";
        }
        Instant instant;
        try {
            instant = Instant.ofEpochMilli(millis);
        } catch (Exception ignored) {
            return String.valueOf(millis);
        }
        return millis + " (" + UTC_TIME_FORMAT.format(instant) + " UTC)";
    }

    @Nonnull
    private String formatVector(@Nullable Object value) {
        if (value == null) {
            return "<none>";
        }
        if (value instanceof Vector3d vector) {
            return "("
                    + String.format(Locale.ROOT, "%.2f", vector.x)
                    + ", "
                    + String.format(Locale.ROOT, "%.2f", vector.y)
                    + ", "
                    + String.format(Locale.ROOT, "%.2f", vector.z)
                    + ")";
        }
        return value.toString();
    }

    @Nonnull
    private String stringify(@Nullable Object value, @Nonnull String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private int countArrayEntries(@Nullable Object value) {
        if (value == null || !value.getClass().isArray()) {
            return 0;
        }
        return Array.getLength(value);
    }

    @Nonnull
    private String summarizeArray(@Nullable Object value, int limit) {
        if (value == null || !value.getClass().isArray()) {
            return "<none>";
        }
        int length = Array.getLength(value);
        if (length == 0) {
            return "<none>";
        }
        ArrayList<String> values = new ArrayList<>(Math.min(length, limit));
        for (int i = 0; i < length && i < limit; i++) {
            Object entry = Array.get(value, i);
            if (entry == null) {
                continue;
            }
            String text = String.valueOf(entry).trim();
            if (text.isBlank()) {
                continue;
            }
            values.add(text);
        }
        if (values.isEmpty()) {
            return "<none>";
        }
        if (length > limit) {
            return String.join(", ", values) + " ... +" + (length - limit);
        }
        return String.join(", ", values);
    }

    @Nonnull
    private String summarizeMapEntries(@Nullable Object value, int limit) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return "<none>";
        }
        ArrayList<String> entries = new ArrayList<>(Math.min(map.size(), limit));
        int index = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry == null) {
                continue;
            }
            if (index >= limit) {
                break;
            }
            entries.add(String.valueOf(entry.getKey()) + "=" + String.valueOf(entry.getValue()));
            index++;
        }
        if (entries.isEmpty()) {
            return "<none>";
        }
        if (map.size() > limit) {
            return String.join(", ", entries) + " ... +" + (map.size() - limit);
        }
        return String.join(", ", entries);
    }

    @Nonnull
    private String summarizeTraitValues(@Nullable Object traitValues, int limit) {
        if (traitValues == null || !traitValues.getClass().isArray()) {
            return "<none>";
        }
        int length = Array.getLength(traitValues);
        if (length == 0) {
            return "<none>";
        }
        ArrayList<String> values = new ArrayList<>(Math.min(length, limit));
        for (int i = 0; i < length && i < limit; i++) {
            Object traitValue = Array.get(traitValues, i);
            if (traitValue == null) {
                continue;
            }
            String id = stringify(invoke(traitValue, "getId"), "<unknown>");
            String number = formatNumber(invoke(traitValue, "getValue"));
            values.add(id + "=" + number);
        }
        if (values.isEmpty()) {
            return "<none>";
        }
        if (length > limit) {
            return String.join(", ", values) + " ... +" + (length - limit);
        }
        return String.join(", ", values);
    }
}
