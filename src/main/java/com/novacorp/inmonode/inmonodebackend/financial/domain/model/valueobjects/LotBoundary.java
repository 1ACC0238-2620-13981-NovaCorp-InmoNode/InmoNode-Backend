package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

import java.util.Arrays;
import java.util.List;

/**
 * Polygon of a lot on the project plan, in WGS84 {@code [longitude, latitude]} positions.
 *
 * <p>The ring must be closed, have at least three distinct corners and not cross itself
 * (US-53, Scenario 2: a lot with an invalid geometry is rejected). Holes are not allowed.</p>
 */
public final class LotBoundary {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final int MIN_RING_POSITIONS = 4;

    private final Polygon polygon;

    private LotBoundary(Polygon polygon) {
        this.polygon = polygon;
    }

    /**
     * Builds the boundary from the rings of a GeoJSON Polygon: the outer ring first, then holes.
     *
     * @throws IllegalArgumentException when the geometry is missing or invalid; the message says why
     */
    public static LotBoundary fromPolygonRings(List<List<List<Double>>> rings) {
        if (rings == null || rings.isEmpty()) {
            throw new IllegalArgumentException("a Polygon geometry is required");
        }
        if (rings.size() > 1) {
            throw new IllegalArgumentException("the lot boundary cannot have holes");
        }
        var ring = rings.getFirst();
        if (ring == null || ring.size() < MIN_RING_POSITIONS) {
            throw new IllegalArgumentException("the boundary ring needs at least %d positions".formatted(MIN_RING_POSITIONS));
        }
        var coordinates = ring.stream().map(LotBoundary::toCoordinate).toArray(Coordinate[]::new);
        if (!coordinates[0].equals2D(coordinates[coordinates.length - 1])) {
            throw new IllegalArgumentException("the boundary ring must be closed: its last position must repeat the first");
        }
        var polygon = GEOMETRY_FACTORY.createPolygon(coordinates);
        if (!polygon.isValid() || polygon.getArea() == 0) {
            throw new IllegalArgumentException("the boundary polygon crosses itself or has no area");
        }
        return new LotBoundary(polygon);
    }

    /** Rebuilds a boundary already validated and stored as WKT. */
    public static LotBoundary fromWkt(String wkt) {
        try {
            return new LotBoundary((Polygon) new WKTReader(GEOMETRY_FACTORY).read(wkt));
        } catch (ParseException | ClassCastException ex) {
            throw new IllegalStateException("Stored lot boundary is not a valid WKT polygon", ex);
        }
    }

    public String toWkt() {
        return new WKTWriter().write(polygon);
    }

    /** Includes lots whose polygons touch or intersect the requested viewport. */
    public boolean intersects(MapBounds bounds) {
        return polygon.intersects(GEOMETRY_FACTORY.toGeometry(
                new Envelope(bounds.west(), bounds.east(), bounds.south(), bounds.north())));
    }

    /** The closed outer ring as {@code [longitude, latitude]} positions, ready for a GeoJSON Polygon. */
    public List<List<Double>> coordinates() {
        return Arrays.stream(polygon.getExteriorRing().getCoordinates())
                .map(coordinate -> List.of(coordinate.getX(), coordinate.getY()))
                .toList();
    }

    private static Coordinate toCoordinate(List<Double> position) {
        if (position == null || position.size() < 2 || position.get(0) == null || position.get(1) == null) {
            throw new IllegalArgumentException("every position must be [longitude, latitude]");
        }
        double longitude = position.get(0);
        double latitude = position.get(1);
        if (!(longitude >= -180 && longitude <= 180) || !(latitude >= -90 && latitude <= 90)) {
            throw new IllegalArgumentException("positions must be WGS84 [longitude, latitude] within range");
        }
        return new Coordinate(longitude, latitude);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LotBoundary boundary && polygon.equalsExact(boundary.polygon);
    }

    @Override
    public int hashCode() {
        return toWkt().hashCode();
    }

    @Override
    public String toString() {
        return toWkt();
    }
}
