package com.andrelair.ktayl.claims.readmodel;

import com.andrelair.ktayl.claims.domain.Claim;

import java.util.Optional;

/**
 * The CQRS <strong>query</strong> port (ADR-008). Reads a claim's current state from the
 * eventually-consistent read-model rather than the legacy write store.
 *
 * <p>Two implementations select by profile: {@link JpaClaimReadModel} (profile {@code readmodel} —
 * reads the CNPG projection fed by the CDC projector) and {@link LegacyClaimReadModel} (default —
 * delegates straight to the legacy so the service still answers reads with no DB, e.g. stub/tests).
 */
public interface ClaimReadModel {

    /** Read a claim's current state. {@code Optional.empty()} ⇒ unknown claim (⇒ 404). */
    Optional<Claim> findClaim(String claimNumber);
}
