package com.policyhub.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.policyhub.common.BusinessRuleException;
import com.policyhub.integration.ThirdPartyUnavailableException;
import com.policyhub.payment.PaymentGateway;
import com.policyhub.payment.PaymentRequest;
import com.policyhub.payment.PaymentResult;
import com.policyhub.premium.ProductType;
import com.policyhub.quote.Quote;

@ExtendWith(MockitoExtension.class)
class PolicyIssuanceServiceTest {

    @Mock
    private PolicyService policyService;
    @Mock
    private PaymentGateway paymentGateway;
    @InjectMocks
    private PolicyIssuanceService issuanceService;

    @Test
    void chargesPremiumWithQuoteBasedIdempotencyKeyThenIssues() {
        when(policyService.prepareIssuance("Q-1")).thenReturn(quote());
        when(paymentGateway.charge(any())).thenReturn(new PaymentResult.Approved("PAY-42"));
        PolicyResponse issued = new PolicyResponse("P-1", ProductType.KASKO, "34ABC123", new BigDecimal("25000.00"),
                "TRY", LocalDate.of(2026, 10, 21), LocalDate.of(2027, 10, 21), PolicyStatus.ACTIVE, "PAY-42");
        when(policyService.issueFromQuote("Q-1", "PAY-42")).thenReturn(issued);

        assertThat(issuanceService.issue("Q-1", "tok_visa")).isSameAs(issued);

        var request = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentGateway).charge(request.capture());
        assertThat(request.getValue().idempotencyKey()).isEqualTo("policy-issue-Q-1");
        assertThat(request.getValue().amount()).isEqualByComparingTo("25000.00");
        assertThat(request.getValue().paymentToken()).isEqualTo("tok_visa");
    }

    @Test
    void declinedPaymentIssuesNothing() {
        when(policyService.prepareIssuance("Q-1")).thenReturn(quote());
        when(paymentGateway.charge(any())).thenReturn(new PaymentResult.Declined("insufficient funds"));

        assertThatThrownBy(() -> issuanceService.issue("Q-1", "tok_visa"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Payment declined: insufficient funds");
        verify(policyService, never()).issueFromQuote(anyString(), anyString());
    }

    @Test
    void unavailableGatewayIssuesNothing() {
        when(policyService.prepareIssuance("Q-1")).thenReturn(quote());
        when(paymentGateway.charge(any())).thenThrow(new ThirdPartyUnavailableException("payment-gateway", "HTTP 503", null));

        assertThatThrownBy(() -> issuanceService.issue("Q-1", "tok_visa"))
                .isInstanceOf(ThirdPartyUnavailableException.class);
        verify(policyService, never()).issueFromQuote(anyString(), anyString());
    }

    @Test
    void neverChargesForAQuoteThatCannotBeIssued() {
        when(policyService.prepareIssuance("Q-1")).thenThrow(new BusinessRuleException("Quote Q-1 expired"));

        assertThatThrownBy(() -> issuanceService.issue("Q-1", "tok_visa")).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(paymentGateway);
    }

    private static Quote quote() {
        return new Quote("Q-1", ProductType.KASKO, "34ABC123", 2024, new BigDecimal("1000000.00"), 35, 0,
                new BigDecimal("25000.00"), LocalDate.of(2026, 11, 5), LocalDateTime.of(2026, 10, 21, 9, 0));
    }
}
