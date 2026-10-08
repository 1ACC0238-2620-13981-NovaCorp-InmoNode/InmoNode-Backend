package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PortfolioProject;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.FieldPortfolioResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.FieldPortfolioResource.PortfolioProjectResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.FinancingRulesResource;

import java.util.List;

public final class FieldPortfolioResourceAssembler {

    private FieldPortfolioResourceAssembler() {}

    public static FieldPortfolioResource toResourceFromPortfolio(List<PortfolioProject> portfolio) {
        return new FieldPortfolioResource(portfolio.stream().map(FieldPortfolioResourceAssembler::toProject).toList());
    }

    private static PortfolioProjectResource toProject(PortfolioProject entry) {
        var project = entry.project();
        var coordinates = project.getCoordinates();
        var rules = project.getFinancingRules();
        return new PortfolioProjectResource(project.getId(), project.getName(), project.getLocation(),
                coordinates == null ? null : coordinates.latitude(),
                coordinates == null ? null : coordinates.longitude(),
                project.getCoverImageUrl(),
                new FinancingRulesResource(rules.minDownPaymentPercentage(), rules.annualInterestRate(),
                        rules.maxTermMonths(), rules.lateFeeRate()),
                LotFeatureCollectionResourceAssembler.toResourceFromLots(entry.lots()));
    }
}
