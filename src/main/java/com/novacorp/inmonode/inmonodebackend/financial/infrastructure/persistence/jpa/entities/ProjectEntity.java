package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectStatus;
import com.novacorp.inmonode.inmonodebackend.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "projects", schema = "financial_document_control")
public class ProjectEntity extends AuditableAbstractPersistenceEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private String location;

    private Double latitude;

    private Double longitude;

    @Column(length = 500)
    private String coverImageUrl;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal minDownPaymentPercentage;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal annualInterestRate;

    @Column(nullable = false)
    private int maxTermMonths;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal lateFeeRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status;
}
