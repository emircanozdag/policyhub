package com.policyhub.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.jayway.jsonpath.JsonPath;
import com.policyhub.AbstractOracleIT;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/** Retry, circuit breaker, fallback and idempotency behaviour through the real API, Oracle and HTTP stack. */
class ThirdPartyResilienceIT extends AbstractOracleIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    // ---- vehicle registry -------------------------------------------------------------------------

    @Test
    void transientRegistryFailureIsRetried() throws Exception {
        THIRD_PARTIES.stubFor(WireMock.get("/v1/vehicles/34RES001").inScenario("flaky").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("recovered"));
        THIRD_PARTIES.stubFor(WireMock.get("/v1/vehicles/34RES001").inScenario("flaky").whenScenarioStateIs("recovered")
                .willReturn(okJson(registryVehicle("34RES001", 2023, "ACTIVE"))));

        createQuote("34RES001", 2023)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicleVerification").value("VERIFIED_ONLINE"));
        THIRD_PARTIES.verify(2, getRequestedFor(urlEqualTo("/v1/vehicles/34RES001")));
    }

    @Test
    void registryOutageFallsBackToNightlyLocalCopy() throws Exception {
        THIRD_PARTIES.stubFor(WireMock.get(urlPathMatching("/v1/vehicles/.*")).willReturn(aResponse().withStatus(503)));
        registerLocally("34RES002", 2021, "SCRAPPED");
        registerLocally("34RES003", 2022, "ACTIVE");

        // The local copy still knows the vehicle is scrapped, so the business rule holds during the outage
        createQuote("34RES002", 2021).andExpect(status().isUnprocessableContent());
        createQuote("34RES003", 2022)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicleVerification").value("VERIFIED_OFFLINE"));

        THIRD_PARTIES.verify(3, getRequestedFor(urlEqualTo("/v1/vehicles/34RES002"))); // 1 call + 2 retries
    }

    @Test
    void slowRegistryIsCutOffByTimeoutAndFallsBack() throws Exception {
        THIRD_PARTIES.stubFor(WireMock.get("/v1/vehicles/34RES004")
                .willReturn(okJson(registryVehicle("34RES004", 2023, "ACTIVE")).withFixedDelay(2_500)));

        createQuote("34RES004", 2023)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicleVerification").value("UNVERIFIED"));
    }

    @Test
    void circuitOpensUnderSustainedFailureAndStopsCallingTheRegistry() throws Exception {
        THIRD_PARTIES.stubFor(WireMock.get(urlPathMatching("/v1/vehicles/.*")).willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 4; i++) { // 4 requests x 3 attempts = 12 failed calls >= minimum of 10
            createQuote("34RES01" + i, 2023).andExpect(status().isCreated());
        }
        assertThat(circuitBreakerRegistry.circuitBreaker("vehicle-registry").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        THIRD_PARTIES.resetRequests();
        createQuote("34RES020", 2023)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicleVerification").value("UNVERIFIED"));
        THIRD_PARTIES.verify(0, getRequestedFor(urlPathMatching("/v1/vehicles/.*")));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk()); // an open circuit degrades, it does not take the app down
    }

    // ---- payment gateway --------------------------------------------------------------------------

    @Test
    void paymentRetryReusesTheSameIdempotencyKey() throws Exception {
        THIRD_PARTIES.stubFor(WireMock.post("/v1/payments").inScenario("pos").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("up"));
        THIRD_PARTIES.stubFor(WireMock.post("/v1/payments").inScenario("pos").whenScenarioStateIs("up")
                .willReturn(okJson("""
                        {"paymentId":"PAY-RETRIED","status":"APPROVED"}""")));
        String quoteNumber = quoteNumber(createQuote("34PAY001", 2023));

        issuePolicy(quoteNumber, "tok_visa")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentReference").value("PAY-RETRIED"));

        THIRD_PARTIES.verify(2, postRequestedFor(urlEqualTo("/v1/payments"))
                .withHeader("Idempotency-Key", equalTo("policy-issue-" + quoteNumber)));
    }

    @Test
    void declinedPaymentLeavesQuoteOpen() throws Exception {
        THIRD_PARTIES.stubFor(WireMock.post("/v1/payments")
                .withRequestBody(matchingJsonPath("$.paymentToken", equalTo("tok_insufficient_funds")))
                .willReturn(okJson("""
                        {"paymentId":"PAY-DECLINED","status":"DECLINED","declineReason":"insufficient funds"}""")));
        String quoteNumber = quoteNumber(createQuote("34PAY002", 2023));

        issuePolicy(quoteNumber, "tok_insufficient_funds")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Payment declined: insufficient funds"));
        mockMvc.perform(get("/api/v1/quotes/" + quoteNumber)).andExpect(jsonPath("$.status").value("ACTIVE"));

        issuePolicy(quoteNumber, "tok_visa").andExpect(status().isCreated()); // customer retries with another card
    }

    @Test
    void paymentOutageReturns503WithRetryAfterAndIssuesNothing() throws Exception {
        THIRD_PARTIES.stubFor(WireMock.post("/v1/payments").willReturn(aResponse().withStatus(503)));
        String quoteNumber = quoteNumber(createQuote("34PAY003", 2023));

        issuePolicy(quoteNumber, "tok_visa")
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"));

        mockMvc.perform(get("/api/v1/quotes/" + quoteNumber)).andExpect(jsonPath("$.status").value("ACTIVE"));
        THIRD_PARTIES.verify(3, postRequestedFor(urlEqualTo("/v1/payments")));
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private ResultActions createQuote(String plate, int modelYear) throws Exception {
        return mockMvc.perform(post("/api/v1/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"productType":"KASKO","plateNumber":"%s","vehicleModelYear":%d,
                         "vehicleValue":900000,"driverAge":38,"claimCountLast3Years":0}
                        """.formatted(plate, modelYear)));
    }

    private ResultActions issuePolicy(String quoteNumber, String paymentToken) throws Exception {
        return mockMvc.perform(post("/api/v1/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"quoteNumber":"%s","paymentToken":"%s"}""".formatted(quoteNumber, paymentToken)));
    }

    private static String quoteNumber(ResultActions created) throws Exception {
        return JsonPath.read(created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.quoteNumber");
    }

    private static String registryVehicle(String plate, int modelYear, String status) {
        return """
                {"plateNumber":"%s","chassisNumber":"NMTBA3BE10R123456","brand":"TOYOTA","model":"COROLLA",
                 "modelYear":%d,"status":"%s"}""".formatted(plate, modelYear, status);
    }

    private void registerLocally(String plate, int modelYear, String status) {
        jdbc.sql("""
                        INSERT INTO vehicle (plate_number, chassis_number, brand, model, model_year, kasko_value,
                                             status, source_file_date, updated_at)
                        VALUES (:plate, 'NMTBA3BE10R123456', 'TOYOTA', 'COROLLA', :year, :value, :status,
                                DATE '2026-10-21', SYSTIMESTAMP)""")
                .param("plate", plate)
                .param("year", modelYear)
                .param("value", new BigDecimal("900000.00"))
                .param("status", status)
                .update();
    }
}
