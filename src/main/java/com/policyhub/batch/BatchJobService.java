package com.policyhub.batch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.regex.Pattern;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.policyhub.batch.lifecycle.PolicyLifecycleJobConfig;
import com.policyhub.batch.vehicleimport.VehicleImportJobConfig;
import com.policyhub.common.BusinessRuleException;
import com.policyhub.common.NotFoundException;

/** Single entry point for launching batch jobs, used by the REST API and the scheduler. */
@Service
public class BatchJobService {

    private static final Pattern SAFE_FILE_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$");

    private final JobOperator jobOperator;
    private final Job vehicleImportJob;
    private final Job policyLifecycleJob;
    private final PolicyHubBatchProperties properties;

    public BatchJobService(JobOperator jobOperator,
                           @Qualifier(VehicleImportJobConfig.JOB_NAME) Job vehicleImportJob,
                           @Qualifier(PolicyLifecycleJobConfig.JOB_NAME) Job policyLifecycleJob,
                           PolicyHubBatchProperties properties) {
        this.jobOperator = jobOperator;
        this.vehicleImportJob = vehicleImportJob;
        this.policyLifecycleJob = policyLifecycleJob;
        this.properties = properties;
    }

    /** Imports a file from the inbound directory. Only a bare file name is accepted, never a path. */
    public JobExecution importVehicleFile(String fileName) throws Exception {
        return importVehicleFile(resolveInboundFile(fileName));
    }

    public JobExecution importVehicleFile(Path file) throws Exception {
        var parameters = new JobParametersBuilder()
                .addString(VehicleImportJobConfig.INPUT_FILE, file.toAbsolutePath().normalize().toString())
                .addString(VehicleImportJobConfig.ENCODING, properties.vehicleImport().encoding(), false)
                .toJobParameters();
        return jobOperator.start(vehicleImportJob, parameters);
    }

    public JobExecution runPolicyLifecycle(LocalDate businessDate) throws Exception {
        var parameters = new JobParametersBuilder()
                .addLocalDate(PolicyLifecycleJobConfig.BUSINESS_DATE, businessDate)
                .toJobParameters();
        return jobOperator.start(policyLifecycleJob, parameters);
    }

    private Path resolveInboundFile(String fileName) throws IOException {
        if (fileName == null || !SAFE_FILE_NAME.matcher(fileName).matches() || fileName.contains("..")) {
            throw new BusinessRuleException("Invalid file name: " + fileName);
        }
        Path inbound = properties.vehicleImport().inboundDir().toRealPath();
        Path file = inbound.resolve(fileName).normalize();
        if (!file.startsWith(inbound) || !Files.isRegularFile(file)) {
            throw new NotFoundException("File not found in inbound directory: " + fileName);
        }
        return file;
    }
}
