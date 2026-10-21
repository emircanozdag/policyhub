package com.policyhub.batch.vehicleimport;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.batch.infrastructure.item.ItemProcessor;

import com.policyhub.common.PlateNumbers;
import com.policyhub.vehicle.Vehicle;
import com.policyhub.vehicle.VehicleStatus;

/**
 * Turns detail records into {@link Vehicle}s and filters out header/trailer records (already checked
 * by {@link VehicleFileValidator}). Stateless on purpose: fault-tolerant steps may process an item
 * more than once after a rollback.
 */
public class VehicleRecordProcessor implements ItemProcessor<VehicleFileRecord, Vehicle> {

    private static final int VIN_LENGTH = 17;
    private static final int OLDEST_MODEL_YEAR = 1950;

    private final LocalDate fileDate;

    public VehicleRecordProcessor(LocalDate fileDate) {
        this.fileDate = fileDate;
    }

    @Override
    public Vehicle process(VehicleFileRecord item) {
        return switch (item) {
            case VehicleFileRecord.Header header -> null;
            case VehicleFileRecord.Trailer trailer -> null;
            case VehicleFileRecord.Detail detail -> toVehicle(detail);
        };
    }

    private Vehicle toVehicle(VehicleFileRecord.Detail detail) {
        if (!PlateNumbers.isValid(detail.plateNumber())) {
            throw invalid(detail, "invalid plate number");
        }
        if (detail.chassisNumber().length() != VIN_LENGTH) {
            throw invalid(detail, "chassis number must be " + VIN_LENGTH + " characters");
        }
        if (detail.modelYear() < OLDEST_MODEL_YEAR || detail.modelYear() > fileDate.getYear() + 1) {
            throw invalid(detail, "implausible model year " + detail.modelYear());
        }
        if (detail.kaskoValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw invalid(detail, "kasko value must be positive");
        }
        return new Vehicle(
                PlateNumbers.normalize(detail.plateNumber()),
                detail.chassisNumber(),
                detail.brand(),
                detail.model(),
                detail.modelYear(),
                detail.kaskoValue(),
                status(detail),
                fileDate);
    }

    private static VehicleStatus status(VehicleFileRecord.Detail detail) {
        return switch (detail.statusCode()) {
            case "A" -> VehicleStatus.ACTIVE;
            case "S" -> VehicleStatus.SCRAPPED;
            default -> throw invalid(detail, "unknown status code '" + detail.statusCode() + "'");
        };
    }

    private static InvalidVehicleRecordException invalid(VehicleFileRecord.Detail detail, String reason) {
        return new InvalidVehicleRecordException("Plate '%s': %s".formatted(detail.plateNumber(), reason));
    }
}
