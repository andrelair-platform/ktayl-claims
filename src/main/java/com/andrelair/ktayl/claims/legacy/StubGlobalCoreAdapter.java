package com.andrelair.ktayl.claims.legacy;

import com.andrelair.ktayl.claims.domain.Claim;
import com.andrelair.ktayl.claims.domain.ClaimNotFoundException;
import com.andrelair.ktayl.claims.domain.ClaimStatus;
import com.andrelair.ktayl.claims.domain.IllegalTransitionException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LOCAL / DEV stand-in for GlobalCore until the real SOAP + Oracle legacy is provisioned (S001/S002).
 * Seeded with a small <strong>Property</strong> policy book, and it keeps claim state so the FNOL +
 * lifecycle (reserve/settle) flow runs end-to-end offline — mirroring the legacy's in-DB state machine
 * (S002). Replaced by the SOAP adapter (spring-ws) once the legacy is up — activate that with the
 * {@code soap} profile; this stub is the default (any profile except {@code soap}).
 */
@Component
@Profile("!soap")
public class StubGlobalCoreAdapter implements GlobalCorePort {

    /** Mutable legacy-side claim record (what the Oracle CLAIM row would hold). */
    private static final class StoredClaim {
        final String policyNumber;
        final LocalDate lossDate;
        final String peril;
        final String claimantName;
        final Instant registeredAt;
        ClaimStatus status;

        StoredClaim(String policyNumber, LocalDate lossDate, String peril, String claimantName) {
            this.policyNumber = policyNumber;
            this.lossDate = lossDate;
            this.peril = peril;
            this.claimantName = claimantName;
            this.registeredAt = Instant.now();
            this.status = ClaimStatus.NOTIFIED;
        }

        Claim toClaim(String claimNumber) {
            return new Claim(claimNumber, policyNumber, status, lossDate, peril, claimantName, registeredAt);
        }
    }

    private final Map<String, PolicyView> policies = new HashMap<>();
    private final Map<String, StoredClaim> claims = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(0);

    public StubGlobalCoreAdapter() {
        seed("POL-PROP-0001", "Durand SARL",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                Set.of("FIRE", "WATER_DAMAGE", "STORM", "THEFT"));
        seed("POL-PROP-0002", "Boulangerie Lemoine",
                LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28),
                Set.of("FIRE", "WATER_DAMAGE"));
    }

    private void seed(String number, String holder, LocalDate start, LocalDate end, Set<String> perils) {
        policies.put(number, new PolicyView(number, holder, start, end, perils));
    }

    @Override
    public Optional<PolicyView> findPolicy(String policyNumber) {
        return Optional.ofNullable(policies.get(policyNumber));
    }

    @Override
    public String createClaim(String policyNumber, LocalDate lossDate, String peril, String claimantName) {
        String claimNumber = "CLM-%d-%06d".formatted(LocalDate.now().getYear(), sequence.incrementAndGet());
        claims.put(claimNumber, new StoredClaim(policyNumber, lossDate, peril, claimantName));
        return claimNumber;
    }

    @Override
    public Optional<Claim> findClaim(String claimNumber) {
        StoredClaim c = claims.get(claimNumber);
        return c == null ? Optional.empty() : Optional.of(c.toClaim(claimNumber));
    }

    @Override
    public void reserve(String claimNumber, BigDecimal amount) {
        StoredClaim c = require(claimNumber);
        // legal to reserve from N / U, or re-adjust while already R (mirrors the Oracle state machine)
        if (c.status != ClaimStatus.NOTIFIED && c.status != ClaimStatus.UNDER_REVIEW
                && c.status != ClaimStatus.RESERVED) {
            throw new IllegalTransitionException(
                    "cannot reserve a claim in state " + c.status);
        }
        c.status = ClaimStatus.RESERVED;
    }

    @Override
    public void settle(String claimNumber, BigDecimal amount) {
        StoredClaim c = require(claimNumber);
        if (c.status != ClaimStatus.RESERVED) {
            throw new IllegalTransitionException(
                    "cannot settle a claim in state " + c.status + " (must be RESERVED)");
        }
        c.status = ClaimStatus.SETTLED;
    }

    private StoredClaim require(String claimNumber) {
        StoredClaim c = claims.get(claimNumber);
        if (c == null) {
            throw new ClaimNotFoundException(claimNumber);
        }
        return c;
    }
}
