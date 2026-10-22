package com.policyhub.policy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.policyhub.common.NotFoundException;
import com.policyhub.common.ReferenceNumberGenerator;
import com.policyhub.quote.Quote;
import com.policyhub.quote.QuoteRepository;

@Service
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final QuoteRepository quoteRepository;
    private final ReferenceNumberGenerator referenceNumbers;
    private final Clock clock;

    public PolicyService(PolicyRepository policyRepository, QuoteRepository quoteRepository,
                         ReferenceNumberGenerator referenceNumbers, Clock clock) {
        this.policyRepository = policyRepository;
        this.quoteRepository = quoteRepository;
        this.referenceNumbers = referenceNumbers;
        this.clock = clock;
    }

    /** Checks the quote can be issued today and returns it, so payment is only taken for issuable quotes. */
    @Transactional(readOnly = true)
    public Quote prepareIssuance(String quoteNumber) {
        Quote quote = findQuote(quoteNumber);
        quote.checkConvertible(LocalDate.now(clock));
        return quote;
    }

    /** Issues a one-year policy starting today from an active, non-expired quote whose premium is paid. */
    @Transactional
    public PolicyResponse issueFromQuote(String quoteNumber, String paymentReference) {
        Quote quote = findQuote(quoteNumber);
        LocalDate today = LocalDate.now(clock);
        quote.convert(today);

        Policy policy = new Policy(
                referenceNumbers.next("P"),
                quote.getId(),
                quote.getProductType(),
                quote.getPlateNumber(),
                quote.getPremium(),
                today,
                today.plusYears(1),
                paymentReference,
                LocalDateTime.now(clock));
        return PolicyResponse.from(policyRepository.save(policy));
    }

    @Transactional(readOnly = true)
    public PolicyResponse getPolicy(String policyNumber) {
        return PolicyResponse.from(findByNumber(policyNumber));
    }

    @Transactional
    public PolicyResponse cancel(String policyNumber) {
        Policy policy = findByNumber(policyNumber);
        policy.cancel();
        return PolicyResponse.from(policy);
    }

    private Quote findQuote(String quoteNumber) {
        return quoteRepository.findByQuoteNumber(quoteNumber)
                .orElseThrow(() -> new NotFoundException("Quote not found: " + quoteNumber));
    }

    private Policy findByNumber(String policyNumber) {
        return policyRepository.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> new NotFoundException("Policy not found: " + policyNumber));
    }
}
