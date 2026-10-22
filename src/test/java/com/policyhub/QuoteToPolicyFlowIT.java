package com.policyhub;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/** End-to-end quote to policy flow against a real Oracle database (Flyway migrations + JPA mappings). */
class QuoteToPolicyFlowIT extends AbstractOracleIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void quoteCanBeIssuedAsPolicyExactlyOnce() throws Exception {
        String quoteJson = mockMvc.perform(post("/api/v1/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productType":"TRAFFIC","plateNumber":"06 AB 1234","vehicleModelYear":2022,
                                 "vehicleValue":750000,"driverAge":40,"claimCountLast3Years":0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.premium").value(7200.00))
                .andReturn().getResponse().getContentAsString();
        String quoteNumber = JsonPath.read(quoteJson, "$.quoteNumber");

        String issueBody = "{\"quoteNumber\":\"%s\",\"paymentToken\":\"tok_visa\"}".formatted(quoteNumber);
        String policyJson = mockMvc.perform(post("/api/v1/policies")
                        .contentType(MediaType.APPLICATION_JSON).content(issueBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.paymentReference").value(DEFAULT_PAYMENT_ID))
                .andReturn().getResponse().getContentAsString();
        String policyNumber = JsonPath.read(policyJson, "$.policyNumber");

        mockMvc.perform(get("/api/v1/quotes/" + quoteNumber))
                .andExpect(jsonPath("$.status").value("CONVERTED"));
        mockMvc.perform(get("/api/v1/policies/" + policyNumber))
                .andExpect(jsonPath("$.plateNumber").value("06AB1234"));
        mockMvc.perform(post("/api/v1/policies")
                        .contentType(MediaType.APPLICATION_JSON).content(issueBody))
                .andExpect(status().isUnprocessableContent());
    }
}
