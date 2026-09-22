package com.andrelair.ktayl.claims.application;

import java.math.BigDecimal;

/**
 * The claims authority matrix — the per-role caps a reserve/settlement may not exceed (S004).
 * A real payout above authority must go to a senior; enforced server-side in the ACL (threat T7).
 */
public enum Authority {
    ADJUSTER(new BigDecimal("50000"),   new BigDecimal("10000")),
    SENIOR  (new BigDecimal("1000000"), new BigDecimal("500000"));

    private final BigDecimal maxReserve;
    private final BigDecimal maxSettle;

    Authority(BigDecimal maxReserve, BigDecimal maxSettle) {
        this.maxReserve = maxReserve;
        this.maxSettle = maxSettle;
    }

    public BigDecimal maxReserve() {
        return maxReserve;
    }

    public BigDecimal maxSettle() {
        return maxSettle;
    }
}
