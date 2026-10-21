package com.policyhub.premium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;

import org.junit.jupiter.api.Test;

class PremiumCalculatorRegistryTest {

    @Test
    void resolvesCalculatorByProduct() {
        var kasko = new KaskoPremiumCalculator();
        var traffic = new TrafficPremiumCalculator();
        var registry = new PremiumCalculatorRegistry(List.of(kasko, traffic));

        assertThat(registry.forProduct(ProductType.KASKO)).isSameAs(kasko);
        assertThat(registry.forProduct(ProductType.TRAFFIC)).isSameAs(traffic);
    }

    @Test
    void failsForUnregisteredProduct() {
        var registry = new PremiumCalculatorRegistry(List.of(new KaskoPremiumCalculator()));

        assertThatIllegalArgumentException().isThrownBy(() -> registry.forProduct(ProductType.TRAFFIC));
    }

    @Test
    void rejectsDuplicateCalculators() {
        assertThatIllegalStateException().isThrownBy(() ->
                new PremiumCalculatorRegistry(List.of(new KaskoPremiumCalculator(), new KaskoPremiumCalculator())));
    }
}
