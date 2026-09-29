package com.andrelair.ktayl.claims.readmodel;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies one {@code gc_claim} CDC record to the read-model — an idempotent, last-writer-wins upsert
 * (JetStream delivery is at-least-once, so a redelivery/out-of-order event must be a no-op or a newer
 * overwrite, never a regression). Separated from the NATS wiring ({@link CdcProjector}) so it tests
 * against a real Postgres without a broker.
 */
@Service
@Profile("readmodel")
public class ClaimProjectionService {

    private static final Logger log = LoggerFactory.getLogger(ClaimProjectionService.class);

    private final ClaimReadRepository repo;

    public ClaimProjectionService(ClaimReadRepository repo) {
        this.repo = repo;
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
        CdcClaimMapper.applyTo(entity, payload);
        repo.save(entity);
        log.debug("projected claim id={} number={} status={} deleted={}",
                sourceId, CdcClaimMapper.claimNumber(payload),
                payload.path("status").asText(null), CdcClaimMapper.isDelete(payload));
    }
}
