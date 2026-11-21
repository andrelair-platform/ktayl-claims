package com.andrelair.ktayl.claims.readmodel;

import io.nats.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Drains the claim financial-event outbox to NATS on a schedule (CLM-FIN-02, ADR-021). Publishes each
 * {@code pending} row to JetStream (the subject {@code insurance.claims.financial}, captured by the
 * {@code CLAIMS_EVENTS} stream) and marks it {@code published}. A NATS/stream outage (or the stream not yet
 * provisioned) leaves rows {@code pending} → retried next tick; the read-model projection is never blocked.
 * The ktayl-core {@code claimsgl} module is the durable consumer that turns these into GL postings.
 *
 * <p>JetStream publish (not core NATS fire-and-forget) so the broker acks persistence — reliable delivery to
 * the durable consumer. Idempotent end-to-end: the deterministic {@code event_id} is the consumer's dedup key
 * (and this outbox already deduped on it at enqueue).
 */
@Component
@Profile("readmodel")
public class ClaimEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventPublisher.class);
    private static final int BATCH = 50;

    private final ClaimEventOutboxRepository outbox;
    private final Connection nats;

    public ClaimEventPublisher(ClaimEventOutboxRepository outbox, Connection nats) {
        this.outbox = outbox;
        this.nats = nats;
    }

    @Scheduled(fixedDelayString = "${claims.events.publish-interval-ms:15000}",
            initialDelayString = "${claims.events.publish-initial-ms:10000}")
    public void drain() {
        List<ClaimEventOutboxEntity> batch = outbox.findByStatusOrderByCreatedAt("pending", Limit.of(BATCH));
        if (batch.isEmpty()) {
            return;
        }
        int ok = 0;
        for (ClaimEventOutboxEntity row : batch) {
            UUID id = row.getId();
            try {
                publishOne(id);
                ok++;
            } catch (Exception e) {
                log.warn("claim financial event publish failed for {}, will retry: {}", row.getEventId(), e.getMessage());
                recordFailure(id, e.getMessage());
            }
        }
        log.debug("claim financial outbox drain: {}/{} published", ok, batch.size());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOne(UUID id) throws Exception {
        ClaimEventOutboxEntity row = outbox.findById(id).orElse(null);
        if (row == null || "published".equals(row.getStatus())) {
            return;
        }
        // JetStream publish → the broker persists + acks; throws if no stream captures the subject (then the
        // row stays pending and is retried once CLAIMS_EVENTS is provisioned).
        nats.jetStream().publish(row.getSubject(), row.getPayload().getBytes(StandardCharsets.UTF_8));
        row.markPublished(Instant.now());
        log.info("published claim financial event {} → {}", row.getEventId(), row.getSubject());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID id, String error) {
        outbox.findById(id).ifPresent(r -> r.recordFailure(error));
    }
}
