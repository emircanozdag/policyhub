package com.policyhub.batch.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.policyhub.AbstractOracleIT;
import com.policyhub.batch.BatchJobService;
import com.policyhub.policy.Policy;
import com.policyhub.policy.PolicyRepository;
import com.policyhub.policy.PolicyStatus;
import com.policyhub.premium.ProductType;
import com.policyhub.quote.Quote;
import com.policyhub.quote.QuoteRepository;
import com.policyhub.quote.QuoteStatus;

class PolicyLifecycleJobIT extends AbstractOracleIT {

    /** A fixed business date in the past, so data created by other ITs (dated "today") is never in scope. */
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2025, 3, 10);

    @Autowired
    private BatchJobService batchJobService;
    @Autowired
    private QuoteRepository quoteRepository;
    @Autowired
    private PolicyRepository policyRepository;
    @Autowired
    private JdbcClient jdbc;

    @Test
    void expiresStaleRecordsAndCreatesRenewalQuotesExactlyOnce() throws Exception {
        Quote staleQuote = quoteRepository.save(quote("34LCY000", BUSINESS_DATE.minusDays(1)));
        Policy ended = issue("34LCY001", BUSINESS_DATE.minusDays(1));
        Policy renewable = issue("34LCY002", BUSINESS_DATE.plusDays(15));
        Policy scrapped = issue("34LCY003", BUSINESS_DATE.plusDays(10));
        Policy notYetDue = issue("34LCY004", BUSINESS_DATE.plusDays(60));
        registerVehicle("34LCY002", "800000.00", "ACTIVE");
        registerVehicle("34LCY003", "500000.00", "SCRAPPED");

        var execution = batchJobService.runPolicyLifecycle(BUSINESS_DATE);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(quoteRepository.findById(staleQuote.getId()).orElseThrow().getStatus()).isEqualTo(QuoteStatus.EXPIRED);
        assertThat(policyRepository.findById(ended.getId()).orElseThrow().getStatus()).isEqualTo(PolicyStatus.EXPIRED);
        assertThat(policyRepository.findById(renewable.getId()).orElseThrow().getStatus()).isEqualTo(PolicyStatus.ACTIVE);

        List<Map<String, Object>> renewals = renewalsOf(renewable);
        assertThat(renewals).singleElement().satisfies(renewal -> {
            // 800,000 (registry value) x 2.5% x 1.10 (vehicle 4 years old) = 22,000
            assertThat((BigDecimal) renewal.get("PREMIUM")).isEqualByComparingTo("22000.00");
            assertThat(renewal.get("STATUS")).isEqualTo("ACTIVE");
            assertThat(renewal.get("DRIVER_AGE")).isEqualTo(BigDecimal.valueOf(41));
        });
        assertThat(renewalsOf(scrapped)).isEmpty();
        assertThat(renewalsOf(notYetDue)).isEmpty();

        // The next night's run must not create a second renewal for the same policy
        batchJobService.runPolicyLifecycle(BUSINESS_DATE.plusDays(1));
        assertThat(renewalsOf(renewable)).hasSize(1);

        assertThatThrownBy(() -> batchJobService.runPolicyLifecycle(BUSINESS_DATE))
                .isInstanceOf(JobInstanceAlreadyCompleteException.class);
    }

    private Policy issue(String plate, LocalDate endDate) {
        Quote quote = quote(plate, endDate.minusYears(1).plusDays(15));
        quote.convert(endDate.minusYears(1));
        quote = quoteRepository.save(quote);
        return policyRepository.save(new Policy("P-" + plate, quote.getId(), ProductType.KASKO, plate,
                quote.getPremium(), endDate.minusYears(1), endDate, LocalDateTime.now()));
    }

    private static Quote quote(String plate, LocalDate validUntil) {
        return new Quote("Q-" + plate, ProductType.KASKO, plate, 2021, new BigDecimal("600000.00"), 40, 0,
                new BigDecimal("15000.00"), validUntil, LocalDateTime.now());
    }

    private void registerVehicle(String plate, String kaskoValue, String status) {
        jdbc.sql("""
                        INSERT INTO vehicle (plate_number, chassis_number, brand, model, model_year, kasko_value,
                                             status, source_file_date, updated_at)
                        VALUES (:plate, 'NMTBA3BE10R123456', 'TOYOTA', 'COROLLA', 2021, :value, :status, :fileDate, SYSTIMESTAMP)""")
                .param("plate", plate)
                .param("value", new BigDecimal(kaskoValue))
                .param("status", status)
                .param("fileDate", BUSINESS_DATE)
                .update();
    }

    private List<Map<String, Object>> renewalsOf(Policy policy) {
        return jdbc.sql("SELECT premium, status, driver_age FROM quote WHERE renewal_of_policy_id = :id")
                .param("id", policy.getId())
                .query()
                .listOfRows();
    }
}
