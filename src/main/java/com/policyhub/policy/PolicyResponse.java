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
        PolicyStatus status) {

    static PolicyResponse from(Policy policy) {
        return new PolicyResponse(policy.getPolicyNumber(), policy.getProductType(), policy.getPlateNumber(),
                policy.getPremium(), "TRY", policy.getStartDate(), policy.getEndDate(), policy.getStatus());
    }
}
