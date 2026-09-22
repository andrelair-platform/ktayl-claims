package com.andrelair.ktayl.claims.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Record a settlement payment on a claim. */
public record SettleRequest(@NotNull @Positive BigDecimal amount) {}
