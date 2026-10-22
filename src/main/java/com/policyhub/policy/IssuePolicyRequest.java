package com.policyhub.policy;

import jakarta.validation.constraints.NotBlank;

/**
 * @param paymentToken single-use token from the payment provider's hosted card form (never raw card data)
 */
public record IssuePolicyRequest(@NotBlank String quoteNumber, @NotBlank String paymentToken) {
}
