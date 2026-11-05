package com.adapteraspring.state;

import com.adapteraspring.enumerates.StateEnum;
import com.adapteraspring.exception.IllegalStateTransitionException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class TaskTransitions {
    private static final Map<StateEnum, Set<StateEnum>> ALLOWED = new EnumMap<>(StateEnum.class);

    static {
        ALLOWED.put(StateEnum.CREATED, EnumSet.of(StateEnum.IN_PROGRESS, StateEnum.FAILED));
        ALLOWED.put(StateEnum.IN_PROGRESS,
                EnumSet.of(StateEnum.COMPLETED, StateEnum.FAILED, StateEnum.INTERRUPTED));
        ALLOWED.put(StateEnum.INTERRUPTED, EnumSet.of(StateEnum.IN_PROGRESS, StateEnum.FAILED));
        // Terminal states deliberately have no exits.
        ALLOWED.put(StateEnum.COMPLETED, EnumSet.noneOf(StateEnum.class));
        ALLOWED.put(StateEnum.FAILED, EnumSet.noneOf(StateEnum.class));
    }

    private TaskTransitions() {
    }

    public static boolean canTransition(StateEnum from, StateEnum to) {
        return from != null && to != null && ALLOWED.get(from).contains(to);
    }

    public static void requireTransition(StateEnum from, StateEnum to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateTransitionException(from, to);
        }
    }
}
