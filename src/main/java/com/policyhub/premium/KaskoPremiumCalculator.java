package com.policyhub.premium;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

/**
 * Kasko premium = vehicle value x base rate x driver-age factor x vehicle-age factor x claim factor.
 * Rates are illustrative, not real tariffs.
 */
@Component
public class KaskoPremiumCalculator implements PremiumCalculator {

    static final BigDecimal BASE_RATE = new BigDecimal("0.025");
    static final BigDecimal MINIMUM_PREMIUM = new BigDecimal("5000.00");
    private static final BigDecimal CLAIM_SURCHARGE = new BigDecimal("0.15");

    @Override
    public ProductType productType() {
        return ProductType.KASKO;
    }

    @Override
    public BigDecimal calculate(RiskProfile profile) {
        BigDecimal premium = profile.vehicleValue()
                .multiply(BASE_RATE)
                .multiply(driverAgeFactor(profile.driverAge()))
                .multiply(vehicleAgeFactor(profile.vehicleAgeYears()))
                .multiply(claimFactor(profile.claimCountLast3Years()))
                .setScale(2, RoundingMode.HALF_UP);
        return premium.max(MINIMUM_PREMIUM);
    }

    private BigDecimal driverAgeFactor(int age) {
        if (age < 25) {
            return new BigDecimal("1.40");
        }
        if (age > 65) {
            return new BigDecimal("1.20");
        }
        return BigDecimal.ONE;
    }

    private BigDecimal vehicleAgeFactor(int vehicleAgeYears) {
        if (vehicleAgeYears <= 3) {
            return BigDecimal.ONE;
        }
        if (vehicleAgeYears <= 10) {
            return new BigDecimal("1.10");
        }
        return new BigDecimal("1.25");
    }

    private BigDecimal claimFactor(int claimCount) {
        return BigDecimal.ONE.add(CLAIM_SURCHARGE.multiply(BigDecimal.valueOf(claimCount)));
    }
}
