package com.andrelair.ktayl.claims.domain;

/**
 * A claim lifecycle step was attempted from a state that doesn't allow it (e.g. settle before reserve).
 * Mirrors the legacy state machine, which raises ORA-20001 in the DB (S002).
 */
public class IllegalTransitionException extends RuntimeException {
    public IllegalTransitionException(String message) {
        super(message);
    }
}
