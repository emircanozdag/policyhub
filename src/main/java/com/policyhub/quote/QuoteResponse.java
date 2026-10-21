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
        LocalDate validUntil) {

    static QuoteResponse from(Quote quote) {
        return new QuoteResponse(quote.getQuoteNumber(), quote.getProductType(), quote.getPlateNumber(),
                quote.getPremium(), "TRY", quote.getStatus(), quote.getValidUntil());
    }
}
