-- Rekey the read-model on the legacy SOURCE primary key (gc_claim.id), not the mutable claim_number.
--
-- Why: GlobalCore.createClaim inserts a row with a temporary `TMP-<nanoTime>` claim_number, then UPDATEs
-- it to `CLM-<year>-<id>` (it needs the auto-increment id first). Keying the projection on claim_number
-- made the INSERT(TMP) and the UPDATE(CLM) of the SAME source row land as TWO read-model rows → an orphan
-- `TMP-…` row per live-created claim. Keying on the stable source `id` makes both events upsert the SAME
-- row (claim_number transitions TMP→CLM in place). claim_number stays UNIQUE (the query lookup key).
--
-- The read-model is a derived projection (rebuildable from the CDC stream), so a destructive recreate is
-- safe; a fresh durable consumer (cdc.durable bumped) replays the stream from the start to repopulate.
DROP TABLE IF EXISTS claim_read;

CREATE TABLE claim_read (
    claim_id          bigint       PRIMARY KEY,          -- gc_claim.id (stable source PK)
    claim_number      varchar(32)  NOT NULL UNIQUE,      -- business key (TMP-… → CLM-… in place)
    policy_number     varchar(32)  NOT NULL,
    status            varchar(24)  NOT NULL,
    loss_date         date         NOT NULL,
    peril             varchar(48)  NOT NULL,
    claimant_name     varchar(160) NOT NULL,
    reserve_minor     bigint,
    settlement_minor  bigint,
    registered_at     timestamptz  NOT NULL,
    source_updated_at timestamptz,
    deleted           boolean      NOT NULL DEFAULT false,
    projected_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_claim_read_policy ON claim_read (policy_number);
CREATE INDEX idx_claim_read_status ON claim_read (status);
