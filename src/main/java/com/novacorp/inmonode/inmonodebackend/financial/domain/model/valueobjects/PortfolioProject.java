package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;

import java.util.List;

/**
 * A project of the field agent portfolio with all its lots, ordered by code.
 */
public record PortfolioProject(Project project, List<Lot> lots) {

    public PortfolioProject {
        lots = List.copyOf(lots);
    }
}
