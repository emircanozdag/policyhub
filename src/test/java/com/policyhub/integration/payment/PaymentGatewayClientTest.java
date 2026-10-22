package com.policyhub.integration.payment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.policyhub.integration.IntegrationConfig;
import com.policyhub.integration.IntegrationProperties;
import com.policyhub.integration.ThirdPartyIntegrationException;
import com.policyhub.integration.ThirdPartyUnavailableException;
import com.policyhub.payment.PaymentRequest;
import com.policyhub.payment.PaymentResult;

class PaymentGatewayClientTest {

    @RegisterExtension
    static WireMockExtension gateway = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final PaymentRequest REQUEST = new PaymentRequest("policy-issue-Q-1", new BigDecimal("25000.00"),
            "TRY", "tok_visa", "KASKO premium");

    private PaymentGatewayClient client;

    /** Generous timeout so a cold JVM on a slow CI runner never looks like a slow partner. */
    private static final Duration NORMAL_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(200);

    @BeforeEach
    void setUp() {
        client = client(NORMAL_TIMEOUT);
    }

    private static PaymentGatewayClient client(Duration readTimeout) {
        var endpoint = new IntegrationProperties.Endpoint(URI.create(gateway.baseUrl()), "pos-key",
                Duration.ofSeconds(2), readTimeout);
        return new PaymentGatewayClient(IntegrationConfig.restClient(RestClient.builder(), endpoint));
    }

    @Test
    void sendsIdempotencyKeyCredentialsAndAmount() {
        gateway.stubFor(post("/v1/payments").willReturn(okJson("""
                {"paymentId":"PAY-42","status":"APPROVED"}""")));

        assertThat(client.charge(REQUEST)).isEqualTo(new PaymentResult.Approved("PAY-42"));
        gateway.verify(postRequestedFor(urlEqualTo("/v1/payments"))
                .withHeader("Idempotency-Key", equalTo("policy-issue-Q-1"))
                .withHeader("Authorization", equalTo("Bearer pos-key"))
                .withRequestBody(equalToJson("""
                        {"amount":25000.00,"currency":"TRY","paymentToken":"tok_visa","description":"KASKO premium"}""")));
    }

    @Test
    void mapsDecline() {
        gateway.stubFor(post("/v1/payments").willReturn(okJson("""
                {"paymentId":"PAY-43","status":"DECLINED","declineReason":"insufficient funds"}""")));

        assertThat(client.charge(REQUEST)).isEqualTo(new PaymentResult.Declined("insufficient funds"));
    }

    @Test
    void serverErrorAndTimeoutAreTransient() {
        gateway.stubFor(post("/v1/payments").willReturn(aResponse().withStatus(502)));
        assertThatThrownBy(() -> client.charge(REQUEST)).isInstanceOf(ThirdPartyUnavailableException.class);

        gateway.stubFor(post("/v1/payments").willReturn(okJson("{}").withFixedDelay(1_000)));
        assertThatThrownBy(() -> client(SHORT_TIMEOUT).charge(REQUEST)).isInstanceOf(ThirdPartyUnavailableException.class);
    }

    @Test
    void rejectedRequestIsAContractProblem() {
        gateway.stubFor(post("/v1/payments").willReturn(aResponse().withStatus(422)));

        assertThatThrownBy(() -> client.charge(REQUEST)).isInstanceOf(ThirdPartyIntegrationException.class);
    }
}
