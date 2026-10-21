package com.policyhub.common;

import java.util.Locale;
import java.util.regex.Pattern;

/** Turkish licence plate format: province code (01-81), 1-3 letters, 2-4 digits, e.g. {@code 34 ABC 123}. */
public final class PlateNumbers {

    public static final String REGEX = "^(0[1-9]|[1-7][0-9]|8[01])\\s?[A-Z]{1,3}\\s?\\d{2,4}$";

    private static final Pattern PATTERN = Pattern.compile(REGEX);

    private PlateNumbers() {
    }

    /** Canonical form used as a key everywhere: upper case, no spaces ({@code 34ABC123}). */
    public static String normalize(String plate) {
        return plate.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String plate) {
        return plate != null && PATTERN.matcher(plate.toUpperCase(Locale.ROOT)).matches();
    }
}
