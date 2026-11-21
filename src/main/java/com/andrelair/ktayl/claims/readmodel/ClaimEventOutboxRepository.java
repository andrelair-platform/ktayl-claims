package com.andrelair.ktayl.claims.readmodel;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClaimEventOutboxRepository extends JpaRepository<ClaimEventOutboxEntity, UUID> {

    /** The next batch of unpublished events, oldest first (the publisher's work queue). */
    List<ClaimEventOutboxEntity> findByStatusOrderByCreatedAt(String status, Limit limit);

    /** DB-level idempotency: a redelivered CDC record (same deterministic event id) never re-enqueues. */
    boolean existsByEventId(String eventId);
}
