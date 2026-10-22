package com.policyhub.integration.vehicleregistry;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.policyhub.integration.ThirdPartyIntegrationException;
import com.policyhub.integration.ThirdPartyUnavailableException;
import com.policyhub.vehicle.VehicleInquiry;
import com.policyhub.vehicle.VehicleLookup;
import com.policyhub.vehicle.VehicleRepository;
import com.policyhub.vehicle.VehicleStatus;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Adapter for the national vehicle registry API.
 * <p>
 * Transient failures are retried; repeated failures open the circuit so a struggling registry is not
 * hammered. Either way the caller falls back to the local copy loaded nightly by the vehicle import job,
 * so quoting keeps working when the registry is down. Aspect order: Retry wraps CircuitBreaker, and the
 * fallback sits on Retry so it runs only after the retries are exhausted.
 */
@Component
public class VehicleRegistryClient implements VehicleInquiry {

    static final String SYSTEM = "vehicle-registry";
    private static final Logger log = LoggerFactory.getLogger(VehicleRegistryClient.class);

    private final RestClient restClient;
    private final VehicleRepository localCopy;

    public VehicleRegistryClient(@Qualifier("vehicleRegistryRestClient") RestClient restClient,
                                 VehicleRepository localCopy) {
        this.restClient = restClient;
        this.localCopy = localCopy;
    }

    @Override
    @Retry(name = SYSTEM, fallbackMethod = "lookupInLocalCopy")
    @CircuitBreaker(name = SYSTEM)
    public Optional<VehicleLookup> lookup(String plateNumber) {
        try {
            return restClient.get()
                    .uri("/v1/vehicles/{plate}", plateNumber)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 404) {
                            return Optional.<VehicleLookup>empty();
                        }
                        if (response.getStatusCode().is5xxServerError() || status == 429) {
                            throw new ThirdPartyUnavailableException(SYSTEM, "HTTP " + status, null);
                        }
                        if (response.getStatusCode().isError()) {
                            throw new ThirdPartyIntegrationException(SYSTEM, "unexpected HTTP " + status);
                        }
                        return Optional.of(toLookup(response.bodyTo(RegistryVehicle.class)));
                    });
        } catch (ResourceAccessException e) {
            throw new ThirdPartyUnavailableException(SYSTEM, e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused") // invoked reflectively by Resilience4j
    private Optional<VehicleLookup> lookupInLocalCopy(String plateNumber, Throwable cause) {
        log.warn("Vehicle registry lookup for {} failed, using local copy: {}", plateNumber, cause.toString());
        return localCopy.findByPlateNumber(plateNumber).map(VehicleLookup::fromLocalCopy);
    }

    private static VehicleLookup toLookup(RegistryVehicle body) {
        if (body == null || body.plateNumber() == null) {
            throw new ThirdPartyIntegrationException(SYSTEM, "empty response body");
        }
        VehicleStatus status = switch (String.valueOf(body.status())) {
            case "ACTIVE" -> VehicleStatus.ACTIVE;
            case "SCRAPPED" -> VehicleStatus.SCRAPPED;
            default -> throw new ThirdPartyIntegrationException(SYSTEM, "unknown vehicle status " + body.status());
        };
        return new VehicleLookup(body.plateNumber(), body.chassisNumber(), body.brand(), body.model(),
                body.modelYear(), status, VehicleLookup.Source.REGISTRY_API);
    }

    /** The partner's wire format; kept private to this adapter so it never leaks into the domain. */
    record RegistryVehicle(String plateNumber, String chassisNumber, String brand, String model, int modelYear,
                           String status) {
    }
}
