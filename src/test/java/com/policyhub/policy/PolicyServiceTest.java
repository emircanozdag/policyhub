package com.policyhub.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.policyhub.common.BusinessRuleException;
import com.policyhub.common.NotFoundException;
import com.policyhub.common.ReferenceNumberGenerator;
import com.policyhub.premium.ProductType;
import com.policyhub.quote.Quote;
import com.policyhub.quote.QuoteRepository;
import com.policyhub.quote.QuoteStatus;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T09:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @Mock
    private PolicyRepository policyRepository;
    @Mock
    private QuoteRepository quoteRepository;

    private PolicyService policyService;

    @BeforeEach
    void setUp() {
        policyService = new PolicyService(policyRepository, quoteRepository, new ReferenceNumberGenerator(CLOCK), CLOCK);
    }

    @Test
    void issuesOneYearPolicyAndConvertsQuote() {
        Quote quote = quoteValidUntil(TODAY.plusDays(10));
        when(quoteRepository.findByQuoteNumber("Q-1")).thenReturn(Optional.of(quote));
        when(policyRepository.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

        PolicyResponse policy = policyService.issueFromQuote("Q-1", "PAY-1");

        assertThat(policy.status()).isEqualTo(PolicyStatus.ACTIVE);
        assertThat(policy.startDate()).isEqualTo(TODAY);
        assertThat(policy.endDate()).isEqualTo(TODAY.plusYears(1));
        assertThat(policy.premium()).isEqualByComparingTo("25000.00");
        assertThat(policy.policyNumber()).startsWith("P-20261021-");
        assertThat(policy.paymentReference()).isEqualTo("PAY-1");
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.CONVERTED);
    }

    @Test
    void prepareIssuanceRejectsExpiredQuoteBeforeAnyPayment() {
        when(quoteRepository.findByQuoteNumber("Q-1")).thenReturn(Optional.of(quoteValidUntil(TODAY.minusDays(1))));

        assertThatThrownBy(() -> policyService.prepareIssuance("Q-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsExpiredQuote() {
        when(quoteRepository.findByQuoteNumber("Q-1")).thenReturn(Optional.of(quoteValidUntil(TODAY.minusDays(1))));

        assertThatThrownBy(() -> policyService.issueFromQuote("Q-1", "PAY-1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expired");
        verify(policyRepository, never()).save(any());
    }

    @Test
    void rejectsAlreadyConvertedQuote() {
        Quote quote = quoteValidUntil(TODAY.plusDays(10));
        quote.convert(TODAY);
        when(quoteRepository.findByQuoteNumber("Q-1")).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> policyService.issueFromQuote("Q-1", "PAY-1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CONVERTED");
    }

    @Test
    void failsForUnknownQuote() {
        when(quoteRepository.findByQuoteNumber("Q-404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policyService.issueFromQuote("Q-404", "PAY-1")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancelsActivePolicyOnlyOnce() {
        Policy policy = new Policy("P-1", 1L, ProductType.KASKO, "34ABC123", new BigDecimal("25000.00"),
                TODAY, TODAY.plusYears(1), "PAY-1", LocalDateTime.now(CLOCK));
        when(policyRepository.findByPolicyNumber("P-1")).thenReturn(Optional.of(policy));

        assertThat(policyService.cancel("P-1").status()).isEqualTo(PolicyStatus.CANCELLED);
        assertThatThrownBy(() -> policyService.cancel("P-1")).isInstanceOf(BusinessRuleException.class);
    }

    private static Quote quoteValidUntil(LocalDate validUntil) {
        return new Quote("Q-1", ProductType.KASKO, "34ABC123", 2024, new BigDecimal("1000000.00"), 35, 0,
                new BigDecimal("25000.00"), validUntil, LocalDateTime.now(CLOCK));
    }
}
