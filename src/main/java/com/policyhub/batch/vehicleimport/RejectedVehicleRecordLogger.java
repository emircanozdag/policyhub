package com.policyhub.batch.vehicleimport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;

import com.policyhub.vehicle.Vehicle;

/** Logs every skipped record with enough context for operations to correct it at the source. */
class RejectedVehicleRecordLogger implements SkipListener<VehicleFileRecord, Vehicle> {

    private static final Logger log = LoggerFactory.getLogger(RejectedVehicleRecordLogger.class);

    @Override
    public void onSkipInRead(Throwable t) {
        if (t instanceof FlatFileParseException e) {
            log.warn("Rejected vehicle record: line={} reason=\"{}\" input=\"{}\"",
                    e.getLineNumber(), rootMessage(e), e.getInput());
        } else {
            log.warn("Rejected vehicle record: reason=\"{}\"", rootMessage(t));
        }
    }

    @Override
    public void onSkipInProcess(VehicleFileRecord item, Throwable t) {
        log.warn("Rejected vehicle record: reason=\"{}\" record={}", t.getMessage(), item);
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
