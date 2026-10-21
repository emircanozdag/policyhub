package com.policyhub.batch;

import java.util.List;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;

public record JobExecutionResponse(
        String jobName,
        long executionId,
        String status,
        String exitCode,
        String exitDescription,
        List<StepSummary> steps) {

    public record StepSummary(String name, String status, long readCount, long writeCount, long filterCount,
                              long skipCount) {

        static StepSummary from(StepExecution step) {
            return new StepSummary(step.getStepName(), step.getStatus().name(), step.getReadCount(),
                    step.getWriteCount(), step.getFilterCount(), step.getSkipCount());
        }
    }

    static JobExecutionResponse from(JobExecution execution) {
        return new JobExecutionResponse(
                execution.getJobInstance().getJobName(),
                execution.getId(),
                execution.getStatus().name(),
                execution.getExitStatus().getExitCode(),
                execution.getExitStatus().getExitDescription(),
                execution.getStepExecutions().stream().map(StepSummary::from).toList());
    }
}
