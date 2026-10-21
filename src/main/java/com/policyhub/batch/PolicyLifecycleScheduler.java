package com.policyhub.batch;

import java.time.Clock;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "policyhub.batch.lifecycle.schedule-enabled", havingValue = "true", matchIfMissing = true)
public class PolicyLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(PolicyLifecycleScheduler.class);

    private final BatchJobService batchJobService;
    private final Clock clock;

    public PolicyLifecycleScheduler(BatchJobService batchJobService, Clock clock) {
        this.batchJobService = batchJobService;
        this.clock = clock;
    }

    @Scheduled(cron = "${policyhub.batch.lifecycle.cron}", zone = "Europe/Istanbul")
    public void runNightly() {
        LocalDate businessDate = LocalDate.now(clock);
        try {
            JobExecution execution = batchJobService.runPolicyLifecycle(businessDate);
            log.info("Policy lifecycle run for {} finished with {}", businessDate, execution.getExitStatus().getExitCode());
        } catch (JobInstanceAlreadyCompleteException e) {
            log.info("Policy lifecycle run for {} already completed, skipping", businessDate);
        } catch (Exception e) {
            log.error("Policy lifecycle run for {} could not be started", businessDate, e);
        }
    }
}
