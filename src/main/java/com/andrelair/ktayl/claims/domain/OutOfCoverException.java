package com.andrelair.ktayl.claims.domain;

/** FNOL rejected because the loss is not covered by the policy (coverage check, S003). */
public class OutOfCoverException extends RuntimeException {
    public OutOfCoverException(String reason) {
        super(reason);
    }
}
