package com.policyhub.premium;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TrafficPremiumCalculatorTest {

    private final TrafficPremiumCalculator calculator = new TrafficPremiumCalculator();

    @ParameterizedTest(name = "driverAge={0}, claims={1} -> {2}")
    @CsvSource({
            "35, 0, 7200.00",    // no-claim discount
            "35, 1, 10000.00",   // +25% per claim
            "35, 3, 14000.00",
            "22, 0, 9360.00",    // young driver x1.30
            "22, 2, 15600.00"
    })
    void appliesClaimStepAndDriverAge(int driverAge, int claims, String expected) {
        RiskProfile profile = new RiskProfile(5, new BigDecimal("750000.00"), driverAge, claims);

        assertThat(calculator.calculate(profile)).isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"100000.00", "5000000.00"})
    void ignoresVehicleValue(String vehicleValue) {
        RiskProfile profile = new RiskProfile(5, new BigDecimal(vehicleValue), 35, 0);

        assertThat(calculator.calculate(profile)).isEqualByComparingTo("7200.00");
    }
}
