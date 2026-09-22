package com.andrelair.ktayl.claims.web;

import com.andrelair.ktayl.claims.domain.Claim;

import java.time.Instant;
import java.time.LocalDate;

/** The clean JSON claim the ACL returns. */
public record ClaimResponse(
        String claimNumber,
        String policyNumber,
        String status,
        LocalDate lossDate,
        String peril,
        String claimantName,
        Instant registeredAt
) {
    static ClaimResponse from(Claim c) {
        return new ClaimResponse(c.claimNumber(), c.policyNumber(), c.status().name(),
                c.lossDate(), c.peril(), c.claimantName(), c.registeredAt());
    }
}
