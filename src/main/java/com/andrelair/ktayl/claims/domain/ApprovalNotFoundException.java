package com.andrelair.ktayl.claims.domain;

/** No reserve approval with that id exists (CLM-FIN-03a). */
public class ApprovalNotFoundException extends RuntimeException {
    public ApprovalNotFoundException(String id) {
        super("unknown reserve approval: " + id);
    }
}
