package com.policyhub.policy;

import org.springframework.stereotype.Service;

import com.policyhub.common.BusinessRuleException;
import com.policyhub.payment.PaymentGateway;
import com.policyhub.payment.PaymentRequest;
import com.policyhub.payment.PaymentResult;
import com.policyhub.quote.Quote;

/**
 * Issues a policy against payment: validate, charge, then convert and persist.
 * <p>
 * The charge runs outside any database transaction, so a slow payment provider never holds a
 * connection or row locks. The idempotency key is derived from the quote, so if the final transaction
 * fails (or two requests race for the same quote), a repeated request is answered with the original
 * payment instead of charging the customer a second time.
 */
@Service
public class PolicyIssuanceService {

    private final PolicyService policyService;
    private final PaymentGateway paymentGateway;

    public PolicyIssuanceService(PolicyService policyService, PaymentGateway paymentGateway) {
        this.policyService = policyService;
        this.paymentGateway = paymentGateway;
    }

    public PolicyResponse issue(String quoteNumber, String paymentToken) {
        Quote quote = policyService.prepareIssuance(quoteNumber);

        PaymentResult result = paymentGateway.charge(new PaymentRequest(
                idempotencyKey(quoteNumber),
                quote.getPremium(),
                "TRY",
                paymentToken,
                "%s premium, quote %s, plate %s".formatted(quote.getProductType(), quoteNumber, quote.getPlateNumber())));

        return switch (result) {
            case PaymentResult.Declined declined ->
                    throw new BusinessRuleException("Payment declined: " + declined.reason());
            case PaymentResult.Approved approved -> policyService.issueFromQuote(quoteNumber, approved.paymentId());
        };
    }

    static String idempotencyKey(String quoteNumber) {
        return "policy-issue-" + quoteNumber;
    }
}
