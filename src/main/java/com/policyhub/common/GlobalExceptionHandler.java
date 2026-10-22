package com.policyhub.common;

import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.policyhub.integration.ThirdPartyIntegrationException;
import com.policyhub.integration.ThirdPartyUnavailableException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

/** Maps domain exceptions to RFC 9457 problem responses; validation errors are handled by the base class. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Matches the circuit breakers' open-state wait, so well-behaved clients come back when it may have closed. */
    private static final String RETRY_AFTER_SECONDS = "30";

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    }

    /** The same file / business date was already processed, or is being processed right now. */
    @ExceptionHandler({JobInstanceAlreadyCompleteException.class, JobExecutionAlreadyRunningException.class})
    ProblemDetail handleJobAlreadyRun(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** A partner is down (retries exhausted) or its circuit is open. Transient: tell the client when to retry. */
    @ExceptionHandler({ThirdPartyUnavailableException.class, CallNotPermittedException.class})
    ResponseEntity<ProblemDetail> handleThirdPartyUnavailable(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "A third-party service is temporarily unavailable. Retrying the request later is safe.");
        problem.setProperty("cause", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(problem);
    }

    @ExceptionHandler(ThirdPartyIntegrationException.class)
    ProblemDetail handleThirdPartyIntegration(ThirdPartyIntegrationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }
}
