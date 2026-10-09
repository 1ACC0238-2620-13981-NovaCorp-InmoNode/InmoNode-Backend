package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Inventory figures of a project shown in the catalog (US-15): price range and availability.
 *
 * @param minPrice {@code null} when the project has no lots
 * @param maxPrice {@code null} when the project has no lots
 */
public record LotStatistics(long totalLots, long availableLots, long soldLots,
                            @Nullable Money minPrice, @Nullable Money maxPrice) {

    public static final LotStatistics EMPTY = new LotStatistics(0, 0, 0, null, null);

    /** Share of lots still available, as a percentage with two decimals; 0 without lots. */
    public BigDecimal availabilityPercentage() {
        if (totalLots == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(availableLots * 100)
                .divide(BigDecimal.valueOf(totalLots), 2, RoundingMode.HALF_UP);
    }

    /** US-15, Scenario 2: every lot of the project is sold. */
    public boolean isSoldOut() {
        return totalLots > 0 && soldLots == totalLots;
    }
}
