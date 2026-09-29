-- GlobalCore legacy schema (SIMULATED Oracle-era core — MySQL 8, ADR-002/006 amended 2026-09-29).
-- This is the legacy system's OWN database. The ACL never touches it directly (it goes through SOAP +
-- the Postgres read-model); Debezium's MySQL binlog connector captures these tables → NATS (Slice C).
-- ROW-format binlog + GTID are enabled at the server (see docker-compose) so every change here is a CDC event.

CREATE DATABASE IF NOT EXISTS globalcore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE globalcore;

-- Policy book (read-only reference the ACL reads for the FNOL coverage check).
CREATE TABLE gc_policy (
    policy_number   VARCHAR(32)  NOT NULL PRIMARY KEY,
    holder_name     VARCHAR(160) NOT NULL,
    effective_date  DATE         NOT NULL,
    expiry_date     DATE         NOT NULL
) ENGINE=InnoDB;

-- Covered perils per policy (child table — legacy normalisation).
CREATE TABLE gc_policy_peril (
    policy_number   VARCHAR(32) NOT NULL,
    peril           VARCHAR(32) NOT NULL,
    PRIMARY KEY (policy_number, peril),
    CONSTRAINT fk_peril_policy FOREIGN KEY (policy_number) REFERENCES gc_policy(policy_number)
) ENGINE=InnoDB;

-- Claims — the legacy owns the lifecycle state machine (the ultimate guard, ADR-003).
-- status flow: NOTIFIED -> UNDER_ASSESSMENT -> RESERVED -> SETTLED | REFUSED -> CLOSED (+ reopen).
-- Money in eurocents (BIGINT minor units), platform convention.
CREATE TABLE gc_claim (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    claim_number      VARCHAR(32)  NOT NULL UNIQUE,          -- CLM-{year}-{id:06d}, allocated by GlobalCore
    policy_number     VARCHAR(32)  NOT NULL,
    status            VARCHAR(24)  NOT NULL DEFAULT 'NOTIFIED',
    loss_date         DATE         NOT NULL,
    peril             VARCHAR(32)  NOT NULL,
    claimant_name     VARCHAR(160) NOT NULL,
    reserve_minor     BIGINT       NULL,                     -- current reserve (eurocents)
    settlement_minor  BIGINT       NULL,                     -- final paid amount (eurocents)
    registered_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_claim_policy FOREIGN KEY (policy_number) REFERENCES gc_policy(policy_number)
) ENGINE=InnoDB;

CREATE INDEX ix_claim_policy ON gc_claim(policy_number);
