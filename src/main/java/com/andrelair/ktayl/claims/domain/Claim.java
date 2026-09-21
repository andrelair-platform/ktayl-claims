package com.andrelair.ktayl.claims.domain;

import java.time.Instant;
import java.time.LocalDate;

/** A claim as the ACL exposes it — a clean view over the legacy (GlobalCore) record. */
public record Claim(
        String claimNumber,
        String policyNumber,
        ClaimStatus status,
        LocalDate lossDate,
        String peril,
        String claimantName,
        Instant registeredAt
) {}
