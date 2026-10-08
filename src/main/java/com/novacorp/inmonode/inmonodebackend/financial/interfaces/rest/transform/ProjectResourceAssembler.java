package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.FinancingRulesResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ProjectResource;

public final class ProjectResourceAssembler {

    private ProjectResourceAssembler() {}

    public static ProjectResource toResourceFromEntity(Project project) {
        var coordinates = project.getCoordinates();
        var rules = project.getFinancingRules();
        return new ProjectResource(project.getId(), project.getName(), project.getLocation(),
                coordinates == null ? null : coordinates.latitude(),
                coordinates == null ? null : coordinates.longitude(),
                project.getCoverImageUrl(),
                new FinancingRulesResource(rules.minDownPaymentPercentage(), rules.annualInterestRate(),
                        rules.maxTermMonths(), rules.lateFeeRate()),
                project.getStatus().name());
    }
}
