package com.policyhub.vehicle;

import java.util.Optional;

/** Port: answers "what does the official registry know about this plate?". */
public interface VehicleInquiry {

    /** @return the vehicle, or empty when no source knows the plate */
    Optional<VehicleLookup> lookup(String plateNumber);
}
