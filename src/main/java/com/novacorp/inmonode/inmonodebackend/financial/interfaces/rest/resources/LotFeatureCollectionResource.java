package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lots of a project as a GeoJSON FeatureCollection (RFC 7946), ready to draw on the interactive map.
 */
public record LotFeatureCollectionResource(String type, List<LotFeatureResource> features) {

    /** One lot: its Polygon on the plan and its commercial data. */
    public record LotFeatureResource(String type, Long id, GeoJsonGeometryResource geometry,
                                     LotPropertiesResource properties) {
    }

    /**
     * @param status AVAILABLE, BLOCKED, PENDING_VERIFICATION, RESERVED or SOLD
     */
    public record LotPropertiesResource(String code, BigDecimal area, BigDecimal front, BigDecimal depth,
                                        BigDecimal price, String currency, String status) {
    }
}
