package com.andrelair.ktayl.claims.application;

import com.andrelair.ktayl.claims.domain.Claim;
import com.andrelair.ktayl.claims.domain.ClaimNotFoundException;
import com.andrelair.ktayl.claims.domain.IllegalTransitionException;
import com.andrelair.ktayl.claims.domain.PaymentRequestNotFoundException;
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
 * The claim-payment request → approve → execute workflow (CLM-FIN-03b, threat T7). Available on the
 * {@code readmodel} profile. A settlement within the requester's authority AND not a large loss is paid
 * straight into the legacy (as before); anything above authority OR a large loss is held as a REQUESTED
 * payment that a <b>different</b> person must APPROVE and an authorised, non-requester executor must EXECUTE
 * — three recorded steps (segregation of duties). Only the EXECUTE step drives {@code legacy.settle} (→ CDC →
 * the GL, CLM-FIN-02). The request record is its own append-only audit.
 */
@Service
@Profile("readmodel")
public class PaymentApprovalService {

    private static final Logger log = LoggerFactory.getLogger(PaymentApprovalService.class);

    /** The outcome of submitting a payment: paid immediately, or opened as a request for approval. */
    public record Outcome(String status, Claim claim, UUID requestId) {
        public static Outcome paid(Claim c) { return new Outcome("PAID", c, null); }
        public static Outcome requested(UUID id) { return new Outcome("PENDING_APPROVAL", null, id); }
    }

    private final PaymentRequestRepository requests;
    private final GlobalCorePort legacy;
    private final BigDecimal largeLossThreshold;

    public PaymentApprovalService(PaymentRequestRepository requests, GlobalCorePort legacy,
            @Value("${claims.payment.large-loss-threshold:100000}") BigDecimal largeLossThreshold) {
        this.requests = requests;
        this.legacy = legacy;
        this.largeLossThreshold = largeLossThreshold;
    }

    /** Submit a payment: pay directly if within authority + not a large loss, else open a request. */
    @Transactional
    public Outcome submit(Actor requester, String claimNumber, BigDecimal amount) {
        legacy.findClaim(claimNumber).orElseThrow(() -> new ClaimNotFoundException(claimNumber));
        if (PaymentApprovalPolicy.requiresApproval(requester, amount, largeLossThreshold)) {
            PaymentRequestEntity r = requests.save(
                    new PaymentRequestEntity(UUID.randomUUID(), claimNumber, amount, requester, Instant.now()));
            log.info("payment {} on {} by {} ({}) → REQUESTED {}", amount, claimNumber, requester.id(),
                    requester.authority(), r.getId());
            return Outcome.requested(r.getId());
        }
        legacy.settle(claimNumber, amount);
        return Outcome.paid(authoritative(claimNumber));
    }

    /** Approve a requested payment (four-eyes + authority). Does NOT pay yet — execution is a separate step. */
    @Transactional
    public void approve(Actor approver, UUID requestId) {
        PaymentRequestEntity r = require(requestId);
        if (!r.isRequested()) {
            throw new IllegalTransitionException("payment request " + requestId + " is " + r.getStatus()
                    + " — only a requested payment can be approved");
        }
        PaymentApprovalPolicy.checkCanApprove(approver, r.getRequestedBy(), r.getAmount()); // 403 on self/authority
        r.approve(approver, Instant.now());
        requests.save(r);
        log.info("payment request {} APPROVED by {} ({})", requestId, approver.id(), approver.authority());
    }

    /** Execute an approved payment — the disbursement. Drives {@code legacy.settle}. Returns the paid claim. */
    @Transactional
    public Claim execute(Actor executor, UUID requestId) {
        PaymentRequestEntity r = require(requestId);
        if (!r.isApproved()) {
            throw new IllegalTransitionException("payment request " + requestId + " is " + r.getStatus()
                    + " — only an approved payment can be executed");
        }
        PaymentApprovalPolicy.checkCanExecute(executor, r.getRequestedBy(), r.getAmount()); // 403 on requester/authority
        legacy.settle(r.getClaimNumber(), r.getAmount());   // the actual payout
        r.execute(executor, Instant.now());
        requests.save(r);
        log.info("payment request {} EXECUTED by {} ({}) → settled {} on {}", requestId, executor.id(),
                executor.authority(), r.getAmount(), r.getClaimNumber());
        return authoritative(r.getClaimNumber());
    }

    /** Reject a requested payment (four-eyes + authority), with a reason. */
    @Transactional
    public void reject(Actor approver, UUID requestId, String reason) {
        PaymentRequestEntity r = require(requestId);
        if (!r.isRequested()) {
            throw new IllegalTransitionException("payment request " + requestId + " is " + r.getStatus());
        }
        PaymentApprovalPolicy.checkCanApprove(approver, r.getRequestedBy(), r.getAmount());
        r.reject(approver, reason, Instant.now());
        requests.save(r);
        log.info("payment request {} REJECTED by {} ({}): {}", requestId, approver.id(), approver.authority(), reason);
    }

    @Transactional(readOnly = true)
    public List<PaymentRequestEntity> listOpen() {
        List<PaymentRequestEntity> open = requests.findByStatusOrderByRequestedAt("requested");
        open.addAll(requests.findByStatusOrderByRequestedAt("approved"));
        return open;
    }

    private PaymentRequestEntity require(UUID id) {
        return requests.findById(id).orElseThrow(() -> new PaymentRequestNotFoundException(id.toString()));
    }

    private Claim authoritative(String claimNumber) {
        return legacy.findClaim(claimNumber).orElseThrow(() -> new ClaimNotFoundException(claimNumber));
    }
}
