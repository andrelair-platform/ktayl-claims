package com.andrelair.ktayl.claims.readmodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L2 — the projector's upsert against a REAL Postgres (Flyway-migrated), proving the properties a
 * mock can't: idempotent insert→update, last-writer-wins on a stale redelivery, and tombstone delete.
 * This is the integration backing behind the CDC boundary (testing.md: every mocked boundary needs a
 * real integration or contract behind it).
 */
@DataJpaTest
@ActiveProfiles("readmodel")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ClaimProjectionService.class})
@Testcontainers
class ClaimProjectionServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("nats.url", () -> "nats://unused:4222");   // no NATS bean in this slice
    }

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    ClaimProjectionService projection;
    @Autowired
    ClaimReadRepository repo;

    private JsonNode cdc(String status, long updatedAt, boolean deleted) throws Exception {
        return json.readTree("""
            {"payload":{"claim_number":"CLM-2026-000042","policy_number":"POL-PROP-0001",
              "status":"%s","loss_date":20716,"peril":"FIRE","claimant_name":"IT Co",
              "reserve_minor":null,"settlement_minor":null,
              "registered_at":1790691997699328,"updated_at":%d,"__deleted":"%s"}}
            """.formatted(status, updatedAt, deleted));
    }

    @Test
    void insertThenUpdateThenStaleRedelivery() throws Exception {
        projection.project(cdc("NOTIFIED", 1000, false));
        assertThat(repo.findById("CLM-2026-000042").orElseThrow().toClaim().status().name())
                .isEqualTo("NOTIFIED");

        // a newer event (RESERVED) upserts in place — no duplicate row
        projection.project(cdc("RESERVED", 2000, false));
        assertThat(repo.count()).isEqualTo(1);
        assertThat(repo.findById("CLM-2026-000042").orElseThrow().toClaim().status().name())
                .isEqualTo("RESERVED");

        // an OLDER redelivery (updated_at 1500 < 2000) must NOT regress the row
        projection.project(cdc("NOTIFIED", 1500, false));
        assertThat(repo.findById("CLM-2026-000042").orElseThrow().toClaim().status().name())
                .isEqualTo("RESERVED");
    }

    @Test
    void tombstoneMarksDeleted() throws Exception {
        projection.project(cdc("NOTIFIED", 1000, false));
        projection.project(cdc("NOTIFIED", 2000, true));
        assertThat(repo.findById("CLM-2026-000042").orElseThrow().isDeleted()).isTrue();
    }
}
