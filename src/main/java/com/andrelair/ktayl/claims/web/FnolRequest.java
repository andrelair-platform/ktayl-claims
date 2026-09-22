package com.andrelair.ktayl.claims.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** FNOL intake payload. Validated at the edge before it becomes a {@code FnolCommand}. */
public record FnolRequest(
        @NotBlank String policyNumber,
        @NotNull LocalDate lossDate,
        @NotBlank String peril,
        @NotBlank String claimantName,
        String description
) {}
