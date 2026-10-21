package com.policyhub.quote;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.policyhub.common.BusinessRuleException;
import com.policyhub.common.NotFoundException;
import com.policyhub.common.PlateNumbers;
import com.policyhub.common.ReferenceNumberGenerator;
import com.policyhub.premium.PremiumCalculatorRegistry;
import com.policyhub.premium.RiskProfile;

@Service
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final PremiumCalculatorRegistry calculators;
    private final ReferenceNumberGenerator referenceNumbers;
    private final QuoteProperties properties;
    private final Clock clock;

    public QuoteService(QuoteRepository quoteRepository, PremiumCalculatorRegistry calculators,
                        ReferenceNumberGenerator referenceNumbers, QuoteProperties properties, Clock clock) {
        this.quoteRepository = quoteRepository;
        this.calculators = calculators;
        this.referenceNumbers = referenceNumbers;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public QuoteResponse createQuote(CreateQuoteRequest request) {
        LocalDate today = LocalDate.now(clock);
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
                PlateNumbers.normalize(request.plateNumber()),
                request.vehicleModelYear(),
                request.vehicleValue(),
                request.driverAge(),
                request.claimCountLast3Years(),
                premium,
                today.plusDays(properties.validityDays()),
                LocalDateTime.now(clock));
        return QuoteResponse.from(quoteRepository.save(quote));
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
