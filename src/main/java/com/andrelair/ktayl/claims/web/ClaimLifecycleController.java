package com.andrelair.ktayl.claims.web;

import com.andrelair.ktayl.claims.application.Actor;
import com.andrelair.ktayl.claims.application.Authority;
import com.andrelair.ktayl.claims.application.ClaimLifecycleService;
import com.andrelair.ktayl.claims.application.PaymentApprovalService;
import com.andrelair.ktayl.claims.application.PaymentRequestEntity;
import com.andrelair.ktayl.claims.application.ReserveApprovalEntity;
import com.andrelair.ktayl.claims.application.ReserveApprovalService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Claim lifecycle endpoints (S004): reserve then settle. The acting authority comes from the
 * {@code X-Claims-Authority} header (ADJUSTER / SENIOR) — real auth = Authentik OIDC later (ADR-005).
 * Above-authority ⇒ 403; an out-of-order step (e.g. settle before reserve) ⇒ 409.
 *
 * <p><b>Reserve four-eyes (CLM-FIN-03a).</b> When the {@link ReserveApprovalService} is present (the
 * {@code readmodel} profile — prod/dev), a reserve above the requester's authority OR a large loss returns
 * {@code 202} with a {@code PENDING_APPROVAL} handle instead of being applied; a different, higher-authority
 * person then approves it (self-approval ⇒ 403; an already-decided approval ⇒ 409). Without that service
 * (stub-only local mode) a reserve applies directly and an above-authority amount is a plain {@code 403}.
 */
@RestController
@RequestMapping("/api/claims")
public class ClaimLifecycleController {

    /** The 202 body when a reserve is held for approval. */
    public record ReserveApprovalHandle(UUID approvalId, String status) {}

    public record ReserveApprovalView(UUID approvalId, String claimNumber, java.math.BigDecimal amount,
            String requestedBy, String status) {
        static ReserveApprovalView from(ReserveApprovalEntity a) {
            return new ReserveApprovalView(a.getId(), a.getClaimNumber(), a.getAmount(), a.getRequestedBy(), a.getStatus());
        }
    }

    public record RejectRequest(String reason) {}

    /** The 202 body when a payment is held for approval. */
    public record PaymentRequestHandle(UUID requestId, String status) {}

    public record PaymentRequestView(UUID requestId, String claimNumber, java.math.BigDecimal amount,
            String requestedBy, String status, String approvedBy) {
        static PaymentRequestView from(PaymentRequestEntity r) {
            return new PaymentRequestView(r.getId(), r.getClaimNumber(), r.getAmount(), r.getRequestedBy(),
                    r.getStatus(), r.getApprovedBy());
        }
    }

    private final ClaimLifecycleService lifecycle;
    private final ObjectProvider<ReserveApprovalService> reserveApprovals;
    private final ObjectProvider<PaymentApprovalService> paymentApprovals;

    public ClaimLifecycleController(ClaimLifecycleService lifecycle,
            ObjectProvider<ReserveApprovalService> reserveApprovals,
            ObjectProvider<PaymentApprovalService> paymentApprovals) {
        this.lifecycle = lifecycle;
        this.reserveApprovals = reserveApprovals;
        this.paymentApprovals = paymentApprovals;
    }

    /** The claims inbox — newest-first, optional {@code ?status=} filter. Reads the read-model. */
    @GetMapping
    public List<ClaimResponse> list(@RequestParam(required = false) String status) {
        return lifecycle.list(status).stream().map(ClaimResponse::from).toList();
    }

    /** Read a claim's current state. Unknown claim ⇒ 404 (no authority header needed — a plain read). */
    @GetMapping("/{claimNumber}")
    public ClaimResponse get(@PathVariable String claimNumber) {
        return ClaimResponse.from(lifecycle.get(claimNumber));
    }

    /**
     * Reserve on a claim. Within authority + not a large loss ⇒ 200 applied (ClaimResponse). Above authority
     * OR a large loss (readmodel profile) ⇒ 202 + a {@link ReserveApprovalHandle} (NOT applied) for four-eyes.
     */
    @PostMapping("/{claimNumber}/reserve")
    public ResponseEntity<?> reserve(
            @PathVariable String claimNumber,
            @RequestHeader("X-Claims-Authority") Authority authority,
            @RequestHeader(value = "X-Claims-User", defaultValue = "unknown") String user,
            @Valid @RequestBody ReserveRequest req) {
        Actor actor = new Actor(user, authority);
        ReserveApprovalService approvals = reserveApprovals.getIfAvailable();
        if (approvals == null) {
            // stub-only local mode: no approval store — apply directly (403 above authority, as before).
            return ResponseEntity.ok(ClaimResponse.from(lifecycle.reserve(actor, claimNumber, req.amount())));
        }
        ReserveApprovalService.Outcome outcome = approvals.submit(actor, claimNumber, req.amount());
        if ("PENDING_APPROVAL".equals(outcome.status())) {
            return ResponseEntity.accepted().body(new ReserveApprovalHandle(outcome.approvalId(), outcome.status()));
        }
        return ResponseEntity.ok(ClaimResponse.from(outcome.claim()));
    }

    /** Approve a pending reserve (a different, higher-authority person) ⇒ applies it. Self-approve ⇒ 403. */
    @PostMapping("/reserve-approvals/{approvalId}/approve")
    public ClaimResponse approveReserve(
            @PathVariable UUID approvalId,
            @RequestHeader("X-Claims-Authority") Authority authority,
            @RequestHeader(value = "X-Claims-User", defaultValue = "unknown") String user) {
        return ClaimResponse.from(reserveApprovals.getObject().approve(new Actor(user, authority), approvalId));
    }

    /** Reject a pending reserve (a different, higher-authority person), with a reason. */
    @PostMapping("/reserve-approvals/{approvalId}/reject")
    public ResponseEntity<Void> rejectReserve(
            @PathVariable UUID approvalId,
            @RequestHeader("X-Claims-Authority") Authority authority,
            @RequestHeader(value = "X-Claims-User", defaultValue = "unknown") String user,
            @RequestBody(required = false) RejectRequest req) {
        String reason = req == null ? null : req.reason();
        reserveApprovals.getObject().reject(new Actor(user, authority), approvalId, reason);
        return ResponseEntity.noContent().build();
    }

    /** The pending reserve-approval queue (for a reviewer). */
    @GetMapping("/reserve-approvals")
    public List<ReserveApprovalView> pendingReserveApprovals() {
        return reserveApprovals.getObject().listPending().stream().map(ReserveApprovalView::from).toList();
    }

    /**
     * Settle (pay) a claim. Within authority + not a large loss ⇒ 200 paid (ClaimResponse). Above authority
     * OR a large loss (readmodel profile) ⇒ 202 + a {@link PaymentRequestHandle} (NOT paid) — the payment
     * then goes request → approve → execute (CLM-FIN-03b).
     */
    @PostMapping("/{claimNumber}/settle")
    public ResponseEntity<?> settle(
            @PathVariable String claimNumber,
            @RequestHeader("X-Claims-Authority") Authority authority,
            @RequestHeader(value = "X-Claims-User", defaultValue = "unknown") String user,
            @Valid @RequestBody SettleRequest req) {
        Actor actor = new Actor(user, authority);
        PaymentApprovalService payments = paymentApprovals.getIfAvailable();
        if (payments == null) {
            // stub-only local mode: apply directly (403 above authority, as before).
            return ResponseEntity.ok(ClaimResponse.from(lifecycle.settle(actor, claimNumber, req.amount())));
        }
        PaymentApprovalService.Outcome outcome = payments.submit(actor, claimNumber, req.amount());
        if ("PENDING_APPROVAL".equals(outcome.status())) {
            return ResponseEntity.accepted().body(new PaymentRequestHandle(outcome.requestId(), outcome.status()));
        }
        return ResponseEntity.ok(ClaimResponse.from(outcome.claim()));
    }

    /** Approve a requested payment (a different, higher-authority person). Does NOT pay — execute does. */
    @PostMapping("/payment-requests/{requestId}/approve")
    public ResponseEntity<Void> approvePayment(
            @PathVariable UUID requestId,
            @RequestHeader("X-Claims-Authority") Authority authority,
            @RequestHeader(value = "X-Claims-User", defaultValue = "unknown") String user) {
        paymentApprovals.getObject().approve(new Actor(user, authority), requestId);
        return ResponseEntity.noContent().build();
    }

    /** Execute an approved payment (an authorised, non-requester executor) ⇒ the disbursement (settle). */
    @PostMapping("/payment-requests/{requestId}/execute")
    public ClaimResponse executePayment(
            @PathVariable UUID requestId,
            @RequestHeader("X-Claims-Authority") Authority authority,
            @RequestHeader(value = "X-Claims-User", defaultValue = "unknown") String user) {
        return ClaimResponse.from(paymentApprovals.getObject().execute(new Actor(user, authority), requestId));
    }

    /** Reject a requested payment (a different, higher-authority person), with a reason. */
    @PostMapping("/payment-requests/{requestId}/reject")
    public ResponseEntity<Void> rejectPayment(
            @PathVariable UUID requestId,
            @RequestHeader("X-Claims-Authority") Authority authority,
            @RequestHeader(value = "X-Claims-User", defaultValue = "unknown") String user,
            @RequestBody(required = false) RejectRequest req) {
        paymentApprovals.getObject().reject(new Actor(user, authority), requestId, req == null ? null : req.reason());
        return ResponseEntity.noContent().build();
    }

    /** The open payment-request queue (requested + approved, for a reviewer / executor). */
    @GetMapping("/payment-requests")
    public List<PaymentRequestView> openPaymentRequests() {
        return paymentApprovals.getObject().listOpen().stream().map(PaymentRequestView::from).toList();
    }
}
