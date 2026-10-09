package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.math.BigDecimal;

/**
 * Data of a lot in the {@code properties} of its plan feature. Measures in meters, price in soles (PEN).
 *
 * @param front optional, for regular lots
 * @param depth optional, for regular lots
 */
public record LotFeaturePropertiesResource(String code, BigDecimal area, BigDecimal front, BigDecimal depth,
                                           BigDecimal price) {
}
