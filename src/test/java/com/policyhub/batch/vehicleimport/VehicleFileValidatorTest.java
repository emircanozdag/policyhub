package com.policyhub.batch.vehicleimport;

import static com.policyhub.batch.vehicleimport.VehicleFileFixture.detail;
import static com.policyhub.batch.vehicleimport.VehicleFileFixture.header;
import static com.policyhub.batch.vehicleimport.VehicleFileFixture.trailer;
import static com.policyhub.batch.vehicleimport.VehicleFileFixture.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VehicleFileValidatorTest {

    private static final LocalDate FILE_DATE = LocalDate.of(2026, 10, 21);

    private final VehicleFileValidator validator = new VehicleFileValidator();

    @TempDir
    Path dir;

    @Test
    void returnsControlTotalsForWellFormedFile() throws Exception {
        Path file = write(dir, "ok.dat", header(FILE_DATE),
                detail("34ABC123", 2023, 100_000_00L, "A"),
                detail("06AB1234", 2021, 200_000_00L, "A"),
                trailer(2));

        var totals = validator.validate(file, StandardCharsets.UTF_8);

        assertThat(totals.header().fileDate()).isEqualTo(FILE_DATE);
        assertThat(totals.detailCount()).isEqualTo(2);
    }

    @Test
    void countsMalformedDetailsTooSoTheyAreSkippedNotLost() throws Exception {
        Path file = write(dir, "corrupt-detail.dat", header(FILE_DATE),
                detail("34ABC123", "NMTBA3BE10R123456", 2023, "00000ABC00000", "A"),
                trailer(1));

        assertThat(validator.validate(file, StandardCharsets.UTF_8).detailCount()).isEqualTo(1);
    }

    @Test
    void rejectsTrailerCountMismatch() throws Exception {
        Path file = write(dir, "mismatch.dat", header(FILE_DATE), detail("34ABC123", 2023, 100_000_00L, "A"), trailer(2));

        assertThatThrownBy(() -> validator.validate(file, StandardCharsets.UTF_8))
                .isInstanceOf(VehicleFileFormatException.class)
                .hasMessageContaining("declares 2").hasMessageContaining("contains 1");
    }

    @Test
    void rejectsTruncatedFileWithoutTrailer() throws Exception {
        Path file = write(dir, "truncated.dat", header(FILE_DATE), detail("34ABC123", 2023, 100_000_00L, "A"));

        assertThatThrownBy(() -> validator.validate(file, StandardCharsets.UTF_8))
                .isInstanceOf(VehicleFileFormatException.class)
                .hasMessageContaining("Missing trailer");
    }

    @Test
    void rejectsFileWithoutHeader() throws Exception {
        Path file = write(dir, "no-header.dat", detail("34ABC123", 2023, 100_000_00L, "A"), trailer(1));

        assertThatThrownBy(() -> validator.validate(file, StandardCharsets.UTF_8))
                .isInstanceOf(VehicleFileFormatException.class)
                .hasMessageContaining("Missing header");
    }

    @Test
    void rejectsRecordsAfterTrailer() throws Exception {
        Path file = write(dir, "after-trailer.dat", header(FILE_DATE), trailer(0), detail("34ABC123", 2023, 1L, "A"));

        assertThatThrownBy(() -> validator.validate(file, StandardCharsets.UTF_8))
                .isInstanceOf(VehicleFileFormatException.class)
                .hasMessageContaining("after trailer");
    }

    @Test
    void rejectsUnknownRecordType() throws Exception {
        Path file = write(dir, "unknown.dat", header(FILE_DATE), "X" + " ".repeat(99), trailer(0));

        assertThatThrownBy(() -> validator.validate(file, StandardCharsets.UTF_8))
                .isInstanceOf(VehicleFileFormatException.class)
                .hasMessageContaining("Unknown record type 'X' at line 2");
    }

    @Test
    void rejectsRecordsLongerThanLrecl() throws Exception {
        Path file = write(dir, "long.dat", header(FILE_DATE) + "EXTRA", trailer(0));

        assertThatThrownBy(() -> validator.validate(file, StandardCharsets.UTF_8))
                .isInstanceOf(VehicleFileFormatException.class)
                .hasMessageContaining("LRECL");
    }
}
