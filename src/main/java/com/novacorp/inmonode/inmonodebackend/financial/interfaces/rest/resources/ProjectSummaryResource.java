package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.math.BigDecimal;

/**
 * Catalog card of a published project (US-15).
 *
 * @param priceRange             cheapest and most expensive lot; {@code null} when the project has no lots
 * @param availabilityPercentage share of lots still available, two decimals
 * @param soldOut                every lot is sold: the card stays visible with a "Sold Out" label
 */
public record ProjectSummaryResource(Long id, String name, String location, Double latitude, Double longitude,
                                     String coverImageUrl, PriceRangeResource priceRange, long totalLots,
                                     BigDecimal availabilityPercentage, boolean soldOut) {
}
