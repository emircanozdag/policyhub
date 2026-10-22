package com.policyhub.policy;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.policyhub.premium.ProductType;

public record PolicyResponse(
        String policyNumber,
        ProductType productType,
        String plateNumber,
        BigDecimal premium,
        String currency,
        LocalDate startDate,
        LocalDate endDate,
        PolicyStatus status,
        String paymentReference) {

    /** Amounts always carry two decimals, even when Oracle hands back {@code NUMBER(15,2)} as {@code 31250}. */
    static PolicyResponse from(Policy policy) {
        return new PolicyResponse(policy.getPolicyNumber(), policy.getProductType(), policy.getPlateNumber(),
                policy.getPremium().setScale(2), "TRY", policy.getStartDate(), policy.getEndDate(), policy.getStatus(),
                policy.getPaymentReference());
    }
}
