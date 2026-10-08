package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/**
 * WGS84 position used to place a project on the map of the portal.
 */
public record GeoPoint(double latitude, double longitude) {

    public GeoPoint {
        if (!(latitude >= -90 && latitude <= 90)) {
            throw new IllegalArgumentException("latitude must be between -90 and 90");
        }
        if (!(longitude >= -180 && longitude <= 180)) {
            throw new IllegalArgumentException("longitude must be between -180 and 180");
        }
    }
}
