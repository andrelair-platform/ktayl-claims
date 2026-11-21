package com.andrelair.ktayl.claims.readmodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L1 — the claim financial-event money logic (CLM-FIN-02): the reserve/settlement MOVEMENT math, the wire
 * shape ktayl-core consumes, and the deterministic event id. Pure (no DB, no broker) — the DB-backed
 * atomic-outbox write is proven in {@link ClaimProjectionServiceIT}.
 */
class ClaimFinancialEventFactoryTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ClaimFinancialEventFactory factory = new ClaimFinancialEventFactory(json);

    private ClaimReadEntity claim(Long reserveMinor, Long settlementMinor) {
        ClaimReadEntity e = new ClaimReadEntity();
        e.setClaimId(55L);
        e.setClaimNumber("CLM-2026-000055");
        e.setPolicyNumber("POL-PROP-0001");
        e.setReserveMinor(reserveMinor);
        e.setSettlementMinor(settlementMinor);
        e.setSourceUpdatedAt(OffsetDateTime.of(2026, 3, 1, 9, 0, 0, 0, ZoneOffset.UTC));
        return e;
    }

    @Test
    void reserve_increase_emits_the_signed_movement_with_core_wire_shape() throws Exception {
        var emission = factory.reserveMovement(claim(500_000L, null), null); // 0 → 500000
        assertThat(emission).isNotNull();
        JsonNode e = json.readTree(emission.json());
        assertThat(e.get("type").asText()).isEqualTo("reserve-set");
        assertThat(e.get("claim_number").asText()).isEqualTo("CLM-2026-000055");
        assertThat(e.get("policy_number").asText()).isEqualTo("POL-PROP-0001");
        assertThat(e.get("amount_minor").asLong()).isEqualTo(500_000L);
        assertThat(e.get("currency").asText()).isEqualTo("EUR");
        assertThat(e.get("occurred_on").asText()).isEqualTo("2026-03-01");
        assertThat(e.get("event_id").asText()).isEqualTo(emission.eventId());
    }

    @Test
    void reserve_adjustment_emits_only_the_delta() throws Exception {
        var up = factory.reserveMovement(claim(700_000L, null), 500_000L);   // +200000
        assertThat(json.readTree(up.json()).get("amount_minor").asLong()).isEqualTo(200_000L);
        var down = factory.reserveMovement(claim(400_000L, null), 500_000L); // −100000 (a release)
        assertThat(json.readTree(down.json()).get("amount_minor").asLong()).isEqualTo(-100_000L);
    }

    @Test
    void no_reserve_change_emits_nothing() {
        assertThat(factory.reserveMovement(claim(500_000L, null), 500_000L)).isNull();
        assertThat(factory.reserveMovement(claim(null, null), null)).isNull();
    }

    @Test
    void settlement_increase_emits_a_positive_payment_a_decrease_emits_nothing() throws Exception {
        var pay = factory.paymentMovement(claim(500_000L, 300_000L), null); // 0 → 300000
        assertThat(json.readTree(pay.json()).get("type").asText()).isEqualTo("payment-executed");
        assertThat(json.readTree(pay.json()).get("amount_minor").asLong()).isEqualTo(300_000L);
        assertThat(factory.paymentMovement(claim(500_000L, 200_000L), 300_000L)).isNull(); // decrease → skip
    }

    @Test
    void event_id_is_deterministic_per_movement() {
        var a = factory.reserveMovement(claim(500_000L, null), null);
        var b = factory.reserveMovement(claim(500_000L, null), null); // same inputs → same id (dedup)
        assertThat(a.eventId()).isEqualTo(b.eventId());
        assertThat(a.eventId()).isEqualTo("CLM-2026-000055:reserve-set:" + OffsetDateTime
                .of(2026, 3, 1, 9, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli());
    }
}
