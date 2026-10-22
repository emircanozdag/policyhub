package com.policyhub.batch.lifecycle;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.policyhub.common.ReferenceNumberGenerator;
import com.policyhub.premium.PremiumCalculatorRegistry;
import com.policyhub.premium.RiskProfile;
import com.policyhub.quote.Quote;
import com.policyhub.quote.VehicleVerification;
import com.policyhub.vehicle.VehicleStatus;

/**
 * Re-prices an expiring policy for the next policy year. The risk is aged by one year (older vehicle,
 * older driver) and, when the registry has a newer kasko value for the vehicle, that value is used
 * instead of the one quoted last year. Scrapped vehicles are not offered a renewal.
 */
@Component
public class RenewalQuoteProcessor implements ItemProcessor<RenewalCandidate, Quote> {

    private static final Logger log = LoggerFactory.getLogger(RenewalQuoteProcessor.class);

    private final PremiumCalculatorRegistry calculators;
    private final ReferenceNumberGenerator referenceNumbers;
    private final Clock clock;

    public RenewalQuoteProcessor(PremiumCalculatorRegistry calculators, ReferenceNumberGenerator referenceNumbers,
                                 Clock clock) {
        this.calculators = calculators;
        this.referenceNumbers = referenceNumbers;
        this.clock = clock;
    }

    @Override
    public Quote process(RenewalCandidate candidate) {
        if (VehicleStatus.SCRAPPED.name().equals(candidate.vehicleStatus())) {
            log.info("No renewal for policy {}: vehicle {} is scrapped", candidate.policyNumber(), candidate.plateNumber());
            return null;
        }

        BigDecimal vehicleValue = candidate.currentKaskoValue() != null
                ? candidate.currentKaskoValue()
                : candidate.vehicleValue();
        int driverAge = candidate.driverAge() + 1;
        RiskProfile profile = new RiskProfile(
                Math.max(0, candidate.endDate().getYear() - candidate.vehicleModelYear()),
                vehicleValue,
                driverAge,
                // Claim history is carried over until the claims module (phase 6) feeds real counts.
                candidate.claimCount());
        BigDecimal premium = calculators.forProduct(candidate.productType()).calculate(profile);

        Quote renewal = Quote.renewalOf(
                candidate.policyId(),
                referenceNumbers.next("Q"),
                candidate.productType(),
                candidate.plateNumber(),
                candidate.vehicleModelYear(),
                vehicleValue,
                driverAge,
                candidate.claimCount(),
                premium,
                candidate.endDate(),
                LocalDateTime.now(clock));
        renewal.recordVehicleVerification(candidate.vehicleStatus() != null
                ? VehicleVerification.VERIFIED_OFFLINE
                : VehicleVerification.UNVERIFIED);
        return renewal;
    }
}
