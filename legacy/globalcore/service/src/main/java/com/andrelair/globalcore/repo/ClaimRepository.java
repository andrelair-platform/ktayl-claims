package com.andrelair.globalcore.repo;

import com.andrelair.globalcore.domain.ClaimEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClaimRepository extends JpaRepository<ClaimEntity, Long> {
    Optional<ClaimEntity> findByClaimNumber(String claimNumber);
}
