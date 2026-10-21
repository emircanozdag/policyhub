package com.policyhub.common;

/** A request that is well-formed but violates a business rule (e.g. issuing an expired quote). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
