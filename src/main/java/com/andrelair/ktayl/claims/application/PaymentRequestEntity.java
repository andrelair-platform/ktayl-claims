package com.andrelair.ktayl.claims.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A claim-payment request moving through request → approve → execute (CLM-FIN-03b). Held in the claims DB
 * because the frozen legacy only knows EXECUTED settlements. The record is its own append-only audit: who
 * requested, who approved, who executed, when. Lives in the {@code readmodel}-profile datasource.
 */
@Entity
@Table(name = "payment_request")
public class PaymentRequestEntity {

    @Id
    private UUID id;

    @Column(name = "claim_number", nullable = false)
    private String claimNumber;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "requested_authority", nullable = false)
    private String requestedAuthority;

    @Column(nullable = false)
    private String status;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_authority")
    private String approvedAuthority;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "executed_by")
    private String executedBy;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column
    private String reason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    protected PaymentRequestEntity() {
        // JPA
    }

    public PaymentRequestEntity(UUID id, String claimNumber, BigDecimal amount, Actor requester, Instant requestedAt) {
        this.id = id;
        this.claimNumber = claimNumber;
        this.amount = amount;
        this.requestedBy = requester.id();
        this.requestedAuthority = requester.authority().name();
        this.status = "requested";
        this.requestedAt = requestedAt;
    }

    public void approve(Actor approver, Instant at) {
        this.status = "approved";
        this.approvedBy = approver.id();
        this.approvedAuthority = approver.authority().name();
        this.approvedAt = at;
    }

    public void execute(Actor executor, Instant at) {
        this.status = "executed";
        this.executedBy = executor.id();
        this.executedAt = at;
    }

    public void reject(Actor approver, String reason, Instant at) {
        this.status = "rejected";
        this.approvedBy = approver.id();
        this.approvedAuthority = approver.authority().name();
        this.reason = reason;
        this.approvedAt = at;
    }

    public boolean isRequested() { return "requested".equals(status); }
    public boolean isApproved() { return "approved".equals(status); }

    public UUID getId() { return id; }
    public String getClaimNumber() { return claimNumber; }
    public BigDecimal getAmount() { return amount; }
    public String getRequestedBy() { return requestedBy; }
    public String getStatus() { return status; }
    public String getApprovedBy() { return approvedBy; }
    public String getExecutedBy() { return executedBy; }
}
