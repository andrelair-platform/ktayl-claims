package com.andrelair.ktayl.claims.readmodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L1 — the CDC decode. Fixtures are REAL Debezium {@code gc_claim} records captured off NATS
 * ({@code claims-cdc.globalcore.gc_claim}), so this validates the projector's wire-format assumption
 * against the actual collaborator (mock discipline: the projection code is otherwise only ever fed
 * mocks). Covers the schema-envelope unwrap + the Debezium temporal/money type decode.
 */
class CdcClaimMapperTest {

    private final ObjectMapper json = new ObjectMapper();

    // Exactly the value observed on the stream for CLM-2026-000004 (JSON-converter envelope form).
    private static final String WRAPPED = """
        {"schema":{"type":"struct","name":"claims-cdc.globalcore.gc_claim.Value"},
         "payload":{"id":4,"claim_number":"CLM-2026-000004","policy_number":"POL-PROP-0001",
           "status":"NOTIFIED","loss_date":20716,"peril":"FIRE","claimant_name":"CDC Proof Ltd",
           "reserve_minor":null,"settlement_minor":null,
           "registered_at":1790691997699328,"updated_at":1790691997699363,"__deleted":"false"}}
        """;

    private JsonNode parse(String s) throws Exception {
        return json.readTree(s);
    }

    @Test
    void unwrapsTheSchemaEnvelope() throws Exception {
        JsonNode payload = CdcClaimMapper.payloadOf(parse(WRAPPED));
        assertThat(CdcClaimMapper.claimNumber(payload)).isEqualTo("CLM-2026-000004");
    }

    @Test
    void decodesEveryFieldAndDebeziumTypes() throws Exception {
        JsonNode payload = CdcClaimMapper.payloadOf(parse(WRAPPED));
        ClaimReadEntity e = new ClaimReadEntity();
        CdcClaimMapper.applyTo(e, payload);

        var claim = e.toClaim();
        assertThat(claim.claimNumber()).isEqualTo("CLM-2026-000004");
        assertThat(claim.policyNumber()).isEqualTo("POL-PROP-0001");
        assertThat(claim.status().name()).isEqualTo("NOTIFIED");
        assertThat(claim.peril()).isEqualTo("FIRE");
        assertThat(claim.claimantName()).isEqualTo("CDC Proof Ltd");
        // io.debezium.time.Date (days since epoch) → the FNOL's lossDate 2026-09-20
        assertThat(claim.lossDate()).isEqualTo(LocalDate.of(2026, 9, 20));
        // io.debezium.time.MicroTimestamp (micros since epoch) → exact Instant
        assertThat(claim.registeredAt()).isEqualTo(Instant.ofEpochSecond(1790691997L, 699_328_000L));
        assertThat(e.isDeleted()).isFalse();
    }

    @Test
    void nullMoneyStaysNull() throws Exception {
        ClaimReadEntity e = new ClaimReadEntity();
        CdcClaimMapper.applyTo(e, CdcClaimMapper.payloadOf(parse(WRAPPED)));
        // reserve/settlement not set yet on a NOTIFIED claim
        assertThat(e.getSourceUpdatedAt()).isNotNull();
    }

    @Test
    void tombstoneRewriteIsADelete() throws Exception {
        String deleted = WRAPPED.replace("\"__deleted\":\"false\"", "\"__deleted\":\"true\"");
        JsonNode payload = CdcClaimMapper.payloadOf(parse(deleted));
        assertThat(CdcClaimMapper.isDelete(payload)).isTrue();
        ClaimReadEntity e = new ClaimReadEntity();
        CdcClaimMapper.applyTo(e, payload);
        assertThat(e.isDeleted()).isTrue();
    }

    @Test
    void isNewerGuardsAgainstStaleRedelivery() throws Exception {
        JsonNode payload = CdcClaimMapper.payloadOf(parse(WRAPPED));
        ClaimReadEntity existing = new ClaimReadEntity();
        CdcClaimMapper.applyTo(existing, payload);
        // same updated_at → not older → treated as apply-able (idempotent, >=)
        assertThat(CdcClaimMapper.isNewer(payload, existing)).isTrue();

        // an OLDER updated_at must be rejected
        String older = WRAPPED.replace("\"updated_at\":1790691997699363", "\"updated_at\":1790691997000000");
        assertThat(CdcClaimMapper.isNewer(CdcClaimMapper.payloadOf(parse(older)), existing)).isFalse();
    }
}
