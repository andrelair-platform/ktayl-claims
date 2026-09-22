package com.andrelair.ktayl.claims.domain;

/**
 * A reserve/settlement above the actor's authority (threat T7 — a settle above authority is a real
 * payout). Enforced server-side in the ACL, never trusting the caller.
 */
public class AuthorityException extends RuntimeException {
    public AuthorityException(String message) {
        super(message);
    }
}
