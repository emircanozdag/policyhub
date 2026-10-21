package com.policyhub.batch.vehicleimport;

import static com.policyhub.batch.vehicleimport.VehicleFileFixture.detail;
import static com.policyhub.batch.vehicleimport.VehicleFileFixture.header;
import static com.policyhub.batch.vehicleimport.VehicleFileFixture.trailer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.file.mapping.PatternMatchingCompositeLineMapper;

class VehicleRecordLayoutTest {

    private final PatternMatchingCompositeLineMapper<VehicleFileRecord> mapper = VehicleRecordLayout.lineMapper();

    @Test
    void mapsHeader() throws Exception {
        assertThat(mapper.mapLine(header(LocalDate.of(2026, 10, 21)), 1))
                .isEqualTo(new VehicleFileRecord.Header(LocalDate.of(2026, 10, 21), "MFVEHREG"));
    }

    @Test
    void mapsDetailWithImpliedDecimals() throws Exception {
        VehicleFileRecord record = mapper.mapLine(detail("34ABC123", 2023, 1_250_000_75L, "A"), 2);

        assertThat(record).isEqualTo(new VehicleFileRecord.Detail("34ABC123", "NMTBA3BE10R123456", "TOYOTA",
                "COROLLA 1.5", 2023, new BigDecimal("1250000.75"), "A"));
    }

    @Test
    void mapsTrailer() throws Exception {
        assertThat(mapper.mapLine(trailer(42), 3)).isEqualTo(new VehicleFileRecord.Trailer(42));
    }

    @Test
    void toleratesStrippedTrailingFiller() throws Exception {
        String stripped = detail("34ABC123", 2023, 1_000_00L, "A").stripTrailing();

        assertThat(mapper.mapLine(stripped, 2)).isInstanceOf(VehicleFileRecord.Detail.class);
    }

    @Test
    void rejectsNonNumericZonedDecimal() {
        String corrupt = detail("34ABC123", "NMTBA3BE10R123456", 2023, "000009X500000", "A");

        assertThatThrownBy(() -> mapper.mapLine(corrupt, 2)).isInstanceOf(NumberFormatException.class);
    }
}
