package com.policyhub.premium;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Resolves the {@link PremiumCalculator} strategy for a product. All calculator beans
 * are collected by Spring; duplicates for the same product fail fast at startup.
 */
@Component
public class PremiumCalculatorRegistry {

    private final Map<ProductType, PremiumCalculator> calculators = new EnumMap<>(ProductType.class);

    public PremiumCalculatorRegistry(List<PremiumCalculator> calculators) {
        for (PremiumCalculator calculator : calculators) {
            PremiumCalculator previous = this.calculators.putIfAbsent(calculator.productType(), calculator);
            if (previous != null) {
                throw new IllegalStateException("Multiple premium calculators registered for " + calculator.productType());
            }
        }
    }

    public PremiumCalculator forProduct(ProductType productType) {
        PremiumCalculator calculator = calculators.get(productType);
        if (calculator == null) {
            throw new IllegalArgumentException("No premium calculator registered for " + productType);
        }
        return calculator;
    }
}
