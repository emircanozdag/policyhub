package com.policyhub.batch.vehicleimport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Builds VEHREC records byte-exact to the copybook, so tests read like the file they describe. */
public final class VehicleFileFixture {

    private VehicleFileFixture() {
    }

    public static String header(LocalDate fileDate) {
        return pad("H%s%-8s".formatted(fileDate.format(DateTimeFormatter.BASIC_ISO_DATE), "MFVEHREG"));
    }

    /** @param kasko13 the raw 13-digit PIC 9(11)V99 field, e.g. {@code value(1_250_000_00L)} */
    public static String detail(String plate, String chassis, int modelYear, String kasko13, String status) {
        return pad("D%-10s%-17s%-15s%-20s%04d%13s%s".formatted(plate, chassis, "TOYOTA", "COROLLA 1.5", modelYear,
                kasko13, status));
    }

    public static String detail(String plate, int modelYear, long kaskoKurus, String status) {
        return detail(plate, "NMTBA3BE10R123456", modelYear, value(kaskoKurus), status);
    }

    public static String trailer(long recordCount) {
        return pad("T%09d".formatted(recordCount));
    }

    public static String value(long kurus) {
        return "%013d".formatted(kurus);
    }

    public static Path write(Path dir, String fileName, String... records) throws IOException {
        return Files.write(dir.resolve(fileName), List.of(records));
    }

    private static String pad(String record) {
        if (record.length() > VehicleRecordLayout.RECORD_LENGTH) {
            throw new IllegalArgumentException("Record longer than LRECL: " + record);
        }
        return "%-100s".formatted(record);
    }
}
