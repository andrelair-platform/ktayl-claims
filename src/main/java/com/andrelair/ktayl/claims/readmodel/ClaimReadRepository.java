package com.andrelair.ktayl.claims.readmodel;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository over the {@code claim_read} projection. */
public interface ClaimReadRepository extends JpaRepository<ClaimReadEntity, String> {
}
