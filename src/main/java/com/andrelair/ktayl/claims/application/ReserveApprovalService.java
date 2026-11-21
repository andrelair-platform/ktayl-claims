package com.andrelair.ktayl.claims.application;

import com.andrelair.ktayl.claims.domain.ApprovalNotFoundException;
import com.andrelair.ktayl.claims.domain.Claim;
import com.andrelair.ktayl.claims.domain.ClaimNotFoundException;
import com.andrelair.ktayl.claims.domain.IllegalTransitionException;
import com.andrelair.ktayl.claims.legacy.GlobalCorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The reserve four-eyes workflow (CLM-FIN-03a, threat T7). Available on the {@code readmodel} profile (the
 * only claims DB). A reserve within the requester's authority AND not a large loss is applied straight to the
 * legacy (as before); anything above authority OR a large loss is held as a PENDING approval and only driven
 * into the legacy once a <b>different</b>, sufficiently-authorised person approves it (the legacy then emits
 * the reserve movement via CDC → the GL, CLM-FIN-02). The approval record is its own append-only audit.
 */
@Service
@Profile("readmodel")
public class ReserveApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ReserveApprovalService.class);

    /** The outcome of submitting a reserve: either applied immediately, or held pending approval. */
    public record Outcome(String status, Claim claim, UUID approvalId) {
        public static Outcome applied(Claim c) { return new Outcome("APPLIED", c, null); }
        public static Outcome pending(UUID id) { return new Outcome("PENDING_APPROVAL", null, id); }
    }

    private final ReserveApprovalRepository approvals;
    private final GlobalCorePort legacy;
    private final BigDecimal largeLossThreshold;

    public ReserveApprovalService(ReserveApprovalRepository approvals, GlobalCorePort legacy,
            @Value("${claims.reserve.large-loss-threshold:100000}") BigDecimal largeLossThreshold) {
        this.approvals = approvals;
        this.legacy = legacy;
        this.largeLossThreshold = largeLossThreshold;
    }

    /** Submit a reserve: apply directly if within authority + not a large loss, else hold for approval. */
    @Transactional
    public Outcome submit(Actor requester, String claimNumber, BigDecimal amount) {
        legacy.findClaim(claimNumber).orElseThrow(() -> new ClaimNotFoundException(claimNumber));
        if (ReserveApprovalPolicy.requiresApproval(requester, amount, largeLossThreshold)) {
            ReserveApprovalEntity a = approvals.save(
                    new ReserveApprovalEntity(UUID.randomUUID(), claimNumber, amount, requester, Instant.now()));
            log.info("reserve {} on {} by {} ({}) → PENDING approval {}", amount, claimNumber, requester.id(),
                    requester.authority(), a.getId());
            return Outcome.pending(a.getId());
        }
        legacy.reserve(claimNumber, amount);
        return Outcome.applied(authoritative(claimNumber));
    }

    /** Approve a pending reserve (four-eyes + authority) → apply it to the legacy. Returns the applied claim. */
    @Transactional
    public Claim approve(Actor approver, UUID approvalId) {
        ReserveApprovalEntity a = require(approvalId);
        if (!a.isPending()) {
            throw new IllegalTransitionException("reserve approval " + approvalId + " is already " + a.getStatus());
        }
        ReserveApprovalPolicy.checkCanApprove(approver, a.getRequestedBy(), a.getAmount()); // 403 on self/authority
        legacy.reserve(a.getClaimNumber(), a.getAmount());   // drive the approved reserve into the legacy
        a.approve(approver, Instant.now());
        approvals.save(a);
        log.info("reserve approval {} APPROVED by {} ({}) → reserved {} on {}", approvalId, approver.id(),
                approver.authority(), a.getAmount(), a.getClaimNumber());
        return authoritative(a.getClaimNumber());
    }

    /** Reject a pending reserve (four-eyes + authority), with a reason. */
    @Transactional
    public void reject(Actor approver, UUID approvalId, String reason) {
        ReserveApprovalEntity a = require(approvalId);
        if (!a.isPending()) {
            throw new IllegalTransitionException("reserve approval " + approvalId + " is already " + a.getStatus());
        }
        ReserveApprovalPolicy.checkCanApprove(approver, a.getRequestedBy(), a.getAmount());
        a.reject(approver, reason, Instant.now());
        approvals.save(a);
        log.info("reserve approval {} REJECTED by {} ({}): {}", approvalId, approver.id(), approver.authority(), reason);
    }

    @Transactional(readOnly = true)
    public List<ReserveApprovalEntity> listPending() {
        return approvals.findByStatusOrderByRequestedAt("pending");
    }

    private ReserveApprovalEntity require(UUID id) {
        return approvals.findById(id).orElseThrow(() -> new ApprovalNotFoundException(id.toString()));
    }

    private Claim authoritative(String claimNumber) {
        return legacy.findClaim(claimNumber).orElseThrow(() -> new ClaimNotFoundException(claimNumber));
    }
}
