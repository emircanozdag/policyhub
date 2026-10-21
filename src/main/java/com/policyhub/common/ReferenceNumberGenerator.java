package com.policyhub.common;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

/** Generates human-readable business keys such as {@code Q-20261021-482913}. */
@Component
public class ReferenceNumberGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public ReferenceNumberGenerator(Clock clock) {
        this.clock = clock;
    }

    public String next(String prefix) {
        return "%s-%s-%06d".formatted(prefix, LocalDate.now(clock).format(DATE), random.nextInt(1_000_000));
    }
}
