package com.policyhub.quote;

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
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.policyhub.common.BusinessRuleException;
import com.policyhub.common.ReferenceNumberGenerator;
import com.policyhub.premium.KaskoPremiumCalculator;
import com.policyhub.premium.PremiumCalculatorRegistry;
import com.policyhub.premium.ProductType;
import com.policyhub.premium.TrafficPremiumCalculator;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T09:00:00Z"), ZoneOffset.UTC);

    @Mock
    private QuoteRepository quoteRepository;

    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        var registry = new PremiumCalculatorRegistry(List.of(new KaskoPremiumCalculator(), new TrafficPremiumCalculator()));
        quoteService = new QuoteService(quoteRepository, registry, new ReferenceNumberGenerator(CLOCK),
                new QuoteProperties(15), CLOCK);
    }

    @Test
    void pricesAndStoresQuote() {
        when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
        var request = new CreateQuoteRequest(ProductType.KASKO, "34 abc 123", 2025,
                new BigDecimal("1000000.00"), 35, 0);

        QuoteResponse quote = quoteService.createQuote(request);

        assertThat(quote.premium()).isEqualByComparingTo("25000.00");
        assertThat(quote.plateNumber()).isEqualTo("34ABC123");
        assertThat(quote.status()).isEqualTo(QuoteStatus.ACTIVE);
        assertThat(quote.validUntil()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(quote.quoteNumber()).startsWith("Q-20261021-");
    }

    @Test
    void rejectsFutureModelYear() {
        var request = new CreateQuoteRequest(ProductType.TRAFFIC, "06 AB 1234", 2030,
                new BigDecimal("1000000.00"), 35, 0);

        assertThatThrownBy(() -> quoteService.createQuote(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("model year");
        verify(quoteRepository, never()).save(any());
    }
}
