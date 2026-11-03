package com.adapteraspring.state;

import com.adapteraspring.enumerates.StateEnum;
import com.adapteraspring.exception.IllegalStateTransitionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskTransitionsTest {
    @Test
    void acceptsEveryLegalTransition() {
        assertTrue(TaskTransitions.canTransition(StateEnum.CREATED, StateEnum.IN_PROGRESS));
        assertTrue(TaskTransitions.canTransition(StateEnum.CREATED, StateEnum.FAILED));
        assertTrue(TaskTransitions.canTransition(StateEnum.IN_PROGRESS, StateEnum.COMPLETED));
        assertTrue(TaskTransitions.canTransition(StateEnum.IN_PROGRESS, StateEnum.FAILED));
        assertTrue(TaskTransitions.canTransition(StateEnum.IN_PROGRESS, StateEnum.INTERRUPTED));
        assertTrue(TaskTransitions.canTransition(StateEnum.INTERRUPTED, StateEnum.IN_PROGRESS));
        assertTrue(TaskTransitions.canTransition(StateEnum.INTERRUPTED, StateEnum.FAILED));
    }

    @Test
    void rejectsIllegalAndTerminalTransitions() {
        for (StateEnum from : StateEnum.values()) {
            for (StateEnum to : StateEnum.values()) {
                if (!TaskTransitions.canTransition(from, to)) {
                    assertThrows(IllegalStateTransitionException.class,
                            () -> TaskTransitions.requireTransition(from, to));
                }
            }
        }
        assertFalse(TaskTransitions.canTransition(StateEnum.COMPLETED, StateEnum.IN_PROGRESS));
        assertFalse(TaskTransitions.canTransition(StateEnum.FAILED, StateEnum.IN_PROGRESS));
    }

    @Test
    void requireAcceptsLegalTransition() {
        assertDoesNotThrow(() ->
                TaskTransitions.requireTransition(StateEnum.IN_PROGRESS, StateEnum.INTERRUPTED));
    }
}
