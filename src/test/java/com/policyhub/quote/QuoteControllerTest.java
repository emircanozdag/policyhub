package com.policyhub.quote;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.policyhub.common.NotFoundException;
import com.policyhub.premium.ProductType;

@WebMvcTest(QuoteController.class)
class QuoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuoteService quoteService;

    @Test
    void createsQuote() throws Exception {
        when(quoteService.createQuote(any())).thenReturn(new QuoteResponse("Q-20261021-000001", ProductType.KASKO,
                "34ABC123", new BigDecimal("25000.00"), "TRY", QuoteStatus.ACTIVE, LocalDate.of(2026, 11, 5)));

        mockMvc.perform(post("/api/v1/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productType":"KASKO","plateNumber":"34 ABC 123","vehicleModelYear":2024,
                                 "vehicleValue":1000000,"driverAge":35,"claimCountLast3Years":0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/quotes/Q-20261021-000001"))
                .andExpect(jsonPath("$.premium").value(25000.00))
                .andExpect(jsonPath("$.currency").value("TRY"));
    }

    @Test
    void rejectsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productType":"KASKO","plateNumber":"not-a-plate","vehicleModelYear":2024,
                                 "vehicleValue":1000000,"driverAge":16,"claimCountLast3Years":0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsProblemDetailWhenQuoteMissing() throws Exception {
        when(quoteService.getQuote("Q-404")).thenThrow(new NotFoundException("Quote not found: Q-404"));

        mockMvc.perform(get("/api/v1/quotes/Q-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Quote not found: Q-404"));
    }
}
