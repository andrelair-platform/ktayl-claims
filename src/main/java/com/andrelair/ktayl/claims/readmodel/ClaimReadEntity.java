package com.andrelair.ktayl.claims.readmodel;

import com.andrelair.ktayl.claims.domain.Claim;
import com.andrelair.ktayl.claims.domain.ClaimStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** JPA mapping of the {@code claim_read} projection table (Slice D / ADR-008). */
@Entity
@Table(name = "claim_read")
public class ClaimReadEntity {

    @Id
    @Column(name = "claim_number")
    private String claimNumber;

    @Column(name = "policy_number", nullable = false)
    private String policyNumber;

    @Column(nullable = false)
    private String status;

    @Column(name = "loss_date", nullable = false)
    private LocalDate lossDate;

    @Column(nullable = false)
    private String peril;

    @Column(name = "claimant_name", nullable = false)
    private String claimantName;

    @Column(name = "reserve_minor")
    private Long reserveMinor;

    @Column(name = "settlement_minor")
    private Long settlementMinor;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "source_updated_at")
    private OffsetDateTime sourceUpdatedAt;

    @Column(nullable = false)
    private boolean deleted;

    protected ClaimReadEntity() {
        // JPA
    }

    /** Project this row into the ACL's clean {@link Claim} view. */
    public Claim toClaim() {
        return new Claim(claimNumber, policyNumber, ClaimStatus.valueOf(status),
                lossDate, peril, claimantName, registeredAt);
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public boolean isDeleted() {
        return deleted;
    }

    // package-private setters used only by the projector's upsert.
    void setClaimNumber(String v) { this.claimNumber = v; }
    void setPolicyNumber(String v) { this.policyNumber = v; }
    void setStatus(String v) { this.status = v; }
    void setLossDate(LocalDate v) { this.lossDate = v; }
    void setPeril(String v) { this.peril = v; }
    void setClaimantName(String v) { this.claimantName = v; }
    void setReserveMinor(Long v) { this.reserveMinor = v; }
    void setSettlementMinor(Long v) { this.settlementMinor = v; }
    void setRegisteredAt(Instant v) { this.registeredAt = v; }
    void setSourceUpdatedAt(OffsetDateTime v) { this.sourceUpdatedAt = v; }
    void setDeleted(boolean v) { this.deleted = v; }

    OffsetDateTime getSourceUpdatedAt() { return sourceUpdatedAt; }
}
