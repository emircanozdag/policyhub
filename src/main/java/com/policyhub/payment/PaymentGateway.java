package com.policyhub.payment;

/** Port: collects a premium through the virtual POS provider. */
public interface PaymentGateway {

    /**
     * Charges the request. Calls with the same idempotency key must charge at most once, so a call may be
     * retried safely after a timeout.
     */
    PaymentResult charge(PaymentRequest request);
}
