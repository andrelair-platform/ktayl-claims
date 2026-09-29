package com.andrelair.ktayl.claims.application;

import com.andrelair.ktayl.claims.domain.Claim;
import com.andrelair.ktayl.claims.domain.ClaimNotFoundException;
import com.andrelair.ktayl.claims.legacy.GlobalCorePort;
import com.andrelair.ktayl.claims.readmodel.ClaimReadModel;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Claim lifecycle (S004): set/adjust the reserve, then settle — each **authority-checked server-side
 * first** (T7), then driven into the legacy, whose state machine is the ultimate guard (illegal
 * transitions are rejected in the DB, S002).
 *
 * <p><strong>CQRS (ADR-008).</strong> Commands (reserve/settle) go through the legacy write port and
 * return the <em>authoritative</em> post-command state (read-your-writes) so the caller never sees a
 * stale mutation response. A plain query ({@link #get}) reads the eventually-consistent
 * {@link ClaimReadModel} projection instead — the read path no longer touches the legacy.
 */
@Service
public class ClaimLifecycleService {

    private final GlobalCorePort legacy;
    private final ClaimReadModel readModel;

    public ClaimLifecycleService(GlobalCorePort legacy, ClaimReadModel readModel) {
        this.legacy = legacy;
        this.readModel = readModel;
    }

    public Claim reserve(Actor actor, String claimNumber, BigDecimal amount) {
        AuthorityPolicy.checkReserve(actor, amount);
        legacy.reserve(claimNumber, amount);
        return authoritativeClaim(claimNumber);
    }

    public Claim settle(Actor actor, String claimNumber, BigDecimal amount) {
        AuthorityPolicy.checkSettle(actor, amount);
        legacy.settle(claimNumber, amount);
        return authoritativeClaim(claimNumber);
    }

    /**
     * Query side (ADR-008): read the current state from the eventually-consistent read-model. Unknown
     * ⇒ ClaimNotFoundException (404). May lag a just-issued command by the CDC latency — by design.
     */
    public Claim get(String claimNumber) {
        return readModel.findClaim(claimNumber)
                .orElseThrow(() -> new ClaimNotFoundException(claimNumber));
    }

    /** Query side: the claims inbox (newest-first, optional status filter) from the read-model. */
    public java.util.List<Claim> list(String status) {
        return readModel.list(status);
    }

    /** Read-your-writes: after a command, return the legacy's authoritative state (not the projection). */
    private Claim authoritativeClaim(String claimNumber) {
        return legacy.findClaim(claimNumber)
                .orElseThrow(() -> new ClaimNotFoundException(claimNumber));
    }
}
