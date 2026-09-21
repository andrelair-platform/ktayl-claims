package com.andrelair.ktayl.claims.domain;

/**
 * Claim lifecycle. GlobalCore's in-DB state machine (PL/SQL, S002) is authoritative; the ACL mirrors it.
 * S003 only produces {@link #NOTIFIED}; the rest arrive with S004 (lifecycle/reserve/settle).
 */
public enum ClaimStatus {
    NOTIFIED, UNDER_REVIEW, RESERVED, SETTLED, CLOSED, REJECTED
}
