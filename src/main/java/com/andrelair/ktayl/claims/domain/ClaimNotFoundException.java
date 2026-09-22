package com.andrelair.ktayl.claims.domain;

/** No claim with that number exists in the legacy. */
public class ClaimNotFoundException extends RuntimeException {
    public ClaimNotFoundException(String claimNumber) {
        super("unknown claim: " + claimNumber);
    }
}
