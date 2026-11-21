-- CLM-FIN-02 (ADR-021) — the claim FINANCIAL-EVENT outbox. When the CDC projector applies a gc_claim
-- record whose reserve_minor / settlement_minor moved (vs the prior projected row), it writes one event
-- row here IN THE SAME TRANSACTION as the claim_read upsert — a transactional outbox atomic with the local
-- read-model write (the legacy remains the write SoR; this is reliable-to-NATS emission, not atomic with
-- the legacy). A scheduled publisher drains `pending` rows to NATS JetStream (insurance.claims.financial on
-- the CLAIMS_EVENTS stream) and marks them `published`; a NATS/stream outage leaves rows pending → retried.
-- The ktayl-core `claimsgl` module consumes these and posts the GL (reserve→provisions, payment→cash).
CREATE TABLE claim_event_outbox (
    id           uuid         PRIMARY KEY,
    -- deterministic per movement (claim + type + source_updated_at): a redelivered CDC record yields the
    -- same event_id, so UNIQUE here (and the consumer's own idempotency) dedupes end-to-end.
    event_id     varchar(96)  NOT NULL UNIQUE,
    subject      varchar(80)  NOT NULL,                       -- insurance.claims.financial
    payload      text         NOT NULL,                       -- the ClaimFinancialEvent JSON (core's wire shape)
    status       varchar(16)  NOT NULL DEFAULT 'pending',     -- pending | published
    attempts     int          NOT NULL DEFAULT 0,
    last_error   varchar(500),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    published_at timestamptz
);
CREATE INDEX idx_claim_event_outbox_pending ON claim_event_outbox (status) WHERE status = 'pending';
