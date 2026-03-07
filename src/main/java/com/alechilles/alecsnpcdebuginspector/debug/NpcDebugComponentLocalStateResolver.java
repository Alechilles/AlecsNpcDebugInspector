package com.alechilles.alecsnpcdebuginspector.debug;

import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.util.IAnnotatedComponent;
import com.hypixel.hytale.server.npc.util.IAnnotatedComponentCollection;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Resolves component-local state machine indices into readable state names.
 *
 * <p>This resolver inspects the role's runtime component tree to map each
 * component-local state machine index to the local state names actually used by
 * that component, so inspector output can render transitions such as
 * {@code NeedsSeekFood.ConsumeDelay}.
 */
final class NpcDebugComponentLocalStateResolver {
    private static final Set<String> GENERIC_COMPONENT_STATE_NAMES = Set.of(
            "start",
            "hold",
            "commandmove",
            "progressing",
            "at_goal",
            "stopped"
    );

    private final Map<Role, Map<Integer, Map<Integer, String>>> roleComponentStatesCache =
            Collections.synchronizedMap(new WeakHashMap<>());

    @Nullable
    Int2IntMap readComponentLocalStates(@Nullable StateSupport stateSupport) {
        if (stateSupport == null) {
            return null;
        }
        return readField(stateSupport, "componentLocalStateMachines", Int2IntMap.class);
    }

    @Nonnull
    String resolveComponentLocalStateName(@Nonnull Role role,
                                          @Nonnull StateSupport stateSupport,
                                          int componentIndex,
                                          int localStateIndex) {
        if (localStateIndex == Integer.MIN_VALUE) {
            return "<none>";
        }

        Map<Integer, Map<Integer, String>> knownComponentStates = resolveRoleComponentStates(role, stateSupport);
        Map<Integer, String> stateNames = knownComponentStates.get(componentIndex);
        if (stateNames != null) {
            String mapped = stateNames.get(localStateIndex);
            if (mapped != null && !mapped.isBlank()) {
                return mapped;
            }
        }

        return resolveStateNameFromHelper(stateSupport, localStateIndex);
    }

    @Nullable
    String resolvePrimaryLocalSubStateName(@Nonnull Role role, @Nullable StateSupport stateSupport) {
        Int2IntMap componentLocalStates = readComponentLocalStates(stateSupport);
        if (componentLocalStates == null || componentLocalStates.isEmpty() || stateSupport == null) {
            return null;
        }

        String defaultSubState = resolveDefaultSubState(stateSupport);
        Map<Integer, Map<Integer, String>> knownComponentStates = resolveRoleComponentStates(role, stateSupport);

        List<Int2IntMap.Entry> entries = new ArrayList<>(componentLocalStates.int2IntEntrySet());
        entries.sort(Comparator.comparingInt(Int2IntMap.Entry::getIntKey));

        List<Candidate> candidates = new ArrayList<>(entries.size());
        for (Int2IntMap.Entry entry : entries) {
            int componentIndex = entry.getIntKey();
            int localStateIndex = entry.getIntValue();
            String localStateName = resolveComponentLocalStateName(role, stateSupport, componentIndex, localStateIndex);
            if (!isUsableStateName(localStateName)) {
                continue;
            }
            Map<Integer, String> componentStates = knownComponentStates.get(componentIndex);
            candidates.add(new Candidate(localStateName, hasDefaultLikeState(componentStates, defaultSubState)));
        }

        if (candidates.isEmpty()) {
            return null;
        }

        List<Candidate> preferred = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.componentHasDefaultLikeState) {
                preferred.add(candidate);
            }
        }
        List<Candidate> working = preferred.isEmpty() ? candidates : preferred;

        for (Candidate candidate : working) {
            if (isDefaultLike(candidate.stateName, defaultSubState) || isGenericComponentState(candidate.stateName)) {
                continue;
            }
            return candidate.stateName;
        }

        for (Candidate candidate : working) {
            if (!isDefaultLike(candidate.stateName, defaultSubState)) {
                return candidate.stateName;
            }
        }

        for (Candidate candidate : working) {
            if (isDefaultLike(candidate.stateName, defaultSubState)) {
                return candidate.stateName;
            }
        }

        return working.get(0).stateName;
    }

    @Nonnull
    private Map<Integer, Map<Integer, String>> resolveRoleComponentStates(@Nonnull Role role,
                                                                          @Nonnull StateSupport stateSupport) {
        Map<Integer, Map<Integer, String>> cached = roleComponentStatesCache.get(role);
        if (cached != null) {
            return cached;
        }

        HashMap<Integer, Map<Integer, String>> mapped = new HashMap<>();
        collectComponentLocalStates(role.getRootInstruction(), stateSupport, mapped);
        collectComponentLocalStates(role.getInteractionInstruction(), stateSupport, mapped);
        collectComponentLocalStates(role.getDeathInstruction(), stateSupport, mapped);

        HashMap<Integer, Map<Integer, String>> immutable = new HashMap<>();
        for (Map.Entry<Integer, Map<Integer, String>> entry : mapped.entrySet()) {
            immutable.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        Map<Integer, Map<Integer, String>> value = Map.copyOf(immutable);
        roleComponentStatesCache.put(role, value);
        return value;
    }

    private void collectComponentLocalStates(@Nullable IAnnotatedComponent component,
                                             @Nonnull StateSupport stateSupport,
                                             @Nonnull Map<Integer, Map<Integer, String>> mapped) {
        if (component == null) {
            return;
        }

        registerComponentLocalState(component, stateSupport, mapped);

        if (component instanceof IAnnotatedComponentCollection aggregate) {
            int nestedCount = aggregate.componentCount();
            for (int i = 0; i < nestedCount; i++) {
                IAnnotatedComponent nestedComponent = aggregate.getComponent(i);
                if (nestedComponent != null) {
                    collectComponentLocalStates(nestedComponent, stateSupport, mapped);
                }
            }
        }
    }

    private void registerComponentLocalState(@Nonnull IAnnotatedComponent component,
                                             @Nonnull StateSupport stateSupport,
                                             @Nonnull Map<Integer, Map<Integer, String>> mapped) {
        Boolean componentLocal = readField(component, "componentLocal", Boolean.class);
        if (componentLocal == null || !componentLocal) {
            return;
        }

        Integer componentIndex = readField(component, "componentIndex", Integer.class);
        Integer stateIndex = readField(component, "state", Integer.class);
        if (componentIndex == null || stateIndex == null || componentIndex < 0 || stateIndex < 0) {
            return;
        }

        String stateName = resolveStateNameFromHelper(stateSupport, stateIndex);
        if (!isUsableStateName(stateName)) {
            return;
        }

        mapped.computeIfAbsent(componentIndex, ignored -> new HashMap<>()).put(stateIndex, stateName);
    }

    @Nonnull
    private String resolveStateNameFromHelper(@Nonnull StateSupport stateSupport, int stateIndex) {
        try {
            String stateName = stateSupport.getStateHelper().getStateName(stateIndex);
            if (stateName != null && !stateName.isBlank()) {
                return stateName;
            }
        } catch (RuntimeException ignored) {
            // Best effort only.
        }
        return String.valueOf(stateIndex);
    }

    @Nullable
    private String resolveDefaultSubState(@Nonnull StateSupport stateSupport) {
        try {
            return stateSupport.getStateHelper().getDefaultSubState();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean hasDefaultLikeState(@Nullable Map<Integer, String> componentStates, @Nullable String defaultSubState) {
        if (componentStates == null || componentStates.isEmpty()) {
            return false;
        }
        for (String stateName : componentStates.values()) {
            if (isDefaultLike(stateName, defaultSubState)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDefaultLike(@Nullable String value, @Nullable String defaultSubState) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if ("Default".equalsIgnoreCase(value) || "start".equalsIgnoreCase(value)) {
            return true;
        }
        return defaultSubState != null && value.equalsIgnoreCase(defaultSubState);
    }

    private boolean isGenericComponentState(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return GENERIC_COMPONENT_STATE_NAMES.contains(value.toLowerCase(Locale.ROOT));
    }

    private boolean isUsableStateName(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return !isIntegerText(value);
    }

    private boolean isIntegerText(@Nonnull String value) {
        int start = value.startsWith("-") ? 1 : 0;
        if (start == value.length()) {
            return false;
        }
        for (int i = start; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T> T readField(@Nullable Object target, @Nonnull String fieldName, @Nonnull Class<T> expectedType) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(target);
                if (value == null) {
                    return null;
                }
                if (expectedType.isAssignableFrom(value.getClass())) {
                    return (T) value;
                }
                return null;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    private static final class Candidate {
        private final String stateName;
        private final boolean componentHasDefaultLikeState;

        private Candidate(@Nonnull String stateName, boolean componentHasDefaultLikeState) {
            this.stateName = stateName;
            this.componentHasDefaultLikeState = componentHasDefaultLikeState;
        }
    }
}
