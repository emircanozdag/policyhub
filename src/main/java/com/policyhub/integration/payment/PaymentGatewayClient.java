package com.policyhub.integration.payment;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.policyhub.integration.ThirdPartyIntegrationException;
import com.policyhub.integration.ThirdPartyUnavailableException;
import com.policyhub.payment.PaymentGateway;
import com.policyhub.payment.PaymentRequest;
import com.policyhub.payment.PaymentResult;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Adapter for the virtual POS provider. Retries are safe only because every attempt carries the same
 * {@code Idempotency-Key}. There is deliberately no fallback: a premium is either collected or the
 * policy is not issued.
 */
@Component
public class PaymentGatewayClient implements PaymentGateway {

    static final String SYSTEM = "payment-gateway";

    private final RestClient restClient;

    public PaymentGatewayClient(@Qualifier("paymentGatewayRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    @Retry(name = SYSTEM)
    @CircuitBreaker(name = SYSTEM)
    public PaymentResult charge(PaymentRequest request) {
        try {
            return restClient.post()
                    .uri("/v1/payments")
                    .header("Idempotency-Key", request.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ChargeRequest(request.amount(), request.currency(), request.paymentToken(),
                            request.description()))
                    .exchange((httpRequest, response) -> {
                        int status = response.getStatusCode().value();
                        if (response.getStatusCode().is5xxServerError() || status == 429) {
                            throw new ThirdPartyUnavailableException(SYSTEM, "HTTP " + status, null);
                        }
                        if (response.getStatusCode().isError()) {
                            throw new ThirdPartyIntegrationException(SYSTEM, "unexpected HTTP " + status);
                        }
                        return toResult(response.bodyTo(ChargeResponse.class));
                    });
        } catch (ResourceAccessException e) {
            throw new ThirdPartyUnavailableException(SYSTEM, e.getMessage(), e);
        }
    }

    private static PaymentResult toResult(ChargeResponse body) {
        if (body == null || body.status() == null) {
            throw new ThirdPartyIntegrationException(SYSTEM, "empty response body");
        }
        return switch (body.status()) {
            case "APPROVED" -> new PaymentResult.Approved(body.paymentId());
            case "DECLINED" -> new PaymentResult.Declined(body.declineReason());
            default -> throw new ThirdPartyIntegrationException(SYSTEM, "unknown payment status " + body.status());
        };
    }

    record ChargeRequest(BigDecimal amount, String currency, String paymentToken, String description) {
    }

    record ChargeResponse(String paymentId, String status, String declineReason) {
    }
}
