package com.andrelair.globalcore.service;

import com.andrelair.globalcore.domain.ClaimEntity;
import com.andrelair.globalcore.domain.PolicyEntity;
import com.andrelair.globalcore.repo.ClaimRepository;
import com.andrelair.globalcore.repo.PolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * GlobalCore business logic — the legacy claims core. Owns the lifecycle STATE MACHINE (the ultimate
 * guard, ADR-003): NOTIFIED → (re)RESERVED → SETTLED; settle is only legal from RESERVED; nothing moves
 * once SETTLED/REFUSED/CLOSED. Money is eurocents.
 */
@Service
@Transactional
public class GlobalCoreService {

    private static final Set<String> TERMINAL = Set.of("SETTLED", "REFUSED", "CLOSED");

    private final PolicyRepository policies;
    private final ClaimRepository claims;

    public GlobalCoreService(PolicyRepository policies, ClaimRepository claims) {
        this.policies = policies;
        this.claims = claims;
    }

    @Transactional(readOnly = true)
    public Optional<PolicyEntity> findPolicy(String policyNumber) {
        return policies.findById(policyNumber);
    }

    @Transactional(readOnly = true)
    public Optional<ClaimEntity> findClaim(String claimNumber) {
        return claims.findByClaimNumber(claimNumber);
    }

    /** FNOL: the policy must exist AND cover the peril (coverage check), else a fault. status = NOTIFIED. */
    public ClaimEntity createClaim(String policyNumber, LocalDate lossDate, String peril, String claimantName) {
        PolicyEntity policy = policies.findById(policyNumber)
                .orElseThrow(() -> new LegacyException("NOT_FOUND: policy " + policyNumber));
        if (!policy.getPerils().contains(peril)) {
            throw new LegacyException("NOT_COVERED: policy " + policyNumber + " does not cover peril " + peril);
        }
        ClaimEntity c = new ClaimEntity();
        c.setPolicyNumber(policyNumber);
        c.setStatus("NOTIFIED");
        c.setLossDate(lossDate);
        c.setPeril(peril);
        c.setClaimantName(claimantName);
        c.setRegisteredAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        // claim_number is NOT NULL UNIQUE and needs the generated id → insert with a unique temp, then set.
        c.setClaimNumber("TMP-" + System.nanoTime());
        c = claims.saveAndFlush(c);
        c.setClaimNumber(String.format("CLM-%d-%06d", LocalDate.now().getYear(), c.getId()));
        return claims.save(c);
    }

    /** Set/adjust the reserve → status RESERVED. Legal from any non-terminal state. */
    public ClaimEntity reserve(String claimNumber, long amountMinor) {
        ClaimEntity c = require(claimNumber);
        if (TERMINAL.contains(c.getStatus())) {
            throw new LegacyException("ILLEGAL_TRANSITION: cannot reserve a claim in state " + c.getStatus());
        }
        c.setReserveMinor(amountMinor);
        c.setStatus("RESERVED");
        return claims.save(c);
    }

    /** Settle → status SETTLED. Legal ONLY from RESERVED (settle-before-reserve is rejected). */
    public ClaimEntity settle(String claimNumber, long amountMinor) {
        ClaimEntity c = require(claimNumber);
        if (!"RESERVED".equals(c.getStatus())) {
            throw new LegacyException("ILLEGAL_TRANSITION: cannot settle a claim in state " + c.getStatus());
        }
        c.setSettlementMinor(amountMinor);
        c.setStatus("SETTLED");
        return claims.save(c);
    }

    private ClaimEntity require(String claimNumber) {
        return claims.findByClaimNumber(claimNumber)
                .orElseThrow(() -> new LegacyException("NOT_FOUND: claim " + claimNumber));
    }
}
