package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.*;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.*;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.*;
import java.util.List;

public final class CatalogResourceAssembler {
    private CatalogResourceAssembler() {}
    public static CreateProjectCommand toCommand(CreateCatalogProjectResource resource) {
        if ((resource.latitude() == null) != (resource.longitude() == null)) throw new IllegalArgumentException("latitude and longitude must be sent together");
        var coordinates = resource.latitude() == null ? null : new GeoPoint(resource.latitude(), resource.longitude());
        var rules = resource.financingRules();
        return new CreateProjectCommand(resource.name(), resource.location(), coordinates, resource.coverImageUrl(),
                new FinancingRules(rules.minDownPaymentPercentage(), rules.annualInterestRate(), rules.maxTermMonths(), rules.lateFeeRate()),
                new ProjectStages(resource.stages()).names());
    }
    public static RegisterLotCommand toCommand(Long projectId, RegisterLotResource resource) {
        return new RegisterLotCommand(projectId, resource.stageName(), resource.code(),
                new LotDimensions(resource.area(), resource.front(), resource.depth()), Money.of(resource.price()),
                LotBoundary.fromPolygonRings(resource.polygon()));
    }
    public static CatalogLotResource toResource(Lot lot) {
        var dimensions = lot.getDimensions();
        return new CatalogLotResource(lot.getId(), lot.getProjectId(), lot.getStageName(), lot.getCode(), lot.getStatus().name(),
                dimensions.area(), dimensions.front(), dimensions.depth(), lot.getPrice().amount(), lot.getPrice().currency(),
                new GeoJsonGeometryResource("Polygon", List.of(lot.getBoundary().coordinates())));
    }
}
