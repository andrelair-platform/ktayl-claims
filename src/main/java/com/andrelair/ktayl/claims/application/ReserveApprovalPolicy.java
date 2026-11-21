package com.andrelair.ktayl.claims.application;

import java.math.BigDecimal;

/**
 * The reserve four-eyes rules (CLM-FIN-03a, threat T7) — pure, server-side, no I/O.
 *
 * <ul>
 *   <li><b>requiresApproval</b> — a reserve needs a second pair of eyes when it is <b>above the requester's
 *       authority</b> OR it is a <b>large loss</b> (above the configured threshold, even for a SENIOR). A
 *       large loss always gets a second approver — that is the "large-loss review" control.</li>
 *   <li><b>canApprove</b> — the approver's authority must cover the amount AND they must be a <b>different
 *       person</b> than the requester (no self-approval). Because only a SENIOR covers amounts above the
 *       ADJUSTER cap, a large loss is necessarily approved by a senior — four eyes, senior-gated.</li>
 * </ul>
 */
public final class ReserveApprovalPolicy {

    private ReserveApprovalPolicy() {}

    /** True if this reserve may NOT be applied directly by the requester and must go to approval. */
    public static boolean requiresApproval(Actor requester, BigDecimal amount, BigDecimal largeLossThreshold) {
        boolean aboveAuthority = amount.compareTo(requester.authority().maxReserve()) > 0;
        boolean largeLoss = amount.compareTo(largeLossThreshold) > 0;
        return aboveAuthority || largeLoss;
    }

    /**
     * Validate an approval decision, throwing {@link com.andrelair.ktayl.claims.domain.AuthorityException}
     * (→ 403) if the approver may not approve: self-approval, or authority that does not cover the amount.
     */
    public static void checkCanApprove(Actor approver, String requestedBy, BigDecimal amount) {
        if (approver.id().equals(requestedBy)) {
            throw new com.andrelair.ktayl.claims.domain.AuthorityException(
                    "self-approval is not allowed — a reserve approval needs a different person (four-eyes)");
        }
        if (amount.compareTo(approver.authority().maxReserve()) > 0) {
            throw new com.andrelair.ktayl.claims.domain.AuthorityException(
                    "reserve " + amount + " exceeds the approver's " + approver.authority() + " authority ("
                            + approver.authority().maxReserve() + ")");
        }
    }
}
