package com.andrelair.ktayl.claims.domain;

/** No payment request with that id exists (CLM-FIN-03b). */
public class PaymentRequestNotFoundException extends RuntimeException {
    public PaymentRequestNotFoundException(String id) {
        super("unknown payment request: " + id);
    }
}
