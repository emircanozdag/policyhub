# ADR 0001: Strategy pattern for premium calculation

- **Status:** Accepted
- **Date:** 2026-10-21

## Context

Each insurance product prices risk differently. Kasko depends on vehicle value, vehicle age,
driver age and claim history; Traffic insurance uses a fixed tariff adjusted by a claim step
and ignores vehicle value. More products (DASK, health, travel) are expected.

A single `PremiumService` with a `switch` on product type would grow with every product,
mix unrelated rules, and make each product's pricing hard to test in isolation.

## Decision

- Define a `PremiumCalculator` interface (one implementation per `ProductType`).
- Implementations are Spring beans; `PremiumCalculatorRegistry` collects them into an `EnumMap`
  and fails fast at startup if two calculators claim the same product.
- Calculators take a `RiskProfile` value object, so they have no dependency on JPA entities or
  web DTOs and are unit tested as plain Java.

## Consequences

- Adding a product = adding one class + its test (Open/Closed Principle).
- Pricing logic for each product can be reviewed and changed independently.
- Underwriting rules that *reject* a risk (rather than price it) are a separate concern and will
  be modelled as a Chain of Responsibility in a later ADR.
