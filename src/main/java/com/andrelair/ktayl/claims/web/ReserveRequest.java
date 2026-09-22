package com.andrelair.ktayl.claims.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Set/adjust a claim reserve. */
public record ReserveRequest(@NotNull @Positive BigDecimal amount) {}
