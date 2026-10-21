package com.policyhub.quote;

import java.math.BigDecimal;

import com.policyhub.common.PlateNumbers;
import com.policyhub.premium.ProductType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateQuoteRequest(
        @NotNull ProductType productType,
        @NotBlank @Pattern(regexp = PlateNumbers.REGEX, flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "must be a valid Turkish plate number, e.g. 34 ABC 123") String plateNumber,
        @Min(1980) int vehicleModelYear,
        @NotNull @DecimalMin("10000.00") BigDecimal vehicleValue,
        @Min(18) @Max(99) int driverAge,
        @PositiveOrZero @Max(20) int claimCountLast3Years) {
}
