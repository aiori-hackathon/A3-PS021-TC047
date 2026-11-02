package com.adapteraspring.exception;

import com.adapteraspring.enumerates.StateEnum;

public class IllegalStateTransitionException extends RuntimeException {
    public IllegalStateTransitionException(StateEnum from, StateEnum to) {
        super("Illegal task state transition: " + from + " -> " + to);
    }
}
