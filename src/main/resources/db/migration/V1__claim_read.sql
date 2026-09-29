-- Slice D (ADR-008) — the CQRS query-side read-model.
-- Denormalised projection of the legacy GlobalCore `gc_claim`, kept current by the CDC projector
-- (NATS JetStream `claims-cdc.globalcore.gc_claim` → this table). The command side still writes the
-- legacy via SOAP; this table is EVENTUALLY consistent and is read by GET /api/claims/{claimNumber}.
-- Money is eurocents (bigint), mirroring the legacy `*_minor` columns. Append-safe upsert by PK.
CREATE TABLE claim_read (
    claim_number      varchar(32)  PRIMARY KEY,
    policy_number     varchar(32)  NOT NULL,
    status            varchar(24)  NOT NULL,
    loss_date         date         NOT NULL,
    peril             varchar(48)  NOT NULL,
    claimant_name     varchar(160) NOT NULL,
    reserve_minor     bigint,
    settlement_minor  bigint,
    registered_at     timestamptz  NOT NULL,
    -- CDC bookkeeping: the source row's updated_at (for last-writer-wins) + when we projected it,
    -- and the soft-delete tombstone flag Debezium emits.
    source_updated_at timestamptz,
    deleted           boolean      NOT NULL DEFAULT false,
    projected_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_claim_read_policy ON claim_read (policy_number);
CREATE INDEX idx_claim_read_status ON claim_read (status);
