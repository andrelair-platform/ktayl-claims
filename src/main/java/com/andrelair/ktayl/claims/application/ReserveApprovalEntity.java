package com.andrelair.ktayl.claims.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A pending/decided reserve approval request (CLM-FIN-03a). Held in the claims DB because the frozen legacy
 * cannot model an in-flight approval — it only knows APPLIED reserves. The record is its own audit: who
 * requested, who decided, when. Lives in the {@code readmodel}-profile datasource (the only claims DB).
 */
@Entity
@Table(name = "claim_reserve_approval")
public class ReserveApprovalEntity {

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

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_authority")
    private String decidedAuthority;

    @Column
    private String reason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected ReserveApprovalEntity() {
        // JPA
    }

    public ReserveApprovalEntity(UUID id, String claimNumber, BigDecimal amount, Actor requester, Instant requestedAt) {
        this.id = id;
        this.claimNumber = claimNumber;
        this.amount = amount;
        this.requestedBy = requester.id();
        this.requestedAuthority = requester.authority().name();
        this.status = "pending";
        this.requestedAt = requestedAt;
    }

    public void approve(Actor approver, Instant at) {
        this.status = "approved";
        this.decidedBy = approver.id();
        this.decidedAuthority = approver.authority().name();
        this.decidedAt = at;
    }

    public void reject(Actor approver, String reason, Instant at) {
        this.status = "rejected";
        this.decidedBy = approver.id();
        this.decidedAuthority = approver.authority().name();
        this.reason = reason;
        this.decidedAt = at;
    }

    public boolean isPending() { return "pending".equals(status); }

    public UUID getId() { return id; }
    public String getClaimNumber() { return claimNumber; }
    public BigDecimal getAmount() { return amount; }
    public String getRequestedBy() { return requestedBy; }
    public String getStatus() { return status; }
    public String getDecidedBy() { return decidedBy; }
}
