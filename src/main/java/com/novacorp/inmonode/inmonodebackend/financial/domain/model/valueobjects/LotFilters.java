package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

/** Inclusive ranges for the public lot map (US-16). Missing values leave that dimension unrestricted. */
public record LotFilters(@Nullable BigDecimal minArea, @Nullable BigDecimal maxArea,
                         @Nullable BigDecimal minPrice, @Nullable BigDecimal maxPrice,
                         @Nullable LotStatus status, @Nullable MapBounds bounds) {

    public static final LotFilters NONE = new LotFilters(null, null, null, null, null, null);

    public LotFilters {
        validateRange("area", minArea, maxArea);
        validateRange("price", minPrice, maxPrice);
    }

    private static void validateRange(String name, @Nullable BigDecimal min, @Nullable BigDecimal max) {
        if ((min != null && min.signum() < 0) || (max != null && max.signum() < 0)) {
            throw new IllegalArgumentException(name + " bounds cannot be negative");
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("minimum " + name + " cannot exceed maximum " + name);
        }
    }
}
