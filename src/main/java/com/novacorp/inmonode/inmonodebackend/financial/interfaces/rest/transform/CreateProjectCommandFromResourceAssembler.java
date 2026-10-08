package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.CreateProjectCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.GeoPoint;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.CreateProjectResource;

public final class CreateProjectCommandFromResourceAssembler {

    private CreateProjectCommandFromResourceAssembler() {}

    /**
     * @throws IllegalArgumentException when only one coordinate is sent; the global handler answers 400
     */
    public static CreateProjectCommand toCommandFromResource(CreateProjectResource resource) {
        if ((resource.latitude() == null) != (resource.longitude() == null)) {
            throw new IllegalArgumentException("latitude and longitude must be sent together");
        }
        var coordinates = resource.latitude() == null
                ? null
                : new GeoPoint(resource.latitude(), resource.longitude());
        var rules = resource.financingRules();
        var financingRules = new FinancingRules(rules.minDownPaymentPercentage(), rules.annualInterestRate(),
                rules.maxTermMonths(), rules.lateFeeRate());
        return new CreateProjectCommand(resource.name(), resource.location(), coordinates,
                resource.coverImageUrl(), financingRules);
    }
}
