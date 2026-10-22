package com.policyhub.payment;

import java.math.BigDecimal;

/**
 * @param idempotencyKey stable per business operation (one per quote), so retries never double charge
 * @param paymentToken   single-use token from the provider's hosted card form; card data never reaches us
 */
public record PaymentRequest(String idempotencyKey, BigDecimal amount, String currency, String paymentToken,
                             String description) {
}
