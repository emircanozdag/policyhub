package com.policyhub.quote;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.policyhub.premium.ProductType;

public record QuoteResponse(
        String quoteNumber,
        ProductType productType,
        String plateNumber,
        BigDecimal premium,
        String currency,
        QuoteStatus status,
        LocalDate validUntil,
        VehicleVerification vehicleVerification) {

    static QuoteResponse from(Quote quote) {
        return new QuoteResponse(quote.getQuoteNumber(), quote.getProductType(), quote.getPlateNumber(),
                quote.getPremium().setScale(2), "TRY", quote.getStatus(), quote.getValidUntil(),
                quote.getVehicleVerification());
    }
}
