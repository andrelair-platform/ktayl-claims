package com.andrelair.ktayl.claims.application;

import com.andrelair.ktayl.claims.domain.AuthorityException;

import java.math.BigDecimal;

/** Server-side authority checks for money-moving actions (S004, threat T7). */
final class AuthorityPolicy {

    private AuthorityPolicy() {}

    static void checkReserve(Actor actor, BigDecimal amount) {
        if (amount.compareTo(actor.authority().maxReserve()) > 0) {
            throw new AuthorityException(
                    "reserve " + amount + " exceeds " + actor.authority() + " authority ("
                            + actor.authority().maxReserve() + ")");
        }
    }

    static void checkSettle(Actor actor, BigDecimal amount) {
        if (amount.compareTo(actor.authority().maxSettle()) > 0) {
            throw new AuthorityException(
                    "settlement " + amount + " exceeds " + actor.authority() + " authority ("
                            + actor.authority().maxSettle() + ")");
        }
    }
}
