-- CLM-FIN-03a — the reserve four-eyes approval store. A reserve above the requester's authority OR above the
-- large-loss threshold is held here as PENDING (NOT yet applied to the legacy); a different, higher-authority
-- person approves → the reserve is then driven into the legacy (which emits the movement via CDC → CLM-FIN-02).
-- The record IS the audit: it captures who requested + who decided + when (append-only transitions).
CREATE TABLE claim_reserve_approval (
    id                 uuid          PRIMARY KEY,
    claim_number       varchar(32)   NOT NULL,
    -- euros (the ACL command + authority layer is BigDecimal euros; the eurocents reserve_minor is a
    -- downstream read-model/CDC concern). Kept consistent with ReserveRequest.amount / Authority.maxReserve.
    amount             numeric(18,2) NOT NULL CHECK (amount > 0),
    requested_by       varchar(64)   NOT NULL,
    requested_authority varchar(16) NOT NULL,                 -- ADJUSTER | SENIOR (the requester's role)
    status             varchar(16)  NOT NULL DEFAULT 'pending', -- pending | approved | rejected
    decided_by         varchar(64),
    decided_authority  varchar(16),
    reason             varchar(500),                          -- rejection reason (approvals carry none)
    requested_at       timestamptz  NOT NULL DEFAULT now(),
    decided_at         timestamptz
);
CREATE INDEX idx_reserve_approval_pending ON claim_reserve_approval (status) WHERE status = 'pending';
CREATE INDEX idx_reserve_approval_claim ON claim_reserve_approval (claim_number);
