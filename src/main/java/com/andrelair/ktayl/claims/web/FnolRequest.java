package com.andrelair.ktayl.claims.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * FNOL intake payload. Validated at the edge before it becomes a {@code FnolCommand}.
 *
 * <p><strong>Length bounds mirror the legacy GlobalCore columns</strong> (gc_claim: policy_number
 * varchar(32), peril varchar(48), claimant_name varchar(160)) so oversized input is rejected at the
 * edge with a 400 — NOT passed through to the legacy where it blows up as a 500 (QA-gate finding
 * 2026-09-29: a 5000-char claimantName returned 500). description is bounded generously.
 */
public record FnolRequest(
        @NotBlank @Size(max = 32) String policyNumber,
        @NotNull LocalDate lossDate,
        @NotBlank @Size(max = 48) String peril,
        @NotBlank @Size(max = 160) String claimantName,
        @Size(max = 2000) String description
) {}
