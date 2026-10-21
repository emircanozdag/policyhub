package com.policyhub;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.testcontainers.oracle.OracleContainer;

/**
 * Base class for integration tests. One Oracle container is started per JVM and shared by every IT
 * class (and the cached Spring context), because Oracle takes minutes to boot. Tests therefore use
 * their own plate numbers and dates instead of relying on an empty database.
 */
@SpringBootTest(properties = "policyhub.batch.lifecycle.schedule-enabled=false")
@AutoConfigureMockMvc
public abstract class AbstractOracleIT {

    @ServiceConnection
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    static {
        ORACLE.start();
    }
}
