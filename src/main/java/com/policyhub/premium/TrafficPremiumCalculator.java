package com.policyhub.premium;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

/**
 * Traffic insurance uses a fixed tariff adjusted by a no-claim / claim step,
 * loosely modelled on the Turkish "basamak" system. Vehicle value is irrelevant.
 * Rates are illustrative, not real tariffs.
 */
@Component
public class TrafficPremiumCalculator implements PremiumCalculator {

    static final BigDecimal BASE_TARIFF = new BigDecimal("8000.00");
    private static final BigDecimal NO_CLAIM_DISCOUNT = new BigDecimal("0.90");
    private static final BigDecimal CLAIM_SURCHARGE = new BigDecimal("0.25");
    private static final BigDecimal YOUNG_DRIVER_FACTOR = new BigDecimal("1.30");

    @Override
    public ProductType productType() {
        return ProductType.TRAFFIC;
    }

    @Override
    public BigDecimal calculate(RiskProfile profile) {
        BigDecimal stepFactor = profile.claimCountLast3Years() == 0
                ? NO_CLAIM_DISCOUNT
                : BigDecimal.ONE.add(CLAIM_SURCHARGE.multiply(BigDecimal.valueOf(profile.claimCountLast3Years())));
        BigDecimal ageFactor = profile.driverAge() < 25 ? YOUNG_DRIVER_FACTOR : BigDecimal.ONE;

        return BASE_TARIFF.multiply(stepFactor)
                .multiply(ageFactor)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
