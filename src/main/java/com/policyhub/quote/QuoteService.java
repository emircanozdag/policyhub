package com.policyhub.quote;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.policyhub.common.BusinessRuleException;
import com.policyhub.common.NotFoundException;
import com.policyhub.common.PlateNumbers;
import com.policyhub.common.ReferenceNumberGenerator;
import com.policyhub.premium.PremiumCalculatorRegistry;
import com.policyhub.premium.RiskProfile;
import com.policyhub.vehicle.VehicleInquiry;
import com.policyhub.vehicle.VehicleLookup;
import com.policyhub.vehicle.VehicleStatus;

@Service
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final PremiumCalculatorRegistry calculators;
    private final ReferenceNumberGenerator referenceNumbers;
    private final VehicleInquiry vehicleInquiry;
    private final QuoteProperties properties;
    private final Clock clock;

    public QuoteService(QuoteRepository quoteRepository, PremiumCalculatorRegistry calculators,
                        ReferenceNumberGenerator referenceNumbers, VehicleInquiry vehicleInquiry,
                        QuoteProperties properties, Clock clock) {
        this.quoteRepository = quoteRepository;
        this.calculators = calculators;
        this.referenceNumbers = referenceNumbers;
        this.vehicleInquiry = vehicleInquiry;
        this.properties = properties;
        this.clock = clock;
    }

    /*
     * Not @Transactional on purpose: the registry call happens before any database work, so a slow
     * partner never holds a pooled connection. The single save below runs in the repository's own transaction.
     */
    public QuoteResponse createQuote(CreateQuoteRequest request) {
        LocalDate today = LocalDate.now(clock);
        String plate = PlateNumbers.normalize(request.plateNumber());
        VehicleVerification verification = verifyVehicle(plate, request.vehicleModelYear());

        if (request.vehicleModelYear() > today.getYear() + 1) {
            throw new BusinessRuleException("Vehicle model year cannot be later than " + (today.getYear() + 1));
        }

        RiskProfile profile = new RiskProfile(
                Math.max(0, today.getYear() - request.vehicleModelYear()),
                request.vehicleValue(),
                request.driverAge(),
                request.claimCountLast3Years());
        BigDecimal premium = calculators.forProduct(request.productType()).calculate(profile);

        Quote quote = new Quote(
                referenceNumbers.next("Q"),
                request.productType(),
                plate,
                request.vehicleModelYear(),
                request.vehicleValue(),
                request.driverAge(),
                request.claimCountLast3Years(),
                premium,
                today.plusDays(properties.validityDays()),
                LocalDateTime.now(clock));
        quote.recordVehicleVerification(verification);
        return QuoteResponse.from(quoteRepository.save(quote));
    }

    private VehicleVerification verifyVehicle(String plate, int declaredModelYear) {
        Optional<VehicleLookup> found = vehicleInquiry.lookup(plate);
        if (found.isEmpty()) {
            return VehicleVerification.UNVERIFIED;
        }
        VehicleLookup vehicle = found.get();
        if (vehicle.status() == VehicleStatus.SCRAPPED) {
            throw new BusinessRuleException("Vehicle %s is deregistered (scrapped) and cannot be insured".formatted(plate));
        }
        if (vehicle.modelYear() != declaredModelYear) {
            throw new BusinessRuleException("Declared model year %d does not match the registry (%d) for %s"
                    .formatted(declaredModelYear, vehicle.modelYear(), plate));
        }
        return vehicle.source() == VehicleLookup.Source.REGISTRY_API
                ? VehicleVerification.VERIFIED_ONLINE
                : VehicleVerification.VERIFIED_OFFLINE;
    }

    @Transactional(readOnly = true)
    public QuoteResponse getQuote(String quoteNumber) {
        return QuoteResponse.from(findByNumber(quoteNumber));
    }

    Quote findByNumber(String quoteNumber) {
        return quoteRepository.findByQuoteNumber(quoteNumber)
                .orElseThrow(() -> new NotFoundException("Quote not found: " + quoteNumber));
    }
}
