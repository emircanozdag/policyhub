package com.policyhub.vehicle;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.policyhub.common.NotFoundException;
import com.policyhub.common.PlateNumbers;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private final VehicleRepository vehicleRepository;

    public VehicleController(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @GetMapping("/{plateNumber}")
    public Vehicle get(@PathVariable String plateNumber) {
        String plate = PlateNumbers.normalize(plateNumber);
        return vehicleRepository.findByPlateNumber(plate)
                .orElseThrow(() -> new NotFoundException("Vehicle not found: " + plate));
    }
}
