package com.policyhub.batch;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("policyhub.batch")
public record PolicyHubBatchProperties(VehicleImport vehicleImport, Lifecycle lifecycle) {

    /**
     * @param inboundDir directory the mainframe extract is delivered to (only files here can be imported)
     * @param encoding   character set of the delivered file (text-mode transfer converts EBCDIC on the way)
     * @param chunkSize  records committed per transaction
     * @param skipLimit  malformed detail records tolerated before the whole run fails
     */
    public record VehicleImport(Path inboundDir, String encoding, int chunkSize, int skipLimit) {
    }

    /**
     * @param scheduleEnabled    whether the nightly cron trigger is active
     * @param cron               cron expression (Europe/Istanbul) for the nightly run
     * @param renewalWindowDays  policies ending within this many days get a renewal quote
     * @param chunkSize          policies processed per transaction
     */
    public record Lifecycle(boolean scheduleEnabled, String cron, int renewalWindowDays, int chunkSize) {
    }
}
