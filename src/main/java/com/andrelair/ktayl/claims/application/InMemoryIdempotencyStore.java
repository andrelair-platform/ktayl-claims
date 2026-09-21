package com.andrelair.ktayl.claims.application;

import com.andrelair.ktayl.claims.domain.Claim;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory dedup — sufficient for the local slice; swapped for a shared store in S008 (RES-3). */
@Component
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final ConcurrentHashMap<String, Claim> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Claim> find(String idempotencyKey) {
        return Optional.ofNullable(store.get(idempotencyKey));
    }

    @Override
    public void save(String idempotencyKey, Claim claim) {
        store.putIfAbsent(idempotencyKey, claim);
    }
}
