package com.policyhub.vehicle;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A vehicle and its current kasko value, as published by the vehicle registry extract. */
public record Vehicle(
        String plateNumber,
        String chassisNumber,
        String brand,
        String model,
        int modelYear,
        BigDecimal kaskoValue,
        VehicleStatus status,
        LocalDate sourceFileDate) {
}
