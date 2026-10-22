package com.policyhub.integration;

/**
 * A third party could not answer: timeout, connection failure or 5xx. Transient by nature, so it is
 * retried and recorded by the circuit breaker.
 */
public class ThirdPartyUnavailableException extends RuntimeException {

    private final String system;

    public ThirdPartyUnavailableException(String system, String message, Throwable cause) {
        super("%s unavailable: %s".formatted(system, message), cause);
        this.system = system;
    }

    public String getSystem() {
        return system;
    }
}
