package com.policyhub.batch.vehicleimport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Checks the file's envelope before a single record is loaded: exactly one header first, exactly one
 * trailer last, known record types only, and a trailer count matching the detail records. A file that
 * was truncated in transfer is therefore rejected as a whole instead of being half imported.
 */
public class VehicleFileValidator {

    public record ControlTotals(VehicleFileRecord.Header header, long detailCount) {
    }

    public ControlTotals validate(Path file, Charset charset) {
        try (BufferedReader reader = Files.newBufferedReader(file, charset)) {
            VehicleFileRecord.Header header = null;
            VehicleFileRecord.Trailer trailer = null;
            long details = 0;
            long lineNumber = 0;

            String next;
            while ((next = reader.readLine()) != null) {
                String line = next;
                lineNumber++;
                if (line.isBlank()) {
                    throw new VehicleFileFormatException("Blank record at line " + lineNumber);
                }
                if (line.length() > VehicleRecordLayout.RECORD_LENGTH) {
                    throw new VehicleFileFormatException("Record at line %d is %d bytes, LRECL is %d"
                            .formatted(lineNumber, line.length(), VehicleRecordLayout.RECORD_LENGTH));
                }
                if (trailer != null) {
                    throw new VehicleFileFormatException("Record found after trailer at line " + lineNumber);
                }
                switch (line.charAt(0)) {
                    case VehicleRecordLayout.HEADER -> {
                        if (lineNumber != 1) {
                            throw new VehicleFileFormatException("Header must be the first record, found at line " + lineNumber);
                        }
                        header = parse(() -> VehicleRecordLayout.parseHeader(line), "header", 1);
                    }
                    case VehicleRecordLayout.DETAIL -> details++;
                    case VehicleRecordLayout.TRAILER ->
                            trailer = parseTrailer(line, lineNumber);
                    default -> throw new VehicleFileFormatException(
                            "Unknown record type '%s' at line %d".formatted(line.charAt(0), lineNumber));
                }
            }

            if (header == null) {
                throw new VehicleFileFormatException("Missing header record");
            }
            if (trailer == null) {
                throw new VehicleFileFormatException("Missing trailer record (file may be truncated)");
            }
            if (trailer.recordCount() != details) {
                throw new VehicleFileFormatException("Trailer declares %d detail records but file contains %d"
                        .formatted(trailer.recordCount(), details));
            }
            return new ControlTotals(header, details);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    private static VehicleFileRecord.Trailer parseTrailer(String line, long lineNumber) {
        return parse(() -> VehicleRecordLayout.parseTrailer(line), "trailer", lineNumber);
    }

    private static <T> T parse(java.util.function.Supplier<T> parser, String recordName, long lineNumber) {
        try {
            return parser.get();
        } catch (RuntimeException e) {
            throw new VehicleFileFormatException("Malformed %s at line %d: %s".formatted(recordName, lineNumber, e.getMessage()));
        }
    }
}
