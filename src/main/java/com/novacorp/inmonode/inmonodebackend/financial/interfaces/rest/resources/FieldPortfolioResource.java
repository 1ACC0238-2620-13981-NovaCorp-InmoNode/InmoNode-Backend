package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.util.List;

/**
 * Catalog downloaded by the field app (US-02). It carries no timestamp on purpose: identical content gives
 * an identical ETag, so an unchanged catalog is answered with 304 Not Modified (US-39).
 */
public record FieldPortfolioResource(List<PortfolioProjectResource> projects) {

    /** A project with its financing rules and its lots as GeoJSON, as stored on the device. */
    public record PortfolioProjectResource(Long id, String name, String location, Double latitude, Double longitude,
                                           String coverImageUrl, FinancingRulesResource financingRules,
                                           LotFeatureCollectionResource lots) {
    }
}
