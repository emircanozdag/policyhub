package com.policyhub.batch.lifecycle;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.policyhub.premium.ProductType;

/**
 * An active policy close to its end date, joined with the risk data of the quote it was issued from
 * and, when available, the latest registry entry for its vehicle.
 */
public record RenewalCandidate(
        long policyId,
        String policyNumber,
        ProductType productType,
        String plateNumber,
        LocalDate endDate,
        int vehicleModelYear,
        BigDecimal vehicleValue,
        int driverAge,
        int claimCount,
        BigDecimal currentKaskoValue,
        String vehicleStatus) {
}
