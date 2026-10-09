package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/** A non-wrapping WGS84 viewport, in longitude/latitude order. */
public record MapBounds(double west, double south, double east, double north) {

    public MapBounds {
        if (!Double.isFinite(west) || !Double.isFinite(south) || !Double.isFinite(east)
                || !Double.isFinite(north) || west < -180 || east > 180 || south < -90 || north > 90
                || west >= east || south >= north) {
            throw new IllegalArgumentException("map bounds require -180 <= west < east <= 180 and -90 <= south < north <= 90");
        }
    }
}
