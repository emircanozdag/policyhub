package com.policyhub.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

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
import com.policyhub.vehicle.VehicleInquiry;
import com.policyhub.vehicle.VehicleLookup;
import com.policyhub.vehicle.VehicleStatus;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T09:00:00Z"), ZoneOffset.UTC);

    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private VehicleInquiry vehicleInquiry;

    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        var registry = new PremiumCalculatorRegistry(List.of(new KaskoPremiumCalculator(), new TrafficPremiumCalculator()));
        quoteService = new QuoteService(quoteRepository, registry, new ReferenceNumberGenerator(CLOCK), vehicleInquiry,
                new QuoteProperties(15), CLOCK);
        lenient().when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void pricesAndStoresQuote() {
        when(vehicleInquiry.lookup("34ABC123")).thenReturn(Optional.empty());

        QuoteResponse quote = quoteService.createQuote(kasko("34 abc 123", 2025));

        assertThat(quote.premium()).isEqualByComparingTo("25000.00");
        assertThat(quote.plateNumber()).isEqualTo("34ABC123");
        assertThat(quote.status()).isEqualTo(QuoteStatus.ACTIVE);
        assertThat(quote.validUntil()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(quote.quoteNumber()).startsWith("Q-20261021-");
        assertThat(quote.vehicleVerification()).isEqualTo(VehicleVerification.UNVERIFIED);
    }

    @Test
    void marksQuoteVerifiedOnlineWhenRegistryConfirmsVehicle() {
        when(vehicleInquiry.lookup("34ABC123")).thenReturn(Optional.of(vehicle(2025, VehicleStatus.ACTIVE,
                VehicleLookup.Source.REGISTRY_API)));

        assertThat(quoteService.createQuote(kasko("34ABC123", 2025)).vehicleVerification())
                .isEqualTo(VehicleVerification.VERIFIED_ONLINE);
    }

    @Test
    void marksQuoteVerifiedOfflineWhenAnsweredFromLocalCopy() {
        when(vehicleInquiry.lookup("34ABC123")).thenReturn(Optional.of(vehicle(2025, VehicleStatus.ACTIVE,
                VehicleLookup.Source.LOCAL_COPY)));

        assertThat(quoteService.createQuote(kasko("34ABC123", 2025)).vehicleVerification())
                .isEqualTo(VehicleVerification.VERIFIED_OFFLINE);
    }

    @Test
    void rejectsScrappedVehicle() {
        when(vehicleInquiry.lookup("34ABC123")).thenReturn(Optional.of(vehicle(2025, VehicleStatus.SCRAPPED,
                VehicleLookup.Source.REGISTRY_API)));

        assertThatThrownBy(() -> quoteService.createQuote(kasko("34ABC123", 2025)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("scrapped");
        verify(quoteRepository, never()).save(any());
    }

    @Test
    void rejectsModelYearThatContradictsRegistry() {
        when(vehicleInquiry.lookup("34ABC123")).thenReturn(Optional.of(vehicle(2019, VehicleStatus.ACTIVE,
                VehicleLookup.Source.REGISTRY_API)));

        assertThatThrownBy(() -> quoteService.createQuote(kasko("34ABC123", 2025)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not match the registry (2019)");
    }

    @Test
    void rejectsFutureModelYear() {
        when(vehicleInquiry.lookup("06AB1234")).thenReturn(Optional.empty());
        var request = new CreateQuoteRequest(ProductType.TRAFFIC, "06 AB 1234", 2030,
                new BigDecimal("1000000.00"), 35, 0);

        assertThatThrownBy(() -> quoteService.createQuote(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("model year");
        verify(quoteRepository, never()).save(any());
    }

    private static CreateQuoteRequest kasko(String plate, int modelYear) {
        return new CreateQuoteRequest(ProductType.KASKO, plate, modelYear, new BigDecimal("1000000.00"), 35, 0);
    }

    private static VehicleLookup vehicle(int modelYear, VehicleStatus status, VehicleLookup.Source source) {
        return new VehicleLookup("34ABC123", "NMTBA3BE10R123456", "TOYOTA", "COROLLA", modelYear, status, source);
    }
}
