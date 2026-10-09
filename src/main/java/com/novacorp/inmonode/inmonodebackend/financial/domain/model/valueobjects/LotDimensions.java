package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Measures of a lot in meters: the total area is required; front and depth only apply to regular lots.
 */
public record LotDimensions(BigDecimal area, @Nullable BigDecimal front, @Nullable BigDecimal depth) {

    public LotDimensions {
        if (area == null) {
            throw new IllegalArgumentException("area is required");
        }
        area = positive("area", area);
        front = front == null ? null : positive("front", front);
        depth = depth == null ? null : positive("depth", depth);
    }

    private static BigDecimal positive(String name, BigDecimal value) {
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
