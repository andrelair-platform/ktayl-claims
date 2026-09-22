package com.andrelair.ktayl.claims.application;

import com.andrelair.ktayl.claims.domain.Claim;
import com.andrelair.ktayl.claims.domain.ClaimStatus;
import com.andrelair.ktayl.claims.domain.OutOfCoverException;
import com.andrelair.ktayl.claims.legacy.GlobalCorePort;
import com.andrelair.ktayl.claims.legacy.PolicyView;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** L1 — the FNOL use case: happy path + the S003 failure ACs (out-of-cover, idempotency). */
class FnolServiceTest {

    private final AtomicInteger createCalls = new AtomicInteger();

    /** A hand-rolled fake legacy: one Property policy, counts createClaim calls. */
    private final GlobalCorePort legacy = new GlobalCorePort() {
        @Override
        public Optional<PolicyView> findPolicy(String policyNumber) {
            if (!"POL-1".equals(policyNumber)) {
                return Optional.empty();
            }
            return Optional.of(new PolicyView("POL-1", "Acme",
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), Set.of("FIRE", "THEFT")));
        }

        @Override
        public String createClaim(String p, LocalDate d, String peril, String who) {
            return "CLM-1-%06d".formatted(createCalls.incrementAndGet());
        }

        // S004 lifecycle methods — not exercised by the FNOL tests
        @Override
        public java.util.Optional<com.andrelair.ktayl.claims.domain.Claim> findClaim(String claimNumber) {
            return java.util.Optional.empty();
        }

        @Override
        public void reserve(String claimNumber, java.math.BigDecimal amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void settle(String claimNumber, java.math.BigDecimal amount) {
            throw new UnsupportedOperationException();
        }
    };

    private final FnolService service = new FnolService(legacy, new InMemoryIdempotencyStore());

    private FnolCommand cmd(String key, String policy, LocalDate loss, String peril) {
        return new FnolCommand(key, policy, loss, peril, "Jean Dupont", "kitchen fire");
    }

    @Test
    void registersACoveredClaim() {
        Claim claim = service.register(cmd("k1", "POL-1", LocalDate.of(2026, 6, 1), "FIRE"));
        assertThat(claim.status()).isEqualTo(ClaimStatus.NOTIFIED);
        assertThat(claim.claimNumber()).startsWith("CLM-1-");
        assertThat(claim.policyNumber()).isEqualTo("POL-1");
    }

    @Test
    void rejectsUnknownPolicy() {
        assertThatThrownBy(() -> service.register(cmd("k2", "POL-X", LocalDate.of(2026, 6, 1), "FIRE")))
                .isInstanceOf(OutOfCoverException.class)
                .hasMessageContaining("unknown policy");
    }

    @Test
    void rejectsUncoveredPeril() {
        assertThatThrownBy(() -> service.register(cmd("k3", "POL-1", LocalDate.of(2026, 6, 1), "FLOOD")))
                .isInstanceOf(OutOfCoverException.class)
                .hasMessageContaining("peril not covered");
    }

    @Test
    void rejectsLossOutsideCoverPeriod() {
        assertThatThrownBy(() -> service.register(cmd("k4", "POL-1", LocalDate.of(2025, 6, 1), "FIRE")))
                .isInstanceOf(OutOfCoverException.class)
                .hasMessageContaining("outside cover");
    }

    @Test
    void isIdempotent_sameKeyReturnsSameClaim_createsOnce() {
        FnolCommand c = cmd("dup", "POL-1", LocalDate.of(2026, 6, 1), "FIRE");
        Claim first = service.register(c);
        Claim second = service.register(c);

        assertThat(second.claimNumber()).isEqualTo(first.claimNumber());
        assertThat(createCalls.get()).isEqualTo(1);   // the legacy create happened exactly once
    }
}
