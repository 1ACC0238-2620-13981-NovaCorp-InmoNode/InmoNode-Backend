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
    @jakarta.persistence.ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.CollectionTable(name = "project_stages", schema = "financial_document_control",
            joinColumns = @jakarta.persistence.JoinColumn(name = "project_id"))
    @jakarta.persistence.OrderColumn(name = "stage_order")
    @Column(name = "stage_name", nullable = false, length = 80)
    private java.util.List<String> stages = new java.util.ArrayList<>();


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

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal annualInterestRate;

    @Column(nullable = false)
    private int maxTermMonths;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal lateFeeRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status;
}
