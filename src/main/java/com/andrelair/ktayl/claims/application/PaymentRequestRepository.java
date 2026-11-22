package com.andrelair.ktayl.claims.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequestEntity, UUID> {

    List<PaymentRequestEntity> findByStatusOrderByRequestedAt(String status);

    List<PaymentRequestEntity> findByClaimNumberOrderByRequestedAtDesc(String claimNumber);
}
