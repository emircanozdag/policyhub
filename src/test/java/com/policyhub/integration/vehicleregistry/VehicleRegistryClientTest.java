package com.policyhub.integration.vehicleregistry;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.policyhub.vehicle.VehicleLookup;
import com.policyhub.vehicle.VehicleStatus;

/** Wire-level behaviour of the adapter (no Spring, no resilience aspects): mapping and error translation. */
class VehicleRegistryClientTest {

    @RegisterExtension
    static WireMockExtension registry = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private VehicleRegistryClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new IntegrationProperties.Endpoint(URI.create(registry.baseUrl()), "registry-key",
                Duration.ofMillis(500), Duration.ofMillis(300));
        client = new VehicleRegistryClient(IntegrationConfig.restClient(RestClient.builder(), endpoint), null);
    }

    @Test
    void mapsVehicleAndSendsApiKey() {
        registry.stubFor(get("/v1/vehicles/34ABC123").willReturn(okJson("""
                {"plateNumber":"34ABC123","chassisNumber":"NMTBA3BE10R123456","brand":"TOYOTA",
                 "model":"COROLLA 1.5","modelYear":2023,"status":"SCRAPPED"}""")));

        assertThat(client.lookup("34ABC123")).contains(new VehicleLookup("34ABC123", "NMTBA3BE10R123456", "TOYOTA",
                "COROLLA 1.5", 2023, VehicleStatus.SCRAPPED, VehicleLookup.Source.REGISTRY_API));
        registry.verify(getRequestedFor(urlEqualTo("/v1/vehicles/34ABC123"))
                .withHeader("Authorization", equalTo("Bearer registry-key")));
    }

    @Test
    void unknownPlateIsAnAnswerNotAFailure() {
        registry.stubFor(get("/v1/vehicles/34XYZ999").willReturn(aResponse().withStatus(404)));

        assertThat(client.lookup("34XYZ999")).isEmpty();
    }

    @Test
    void serverErrorIsTransient() {
        registry.stubFor(get("/v1/vehicles/34ABC123").willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.lookup("34ABC123")).isInstanceOf(ThirdPartyUnavailableException.class);
    }

    @Test
    void slowResponseIsTransient() {
        registry.stubFor(get("/v1/vehicles/34ABC123").willReturn(okJson("{}").withFixedDelay(1_000)));

        assertThatThrownBy(() -> client.lookup("34ABC123")).isInstanceOf(ThirdPartyUnavailableException.class);
    }

    @Test
    void clientErrorIsAContractProblem() {
        registry.stubFor(get("/v1/vehicles/34ABC123").willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> client.lookup("34ABC123")).isInstanceOf(ThirdPartyIntegrationException.class);
    }

    @Test
    void unknownStatusIsAContractProblem() {
        registry.stubFor(get("/v1/vehicles/34ABC123").willReturn(okJson("""
                {"plateNumber":"34ABC123","chassisNumber":"X","brand":"B","model":"M","modelYear":2023,"status":"STOLEN"}""")));

        assertThatThrownBy(() -> client.lookup("34ABC123"))
                .isInstanceOf(ThirdPartyIntegrationException.class)
                .hasMessageContaining("STOLEN");
    }
}
