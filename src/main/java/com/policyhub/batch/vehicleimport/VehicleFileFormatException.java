package com.policyhub.batch.vehicleimport;

/** The file's structure or control totals are wrong; the whole file is rejected. Never skipped. */
public class VehicleFileFormatException extends RuntimeException {

    public VehicleFileFormatException(String message) {
        super(message);
    }
}
