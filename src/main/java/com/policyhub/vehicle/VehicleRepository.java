package com.policyhub.vehicle;

import java.util.Optional;

import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Read side of the vehicle registry. Writes happen only through the vehicle import batch job. */
@Repository
public class VehicleRepository {

    private final JdbcClient jdbc;

    public VehicleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Vehicle> findByPlateNumber(String plateNumber) {
        return jdbc.sql("""
                        SELECT plate_number, chassis_number, brand, model, model_year, kasko_value, status, source_file_date
                        FROM vehicle WHERE plate_number = :plate""")
                .param("plate", plateNumber)
                .query(new DataClassRowMapper<>(Vehicle.class))
                .optional();
    }
}
