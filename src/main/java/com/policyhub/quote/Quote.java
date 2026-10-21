package com.policyhub.quote;

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
@Table(name = "quote")
public class Quote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String quoteNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductType productType;

    @Column(nullable = false, length = 16)
    private String plateNumber;

    @Column(nullable = false)
    private int vehicleModelYear;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal vehicleValue;

    @Column(nullable = false)
    private int driverAge;

    @Column(name = "claim_count", nullable = false)
    private int claimCount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal premium;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuoteStatus status;

    @Column(nullable = false)
    private LocalDate validUntil;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Quote() {
        // for JPA
    }

    public Quote(String quoteNumber, ProductType productType, String plateNumber, int vehicleModelYear,
                 BigDecimal vehicleValue, int driverAge, int claimCount, BigDecimal premium,
                 LocalDate validUntil, LocalDateTime createdAt) {
        this.quoteNumber = quoteNumber;
        this.productType = productType;
        this.plateNumber = plateNumber;
        this.vehicleModelYear = vehicleModelYear;
        this.vehicleValue = vehicleValue;
        this.driverAge = driverAge;
        this.claimCount = claimCount;
        this.premium = premium;
        this.validUntil = validUntil;
        this.createdAt = createdAt;
        this.status = QuoteStatus.ACTIVE;
    }

    /** Marks the quote as converted into a policy. A quote can be converted only once, before it expires. */
    public void convert(LocalDate today) {
        if (status != QuoteStatus.ACTIVE) {
            throw new BusinessRuleException("Quote %s is %s and cannot be converted".formatted(quoteNumber, status));
        }
        if (today.isAfter(validUntil)) {
            throw new BusinessRuleException("Quote %s expired on %s".formatted(quoteNumber, validUntil));
        }
        this.status = QuoteStatus.CONVERTED;
    }

    public Long getId() {
        return id;
    }

    public String getQuoteNumber() {
        return quoteNumber;
    }

    public ProductType getProductType() {
        return productType;
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public int getVehicleModelYear() {
        return vehicleModelYear;
    }

    public BigDecimal getVehicleValue() {
        return vehicleValue;
    }

    public int getDriverAge() {
        return driverAge;
    }

    public int getClaimCount() {
        return claimCount;
    }

    public BigDecimal getPremium() {
        return premium;
    }

    public QuoteStatus getStatus() {
        return status;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
