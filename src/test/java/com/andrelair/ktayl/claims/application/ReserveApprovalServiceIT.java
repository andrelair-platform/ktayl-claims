package com.andrelair.ktayl.claims.application;

import com.andrelair.ktayl.claims.domain.Claim;
import com.andrelair.ktayl.claims.domain.ClaimStatus;
import com.andrelair.ktayl.claims.legacy.GlobalCorePort;
import com.andrelair.ktayl.claims.legacy.PolicyView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L2 — the reserve approval workflow against a REAL Postgres (Flyway V4), proving the migration + JPA mapping
 * + the four-eyes transitions persist (CLM-FIN-03a). The legacy is a small in-test stand-in (the DB, not the
 * SOAP legacy, is what this proves). Runs in CI (Docker present).
 */
@DataJpaTest
@ActiveProfiles("readmodel")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReserveApprovalServiceIT.TestLegacy.class})
@Testcontainers
class ReserveApprovalServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("nats.url", () -> "nats://unused:4222");
    }

    @Autowired ReserveApprovalRepository repo;
    @Autowired GlobalCorePort legacy;

    private ReserveApprovalService service() {
        return new ReserveApprovalService(repo, legacy, new BigDecimal("100000"));
    }

    @Test
    void anAboveAuthorityReserveIsPersistedPendingThenApprovedAppliesToTheLegacy() {
        var svc = service();
        var adjuster = new Actor("adj.sophie", Authority.ADJUSTER);
        var senior = new Actor("sen.marc", Authority.SENIOR);

        var out = svc.submit(adjuster, "CLM-IT-1", new BigDecimal("60000")); // above the 50k adjuster cap
        assertThat(out.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(repo.findByStatusOrderByRequestedAt("pending")).hasSize(1);

        Claim applied = svc.approve(senior, out.approvalId());
        assertThat(applied.status()).isEqualTo(ClaimStatus.RESERVED);
        assertThat(repo.findById(out.approvalId()).orElseThrow().getStatus()).isEqualTo("approved");
        assertThat(repo.findByStatusOrderByRequestedAt("pending")).isEmpty();
        assertThat(((TestLegacy.Stub) legacy).reservedAmount).isEqualByComparingTo("60000");
    }

    @Test
    void selfApprovalIsRejectedAndTheRequestStaysPending() {
        var svc = service();
        var senior = new Actor("sen.marc", Authority.SENIOR);
        var out = svc.submit(senior, "CLM-IT-2", new BigDecimal("200000")); // large loss → pending even for senior
        assertThatThrownBy(() -> svc.approve(senior, out.approvalId()))
                .isInstanceOf(com.andrelair.ktayl.claims.domain.AuthorityException.class);
        assertThat(repo.findById(out.approvalId()).orElseThrow().getStatus()).isEqualTo("pending");
    }

    @TestConfiguration
    static class TestLegacy {
        @Bean @Profile("readmodel")
        GlobalCorePort legacy() { return new Stub(); }

        static final class Stub implements GlobalCorePort {
            BigDecimal reservedAmount;
            @Override public Optional<PolicyView> findPolicy(String p) { return Optional.empty(); }
            @Override public String createClaim(String p, LocalDate d, String peril, String name) { return "CLM"; }
            @Override public Optional<Claim> findClaim(String claimNumber) {
                return Optional.of(new Claim(claimNumber, "POL-1", ClaimStatus.RESERVED,
                        LocalDate.of(2026, 1, 1), "FIRE", "X", Instant.now()));
            }
            @Override public void reserve(String claimNumber, BigDecimal amount) { this.reservedAmount = amount; }
            @Override public void settle(String claimNumber, BigDecimal amount) {}
        }
    }
}
