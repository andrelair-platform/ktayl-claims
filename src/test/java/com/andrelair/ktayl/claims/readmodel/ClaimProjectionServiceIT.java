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
@Import({ClaimProjectionService.class, ObjectMapper.class})
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

    /** A CDC record for source id 42, business key CLM-2026-000042. */
    private JsonNode cdc(String status, long updatedAt, boolean deleted) throws Exception {
        return cdc(42, "CLM-2026-000042", status, updatedAt, deleted);
    }

    private JsonNode cdc(long id, String claimNumber, String status, long updatedAt, boolean deleted)
            throws Exception {
        return json.readTree("""
            {"payload":{"id":%d,"claim_number":"%s","policy_number":"POL-PROP-0001",
              "status":"%s","loss_date":20716,"peril":"FIRE","claimant_name":"IT Co",
              "reserve_minor":null,"settlement_minor":null,
              "registered_at":1790691997699328,"updated_at":%d,"__deleted":"%s"}}
            """.formatted(id, claimNumber, status, updatedAt, deleted));
    }

    @Test
    void insertThenUpdateThenStaleRedelivery() throws Exception {
        projection.project(cdc("NOTIFIED", 1000, false));
        assertThat(repo.findByClaimNumber("CLM-2026-000042").orElseThrow().toClaim().status().name())
                .isEqualTo("NOTIFIED");

        // a newer event (RESERVED) upserts in place — no duplicate row
        projection.project(cdc("RESERVED", 2000, false));
        assertThat(repo.count()).isEqualTo(1);
        assertThat(repo.findByClaimNumber("CLM-2026-000042").orElseThrow().toClaim().status().name())
                .isEqualTo("RESERVED");

        // an OLDER redelivery (updated_at 1500 < 2000) must NOT regress the row
        projection.project(cdc("NOTIFIED", 1500, false));
        assertThat(repo.findByClaimNumber("CLM-2026-000042").orElseThrow().toClaim().status().name())
                .isEqualTo("RESERVED");
    }

    @Test
    void tombstoneMarksDeleted() throws Exception {
        projection.project(cdc("NOTIFIED", 1000, false));
        projection.project(cdc("NOTIFIED", 2000, true));
        assertThat(repo.findByClaimNumber("CLM-2026-000042").orElseThrow().isDeleted()).isTrue();
    }

    @Test
    void tmpToClmRenameConvergesOnOneRow() throws Exception {
        // The legacy createClaim: INSERT(TMP-…) then UPDATE(CLM-…) of the SAME source row (id=7).
        // Keying on the source id must upsert ONE row (claim_number transitions in place), NOT leave a
        // TMP orphan — the bug this rekey fixes.
        projection.project(cdc(7, "TMP-99999999", "NOTIFIED", 1000, false));
        projection.project(cdc(7, "CLM-2026-000007", "NOTIFIED", 1001, false));

        assertThat(repo.count()).isEqualTo(1);
        assertThat(repo.findByClaimNumber("TMP-99999999")).isEmpty();
        assertThat(repo.findByClaimNumber("CLM-2026-000007").orElseThrow().getClaimId()).isEqualTo(7L);
    }

    // --- CLM-FIN-02: financial-event emission on reserve/settlement movement ---

    private JsonNode cdcMoney(long updatedAt, Long reserveMinor, Long settlementMinor) throws Exception {
        return json.readTree("""
            {"payload":{"id":55,"claim_number":"CLM-2026-000055","policy_number":"POL-PROP-0001",
              "status":"RESERVED","loss_date":20716,"peril":"FIRE","claimant_name":"IT Co",
              "reserve_minor":%s,"settlement_minor":%s,
              "registered_at":1790691997699328,"updated_at":%d,"__deleted":"false"}}
            """.formatted(String.valueOf(reserveMinor), String.valueOf(settlementMinor), updatedAt));
    }

    @Test
    void emitsAReserveMovementEvent() throws Exception {
        // reserve 0 → 500000 : one reserve-set event with the signed movement; wire shape matches core
        projection.project(cdcMoney(1000, 500_000L, null));
        var pending = outbox.findByStatusOrderByCreatedAt("pending", org.springframework.data.domain.Limit.of(10));
        assertThat(pending).hasSize(1);
        JsonNode e = json.readTree(pending.get(0).getPayload());
        assertThat(e.get("type").asText()).isEqualTo("reserve-set");
        assertThat(e.get("claim_number").asText()).isEqualTo("CLM-2026-000055");
        assertThat(e.get("policy_number").asText()).isEqualTo("POL-PROP-0001");
        assertThat(e.get("amount_minor").asLong()).isEqualTo(500_000L);
        assertThat(e.get("currency").asText()).isEqualTo("EUR");
        assertThat(e.get("event_id").asText()).isEqualTo(pending.get(0).getEventId());

        // adjust 500000 → 700000 : a second event carrying only the +200000 delta
        projection.project(cdcMoney(2000, 700_000L, null));
        var after = outbox.findByStatusOrderByCreatedAt("pending", org.springframework.data.domain.Limit.of(10));
        assertThat(after).hasSize(2);
        assertThat(json.readTree(after.get(1).getPayload()).get("amount_minor").asLong()).isEqualTo(200_000L);
    }

    @Test
    void emitsAPaymentEventAndIsIdempotentOnRedelivery() throws Exception {
        projection.project(cdcMoney(1000, 500_000L, null));       // reserve-set
        projection.project(cdcMoney(2000, 500_000L, 300_000L));   // settlement 0 → 300000 : payment-executed
        var pending = outbox.findByStatusOrderByCreatedAt("pending", org.springframework.data.domain.Limit.of(10));
        assertThat(pending).extracting(o -> json.readTree(o.getPayload()).get("type").asText())
                .contains("reserve-set", "payment-executed");
        long paymentCount = pending.stream()
                .filter(o -> {
                    try { return "payment-executed".equals(json.readTree(o.getPayload()).get("type").asText()); }
                    catch (Exception ex) { return false; }
                }).count();
        assertThat(paymentCount).isEqualTo(1);

        // a stale redelivery of the settlement CDC record (same updated_at) must NOT re-enqueue
        projection.project(cdcMoney(2000, 500_000L, 300_000L));
        assertThat(outbox.count()).isEqualTo(pending.size()); // unchanged
    }

    @org.springframework.beans.factory.annotation.Autowired
    ClaimEventOutboxRepository outbox;
}
