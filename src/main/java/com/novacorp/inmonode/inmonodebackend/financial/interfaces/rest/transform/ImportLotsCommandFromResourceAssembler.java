package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ImportLotsCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ImportLotsCommand.LotData;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.GeoJsonFeatureCollectionResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.GeoJsonFeatureResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.GeoJsonGeometryResource;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Turns the GeoJSON plan into raw lot data. Structural problems of a feature are not thrown here: they reach
 * the domain as missing values, so only that lot is rejected and the reason names what is wrong.
 */
public final class ImportLotsCommandFromResourceAssembler {

    private static final String POLYGON = "Polygon";

    private ImportLotsCommandFromResourceAssembler() {}

    public static ImportLotsCommand toCommandFromResource(Long projectId, GeoJsonFeatureCollectionResource plan) {
        var lots = plan.features().stream().map(ImportLotsCommandFromResourceAssembler::toLotData).toList();
        return new ImportLotsCommand(projectId, lots);
    }

    private static LotData toLotData(@Nullable GeoJsonFeatureResource feature) {
        if (feature == null) {
            return new LotData(null, null, null, null, null, null);
        }
        var properties = feature.properties();
        var polygon = polygonRings(feature.geometry());
        if (properties == null) {
            return new LotData(null, null, null, null, null, polygon);
        }
        return new LotData(properties.code(), properties.area(), properties.front(), properties.depth(),
                properties.price(), polygon);
    }

    /** The rings of a Polygon geometry; {@code null} for any other geometry. Malformed parts become nulls. */
    private static @Nullable List<List<List<Double>>> polygonRings(@Nullable GeoJsonGeometryResource geometry) {
        if (geometry == null || !POLYGON.equals(geometry.type()) || !(geometry.coordinates() instanceof List<?> rings)) {
            return null;
        }
        return rings.stream()
                .map(ring -> ring instanceof List<?> positions
                        ? positions.stream().map(ImportLotsCommandFromResourceAssembler::position).toList()
                        : null)
                .toList();
    }

    private static @Nullable List<Double> position(Object value) {
        if (value instanceof List<?> numbers && numbers.stream().allMatch(Number.class::isInstance)) {
            return numbers.stream().map(number -> ((Number) number).doubleValue()).toList();
        }
        return null;
    }
}
