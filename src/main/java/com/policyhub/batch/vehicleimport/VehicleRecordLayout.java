package com.policyhub.batch.vehicleimport;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.batch.infrastructure.item.file.mapping.FieldSetMapper;
import org.springframework.batch.infrastructure.item.file.mapping.PatternMatchingCompositeLineMapper;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
import org.springframework.batch.infrastructure.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.infrastructure.item.file.transform.LineTokenizer;
import org.springframework.batch.infrastructure.item.file.transform.Range;

/**
 * Java mirror of the VEHREC copybook. Column ranges are 1-based and inclusive, exactly as they
 * appear when the copybook is laid out byte by byte.
 */
public final class VehicleRecordLayout {

    public static final int RECORD_LENGTH = 100;

    static final char HEADER = 'H';
    static final char DETAIL = 'D';
    static final char TRAILER = 'T';

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    /** PIC 9(11)V99: the last two digits are decimals, no decimal point is stored. */
    private static final int KASKO_VALUE_IMPLIED_DECIMALS = 2;

    private VehicleRecordLayout() {
    }

    public static PatternMatchingCompositeLineMapper<VehicleFileRecord> lineMapper() {
        return new PatternMatchingCompositeLineMapper<>(
                Map.of(
                        HEADER + "*", headerTokenizer(),
                        DETAIL + "*", detailTokenizer(),
                        TRAILER + "*", trailerTokenizer()),
                Map.<String, FieldSetMapper<VehicleFileRecord>>of(
                        HEADER + "*", VehicleRecordLayout::mapHeader,
                        DETAIL + "*", VehicleRecordLayout::mapDetail,
                        TRAILER + "*", VehicleRecordLayout::mapTrailer));
    }

    static VehicleFileRecord.Header parseHeader(String line) {
        return mapHeader(headerTokenizer().tokenize(line));
    }

    static VehicleFileRecord.Trailer parseTrailer(String line) {
        return mapTrailer(trailerTokenizer().tokenize(line));
    }

    private static LineTokenizer headerTokenizer() {
        return tokenizer(
                new String[] {"recordType", "fileDate", "sourceSystem"},
                new Range(1, 1), new Range(2, 9), new Range(10, 17));
    }

    private static LineTokenizer detailTokenizer() {
        return tokenizer(
                new String[] {"recordType", "plateNumber", "chassisNumber", "brand", "model", "modelYear",
                        "kaskoValue", "status"},
                new Range(1, 1), new Range(2, 11), new Range(12, 28), new Range(29, 43), new Range(44, 63),
                new Range(64, 67), new Range(68, 80), new Range(81, 81));
    }

    private static LineTokenizer trailerTokenizer() {
        return tokenizer(new String[] {"recordType", "recordCount"}, new Range(1, 1), new Range(2, 10));
    }

    private static FixedLengthTokenizer tokenizer(String[] names, Range... ranges) {
        var tokenizer = new FixedLengthTokenizer();
        tokenizer.setNames(names);
        tokenizer.setColumns(ranges);
        // Trailing FILLER is often stripped by file transfers; only the mapped columns are required.
        tokenizer.setStrict(false);
        return tokenizer;
    }

    private static VehicleFileRecord.Header mapHeader(FieldSet fields) {
        return new VehicleFileRecord.Header(
                LocalDate.parse(fields.readString("fileDate"), FILE_DATE),
                fields.readString("sourceSystem"));
    }

    private static VehicleFileRecord.Detail mapDetail(FieldSet fields) {
        return new VehicleFileRecord.Detail(
                fields.readString("plateNumber"),
                fields.readString("chassisNumber"),
                fields.readString("brand"),
                fields.readString("model"),
                fields.readInt("modelYear"),
                impliedDecimal(fields.readString("kaskoValue")),
                fields.readString("status"));
    }

    private static VehicleFileRecord.Trailer mapTrailer(FieldSet fields) {
        return new VehicleFileRecord.Trailer(fields.readLong("recordCount"));
    }

    private static BigDecimal impliedDecimal(String digits) {
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
            throw new NumberFormatException("Not an unsigned zoned decimal: '" + digits + "'");
        }
        return new BigDecimal(new BigInteger(digits), KASKO_VALUE_IMPLIED_DECIMALS);
    }
}
