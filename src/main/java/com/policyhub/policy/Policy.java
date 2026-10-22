package com.policyhub.policy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.policyhub.common.BusinessRuleException;
import com.policyhub.premium.ProductType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "policy")
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String policyNumber;

    @Column(nullable = false)
    private Long quoteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductType productType;

    @Column(nullable = false, length = 16)
    private String plateNumber;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal premium;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** Provider's payment id of the collected premium. */
    @Column(length = 64)
    private String paymentReference;

    protected Policy() {
        // for JPA
    }

    public Policy(String policyNumber, Long quoteId, ProductType productType, String plateNumber,
                  BigDecimal premium, LocalDate startDate, LocalDate endDate, String paymentReference,
                  LocalDateTime createdAt) {
        this.policyNumber = policyNumber;
        this.quoteId = quoteId;
        this.productType = productType;
        this.plateNumber = plateNumber;
        this.premium = premium;
        this.startDate = startDate;
        this.endDate = endDate;
        this.paymentReference = paymentReference;
        this.createdAt = createdAt;
        this.status = PolicyStatus.ACTIVE;
    }

    public void cancel() {
        if (status != PolicyStatus.ACTIVE) {
            throw new BusinessRuleException("Policy %s is %s and cannot be cancelled".formatted(policyNumber, status));
        }
        this.status = PolicyStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public Long getQuoteId() {
        return quoteId;
    }

    public ProductType getProductType() {
        return productType;
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public BigDecimal getPremium() {
        return premium;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public PolicyStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getPaymentReference() {
        return paymentReference;
    }
}
