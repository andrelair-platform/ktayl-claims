package com.andrelair.ktayl.claims.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReserveApprovalRepository extends JpaRepository<ReserveApprovalEntity, UUID> {

    List<ReserveApprovalEntity> findByStatusOrderByRequestedAt(String status);

    List<ReserveApprovalEntity> findByClaimNumberOrderByRequestedAtDesc(String claimNumber);
}
