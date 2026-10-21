package com.policyhub.premium;

import java.math.BigDecimal;

/**
 * Underwriting inputs a premium calculator needs. Kept free of persistence and
 * web concerns so calculators can be unit tested in isolation.
 */
public record RiskProfile(
        int vehicleAgeYears,
        BigDecimal vehicleValue,
        int driverAge,
        int claimCountLast3Years) {
}
