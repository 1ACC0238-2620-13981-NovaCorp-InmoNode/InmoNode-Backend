package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

/**
 * GeoJSON geometry. {@code coordinates} stays untyped because its nesting depends on {@code type}
 * (a Point is {@code [x, y]}, a Polygon {@code [[[x, y], ...]]}); only Polygons describe a lot.
 */
public record GeoJsonGeometryResource(String type, Object coordinates) {
}
