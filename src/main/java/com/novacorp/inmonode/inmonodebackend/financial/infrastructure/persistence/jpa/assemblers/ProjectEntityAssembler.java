package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.assemblers;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.GeoPoint;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.ProjectEntity;

/**
 * Translates between the {@link Project} aggregate and its JPA persistence entity.
 */
public final class ProjectEntityAssembler {

    private ProjectEntityAssembler() {}

    public static Project toDomain(ProjectEntity entity) {
        var coordinates = entity.getLatitude() == null || entity.getLongitude() == null
                ? null
                : new GeoPoint(entity.getLatitude(), entity.getLongitude());
        var financingRules = new FinancingRules(entity.getMinDownPaymentPercentage(),
                entity.getAnnualInterestRate(), entity.getMaxTermMonths(), entity.getLateFeeRate());
        return Project.withStages(Project.restore(entity.getId(), entity.getName(), entity.getLocation(), coordinates,
                entity.getCoverImageUrl(), financingRules, entity.getStatus()), entity.getStages());
    }

    /** Copies the aggregate state onto the entity; audit columns and id stay untouched. */
    public static ProjectEntity copyToEntity(Project project, ProjectEntity entity) {
        var coordinates = project.getCoordinates();
        var financingRules = project.getFinancingRules();
        entity.setName(project.getName());
        entity.setLocation(project.getLocation());
        entity.setLatitude(coordinates == null ? null : coordinates.latitude());
        entity.setLongitude(coordinates == null ? null : coordinates.longitude());
        entity.setCoverImageUrl(project.getCoverImageUrl());
        entity.setMinDownPaymentPercentage(financingRules.minDownPaymentPercentage());
        entity.setAnnualInterestRate(financingRules.annualInterestRate());
        entity.setMaxTermMonths(financingRules.maxTermMonths());
        entity.setLateFeeRate(financingRules.lateFeeRate());
        entity.setStatus(project.getStatus());
        if (!entity.getStages().equals(project.getStages())) {
            entity.getStages().clear();
            entity.getStages().addAll(project.getStages());
        }
        return entity;
    }
}
