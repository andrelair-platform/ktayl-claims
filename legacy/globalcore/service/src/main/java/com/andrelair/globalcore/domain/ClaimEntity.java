package com.andrelair.globalcore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A claim — GlobalCore owns the lifecycle state machine (the ultimate guard, ADR-003). Money = eurocents. */
@Entity
@Table(name = "gc_claim")
public class ClaimEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_number")
    private String claimNumber;

    @Column(name = "policy_number")
    private String policyNumber;

    private String status;

    @Column(name = "loss_date")
    private LocalDate lossDate;

    private String peril;

    @Column(name = "claimant_name")
    private String claimantName;

    @Column(name = "reserve_minor")
    private Long reserveMinor;

    @Column(name = "settlement_minor")
    private Long settlementMinor;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getClaimNumber() { return claimNumber; }
    public void setClaimNumber(String v) { this.claimNumber = v; }
    public String getPolicyNumber() { return policyNumber; }
    public void setPolicyNumber(String v) { this.policyNumber = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDate getLossDate() { return lossDate; }
    public void setLossDate(LocalDate v) { this.lossDate = v; }
    public String getPeril() { return peril; }
    public void setPeril(String v) { this.peril = v; }
    public String getClaimantName() { return claimantName; }
    public void setClaimantName(String v) { this.claimantName = v; }
    public Long getReserveMinor() { return reserveMinor; }
    public void setReserveMinor(Long v) { this.reserveMinor = v; }
    public Long getSettlementMinor() { return settlementMinor; }
    public void setSettlementMinor(Long v) { this.settlementMinor = v; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime v) { this.registeredAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
