package com.andrelair.ktayl.claims.readmodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Applies one {@code gc_claim} CDC record to the read-model — an idempotent, last-writer-wins upsert
 * (JetStream delivery is at-least-once, so a redelivery/out-of-order event must be a no-op or a newer
 * overwrite, never a regression). Separated from the NATS wiring ({@link CdcProjector}) so it tests
 * against a real Postgres without a broker.
 *
 * <p><b>Financial-event emission (CLM-FIN-02, ADR-021).</b> When the applied update MOVES a claim's
 * {@code reserve_minor} / {@code settlement_minor}, it writes an {@code insurance.claims.financial} event to
 * the {@link ClaimEventOutboxEntity outbox} IN THE SAME TRANSACTION as the {@code claim_read} upsert — so the
 * event and the projection commit atomically. {@link ClaimEventPublisher} drains the outbox to NATS. The
 * movement is computed against the PRIOR projected amount (captured before the upsert), which is why
 * emission lives here and keys off the authoritative, CDC-delivered legacy change (not the command path).
 */
@Service
@Profile("readmodel")
public class ClaimProjectionService {

    private static final Logger log = LoggerFactory.getLogger(ClaimProjectionService.class);

    private final ClaimReadRepository repo;
    private final ClaimEventOutboxRepository outbox;
    private final ClaimFinancialEventFactory events;

    public ClaimProjectionService(ClaimReadRepository repo, ClaimEventOutboxRepository outbox, ObjectMapper mapper) {
        this.repo = repo;
        this.outbox = outbox;
        this.events = new ClaimFinancialEventFactory(mapper);
    }

    @Transactional
    public void project(JsonNode value) {
        JsonNode payload = CdcClaimMapper.payloadOf(value);
        Long sourceId = CdcClaimMapper.sourceId(payload);
        if (sourceId == null) {
            log.warn("CDC record with no source id — skipped");
            return;
        }

        // Upsert by the STABLE source id, so the createClaim INSERT(TMP-…) + UPDATE(CLM-…) of the same
        // legacy row converge on ONE read-model row (claim_number transitions in place — no orphan).
        ClaimReadEntity entity = repo.findById(sourceId).orElse(null);
        if (entity != null && !CdcClaimMapper.isNewer(payload, entity)) {
            // A stale redelivery — keep the fresher row (idempotent no-op).
            return;
        }
        if (entity == null) {
            entity = new ClaimReadEntity();
        }
        // Capture the PRIOR projected amounts before the upsert overwrites them — the financial movement
        // is new − old (CLM-FIN-02).
        Long priorReserve = entity.getReserveMinor();
        Long priorSettlement = entity.getSettlementMinor();

        CdcClaimMapper.applyTo(entity, payload);
        repo.save(entity);
        log.debug("projected claim id={} number={} status={} deleted={}",
                sourceId, CdcClaimMapper.claimNumber(payload),
                payload.path("status").asText(null), CdcClaimMapper.isDelete(payload));

        // Emit financial events for any reserve/settlement movement — atomically with the projection (same
        // @Transactional). A tombstone delete never emits. Idempotent by the deterministic event_id.
        if (!CdcClaimMapper.isDelete(payload)) {
            emit(events.reserveMovement(entity, priorReserve));
            emit(events.paymentMovement(entity, priorSettlement));
        }
    }

    private void emit(ClaimFinancialEventFactory.Emission emission) {
        if (emission == null || outbox.existsByEventId(emission.eventId())) {
            return;
        }
        outbox.save(new ClaimEventOutboxEntity(events.newId(), emission.eventId(),
                ClaimFinancialEventFactory.SUBJECT, emission.json(), Instant.now()));
        log.info("queued claim financial event {} ({} bytes)", emission.eventId(), emission.json().length());
    }
}
