package com.andrelair.ktayl.claims.application;

import com.andrelair.ktayl.claims.domain.AuthorityException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L2 — the payment request→approve→execute workflow against a REAL Postgres (Flyway V5), proving the
 * migration + JPA mapping + the three-step transitions persist (CLM-FIN-03b). Runs in CI (Docker present).
 */
@DataJpaTest
@ActiveProfiles("readmodel")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PaymentApprovalServiceIT.TestLegacy.class})
@Testcontainers
class PaymentApprovalServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("nats.url", () -> "nats://unused:4222");
    }

    @Autowired PaymentRequestRepository repo;
    @Autowired GlobalCorePort legacy;

    private PaymentApprovalService service() {
        return new PaymentApprovalService(repo, legacy, new BigDecimal("100000"));
    }

    @Test
    void requestThenApproveThenExecutePersistsEachStepAndPaysOnlyOnExecute() {
        var svc = service();
        var adjuster = new Actor("adj.sophie", Authority.ADJUSTER);
        var seniorA = new Actor("sen.marc", Authority.SENIOR);
        var seniorB = new Actor("sen.lea", Authority.SENIOR);

        var out = svc.submit(adjuster, "CLM-IT-1", new BigDecimal("20000")); // above the 10k adjuster settle cap
        assertThat(out.status()).isEqualTo("PENDING_APPROVAL");

        svc.approve(seniorA, out.requestId());
        assertThat(repo.findById(out.requestId()).orElseThrow().getStatus()).isEqualTo("approved");
        assertThat(((TestLegacy.Stub) legacy).settledAmount).isNull();   // approval does NOT pay

        Claim paid = svc.execute(seniorB, out.requestId());
        assertThat(paid.status()).isEqualTo(ClaimStatus.SETTLED);
        assertThat(repo.findById(out.requestId()).orElseThrow().getStatus()).isEqualTo("executed");
        assertThat(((TestLegacy.Stub) legacy).settledAmount).isEqualByComparingTo("20000");
    }

    @Test
    void theRequesterMayNotExecuteTheirOwnApprovedPayment() {
        var svc = service();
        var seniorA = new Actor("sen.marc", Authority.SENIOR);
        var seniorB = new Actor("sen.lea", Authority.SENIOR);
        var out = svc.submit(seniorA, "CLM-IT-2", new BigDecimal("150000")); // large loss → request even for senior
        svc.approve(seniorB, out.requestId());
        assertThatThrownBy(() -> svc.execute(seniorA, out.requestId())).isInstanceOf(AuthorityException.class);
        assertThat(repo.findById(out.requestId()).orElseThrow().getStatus()).isEqualTo("approved");
    }

    @TestConfiguration
    static class TestLegacy {
        @Bean @Profile("readmodel")
        GlobalCorePort legacy() { return new Stub(); }

        static final class Stub implements GlobalCorePort {
            BigDecimal settledAmount;
            @Override public Optional<PolicyView> findPolicy(String p) { return Optional.empty(); }
            @Override public String createClaim(String p, LocalDate d, String peril, String name) { return "CLM"; }
            @Override public Optional<Claim> findClaim(String claimNumber) {
                return Optional.of(new Claim(claimNumber, "POL-1", ClaimStatus.SETTLED,
                        LocalDate.of(2026, 1, 1), "FIRE", "X", Instant.now()));
            }
            @Override public void reserve(String claimNumber, BigDecimal amount) {}
            @Override public void settle(String claimNumber, BigDecimal amount) { this.settledAmount = amount; }
        }
    }
}
