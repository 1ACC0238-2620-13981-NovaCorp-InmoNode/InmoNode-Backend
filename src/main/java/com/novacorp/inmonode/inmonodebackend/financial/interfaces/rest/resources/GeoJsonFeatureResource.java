package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

/**
 * One lot of the plan. Not validated here: a feature with missing or invalid data rejects only that lot.
 */
public record GeoJsonFeatureResource(String type, GeoJsonGeometryResource geometry,
                                     LotFeaturePropertiesResource properties) {
}
