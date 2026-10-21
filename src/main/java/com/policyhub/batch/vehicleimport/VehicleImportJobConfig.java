package com.policyhub.batch.vehicleimport;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.LocalDate;

import javax.sql.DataSource;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

import com.policyhub.batch.PolicyHubBatchProperties;
import com.policyhub.vehicle.Vehicle;

/**
 * Loads the daily vehicle registry / kasko value extract produced on the mainframe.
 * <ol>
 *   <li>{@code validateVehicleFileStep}: envelope and control-total check; a bad file loads nothing.</li>
 *   <li>{@code loadVehiclesStep}: chunked fixed-width read, validation, Oracle {@code MERGE} upsert.
 *       Invalid detail records are skipped and logged up to the configured limit.</li>
 * </ol>
 * The input file is the identifying job parameter, so the same file cannot be imported twice.
 */
@Configuration
public class VehicleImportJobConfig {

    public static final String JOB_NAME = "vehicleImportJob";
    public static final String INPUT_FILE = "inputFile";
    public static final String ENCODING = "encoding";
    static final String FILE_DATE_KEY = "vehicleImport.fileDate";

    private static final String UPSERT_SQL = """
            MERGE INTO vehicle v
            USING (SELECT :plateNumber AS plate_number FROM dual) src
               ON (v.plate_number = src.plate_number)
            WHEN MATCHED THEN UPDATE SET
                 v.chassis_number = :chassisNumber, v.brand = :brand, v.model = :model,
                 v.model_year = :modelYear, v.kasko_value = :kaskoValue, v.status = :status,
                 v.source_file_date = :sourceFileDate, v.updated_at = SYSTIMESTAMP
            WHEN NOT MATCHED THEN INSERT
                 (plate_number, chassis_number, brand, model, model_year, kasko_value, status, source_file_date, updated_at)
                 VALUES (:plateNumber, :chassisNumber, :brand, :model, :modelYear, :kaskoValue, :status, :sourceFileDate, SYSTIMESTAMP)
            """;

    private final PolicyHubBatchProperties.VehicleImport properties;

    public VehicleImportJobConfig(PolicyHubBatchProperties properties) {
        this.properties = properties.vehicleImport();
    }

    @Bean
    Job vehicleImportJob(JobRepository jobRepository, Step validateVehicleFileStep, Step loadVehiclesStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(validateVehicleFileStep)
                .next(loadVehiclesStep)
                .build();
    }

    @Bean
    Step validateVehicleFileStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                 Tasklet validateVehicleFileTasklet) {
        return new StepBuilder("validateVehicleFileStep", jobRepository)
                .tasklet(validateVehicleFileTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    Tasklet validateVehicleFileTasklet(@Value("#{jobParameters['" + INPUT_FILE + "']}") String inputFile,
                                       @Value("#{jobParameters['" + ENCODING + "']}") String encoding) {
        return (contribution, chunkContext) -> {
            var totals = new VehicleFileValidator().validate(Path.of(inputFile), charset(encoding));
            chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                    .putString(FILE_DATE_KEY, totals.header().fileDate().toString());
            contribution.incrementReadCount(); // one file checked
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    Step loadVehiclesStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                          FlatFileItemReader<VehicleFileRecord> vehicleFileReader,
                          VehicleRecordProcessor vehicleRecordProcessor,
                          JdbcBatchItemWriter<Vehicle> vehicleUpsertWriter) {
        return new StepBuilder("loadVehiclesStep", jobRepository)
                .<VehicleFileRecord, Vehicle>chunk(properties.chunkSize())
                .transactionManager(transactionManager)
                .reader(vehicleFileReader)
                .processor(vehicleRecordProcessor)
                .writer(vehicleUpsertWriter)
                .faultTolerant()
                .skip(FlatFileParseException.class, InvalidVehicleRecordException.class)
                .skipLimit(properties.skipLimit())
                .skipListener(new RejectedVehicleRecordLogger())
                .build();
    }

    @Bean
    @StepScope
    FlatFileItemReader<VehicleFileRecord> vehicleFileReader(
            @Value("#{jobParameters['" + INPUT_FILE + "']}") String inputFile,
            @Value("#{jobParameters['" + ENCODING + "']}") String encoding) {
        return new FlatFileItemReaderBuilder<VehicleFileRecord>()
                .name("vehicleFileReader")
                .resource(new FileSystemResource(inputFile))
                .encoding(charset(encoding).name())
                .lineMapper(VehicleRecordLayout.lineMapper())
                .build();
    }

    @Bean
    @StepScope
    VehicleRecordProcessor vehicleRecordProcessor(
            @Value("#{jobExecutionContext['" + FILE_DATE_KEY + "']}") String fileDate) {
        return new VehicleRecordProcessor(LocalDate.parse(fileDate));
    }

    @Bean
    JdbcBatchItemWriter<Vehicle> vehicleUpsertWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Vehicle>()
                .dataSource(dataSource)
                .sql(UPSERT_SQL)
                .itemSqlParameterSourceProvider(vehicle -> new MapSqlParameterSource()
                        .addValue("plateNumber", vehicle.plateNumber())
                        .addValue("chassisNumber", vehicle.chassisNumber())
                        .addValue("brand", vehicle.brand())
                        .addValue("model", vehicle.model())
                        .addValue("modelYear", vehicle.modelYear())
                        .addValue("kaskoValue", vehicle.kaskoValue())
                        .addValue("status", vehicle.status().name())
                        .addValue("sourceFileDate", vehicle.sourceFileDate()))
                .build();
    }

    private Charset charset(String encoding) {
        return Charset.forName(encoding != null ? encoding : properties.encoding());
    }
}
