package com.policyhub.batch.vehicleimport;

/** A single detail record fails business validation; the record is skipped and logged. */
public class InvalidVehicleRecordException extends RuntimeException {

    public InvalidVehicleRecordException(String message) {
        super(message);
    }
}
