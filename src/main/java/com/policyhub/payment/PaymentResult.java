package com.policyhub.payment;

public sealed interface PaymentResult {

    record Approved(String paymentId) implements PaymentResult {
    }

    /** The provider answered and refused, e.g. insufficient funds. Final; not retried. */
    record Declined(String reason) implements PaymentResult {
    }
}
