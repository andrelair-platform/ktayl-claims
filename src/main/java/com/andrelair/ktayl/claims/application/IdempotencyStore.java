package com.andrelair.ktayl.claims.application;

import com.andrelair.ktayl.claims.domain.Claim;

import java.util.Optional;

/**
 * Dedup store for idempotent FNOL (S003) — a client idempotency key ⇒ at most one claim.
 * The in-memory impl is enough for the local slice; a shared/persistent store replaces it in S008
 * (RES-3, concurrency-safe under replay).
 */
public interface IdempotencyStore {
    Optional<Claim> find(String idempotencyKey);
    void save(String idempotencyKey, Claim claim);
}
