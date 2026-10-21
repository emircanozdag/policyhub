package com.policyhub.vehicle;

public enum VehicleStatus {
    ACTIVE,
    /** Deregistered ("hurda"); such vehicles must not be renewed. */
    SCRAPPED
}
