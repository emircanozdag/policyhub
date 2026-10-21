package com.policyhub.premium;

import java.math.BigDecimal;

/**
 * Strategy for pricing a single insurance product. Adding a new product (e.g. DASK)
 * means adding a new implementation; nothing else changes.
 */
public interface PremiumCalculator {

    ProductType productType();

    BigDecimal calculate(RiskProfile profile);
}
