package com.policyhub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.oracle.OracleContainer;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Base class for integration tests. One Oracle container and one WireMock server (standing in for all
 * third parties) are started per JVM and shared by every IT class and the cached Spring context, because
 * Oracle takes minutes to boot. Tests therefore use their own plate numbers and dates instead of
 * relying on an empty database.
 * <p>
 * Before each test the third parties are reset to a "happy" default (unknown plates, approved payments)
 * and all circuit breakers are closed again; tests override the stubs they care about.
 */
@SpringBootTest(properties = {
        "policyhub.batch.lifecycle.schedule-enabled=false",
        "policyhub.integration.vehicle-registry.read-timeout=1s",
        "policyhub.integration.payment-gateway.read-timeout=1s",
        "resilience4j.retry.configs.default.wait-duration=10ms",
        "resilience4j.retry.instances.payment-gateway.wait-duration=10ms"
})
@AutoConfigureMockMvc
public abstract class AbstractOracleIT {

    protected static final String DEFAULT_PAYMENT_ID = "PAY-TEST-0001";

    @ServiceConnection
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    protected static final WireMockServer THIRD_PARTIES = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        ORACLE.start();
        THIRD_PARTIES.start();
    }

    @DynamicPropertySource
    static void thirdPartyUrls(DynamicPropertyRegistry registry) {
        registry.add("policyhub.integration.vehicle-registry.base-url", THIRD_PARTIES::baseUrl);
        registry.add("policyhub.integration.payment-gateway.base-url", THIRD_PARTIES::baseUrl);
    }

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetThirdParties() {
        THIRD_PARTIES.resetAll();
        THIRD_PARTIES.stubFor(get(anyUrl()).atPriority(10).willReturn(aResponse().withStatus(404)));
        THIRD_PARTIES.stubFor(post("/v1/payments").atPriority(10).willReturn(okJson("""
                {"paymentId":"%s","status":"APPROVED"}""".formatted(DEFAULT_PAYMENT_ID))));
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }
}
