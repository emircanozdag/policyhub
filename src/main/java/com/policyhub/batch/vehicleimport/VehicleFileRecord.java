package com.policyhub.batch.vehicleimport;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One 100-byte record of the VEHREC mainframe extract (see {@code docs/mainframe/VEHREC.cpy}). */
public sealed interface VehicleFileRecord {

    /** VEHREC-HEADER: business date of the extract and the producing system. */
    record Header(LocalDate fileDate, String sourceSystem) implements VehicleFileRecord {
    }

    /** VEHREC-DETAIL as found in the file; business validation happens in the processor. */
    record Detail(String plateNumber, String chassisNumber, String brand, String model, int modelYear,
                  BigDecimal kaskoValue, String statusCode) implements VehicleFileRecord {
    }

    /** VEHREC-TRAILER: control total, the number of detail records in the file. */
    record Trailer(long recordCount) implements VehicleFileRecord {
    }
}
