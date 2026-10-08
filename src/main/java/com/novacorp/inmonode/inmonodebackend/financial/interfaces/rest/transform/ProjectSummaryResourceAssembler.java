package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectSummary;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.PriceRangeResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ProjectSummaryResource;

public final class ProjectSummaryResourceAssembler {

    private ProjectSummaryResourceAssembler() {}

    public static ProjectSummaryResource toResourceFromSummary(ProjectSummary summary) {
        var project = summary.project();
        var statistics = summary.lotStatistics();
        var coordinates = project.getCoordinates();
        var priceRange = statistics.minPrice() == null || statistics.maxPrice() == null
                ? null
                : new PriceRangeResource(statistics.minPrice().amount(), statistics.maxPrice().amount(),
                        statistics.minPrice().currency());
        return new ProjectSummaryResource(project.getId(), project.getName(), project.getLocation(),
                coordinates == null ? null : coordinates.latitude(),
                coordinates == null ? null : coordinates.longitude(),
                project.getCoverImageUrl(), priceRange, statistics.totalLots(),
                statistics.availabilityPercentage(), statistics.isSoldOut());
    }
}
