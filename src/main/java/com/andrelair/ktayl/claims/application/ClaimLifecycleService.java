package com.andrelair.ktayl.claims.application;

import com.andrelair.ktayl.claims.domain.Claim;
import com.andrelair.ktayl.claims.domain.ClaimNotFoundException;
import com.andrelair.ktayl.claims.legacy.GlobalCorePort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Claim lifecycle (S004): set/adjust the reserve, then settle — each **authority-checked server-side
 * first** (T7), then driven into the legacy, whose state machine is the ultimate guard (illegal
 * transitions are rejected in the DB, S002).
 */
@Service
public class ClaimLifecycleService {

    private final GlobalCorePort legacy;

    public ClaimLifecycleService(GlobalCorePort legacy) {
        this.legacy = legacy;
    }

    public Claim reserve(Actor actor, String claimNumber, BigDecimal amount) {
        AuthorityPolicy.checkReserve(actor, amount);
        legacy.reserve(claimNumber, amount);
        return currentClaim(claimNumber);
    }

    public Claim settle(Actor actor, String claimNumber, BigDecimal amount) {
        AuthorityPolicy.checkSettle(actor, amount);
        legacy.settle(claimNumber, amount);
        return currentClaim(claimNumber);
    }

    private Claim currentClaim(String claimNumber) {
        return legacy.findClaim(claimNumber)
                .orElseThrow(() -> new ClaimNotFoundException(claimNumber));
    }
}
