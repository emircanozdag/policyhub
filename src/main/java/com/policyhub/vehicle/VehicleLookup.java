package com.policyhub.vehicle;

public record VehicleLookup(
        String plateNumber,
        String chassisNumber,
        String brand,
        String model,
        int modelYear,
        VehicleStatus status,
        Source source) {

    public enum Source {
        /** Live answer from the registry API. */
        REGISTRY_API,
        /** Registry unreachable; answered from last night's imported copy (may be up to a day old). */
        LOCAL_COPY
    }

    public static VehicleLookup fromLocalCopy(Vehicle vehicle) {
        return new VehicleLookup(vehicle.plateNumber(), vehicle.chassisNumber(), vehicle.brand(), vehicle.model(),
                vehicle.modelYear(), vehicle.status(), Source.LOCAL_COPY);
    }
}
