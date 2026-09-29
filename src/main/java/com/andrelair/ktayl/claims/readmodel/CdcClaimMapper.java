package com.andrelair.ktayl.claims.readmodel;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Pure mapping of a Debezium {@code gc_claim} CDC record onto a {@link ClaimReadEntity} — the one
 * piece with real logic (decoding Debezium's wire types), kept free of NATS/JPA so it unit-tests
 * directly (mock discipline: the projector's assumption about the CDC shape is validated here).
 *
 * <p>Wire types (Debezium): {@code loss_date} = {@code io.debezium.time.Date} (days since epoch, int);
 * {@code registered_at}/{@code updated_at} = {@code io.debezium.time.MicroTimestamp} (micros since
 * epoch, long); {@code *_minor} = eurocents (long, nullable); {@code __deleted} = "true"/"false".
 * The value may arrive wrapped as {@code {schema, payload}} (default JSON converter) or flat — call
 * {@link #payloadOf} first.
 */
public final class CdcClaimMapper {

    private CdcClaimMapper() {
    }

    /** Unwrap the Debezium JSON-converter envelope: return {@code .payload} if present, else the node. */
    public static JsonNode payloadOf(JsonNode value) {
        JsonNode payload = value.get("payload");
        return payload != null && !payload.isNull() ? payload : value;
    }

    /** The claim's business key — the projection's primary key. */
    public static String claimNumber(JsonNode payload) {
        return text(payload, "claim_number");
    }

    /** True when the CDC record is a delete (tombstone rewrite: {@code __deleted == "true"}). */
    public static boolean isDelete(JsonNode payload) {
        JsonNode d = payload.get("__deleted");
        return d != null && "true".equals(d.asText());
    }

    /** Apply the CDC payload onto the entity (used for both insert and update — an idempotent upsert). */
    public static void applyTo(ClaimReadEntity e, JsonNode payload) {
        e.setClaimNumber(claimNumber(payload));
        e.setPolicyNumber(text(payload, "policy_number"));
        e.setStatus(text(payload, "status"));
        e.setLossDate(epochDay(payload, "loss_date"));
        e.setPeril(text(payload, "peril"));
        e.setClaimantName(text(payload, "claimant_name"));
        e.setReserveMinor(longOrNull(payload, "reserve_minor"));
        e.setSettlementMinor(longOrNull(payload, "settlement_minor"));
        e.setRegisteredAt(microTimestamp(payload, "registered_at"));
        e.setSourceUpdatedAt(microOffset(payload, "updated_at"));
        e.setDeleted(isDelete(payload));
    }

    /**
     * Last-writer-wins guard: is {@code incoming} newer than what we already stored? A retried/
     * out-of-order CDC delivery (JetStream is at-least-once) must not overwrite a fresher row.
     */
    public static boolean isNewer(JsonNode incomingPayload, ClaimReadEntity existing) {
        OffsetDateTime incoming = microOffset(incomingPayload, "updated_at");
        OffsetDateTime current = existing.getSourceUpdatedAt();
        return current == null || incoming == null || !incoming.isBefore(current);
    }

    private static String text(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Long longOrNull(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return v == null || v.isNull() ? null : v.asLong();
    }

    private static LocalDate epochDay(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return v == null || v.isNull() ? null : LocalDate.ofEpochDay(v.asLong());
    }

    private static Instant microTimestamp(JsonNode n, String f) {
        JsonNode v = n.get(f);
        if (v == null || v.isNull()) {
            return null;
        }
        long micros = v.asLong();
        return Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L),
                Math.floorMod(micros, 1_000_000L) * 1_000L);
    }

    private static OffsetDateTime microOffset(JsonNode n, String f) {
        Instant i = microTimestamp(n, f);
        return i == null ? null : i.atOffset(ZoneOffset.UTC);
    }
}
