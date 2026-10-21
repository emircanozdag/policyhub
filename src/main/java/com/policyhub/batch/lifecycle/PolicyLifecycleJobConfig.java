package com.policyhub.batch.lifecycle;

import java.time.LocalDate;

import javax.sql.DataSource;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.JpaItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

import com.policyhub.batch.PolicyHubBatchProperties;
import com.policyhub.quote.Quote;

import jakarta.persistence.EntityManagerFactory;

/**
 * Nightly policy lifecycle run for a given business date:
 * <ol>
 *   <li>{@code expireQuotesStep}: active quotes past their validity become EXPIRED.</li>
 *   <li>{@code expirePoliciesStep}: active policies past their end date become EXPIRED.</li>
 *   <li>{@code renewalQuotesStep}: policies ending within the renewal window get a re-priced renewal quote.</li>
 * </ol>
 * The business date is the identifying job parameter: each date runs once, and a failed run is restarted
 * rather than re-run. Renewals are additionally protected by a unique constraint on the quote table.
 */
@Configuration
public class PolicyLifecycleJobConfig {

    public static final String JOB_NAME = "policyLifecycleJob";
    public static final String BUSINESS_DATE = "businessDate";

    /*
     * A cursor (not a paging) reader on purpose: writing a renewal quote removes the policy from the
     * NOT EXISTS result set, which would make offset-based pages skip rows.
     */
    private static final String RENEWAL_CANDIDATES_SQL = """
            SELECT p.id AS policy_id, p.policy_number, p.product_type, p.plate_number, p.end_date,
                   q.vehicle_model_year, q.vehicle_value, q.driver_age, q.claim_count,
                   v.kasko_value AS current_kasko_value, v.status AS vehicle_status
              FROM policy p
              JOIN quote q ON q.id = p.quote_id
              LEFT JOIN vehicle v ON v.plate_number = p.plate_number
             WHERE p.status = 'ACTIVE'
               AND p.end_date BETWEEN ? AND ?
               AND NOT EXISTS (SELECT 1 FROM quote r WHERE r.renewal_of_policy_id = p.id)
             ORDER BY p.id
            """;

    private final PolicyHubBatchProperties.Lifecycle properties;

    public PolicyLifecycleJobConfig(PolicyHubBatchProperties properties) {
        this.properties = properties.lifecycle();
    }

    @Bean
    Job policyLifecycleJob(JobRepository jobRepository, Step expireQuotesStep, Step expirePoliciesStep,
                           Step renewalQuotesStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(expireQuotesStep)
                .next(expirePoliciesStep)
                .next(renewalQuotesStep)
                .build();
    }

    @Bean
    Step expireQuotesStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                          Tasklet expireQuotesTasklet) {
        return new StepBuilder("expireQuotesStep", jobRepository)
                .tasklet(expireQuotesTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    Tasklet expireQuotesTasklet(JdbcClient jdbc, @Value("#{jobParameters['" + BUSINESS_DATE + "']}") LocalDate businessDate) {
        return bulkUpdate(jdbc, "UPDATE quote SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND valid_until < :businessDate",
                businessDate);
    }

    @Bean
    Step expirePoliciesStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                            Tasklet expirePoliciesTasklet) {
        return new StepBuilder("expirePoliciesStep", jobRepository)
                .tasklet(expirePoliciesTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    Tasklet expirePoliciesTasklet(JdbcClient jdbc, @Value("#{jobParameters['" + BUSINESS_DATE + "']}") LocalDate businessDate) {
        return bulkUpdate(jdbc, "UPDATE policy SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND end_date < :businessDate",
                businessDate);
    }

    @Bean
    Step renewalQuotesStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                           JdbcCursorItemReader<RenewalCandidate> renewalCandidateReader,
                           RenewalQuoteProcessor renewalQuoteProcessor,
                           JpaItemWriter<Quote> renewalQuoteWriter) {
        return new StepBuilder("renewalQuotesStep", jobRepository)
                .<RenewalCandidate, Quote>chunk(properties.chunkSize())
                .transactionManager(transactionManager)
                .reader(renewalCandidateReader)
                .processor(renewalQuoteProcessor)
                .writer(renewalQuoteWriter)
                .build();
    }

    @Bean
    @StepScope
    JdbcCursorItemReader<RenewalCandidate> renewalCandidateReader(
            DataSource dataSource, @Value("#{jobParameters['" + BUSINESS_DATE + "']}") LocalDate businessDate) {
        return new JdbcCursorItemReaderBuilder<RenewalCandidate>()
                .name("renewalCandidateReader")
                .dataSource(dataSource)
                .sql(RENEWAL_CANDIDATES_SQL)
                .queryArguments(businessDate.plusDays(1), businessDate.plusDays(properties.renewalWindowDays()))
                .rowMapper(new DataClassRowMapper<>(RenewalCandidate.class))
                .build();
    }

    @Bean
    JpaItemWriter<Quote> renewalQuoteWriter(EntityManagerFactory entityManagerFactory) {
        return new JpaItemWriterBuilder<Quote>()
                .entityManagerFactory(entityManagerFactory)
                .usePersist(true)
                .build();
    }

    private static Tasklet bulkUpdate(JdbcClient jdbc, String sql, LocalDate businessDate) {
        return (contribution, chunkContext) -> {
            int updated = jdbc.sql(sql).param("businessDate", businessDate).update();
            contribution.incrementWriteCount(updated);
            return RepeatStatus.FINISHED;
        };
    }
}
