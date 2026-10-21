package com.policyhub.premium;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KaskoPremiumCalculatorTest {

    private final KaskoPremiumCalculator calculator = new KaskoPremiumCalculator();

    @Test
    void appliesBaseRateForStandardRisk() {
        RiskProfile profile = new RiskProfile(2, new BigDecimal("1000000.00"), 35, 0);

        assertThat(calculator.calculate(profile)).isEqualByComparingTo("25000.00");
    }

    @ParameterizedTest(name = "vehicleAge={0}, driverAge={1}, claims={2} -> {3}")
    @CsvSource({
            "2,  22, 0, 35000.00",   // young driver x1.40
            "2,  70, 0, 30000.00",   // senior driver x1.20
            "5,  35, 0, 27500.00",   // 4-10 year old vehicle x1.10
            "12, 35, 0, 31250.00",   // 10+ year old vehicle x1.25
            "2,  35, 2, 32500.00",   // 2 claims x1.30
            "12, 22, 1, 50312.50"    // factors multiply
    })
    void appliesRiskFactors(int vehicleAge, int driverAge, int claims, String expected) {
        RiskProfile profile = new RiskProfile(vehicleAge, new BigDecimal("1000000.00"), driverAge, claims);

        assertThat(calculator.calculate(profile)).isEqualByComparingTo(expected);
    }

    @Test
    void neverGoesBelowMinimumPremium() {
        RiskProfile cheapCar = new RiskProfile(2, new BigDecimal("50000.00"), 35, 0);

        assertThat(calculator.calculate(cheapCar)).isEqualByComparingTo(KaskoPremiumCalculator.MINIMUM_PREMIUM);
    }
}
