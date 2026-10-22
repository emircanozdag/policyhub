package com.policyhub.integration;

/**
 * The third party answered, but not in a way we can use (unexpected 4xx, unknown enum value, …).
 * Retrying would not help, so this is neither retried nor counted by the circuit breaker; it points
 * to a contract or configuration problem that needs a human.
 */
public class ThirdPartyIntegrationException extends RuntimeException {

    public ThirdPartyIntegrationException(String system, String message) {
        super("%s integration error: %s".formatted(system, message));
    }
}
