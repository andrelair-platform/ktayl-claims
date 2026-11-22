-- CLM-FIN-03b — the claim-payment request→approve→execute workflow store. A settlement above the requester's
-- authority OR above the large-loss threshold is held here as REQUESTED (NOT yet paid); a different person
-- APPROVES (four-eyes), then an authorised, non-requester executor EXECUTES the disbursement — which drives
-- the settle into the legacy (→ CDC → GL). Three recorded steps = segregation of duties on real cash-out.
-- The record is its own append-only audit (who requested / approved / executed + when).
CREATE TABLE payment_request (
    id                 uuid          PRIMARY KEY,
    claim_number       varchar(32)   NOT NULL,
    amount             numeric(18,2) NOT NULL CHECK (amount > 0),   -- euros (the ACL command/authority layer)
    requested_by       varchar(64)   NOT NULL,
    requested_authority varchar(16)  NOT NULL,
    status             varchar(16)   NOT NULL DEFAULT 'requested',  -- requested | approved | executed | rejected
    approved_by        varchar(64),
    approved_authority varchar(16),
    approved_at        timestamptz,
    executed_by        varchar(64),
    executed_at        timestamptz,
    reason             varchar(500),                                -- rejection reason
    requested_at       timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_request_open ON payment_request (status) WHERE status IN ('requested', 'approved');
CREATE INDEX idx_payment_request_claim ON payment_request (claim_number);
