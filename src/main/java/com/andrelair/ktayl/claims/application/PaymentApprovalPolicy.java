package com.andrelair.ktayl.claims.application;

import com.andrelair.ktayl.claims.domain.AuthorityException;

import java.math.BigDecimal;

/**
 * The claim-payment four-eyes rules (CLM-FIN-03b, threat T7 — a settle above authority is a real payout).
 * Pure, server-side, no I/O. Mirrors {@link ReserveApprovalPolicy} but keys off the <b>settlement</b>
 * authority ({@link Authority#maxSettle()}), which is tighter than the reserve cap, because this is actual
 * cash leaving the business.
 *
 * <ul>
 *   <li><b>requiresApproval</b> — a payment needs approval when it is above the requester's settlement
 *       authority OR it is a large loss (above the configured threshold, even for a SENIOR).</li>
 *   <li><b>checkCanApprove</b> — the approver's settlement authority must cover the amount AND they must be
 *       a <b>different person</b> than the requester (no self-approval).</li>
 * </ul>
 */
public final class PaymentApprovalPolicy {

    private PaymentApprovalPolicy() {}

    public static boolean requiresApproval(Actor requester, BigDecimal amount, BigDecimal largeLossThreshold) {
        boolean aboveAuthority = amount.compareTo(requester.authority().maxSettle()) > 0;
        boolean largeLoss = amount.compareTo(largeLossThreshold) > 0;
        return aboveAuthority || largeLoss;
    }

    public static void checkCanApprove(Actor approver, String requestedBy, BigDecimal amount) {
        if (approver.id().equals(requestedBy)) {
            throw new AuthorityException(
                    "self-approval is not allowed — a payment approval needs a different person (four-eyes)");
        }
        if (amount.compareTo(approver.authority().maxSettle()) > 0) {
            throw new AuthorityException(
                    "payment " + amount + " exceeds the approver's " + approver.authority() + " settlement authority ("
                            + approver.authority().maxSettle() + ")");
        }
    }

    /**
     * The executor (who disburses an approved payment) must be authorised to settle the amount and must not
     * be the original requester — segregation of duties across request → approve → execute.
     */
    public static void checkCanExecute(Actor executor, String requestedBy, BigDecimal amount) {
        if (executor.id().equals(requestedBy)) {
            throw new AuthorityException(
                    "the requester may not execute their own payment — execution is a separate duty (SoD)");
        }
        if (amount.compareTo(executor.authority().maxSettle()) > 0) {
            throw new AuthorityException(
                    "payment " + amount + " exceeds the executor's " + executor.authority() + " settlement authority ("
                            + executor.authority().maxSettle() + ")");
        }
    }
}
