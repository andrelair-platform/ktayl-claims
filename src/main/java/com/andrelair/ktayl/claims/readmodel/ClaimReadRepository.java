package com.andrelair.ktayl.claims.readmodel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data repository over the {@code claim_read} projection (PK = source claim_id). */
public interface ClaimReadRepository extends JpaRepository<ClaimReadEntity, Long> {

    /** Look up by the business key (the query side reads by claim_number, not the source id). */
    Optional<ClaimReadEntity> findByClaimNumber(String claimNumber);
}
