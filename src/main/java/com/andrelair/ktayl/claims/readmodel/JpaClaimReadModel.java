package com.andrelair.ktayl.claims.readmodel;

import com.andrelair.ktayl.claims.domain.Claim;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * CQRS query side (profile {@code readmodel}) — reads the CNPG {@code claim_read} projection kept
 * current by {@link CdcProjector}. A soft-deleted (tombstoned) row reads as not-found (⇒ 404).
 * Eventually consistent: a read right after a command may lag by the CDC latency (ADR-008).
 */
@Component
@Profile("readmodel")
public class JpaClaimReadModel implements ClaimReadModel {

    private final ClaimReadRepository repo;

    public JpaClaimReadModel(ClaimReadRepository repo) {
        this.repo = repo;
    }

    @Override
    public Optional<Claim> findClaim(String claimNumber) {
        return repo.findById(claimNumber)
                .filter(e -> !e.isDeleted())
                .map(ClaimReadEntity::toClaim);
    }
}
