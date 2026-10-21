package com.policyhub.batch.vehicleimport;

import static com.policyhub.batch.vehicleimport.VehicleFileFixture.detail;
import static com.policyhub.batch.vehicleimport.VehicleFileFixture.header;
import static com.policyhub.batch.vehicleimport.VehicleFileFixture.trailer;
import static com.policyhub.batch.vehicleimport.VehicleFileFixture.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.policyhub.AbstractOracleIT;
import com.policyhub.batch.BatchJobService;
import com.policyhub.vehicle.VehicleRepository;
import com.policyhub.vehicle.VehicleStatus;

class VehicleImportJobIT extends AbstractOracleIT {

    private static final LocalDate FILE_DATE = LocalDate.of(2026, 10, 21);

    @Autowired
    private BatchJobService batchJobService;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private MockMvc mockMvc;

    @TempDir
    Path dir;

    @Test
    void loadsValidRecordsAndSkipsInvalidOnes() throws Exception {
        Path file = write(dir, "VEHREG.SKIPS.DAT", header(FILE_DATE),
                detail("34IMP001", 2023, 1_250_000_00L, "A"),
                detail("34IMP002", 2019, 450_000_00L, "S"),
                detail("34IMP003", "TOO-SHORT", 2023, VehicleFileFixture.value(1L), "A"),         // processor skip
                detail("34IMP004", "NMTBA3BE10R123456", 2023, "00000ABC00000", "A"),             // read skip
                trailer(4));

        JobExecution execution = batchJobService.importVehicleFile(file);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        StepExecution load = step(execution, "loadVehiclesStep");
        assertThat(load.getWriteCount()).isEqualTo(2);
        assertThat(load.getProcessSkipCount()).isEqualTo(1);
        assertThat(load.getReadSkipCount()).isEqualTo(1);

        assertThat(vehicleRepository.findByPlateNumber("34IMP001")).hasValueSatisfying(vehicle -> {
            assertThat(vehicle.kaskoValue()).isEqualByComparingTo("1250000.00");
            assertThat(vehicle.sourceFileDate()).isEqualTo(FILE_DATE);
        });
        assertThat(vehicleRepository.findByPlateNumber("34IMP002"))
                .hasValueSatisfying(vehicle -> assertThat(vehicle.status()).isEqualTo(VehicleStatus.SCRAPPED));
        assertThat(vehicleRepository.findByPlateNumber("34IMP003")).isEmpty();
        assertThat(vehicleRepository.findByPlateNumber("34IMP004")).isEmpty();
    }

    @Test
    void laterFileUpdatesExistingVehicles() throws Exception {
        batchJobService.importVehicleFile(write(dir, "VEHREG.DAY1.DAT", header(FILE_DATE),
                detail("34UPD001", 2022, 900_000_00L, "A"), trailer(1)));
        batchJobService.importVehicleFile(write(dir, "VEHREG.DAY2.DAT", header(FILE_DATE.plusDays(1)),
                detail("34UPD001", 2022, 950_000_00L, "S"), trailer(1)));

        assertThat(vehicleRepository.findByPlateNumber("34UPD001")).hasValueSatisfying(vehicle -> {
            assertThat(vehicle.kaskoValue()).isEqualByComparingTo("950000.00");
            assertThat(vehicle.status()).isEqualTo(VehicleStatus.SCRAPPED);
            assertThat(vehicle.sourceFileDate()).isEqualTo(FILE_DATE.plusDays(1));
        });
    }

    @Test
    void rejectsWholeFileWhenControlTotalsDoNotMatch() throws Exception {
        Path file = write(dir, "VEHREG.BAD.DAT", header(FILE_DATE), detail("34BAD001", 2023, 100_000_00L, "A"), trailer(5));

        JobExecution execution = batchJobService.importVehicleFile(file);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(step(execution, "validateVehicleFileStep").getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getStepExecutions()).extracting(StepExecution::getStepName)
                .doesNotContain("loadVehiclesStep");
        assertThat(vehicleRepository.findByPlateNumber("34BAD001")).isEmpty();
    }

    @Test
    void sameFileCannotBeImportedTwice() throws Exception {
        Path file = write(dir, "VEHREG.ONCE.DAT", header(FILE_DATE), detail("34ONC001", 2023, 100_000_00L, "A"), trailer(1));
        batchJobService.importVehicleFile(file);

        assertThatThrownBy(() -> batchJobService.importVehicleFile(file))
                .isInstanceOf(JobInstanceAlreadyCompleteException.class);
    }

    @Test
    void importsSampleFileFromInboundDirectoryThroughTheApi() throws Exception {
        mockMvc.perform(post("/api/v1/batch/vehicle-import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"VEHREG.20261021.DAT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.steps[1].writeCount").value(5))
                .andExpect(jsonPath("$.steps[1].skipCount").value(1));

        mockMvc.perform(get("/api/v1/vehicles/34 ABC 123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand").value("TOYOTA"))
                .andExpect(jsonPath("$.kaskoValue").value(1250000.00));

        mockMvc.perform(post("/api/v1/batch/vehicle-import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"VEHREG.20261021.DAT\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void refusesPathsOutsideTheInboundDirectory() throws Exception {
        mockMvc.perform(post("/api/v1/batch/vehicle-import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"../../pom.xml\"}"))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/v1/batch/vehicle-import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"MISSING.DAT\"}"))
                .andExpect(status().isNotFound());
    }

    private static StepExecution step(JobExecution execution, String name) {
        return execution.getStepExecutions().stream()
                .filter(step -> step.getStepName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
