package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Project plan as a GeoJSON FeatureCollection: one Polygon feature per lot (RFC 7946, WGS84).
 */
public record GeoJsonFeatureCollectionResource(
        @NotNull @Pattern(regexp = "FeatureCollection") String type,
        @NotNull @Size(min = 1, max = 5000) List<GeoJsonFeatureResource> features) {
}
