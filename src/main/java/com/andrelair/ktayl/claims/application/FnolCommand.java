package com.andrelair.ktayl.claims.application;

import java.time.LocalDate;

/** Input to the FNOL use case (already validated at the edge). */
public record FnolCommand(
        String idempotencyKey,
        String policyNumber,
        LocalDate lossDate,
        String peril,
        String claimantName,
        String description
) {}
