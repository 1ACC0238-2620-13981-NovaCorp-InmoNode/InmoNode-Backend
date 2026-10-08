package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.GeoJsonGeometryResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.LotFeatureCollectionResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.LotFeatureCollectionResource.LotFeatureResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.LotFeatureCollectionResource.LotPropertiesResource;

import java.util.List;

public final class LotFeatureCollectionResourceAssembler {

    private LotFeatureCollectionResourceAssembler() {}

    public static LotFeatureCollectionResource toResourceFromLots(List<Lot> lots) {
        return new LotFeatureCollectionResource("FeatureCollection",
                lots.stream().map(LotFeatureCollectionResourceAssembler::toFeature).toList());
    }

    private static LotFeatureResource toFeature(Lot lot) {
        var dimensions = lot.getDimensions();
        var geometry = new GeoJsonGeometryResource("Polygon", List.of(lot.getBoundary().coordinates()));
        var properties = new LotPropertiesResource(lot.getCode(), dimensions.area(), dimensions.front(),
                dimensions.depth(), lot.getPrice().amount(), lot.getPrice().currency(), lot.getStatus().name());
        return new LotFeatureResource("Feature", lot.getId(), geometry, properties);
    }
}
