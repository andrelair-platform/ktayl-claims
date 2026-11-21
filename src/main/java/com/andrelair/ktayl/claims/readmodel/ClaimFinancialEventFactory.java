package com.andrelair.ktayl.claims.readmodel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the {@code insurance.claims.financial} event(s) implied by a reserve/settlement movement on a
 * projected claim (CLM-FIN-02, ADR-021). The wire shape matches what the ktayl-core {@code claimsgl} module
 * consumes ({@code ClaimFinancialEvent}): snake_case keys, {@code amount_minor} integer eurocents (SIGNED for
 * a reserve movement), {@code occurred_on} a date.
 *
 * <ul>
 *   <li><b>reserve-set</b> — emitted when {@code reserve_minor} moved; {@code amount_minor} = the signed
 *       movement {@code new − old} (0 → no event).</li>
 *   <li><b>payment-executed</b> — emitted when {@code settlement_minor} increased; {@code amount_minor} =
 *       the positive increment (a decrease/reversal is skipped in this slice).</li>
 * </ul>
 *
 * The {@code event_id} is <b>deterministic</b> ({@code claimNumber:type:sourceUpdatedMillis}) so a
 * redelivered CDC record yields the same id → the outbox UNIQUE (and the consumer) dedupe end-to-end.
 */
final class ClaimFinancialEventFactory {

    static final String SUBJECT = "insurance.claims.financial";
    static final String TYPE_RESERVE_SET = "reserve-set";
    static final String TYPE_PAYMENT_EXECUTED = "payment-executed";
    private static final String CURRENCY = "EUR"; // the IS is single-currency (claim_read carries no currency)

    private final ObjectMapper mapper;

    ClaimFinancialEventFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** One financial event to emit (the deterministic id + the serialized payload), or none. */
    record Emission(String eventId, String json) {}

    /** The reserve movement, if {@code newReserve != oldReserve}. null/absent amounts are treated as 0. */
    Emission reserveMovement(ClaimReadEntity claim, Long oldReserveMinor) {
        long oldR = oldReserveMinor == null ? 0L : oldReserveMinor;
        long newR = claim.getReserveMinor() == null ? 0L : claim.getReserveMinor();
        long movement = newR - oldR;
        if (movement == 0L) {
            return null;
        }
        return build(claim, TYPE_RESERVE_SET, movement);
    }

    /** The payment increment, if {@code settlement_minor} increased. A decrease/reversal is skipped. */
    Emission paymentMovement(ClaimReadEntity claim, Long oldSettlementMinor) {
        long oldS = oldSettlementMinor == null ? 0L : oldSettlementMinor;
        long newS = claim.getSettlementMinor() == null ? 0L : claim.getSettlementMinor();
        long increment = newS - oldS;
        if (increment <= 0L) {
            return null;
        }
        return build(claim, TYPE_PAYMENT_EXECUTED, increment);
    }

    private Emission build(ClaimReadEntity claim, String type, long amountMinor) {
        long sourceMillis = claim.getSourceUpdatedAt() == null ? 0L
                : claim.getSourceUpdatedAt().toInstant().toEpochMilli();
        String eventId = claim.getClaimNumber() + ":" + type + ":" + sourceMillis;
        LocalDate occurredOn = claim.getSourceUpdatedAt() == null
                ? LocalDate.now(ZoneOffset.UTC)
                : claim.getSourceUpdatedAt().atZoneSameInstant(ZoneOffset.UTC).toLocalDate();

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_id", eventId);
        event.put("type", type);
        event.put("claim_number", claim.getClaimNumber());
        event.put("policy_number", claim.getPolicyNumber());
        event.put("amount_minor", amountMinor);
        event.put("currency", CURRENCY);
        event.put("occurred_on", occurredOn.toString());
        try {
            return new Emission(eventId, mapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize claim financial event", e);
        }
    }

    UUID newId() {
        return UUID.randomUUID();
    }
}
