package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * Loads the lots of a project plan (US-53, Scenario 2). Each lot arrives raw, as read from the plan,
 * so that an invalid one is rejected on its own without discarding the rest.
 */
public record ImportLotsCommand(Long projectId, List<LotData> lots) {

    /**
     * @param polygon rings of the lot polygon (outer ring first), {@code [longitude, latitude]} positions;
     *                {@code null} when the plan feature has no Polygon geometry
     */
    public record LotData(@Nullable String code, @Nullable BigDecimal area, @Nullable BigDecimal front,
                          @Nullable BigDecimal depth, @Nullable BigDecimal price,
                          @Nullable List<List<List<Double>>> polygon) {
    }
}
