package com.policyhub.batch.vehicleimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.policyhub.vehicle.Vehicle;
import com.policyhub.vehicle.VehicleStatus;

class VehicleRecordProcessorTest {

    private static final LocalDate FILE_DATE = LocalDate.of(2026, 10, 21);

    private final VehicleRecordProcessor processor = new VehicleRecordProcessor(FILE_DATE);

    @Test
    void filtersHeaderAndTrailer() {
        assertThat(processor.process(new VehicleFileRecord.Header(FILE_DATE, "MFVEHREG"))).isNull();
        assertThat(processor.process(new VehicleFileRecord.Trailer(3))).isNull();
    }

    @Test
    void mapsValidDetail() {
        Vehicle vehicle = processor.process(detail("34 ABC 123", "NMTBA3BE10R123456", 2023, "1250000.00", "S"));

        assertThat(vehicle).isEqualTo(new Vehicle("34ABC123", "NMTBA3BE10R123456", "TOYOTA", "COROLLA",
                2023, new BigDecimal("1250000.00"), VehicleStatus.SCRAPPED, FILE_DATE));
    }

    @ParameterizedTest(name = "{5}")
    @CsvSource({
            "99ABC123, NMTBA3BE10R123456, 2023, 1000.00, A, invalid plate number",
            "34ABC123, SHORTVIN,          2023, 1000.00, A, chassis number",
            "34ABC123, NMTBA3BE10R123456, 1949, 1000.00, A, model year",
            "34ABC123, NMTBA3BE10R123456, 2028, 1000.00, A, model year",
            "34ABC123, NMTBA3BE10R123456, 2023, 0.00,    A, must be positive",
            "34ABC123, NMTBA3BE10R123456, 2023, 1000.00, X, unknown status code"
    })
    void rejectsInvalidDetail(String plate, String chassis, int year, String value, String status, String reason) {
        assertThatThrownBy(() -> processor.process(detail(plate, chassis, year, value, status)))
                .isInstanceOf(InvalidVehicleRecordException.class)
                .hasMessageContaining(reason);
    }

    private static VehicleFileRecord.Detail detail(String plate, String chassis, int year, String value, String status) {
        return new VehicleFileRecord.Detail(plate, chassis, "TOYOTA", "COROLLA", year, new BigDecimal(value), status);
    }
}
