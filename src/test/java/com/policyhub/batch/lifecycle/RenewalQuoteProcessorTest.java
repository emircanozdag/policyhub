package com.policyhub.batch.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.policyhub.common.ReferenceNumberGenerator;
import com.policyhub.premium.KaskoPremiumCalculator;
import com.policyhub.premium.PremiumCalculatorRegistry;
import com.policyhub.premium.ProductType;
import com.policyhub.premium.TrafficPremiumCalculator;
import com.policyhub.quote.Quote;
import com.policyhub.quote.QuoteStatus;

class RenewalQuoteProcessorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T02:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate END_DATE = LocalDate.of(2026, 11, 5);

    private final RenewalQuoteProcessor processor = new RenewalQuoteProcessor(
            new PremiumCalculatorRegistry(List.of(new KaskoPremiumCalculator(), new TrafficPremiumCalculator())),
            new ReferenceNumberGenerator(CLOCK), CLOCK);

    @Test
    void repricesWithCurrentKaskoValueAndAgedRisk() {
        Quote renewal = processor.process(candidate(new BigDecimal("800000.00"), "ACTIVE"));

        // 800,000 x 2.5% x 1.10 (vehicle 4 years old at renewal) x 1.00 (driver 41) = 22,000
        assertThat(renewal.getPremium()).isEqualByComparingTo("22000.00");
        assertThat(renewal.getVehicleValue()).isEqualByComparingTo("800000.00");
        assertThat(renewal.getDriverAge()).isEqualTo(41);
        assertThat(renewal.getRenewalOfPolicyId()).isEqualTo(7L);
        assertThat(renewal.getValidUntil()).isEqualTo(END_DATE);
        assertThat(renewal.getStatus()).isEqualTo(QuoteStatus.ACTIVE);
        assertThat(renewal.getQuoteNumber()).startsWith("Q-20261021-");
    }

    @Test
    void fallsBackToLastYearsValueWhenVehicleIsNotInRegistry() {
        Quote renewal = processor.process(candidate(null, null));

        assertThat(renewal.getVehicleValue()).isEqualByComparingTo("600000.00");
        assertThat(renewal.getPremium()).isEqualByComparingTo("16500.00");
    }

    @Test
    void doesNotRenewScrappedVehicles() {
        assertThat(processor.process(candidate(new BigDecimal("800000.00"), "SCRAPPED"))).isNull();
    }

    private static RenewalCandidate candidate(BigDecimal currentKaskoValue, String vehicleStatus) {
        return new RenewalCandidate(7L, "P-20251105-000001", ProductType.KASKO, "34LCY001", END_DATE,
                2022, new BigDecimal("600000.00"), 40, 0, currentKaskoValue, vehicleStatus);
    }
}
