package com.andrelair.ktayl.claims.readmodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A pending/published claim financial event (CLM-FIN-02, ADR-021) — the transactional outbox written by the
 * CDC projector when a reserve/settlement moved, drained to NATS by {@link ClaimEventPublisher}. Reliable
 * emission: a NATS/stream outage leaves the row {@code pending} → retried; the legacy stays the write SoR.
 */
@Entity
@Table(name = "claim_event_outbox")
public class ClaimEventOutboxEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected ClaimEventOutboxEntity() {
        // JPA
    }

    public ClaimEventOutboxEntity(UUID id, String eventId, String subject, String payload, Instant createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.subject = subject;
        this.payload = payload;
        this.status = "pending";
        this.attempts = 0;
        this.createdAt = createdAt;
    }

    public void markPublished(Instant at) {
        this.status = "published";
        this.publishedAt = at;
        this.lastError = null;
    }

    public void recordFailure(String error) {
        this.attempts += 1;
        this.lastError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
    }

    public UUID getId() { return id; }
    public String getEventId() { return eventId; }
    public String getSubject() { return subject; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
}
