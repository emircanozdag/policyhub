package com.policyhub.batch;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Operational endpoints to trigger batch jobs on demand (e.g. re-delivery of a file, catch-up runs). */
@RestController
@RequestMapping("/api/v1/batch")
public class BatchJobController {

    public record VehicleImportRequest(@NotBlank String fileName) {
    }

    private final BatchJobService batchJobService;
    private final Clock clock;

    public BatchJobController(BatchJobService batchJobService, Clock clock) {
        this.batchJobService = batchJobService;
        this.clock = clock;
    }

    @PostMapping("/vehicle-import")
    public JobExecutionResponse importVehicles(@Valid @RequestBody VehicleImportRequest request) throws Exception {
        return JobExecutionResponse.from(batchJobService.importVehicleFile(request.fileName()));
    }

    @PostMapping("/policy-lifecycle")
    public JobExecutionResponse runPolicyLifecycle(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate)
            throws Exception {
        return JobExecutionResponse.from(
                batchJobService.runPolicyLifecycle(businessDate != null ? businessDate : LocalDate.now(clock)));
    }
}
