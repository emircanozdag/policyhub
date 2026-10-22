package com.policyhub.integration;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("policyhub.integration")
public record IntegrationProperties(Endpoint vehicleRegistry, Endpoint paymentGateway) {

    /**
     * @param baseUrl        root URL of the third-party API
     * @param apiKey         bearer token sent with every call (empty when the API needs none)
     * @param connectTimeout time allowed to open a connection
     * @param readTimeout    time allowed for the whole response; a slow partner must not hold our threads
     */
    public record Endpoint(URI baseUrl, String apiKey, Duration connectTimeout, Duration readTimeout) {
    }
}
